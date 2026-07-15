package org.index.patchdownloader.impl.downloader;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.enums.DownloadMode;
import org.index.patchdownloader.enums.StorageStrategy;
import org.index.patchdownloader.instancemanager.DecompressStageManager;
import org.index.patchdownloader.instancemanager.DownloadStageManager;
import org.index.patchdownloader.instancemanager.StoreStageManager;
import org.index.patchdownloader.interfaces.ICondition;
import org.index.patchdownloader.interfaces.IDummyLogger;
import org.index.patchdownloader.interfaces.IPipelineSink;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.model.linkgenerator.GeneralLinkGenerator;
import org.index.patchdownloader.model.pipeline.InFlightBudget;
import org.index.patchdownloader.model.pipeline.verify.IDownloadVerifier;
import org.index.patchdownloader.model.pipeline.request.AbstractFileRequest;
import org.index.patchdownloader.model.pipeline.request.MemoryFileRequest;
import org.index.patchdownloader.model.pipeline.request.TempFileRequest;
import org.index.patchdownloader.model.pipeline.enums.DownloadFailureType;
import org.index.patchdownloader.model.storage.StorageRouter;
import org.index.patchdownloader.util.FileUtils;

/**
 * EN: Single per-run owner of the pipeline. Builds one {@link AbstractFileRequest} per downloadable file
 *     (skipping condition-filtered / un-creatable ones), wires the stage chain, throttles in-flight
 *     work via a memory (MB) + file-count budget, and detects completion deterministically with a
 *     counter + latch (no polling). Exits once, from the main thread, with a summary and an exit code
 *     that reflects failures.<br>
 * RU: Единственный на запуск владелец конвейера. Создаёт по одной {@link AbstractFileRequest} на
 *     скачиваемый файл (пропуская отфильтрованные условиями / несоздаваемые), связывает цепочку
 *     стадий, ограничивает одновременную работу бюджетом памяти (МБ) + числа файлов и детерминированно
 *     определяет завершение счётчиком + защёлкой (без опроса). Выходит один раз из главного потока со
 *     сводкой и кодом выхода, отражающим сбои.<br>
 **/
public class PipelineCoordinator implements IPipelineSink
{
    private final GeneralLinkGenerator _generator;

    private final InFlightBudget _budget;

    private final AtomicInteger _remaining;
    private final AtomicInteger _done;
    private final AtomicInteger _failed;
    private final Queue<AbstractFileRequest> _failedFiles;
    private final CountDownLatch _completionLatch;

    private int _total;
    private int _rejected;
    private IDownloadVerifier _verifier;

    /**
     * EN: Builds the coordinator for a run and its {@link InFlightBudget} (sized from config + heap). <br>
     * RU: Создаёт координатор для запуска и его {@link InFlightBudget} (рассчитан из конфига + кучи). <br>
     * ==================================================================<br>
     * EN: @param generator the loaded link generator providing the file map / RU: @param generator загруженный генератор ссылок с картой файлов <br>
     **/
    public PipelineCoordinator(GeneralLinkGenerator generator)
    {
        _generator = generator;

        _budget = new InFlightBudget(MainConfig.MAX_INFLIGHT_MEMORY_MB, MainConfig.MAX_INFLIGHT_FILES);

        _remaining = new AtomicInteger(0);
        _done = new AtomicInteger(0);
        _failed = new AtomicInteger(0);
        _failedFiles = new ConcurrentLinkedQueue<>();
        _completionLatch = new CountDownLatch(1);
        _total = 0;
        _rejected = 0;
        _verifier = IDownloadVerifier.NONE;
    }

