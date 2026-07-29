package org.index.patchdownloader.impl.conditions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.interfaces.ICondition;
import org.index.patchdownloader.interfaces.IDummyLogger;
import org.index.patchdownloader.interfaces.ILoadable;
import org.index.patchdownloader.interfaces.IThreadResponse;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.model.linkgenerator.GeneralLinkGenerator;
import org.index.patchdownloader.model.sourcecompare.ISourceVerifier;
import org.index.patchdownloader.model.sourcecompare.SourceVerdict;
import org.index.patchdownloader.util.FileUtils;
import org.index.patchdownloader.util.concurrent.PipelineExecutors;

/**
 * EN: Single start-up comparison handler that replaces the two former conditions (restore + source-compare).
 *     It runs BEFORE the download pipeline and decides, per file, whether it can be skipped (already good on
 *     disk) or reused (copied from a local source folder) instead of downloaded. It is driven by two
 *     {@link ISourceVerifier} instances: one rooted at {@code DOWNLOAD_PATH} (the "restore" check — a
 *     {@code FULL_MATCH} there means "already downloaded and good") and one rooted at {@code SOURCE_COMPARE_PATH}
 *     (the "source-compare" copy). Because both roles share one verifier abstraction, restore inherits the same
 *     correct (non-truncating) size comparison and the same akumu-torrent / Content-Delivery-Network (CDN)
 *     verifier selection as source-compare.<br>
 *     {@link #load()} runs TWO SEQUENTIAL phases, never interleaved: Phase 1 (restore) reads the output folder
 *     only and finishes completely; Phase 2 (source-compare) then verifies the source folder and copies, but
 *     skips any file Phase 1 already accepted. This gives restore strict priority over source-compare (a file
 *     good on disk is never re-copied) and provides the read-before-write barrier the akumu piece verifier
 *     needs — no phase ever reads a folder another phase is concurrently writing. Verification of each phase is
 *     parallel on a dedicated {@link ForkJoinPool} bounded to {@code thread_on_parallel_file_check} workers, so
 *     whole-file hashing does not thrash the disk.<br>
 * RU: Единый стартовый обработчик сравнения, заменяющий два прежних условия (restore + source-compare).
 *     Выполняется ДО конвейера загрузки и решает по каждому файлу: можно ли его пропустить (уже валиден на
 *     диске) или переиспользовать (скопировать из локальной папки-источника) вместо загрузки. Управляется двумя
 *     экземплярами {@link ISourceVerifier}: один с корнем {@code DOWNLOAD_PATH} (проверка "restore" —
 *     {@code FULL_MATCH} там означает «уже скачано и валидно»), второй с корнем {@code SOURCE_COMPARE_PATH}
 *     (копирование "source-compare"). Так как обе роли используют одну абстракцию верификатора, restore получает
 *     то же корректное (без обрезания) сравнение размера и тот же выбор верификатора akumu-торрент /
 *     Content-Delivery-Network (CDN), что и source-compare.<br>
 *     {@link #load()} выполняет ДВЕ ПОСЛЕДОВАТЕЛЬНЫЕ фазы, никогда не вперемешку: Фаза 1 (restore) читает только
 *     папку вывода и завершается полностью; Фаза 2 (source-compare) затем проверяет папку-источник и копирует,
 *     но пропускает всё, что Фаза 1 уже приняла. Это даёт restore строгий приоритет над source-compare (валидный
 *     на диске файл никогда не копируется повторно) и обеспечивает барьер «чтение-раньше-записи», нужный
 *     piece-верификатору akumu, — ни одна фаза не читает папку, в которую другая фаза пишет одновременно.
 *     Проверка каждой фазы идёт параллельно на выделенном {@link ForkJoinPool}, ограниченном
 *     {@code thread_on_parallel_file_check} воркерами, чтобы хеширование файлов целиком не забивало диск.<br>
 **/
public class ConditionStartupCompare implements IDummyLogger, ILoadable, ICondition, IThreadResponse
{
    private final GeneralLinkGenerator  _linkGenerator;
    private final List<ICondition>      _filterConditions;
    private final Set<String>           _excludeFileList;
    private final int                   _parallelism;
    private int                         _processTotal;