    /**
     * EN: Runs the whole pipeline on the main thread: builds tasks, wires + starts stages, submits all
     *     tasks (throttled by budget), blocks on the completion latch, then shuts down and exits. <br>
     * RU: Запускает весь конвейер в главном потоке: строит задачи, связывает + запускает стадии,
     *     отправляет все задачи (с учётом бюджета), блокируется на защёлке завершения, затем
     *     останавливает и выходит. <br>
     **/
    public void run()
    {
        prepareTempStorage();

        List<AbstractFileRequest> tasks = buildTasks();
        _total = tasks.size();
        _remaining.set(_total);
        if (_total == 0)
        {
            IDummyLogger.log(IDummyLogger.WARNING, "Nothing to download (all files skipped or file list empty).");
            finish();
            return;
        }

        wireChain();
        startStages();
        // The verifier needs to know which files are (re)downloaded this run, so the torrent-piece verifier can
        // tell a this-run-final file from a stale pre-existing neighbour (every other torrent file is final).
        List<String> scheduledPaths = new ArrayList<>(tasks.size());
        for (AbstractFileRequest task : tasks)
        {
            scheduledPaths.add(task.getLinkPath());
        }
        _verifier = _generator.createDownloadVerifier(scheduledPaths, MainConfig.THREAD_USAGE ? MainConfig.PARALLEL_DECODING : 1);

        for (AbstractFileRequest task : tasks)
        {
            _budget.acquire(task);
            DownloadStageManager.getInstance().submit(task);
        }

        try
        {
            _completionLatch.await();
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
        finish();
    }

    /**
     * EN: Prepares the temp-file storage for a run when the mode may stream files to disk (HYBRID / ALL_TEMP):
     *     creates {@code temp_file_dir} and sweeps orphan raw temp / {@code .part} files left by a previous
     *     crash. In the default {@code ALL_MEMORY} mode nothing streams to disk, so this is skipped entirely and
     *     the filesystem is left byte-identical to before. <br>
     * RU: Готовит хранилище временных файлов для запуска, когда режим может писать файлы на диск (HYBRID /
     *     ALL_TEMP): создаёт {@code temp_file_dir} и сметает сиротские сырые временные / {@code .part} файлы,
     *     оставшиеся от предыдущего сбоя. В режиме по умолчанию {@code ALL_MEMORY} на диск ничего не пишется,
     *     поэтому шаг полностью пропускается, а файловая система остаётся побайтно прежней. <br>
     **/
    private void prepareTempStorage()
    {
        if (MainConfig.DOWNLOAD_MODE == DownloadMode.ALL_MEMORY)
        {
            return;
        }
        int reclaimed = FileUtils.prepareTempDirectory(MainConfig.TEMP_FILE_DIR, MainConfig.DOWNLOAD_PATH);
        if (reclaimed > 0)
        {
            IDummyLogger.log(IDummyLogger.INFO, "Swept " + reclaimed + " orphan temp/.part file(s) from a previous run.");
        }
    }

    /**
     * EN: Builds the task list from the generator's file map, skipping files rejected by conditions or
     *     whose destination folders cannot be created (these are NOT counted towards completion). <br>
     * RU: Формирует список задач по файлам генератора, пропуская отклонённые условиями или те, для
     *     которых нельзя создать целевые папки (они НЕ учитываются в завершении). <br>
     * ==================================================================<br>
     * @return <br>
     *         {List} - EN: the tasks to process / RU: задачи для обработки <br>
     **/
    private List<AbstractFileRequest> buildTasks()
    {
        List<ICondition> conditions = ICondition.loadConditions(_generator);
        List<AbstractFileRequest> tasks = new ArrayList<>();
        long thresholdBytes = (long) MainConfig.TEMP_FILE_THRESHOLD_MB * 1024L * 1024L;
        for (FileInfoHolder fileInfo : _generator.getFileMapHolder().values())
        {
            if (!ICondition.checkCondition(conditions, fileInfo))
            {
                continue;
            }
            if (!FileUtils.createSubFolders(MainConfig.DOWNLOAD_PATH, fileInfo))
            {
                IDummyLogger.log(IDummyLogger.ERROR, "Cannot create destination folder for '" + fileInfo.getLinkPath() + "'. Ignoring.");
                _rejected++;
                continue;
            }
            AbstractFileRequest request = createRequest(fileInfo, thresholdBytes);
            if (request == null)
            {
                // A file selected for download that could not be turned into a task (e.g. over the ALL_MEMORY
                // threshold) — it will NOT be downloaded, so the run is incomplete. Counted so finish() exits
                // non-zero instead of silently reporting success with a missing file (fail-closed).
                _rejected++;
                continue;
            }
            tasks.add(request);
        }
        return tasks;
    }

    /**
     * EN: Builds the storage-strategy-aware request for one file by routing the run's {@link DownloadMode} and
     *     the file's known size through {@link StorageRouter}. In {@code ALL_MEMORY} a file whose known size
     *     exceeds {@code temp_file_threshold_mb} cannot fit the in-memory path, so it is rejected before
     *     download with a clear error and skipped (returning {@code null} → not counted towards completion);
     *     this is the one behaviour {@code ALL_MEMORY} adds, an unconfigured run with only in-threshold files
     *     stays byte-identical. {@code MEMORY} yields a {@link MemoryFileRequest}, {@code TEMPORARY} a
     *     {@link TempFileRequest}. In {@code HYBRID} an unknown size (negative) routes to a temp file (safe),
     *     and {@code ALL_TEMP} routes every file through a temp file regardless of size.<br>
     * RU: Строит запрос с учётом стратегии хранения для одного файла, прогоняя {@link DownloadMode} запуска и
     *     известный размер файла через {@link StorageRouter}. В режиме {@code ALL_MEMORY} файл, известный
     *     размер которого превышает {@code temp_file_threshold_mb}, не помещается на путь в памяти, поэтому он
     *     отклоняется до загрузки с понятной ошибкой и пропускается (возврат {@code null} → не учитывается в
     *     завершении); это единственное поведение, добавляемое режимом {@code ALL_MEMORY}, а незаданный запуск,
     *     где все файлы в пределах порога, остаётся побайтно прежним. {@code MEMORY} даёт
     *     {@link MemoryFileRequest}, {@code TEMPORARY} — {@link TempFileRequest}. В режиме {@code HYBRID}
     *     неизвестный размер (отрицательный) направляется во временный файл (безопасно), а {@code ALL_TEMP}
     *     направляет каждый файл через временный файл независимо от размера.<br>
     * ==================================================================<br>
     * EN: @param fileInfo the static file metadata / RU: @param fileInfo статические метаданные файла <br>
     * EN: @param thresholdBytes the memory-vs-temp threshold in bytes / RU: @param thresholdBytes порог «память против временного файла» в байтах <br>
     * @return <br>
     *         {AbstractFileRequest} - EN: the request backing this file, or {@code null} when the file is
     *         rejected (over-threshold in {@code ALL_MEMORY}) / RU: запрос, обслуживающий этот файл, или
     *         {@code null}, если файл отклонён (сверх порога в режиме {@code ALL_MEMORY}) <br>
     **/
    private AbstractFileRequest createRequest(FileInfoHolder fileInfo, long thresholdBytes)
    {
        long knownSizeBytes = knownSizeBytes(fileInfo);
        if (MainConfig.DOWNLOAD_MODE == DownloadMode.ALL_MEMORY && knownSizeBytes > thresholdBytes)
        {
            IDummyLogger.log(IDummyLogger.ERROR, "Skipping '" + fileInfo.getLinkPath() + "': known size " + knownSizeBytes + " byte(s) exceeds the all-memory threshold of " + MainConfig.TEMP_FILE_THRESHOLD_MB + " MB (" + thresholdBytes + " byte(s)). Use download_mode=hybrid or all_temp to stream it through a temp file.");
            return null;
        }
        StorageStrategy strategy = StorageRouter.pick(MainConfig.DOWNLOAD_MODE, knownSizeBytes, thresholdBytes);
        if (strategy == StorageStrategy.TEMPORARY && !tempOffsetsPlaceable(fileInfo))
        {
            // A multi-part temp file places its concurrently-downloaded parts at fixed absolute offsets in the
            // pre-sized raw temp file; that needs every part's length, and without one the parts' base offsets
            // collide and they overwrite each other on disk. Such a file cannot stream through a temp file
            // correctly, so keep it on the memory path (its parts append per-connection, correct without declared
            // sizes) regardless of the run mode. It is then bounded by the memory budget like any memory file.
            IDummyLogger.log(IDummyLogger.WARNING, "File '" + fileInfo.getLinkPath() + "' is multi-part with an unknown part length; using the in-memory path instead of a temp file (its part offsets cannot be placed on disk).");
            strategy = StorageStrategy.MEMORY;
        }
        if (strategy == StorageStrategy.TEMPORARY)
        {
            return new TempFileRequest(fileInfo);
        }
        return new MemoryFileRequest(fileInfo);
    }

    /**
     * EN: Whether a temp-file request could place this file's parts at fixed absolute offsets. A non-separated
     *     file always can (one raw stream from offset 0, extended as positioned writes arrive). A file split into
     *     parts can only when every part length is known, since the temp strategy derives each part's base offset
     *     from the running sum of the preceding parts' lengths — a missing length would collide two parts onto the
     *     same offset. Mirrors the all-parts-known test of {@link #rawKnownSize}. <br>
     * RU: Может ли запрос с временным файлом разместить части этого файла по фиксированным абсолютным смещениям. Не
     *     разделённый файл может всегда (один сырой поток от смещения 0, расширяемый по мере позиционных записей).
     *     Файл, разбитый на части, — только когда известна длина каждой части, поскольку временная стратегия
     *     выводит базовое смещение каждой части из накопленной суммы длин предыдущих; отсутствующая длина свела бы
     *     две части на одно смещение. Повторяет проверку «все части известны» из {@link #rawKnownSize}. <br>
     * ==================================================================<br>
     * EN: @param fileInfo the static file metadata / RU: @param fileInfo статические метаданные файла <br>
     * @return <br>
     *         {boolean} - EN: true when the parts can be placed on a temp file / RU: true, когда части можно разместить во временном файле <br>
     **/
    private static boolean tempOffsetsPlaceable(FileInfoHolder fileInfo)
    {
        return fileInfo.getAllSeparatedParts().length == 0 || rawKnownSize(fileInfo) > 0;
    }

    /**
     * EN: The file's best-known size in bytes for storage routing, or a negative value when unknown. It takes
     *     the larger of the final (decompressed) file length and the raw download length, so a file is treated
     *     as known when either source (torrent reports the final length, a Content-Delivery-Network reports the
     *     raw download length) supplied a size. For a file split into parts the raw download length is the sum
     *     of the parts' download lengths (matching how the temp strategy pre-sizes its raw file); if any part
     *     length is missing the raw total is unknown and only the final file length can make the size known.<br>
     * RU: Наиболее известный размер файла в байтах для маршрутизации хранения либо отрицательное значение, если
     *     неизвестен. Берётся большее из итоговой (распакованной) длины файла и сырой длины загрузки, поэтому
     *     файл считается известным, если размер сообщил любой источник (торрент сообщает итоговую длину, сеть
     *     доставки контента — сырую длину загрузки). Для файла, разбитого на части, сырая длина загрузки — это
     *     сумма длин загрузки частей (как временная стратегия заранее выделяет свой сырой файл); если длина
     *     какой-либо части отсутствует, сырая сумма неизвестна, и размер может сделать известным только итоговая
     *     длина файла.<br>
     * ==================================================================<br>
     * EN: @param fileInfo the static file metadata / RU: @param fileInfo статические метаданные файла <br>
     * @return <br>
     *         {long} - EN: the known size in bytes, or negative when unknown / RU: известный размер в байтах или отрицательное значение, если неизвестен <br>
     **/
    private static long knownSizeBytes(FileInfoHolder fileInfo)
    {
        long fileLength = fileInfo.getFileLength();
        long rawLength = rawKnownSize(fileInfo);
        return Math.max(fileLength, rawLength);
    }

    /**
     * EN: The raw (still-compressed) download length in bytes, or a negative value when unknown. For a
     *     non-separated file it is the file's own download length; for a file split into parts it is the sum of
     *     the parts' download lengths, and it stays unknown when any part length is missing (a partial sum would
     *     misroute a large file). This mirrors how {@code TempFileRequest} pre-sizes the raw temp file.<br>
     * RU: Сырая (ещё сжатая) длина загрузки в байтах либо отрицательное значение, если неизвестна. Для не
     *     разделённого файла это его собственная длина загрузки; для файла, разбитого на части, это сумма длин
     *     загрузки частей, и она остаётся неизвестной, если длина какой-либо части отсутствует (частичная сумма
     *     ошибочно направила бы большой файл). Это повторяет то, как {@code TempFileRequest} заранее выделяет
     *     сырой временный файл.<br>
     * ==================================================================<br>
     * EN: @param fileInfo the static file metadata / RU: @param fileInfo статические метаданные файла <br>
     * @return <br>
     *         {long} - EN: the raw download size in bytes, or negative when unknown / RU: сырой размер загрузки в байтах или отрицательное значение, если неизвестен <br>
     **/
    private static long rawKnownSize(FileInfoHolder fileInfo)
    {
        FileInfoHolder[] parts = fileInfo.getAllSeparatedParts();
        if (parts.length == 0)
        {
            return fileInfo.getDownloadDataLength();
        }
        long running = 0L;
        for (FileInfoHolder part : parts)
        {
            long length = part == null ? -1L : part.getDownloadDataLength();
            if (length <= 0)
            {
                return -1L;
            }
            running += length;
        }
        return running;
    }

    /**
     * EN: Links the three stages (download -> decompress -> store -> sink), sets this coordinator as
     *     their sink, and injects the run's hash algorithm into the decompress stage. <br>
     * RU: Связывает три стадии (download -> decompress -> store -> sink), назначает этот координатор
     *     их приёмником и передаёт алгоритм хеша запуска в стадию распаковки. <br>
     **/
    private void wireChain()
    {
        DownloadStageManager download = DownloadStageManager.getInstance();
        DecompressStageManager decompress = DecompressStageManager.getInstance();
        StoreStageManager store = StoreStageManager.getInstance();

        download.setNext(decompress);
        decompress.setNext(store);
        store.setNext(null);

        download.setSink(this);
        decompress.setSink(this);
        store.setSink(this);
    }

    /**
     * EN: Starts the three stage pools. When threaded usage is disabled, every stage runs with
     *     parallelism 1. <br>
     * RU: Запускает три пула стадий. Если многопоточность отключена, каждая стадия работает с
     *     параллелизмом 1. <br>
     **/
    private void startStages()
    {
        boolean threaded = MainConfig.THREAD_USAGE;
        DownloadStageManager.getInstance().start(threaded ? MainConfig.PARALLEL_DOWNLOADING : 1);
        DecompressStageManager.getInstance().start(threaded ? MainConfig.PARALLEL_DECODING : 1);
        StoreStageManager.getInstance().start(threaded ? MainConfig.PARALLEL_STORING : 1);
    }

    /**
     * EN: Drains and stops the three stage pools. Called only after the completion latch fired (all tasks
     *     terminal), so no in-flight work is lost. <br>
     * RU: Дожидается завершения задач и останавливает три пула стадий. Вызывается только после срабатывания
     *     защёлки завершения (все задачи терминальны), поэтому незавершённая (выполняемая) работа не теряется. <br>
     **/
    private void shutdownStages()
    {
        DownloadStageManager.getInstance().shutdown();
        DecompressStageManager.getInstance().shutdown();
        StoreStageManager.getInstance().shutdown();
    }

    /**
     * EN: Terminal-success callback (last stage): counts the file and runs shared terminal bookkeeping. <br>
     * RU: Колбэк терминального успеха (последняя стадия): считает файл и выполняет общий учёт
     *     завершения. <br>
     * ==================================================================<br>
     * EN: @param task the completed task / RU: @param task завершённая задача <br>
     **/
    @Override
    public void onDone(AbstractFileRequest task)
    {
        _done.incrementAndGet();
        logProgress(task, true);
        // Hand the finished file to the async verifier (non-blocking); it schedules the read-and-hash so
        // verification overlaps the ongoing download instead of a blocking pass at the end.
        _verifier.onStored(task.getFileInfo());
        onTerminal(task);
    }

    /**
     * EN: Terminal-failure callback: records the failed file and runs shared terminal bookkeeping. <br>
     * RU: Колбэк терминального сбоя: фиксирует проваленный файл и выполняет общий учёт завершения. <br>
     * ==================================================================<br>
     * EN: @param task the failed task / RU: @param task проваленная задача <br>
     * EN: @param failure the failure reason / RU: @param failure причина сбоя <br>
     **/
    @Override
    public void onFailed(AbstractFileRequest task, DownloadFailureType failure)
    {
        _failed.incrementAndGet();
        _failedFiles.add(task);
        logProgress(task, false);
        onTerminal(task);
    }

    /**
     * EN: Shared terminal bookkeeping — MUST run exactly once per counted task (via onDone/onFailed):
     *     releases the task's budget and counts the completion latch down when the last task finishes.
     *     Never call this directly for a task already reported through onDone/onFailed (double-count). <br>
     * RU: Общий учёт при завершении задачи — ДОЛЖЕН выполняться ровно один раз на учтённую задачу (через
     *     onDone/onFailed): освобождает бюджет задачи и опускает защёлку завершения, когда завершается
     *     последняя задача. Не вызывайте напрямую для задачи, уже отчитанной через onDone/onFailed. <br>
     * ==================================================================<br>
     * EN: @param task the task reaching a terminal state / RU: @param task задача, достигшая терминального состояния <br>
     **/
    private void onTerminal(AbstractFileRequest task)
    {
        releaseBudget(task);
        if (_remaining.decrementAndGet() == 0)
        {
            _completionLatch.countDown();
        }
    }

    @Override
    public void releaseBudget(AbstractFileRequest task)
    {
        _budget.release(task);
    }

    /**
     * EN: Logs a per-file progress line (overall percent + file path + OK/FAIL). <br>
     * RU: Логирует строку прогресса по файлу (общий процент + путь файла + OK/FAIL). <br>
     * ==================================================================<br>
     * EN: @param task the finished task / RU: @param task завершённая задача <br>
     * EN: @param success whether the file was stored successfully / RU: @param success успешно ли сохранён файл <br>
     **/
    private void logProgress(AbstractFileRequest task, boolean success)
    {
        int processed = _done.get() + _failed.get();
        int percent = _total == 0 ? 100 : IDummyLogger.getPercentOfCompletion(processed, _total);
        String status = success ? "OK" : "FAIL";
        IDummyLogger.log(IDummyLogger.INFO, "Progress " + IDummyLogger.getPercentMessage(percent) + " | " + task.getLinkPath() + ": " + status);
    }

    /**
     * EN: Shuts the stages down, prints the run summary (ok / failed / total + failed paths), drains the async
     *     post-store verifier and prints its verdict, then exits the JVM once from the main thread — non-zero when
     *     any file failed OR the verifier proved a file/piece corrupt. <br>
     * RU: Останавливает стадии, печатает сводку запуска (ok / failed / total + пути проваленных файлов),
     *     дожидается асинхронного верификатора, работающего после сохранения, и печатает его вердикт, затем один
     *     раз выходит из JVM из главного потока — ненулевой код, если хоть один файл упал ИЛИ верификатор признал
     *     файл/кусок битым. <br>
     **/
    private void finish()
    {
        shutdownStages();
        int failed = _failed.get();
        IDummyLogger.log(failed == 0 && _rejected == 0 ? IDummyLogger.FINE : IDummyLogger.WARNING, "Done: " + _done.get() + " ok, " + failed + " failed, " + _rejected + " not scheduled, " + _total + " total.");
        for (AbstractFileRequest task : _failedFiles)
        {
            IDummyLogger.log(IDummyLogger.ERROR, "FAILED: " + task.getLinkPath() + " (" + task.getFailure() + ")");
        }
        boolean verifyOk = _verifier.awaitAndReport();
        // Fail-CLOSED: a failed download, a file that could not be scheduled (over-threshold / un-creatable folder),
        // or a verification failure all leave the patch incomplete/unproven — none may report success.
        System.exit(failed == 0 && _rejected == 0 && verifyOk ? 0 : 1);
    }
}