    private final boolean               _restoreEnabled;
    private final ISourceVerifier       _restoreVerifier;
    private final boolean               _sourceEnabled;
    private final ISourceVerifier       _sourceVerifier;
    private final File                  _sourceRoot;
    private final boolean               _sourceTrustPartial;

    private final AtomicInteger         _seenCounter;
    private final AtomicInteger         _restoredCounter;
    private final AtomicInteger         _copiedCounter;
    private final AtomicInteger         _partialCounter;
    private int                         _nextPercentNumber;

    public ConditionStartupCompare(GeneralLinkGenerator linkGenerator, List<ICondition> filterConditions)
    {
        _linkGenerator      = linkGenerator;
        _filterConditions   = filterConditions;
        _excludeFileList    = ConcurrentHashMap.newKeySet();

        _restoreEnabled     = MainConfig.RESTORE_DOWNLOADING && (MainConfig.CHECK_BY_NAME || MainConfig.CHECK_BY_HASH_SUM || MainConfig.CHECK_BY_SIZE);

        _sourceRoot         = MainConfig.SOURCE_COMPARE_PATH;
        _sourceEnabled      = _sourceRoot != null && _sourceRoot.isDirectory();
        _sourceTrustPartial = MainConfig.SC_TRUST_PARTIAL;
        if (_sourceRoot != null && !_sourceEnabled)
        {
            String reason = _sourceRoot.exists() ? "is not a directory (a file, not a folder)" : "does not exist";
            IDummyLogger.log(IDummyLogger.WARNING, getClass(), "source_compare_path is set but '" + _sourceRoot + "' " + reason + "; source-compare disabled.", null);
        }

        _restoreVerifier    = _restoreEnabled ? _linkGenerator.createSourceVerifier(MainConfig.DOWNLOAD_PATH, MainConfig.CHECK_BY_SIZE, MainConfig.CHECK_BY_HASH_SUM) : null;
        _sourceVerifier     = _sourceEnabled ? _linkGenerator.createSourceVerifier(_sourceRoot, MainConfig.SC_CHECK_SIZE, MainConfig.SC_CHECK_HASH) : null;

        _seenCounter        = new AtomicInteger(0);
        _restoredCounter    = new AtomicInteger(0);
        _copiedCounter      = new AtomicInteger(0);
        _partialCounter     = new AtomicInteger(0);
        _nextPercentNumber  = 1;
        _parallelism        = Math.clamp(_linkGenerator.getFileMapHolder().size() / 100, 1, MainConfig.THREAD_COUNT_FOR_FILE_CHECK_IN_CONDITION);
    }

    /**
     * EN: Runs the two comparison phases in strict order and logs a summary, over ONLY the files that pass the
     *     run's include/exclude filters (not the whole map). Phase 1 (restore) runs to completion before Phase 2
     *     (source-compare) touches the output folder, which both gives restore priority and gives the akumu
     *     verifier a read-before-write barrier. Synchronous. <br>
     * RU: Выполняет две фазы сравнения в строгом порядке и логирует итог, ТОЛЬКО по файлам, прошедшим
     *     include/exclude-фильтры запуска (не по всей карте). Фаза 1 (restore) завершается полностью до того, как
     *     Фаза 2 (source-compare) тронет папку вывода, что и даёт restore приоритет, и даёт верификатору akumu
     *     барьер «чтение-раньше-записи». Синхронно. <br>
     **/
    @Override
    public void load()
    {
        // Verify only the files this run actually wants (those passing the include/exclude filters), not the whole
        // map. For a CDN source each file has its own final hash, so a filtered-out file is never needed here; for a
        // torrent source a boundary piece still reads its neighbour file straight from disk in the verifier, so
        // restricting the WORK set never changes a wanted file's verdict. This turns '-include_filter System/*' from
        // "hash every file in the patch" into "hash only System/*", the whole point of the filter.
        Collection<FileInfoHolder> allFiles = _linkGenerator.getFileMapHolder().values();
        List<FileInfoHolder> files = allFiles.stream()
                .filter(fileInfo -> ICondition.checkCondition(_filterConditions, fileInfo))
                .toList();
        _processTotal = files.size();
        _nextPercentNumber = Math.max(1, (_processTotal / 100) / 2);
        int filteredOut = allFiles.size() - _processTotal;
        IDummyLogger.log(IDummyLogger.INFO, getClass(), "load() method bump. Startup compare: restore=" + _restoreEnabled + ", source=" + _sourceEnabled + " over " + _processTotal + " filter-selected file(s)" + (filteredOut > 0 ? " (" + filteredOut + " skipped by filter)" : "") + "...", null);

        if (_restoreEnabled)
        {
            runPhase("StartupRestore", files, this::restorePhase);
        }
        if (_sourceEnabled)
        {
            runPhase("StartupSource", files, this::sourcePhase);
        }

        IDummyLogger.log(IDummyLogger.FINE, getClass(), "Startup compare done. Restored=" + _restoredCounter.get() + ", copied=" + _copiedCounter.get() + " (partial=" + _partialCounter.get() + "), to-download=" + (_processTotal - _excludeFileList.size()) + " of " + _processTotal + ".", null);
    }

    /**
     * EN: Dispatches {@code body} over every file, parallel on a bounded {@link ForkJoinPool} (or serially when
     *     the map is tiny). The pool's shutdown/join at the end of try-with-resources is the barrier that makes
     *     this whole phase complete — a happens-before edge — before the next phase starts. Resets the progress
     *     counter so each phase reports 0..100%. <br>
     * RU: Прогоняет {@code body} по каждому файлу, параллельно на ограниченном {@link ForkJoinPool} (или
     *     последовательно, если карта крошечная). Завершение пула (shutdown/join) в конце try-with-resources —
     *     это барьер (отношение happens-before), из-за которого вся фаза завершается до старта следующей.
     *     Сбрасывает счётчик прогресса, чтобы каждая фаза отчитывалась 0..100%. <br>
     * ==================================================================<br>
     * EN: @param poolName the thread-pool name (for diagnostics) / RU: @param poolName имя пула потоков (для диагностики) <br>
     * EN: @param files the files to process / RU: @param files файлы для обработки <br>
     * EN: @param body the per-file action / RU: @param body действие на файл <br>
     **/
    private void runPhase(String poolName, Collection<FileInfoHolder> files, Consumer<FileInfoHolder> body)
    {
        _seenCounter.set(0);
        if (_parallelism <= 1)
        {
            files.forEach(body);
            return;
        }
        try (ForkJoinPool pool = PipelineExecutors.newStagePool(_parallelism, poolName))
        {
            pool.submit(() -> files.parallelStream().forEach(body)).get();
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
        catch (ExecutionException e)
        {
            IDummyLogger.log(IDummyLogger.ERROR, getClass(), "Parallel " + poolName + " stage failed.", e.getCause());
        }
    }

    /**
     * EN: Phase 1 body — verifies one file against the OUTPUT folder and excludes it (skips its download) only
     *     on a {@code FULL_MATCH}. A file with unknown expected length is not restorable and is left to
     *     download. A verify error is logged and the file is left to download (not excluded). Runs on a pool
     *     worker — thread-safe. <br>
     * RU: Тело Фазы 1 — проверяет один файл по папке ВЫВОДА и исключает его (пропускает загрузку) только при
     *     {@code FULL_MATCH}. Файл с неизвестной ожидаемой длиной не восстановим и оставляется на загрузку.
     *     Ошибка проверки логируется, файл оставляется на загрузку (не исключается). Выполняется на воркере пула
     *     — потокобезопасно. <br>
     * ==================================================================<br>
     * EN: @param fileInfo the file to restore-check / RU: @param fileInfo файл для проверки восстановления <br>
     **/
    private void restorePhase(FileInfoHolder fileInfo)
    {
        try
        {
            // A positively-known expected length is required: an unknown size (-1) must not auto-pass.
            if (fileInfo.getFileLength() < 0)
            {
                return;
            }
            // Restore accepts FULL_MATCH only. A torrent PARTIAL_MATCH here means a boundary piece is
            // unverifiable because a NEIGHBOUR output file is not present yet — that says nothing about THIS
            // file being finished, so it must not be treated as restored.
            if (_restoreVerifier.verify(fileInfo) == SourceVerdict.FULL_MATCH)
            {
                _excludeFileList.add(fileInfo.getLinkPath().toLowerCase());
                _restoredCounter.incrementAndGet();
            }
        }
        catch (Exception e)
        {
            IDummyLogger.log(IDummyLogger.ERROR, getClass(), "Restore check failed for " + ("'" + fileInfo.getLinkPath() + "'") + ".", e);
        }
        finally
        {
            progressTick();
        }
    }

    /**
     * EN: Phase 2 body — for a file NOT already accepted by restore, verifies it against the SOURCE folder and,
     *     when copyable, copies the source over the output target and excludes it. The first line enforces
     *     restore-priority: a restored file is never re-copied. A copy error is logged and the file is left to
     *     download (not excluded). Runs on a pool worker — thread-safe. <br>
     * RU: Тело Фазы 2 — для файла, ещё НЕ принятого restore, проверяет его по папке-ИСТОЧНИКУ и, если копируем,
     *     копирует источник поверх цели вывода и исключает его. Первая строка обеспечивает приоритет restore:
     *     восстановленный файл никогда не копируется повторно. Ошибка копирования логируется, файл оставляется на
     *     загрузку (не исключается). Выполняется на воркере пула — потокобезопасно. <br>
     * ==================================================================<br>
     * EN: @param fileInfo the file to source-compare / RU: @param fileInfo файл для сравнения с источником <br>
     **/
    private void sourcePhase(FileInfoHolder fileInfo)
    {
        String key = fileInfo.getLinkPath().toLowerCase();
        if (_excludeFileList.contains(key))
        {
            return;
        }
        try
        {
            SourceVerdict verdict = _sourceVerifier.verify(fileInfo);
            if (verdict.isCopyable(_sourceTrustPartial))
            {
                copyFromSource(fileInfo);
                _excludeFileList.add(key);
                _copiedCounter.incrementAndGet();
                if (verdict == SourceVerdict.PARTIAL_MATCH)
                {
                    _partialCounter.incrementAndGet();
                }
            }
        }
        catch (Exception e)
        {
            IDummyLogger.log(IDummyLogger.ERROR, getClass(), "Source copy failed for " + ("'" + fileInfo.getLinkPath() + "'") + ".", e);
        }
        finally
        {
            progressTick();
        }
    }

    /**
     * EN: Copies the verified-good source file over the download target (creating parent folders), overwriting
     *     an existing target. The target is checked to stay inside {@code DOWNLOAD_PATH} (zip-slip guard). <br>
     * RU: Копирует проверенный файл-источник поверх цели загрузки (создавая родительские папки), перезаписывая
     *     существующую цель. Цель проверяется на нахождение внутри {@code DOWNLOAD_PATH} (zip-slip защита). <br>
     * ==================================================================<br>
     * EN: @param fileInfo the file to copy from source / RU: @param fileInfo файл для копирования из источника <br>
     **/
    private void copyFromSource(FileInfoHolder fileInfo) throws IOException
    {
        File source = new File(_sourceRoot, fileInfo.getLinkPath());
        File target = new File(MainConfig.DOWNLOAD_PATH, fileInfo.getLinkPath());
        FileUtils.ensureWithinDirectory(MainConfig.DOWNLOAD_PATH, target, fileInfo.getLinkPath());
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists())
        {
            throw new IOException("Cannot create the target directory for '" + fileInfo.getLinkPath() + "'.");
        }
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * EN: Increments the per-phase progress counter and logs a percentage line at each step boundary when
     *     progress logging is enabled. <br>
     * RU: Увеличивает счётчик прогресса фазы и логирует строку с процентом на каждой границе шага, если
     *     логирование прогресса включено. <br>
     **/
    private void progressTick()
    {
        if ((_seenCounter.incrementAndGet() % _nextPercentNumber == 0) && MainConfig.LOGGING_FILE_CHECK_IN_CONDITION)
        {
            IDummyLogger.log(IDummyLogger.INFO, getClass(), "Progress: " + IDummyLogger.getPercentMessage(IDummyLogger.getPercentOfCompletion(_seenCounter.get(), _processTotal)), null);
        }
    }

    /**
     * EN: No-op — {@link #load()} performs the comparison synchronously (kept for the {@code IThreadResponse}
     *     contract). <br>
     * RU: Пусто — {@link #load()} выполняет сравнение синхронно (сохранено для контракта
     *     {@code IThreadResponse}). <br>
     **/
    @Override
    public void waitCompletion()
    {
        // load() is synchronous; there is nothing to wait for.
    }

    @Override
    public boolean check(FileInfoHolder fileInfoHolder)
    {
        return !_excludeFileList.contains(fileInfoHolder.getLinkPath().toLowerCase());
    }
}
