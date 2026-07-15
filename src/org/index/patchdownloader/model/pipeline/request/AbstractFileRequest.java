package org.index.patchdownloader.model.pipeline.request;

import java.io.File;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.enums.ArchiveType;
import org.index.patchdownloader.enums.StorageStrategy;
import org.index.patchdownloader.interfaces.IDecompressRequest;
import org.index.patchdownloader.interfaces.IDownloadRequest;
import org.index.patchdownloader.interfaces.IStoreRequest;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.model.pipeline.enums.DownloadFailureType;
import org.index.patchdownloader.model.pipeline.enums.TaskStage;
import org.index.patchdownloader.model.pipeline.enums.TaskState;

/**
 * EN: The single runtime instance that flows through all three pipeline stages. It owns the cross-stage
 *     lifecycle (an explicit {@link TaskState}, a {@link TaskStage} position, the retry counter, the
 *     first-attempt timestamp, the classified failure and the memory permits held) and implements the three
 *     stage-request interfaces so each stage handler reads/writes it through its own view
 *     ({@link IDownloadRequest} / {@link IDecompressRequest} / {@link IStoreRequest}). State transitions are
 *     guarded (no public {@code setState}). The concrete data plane (where raw and decompressed bytes live)
 *     is left to the storage-strategy subclasses (memory / temp), which also decide what
 *     {@link #freeAfterStage} and {@link #freeAllResources} release.<br>
 * RU: Единственный рантайм-объект, проходящий через все три стадии конвейера. Он владеет межстадийным
 *     жизненным циклом (явное {@link TaskState}, позиция {@link TaskStage}, счётчик повторов, время первой
 *     попытки, классифицированный сбой и удерживаемые квоты памяти) и реализует три интерфейса
 *     стадий-запросов, поэтому каждый обработчик стадии читает/пишет его через своё представление
 *     ({@link IDownloadRequest} / {@link IDecompressRequest} / {@link IStoreRequest}). Переходы состояний
 *     защищены (нет публичного {@code setState}). Конкретный слой данных (где живут сырые и распакованные
 *     байты) оставлен подклассам стратегии хранения (память / временный файл), которые также решают, что
 *     освобождают {@link #freeAfterStage} и {@link #freeAllResources}.<br>
 **/
public abstract class AbstractFileRequest implements IDownloadRequest, IDecompressRequest, IStoreRequest
{
    protected final FileInfoHolder _fileInfo;

    private TaskState _state;
    private TaskStage _stage;

    private int _attempts;
    private long _firstAttemptAt;
    private DownloadFailureType _failure;
    private int _acquiredMB;
    private volatile long _downloadEpoch;

    /**
     * EN: Creates a request in {@code CREATED}/{@code DOWNLOAD} state for the given file metadata. <br>
     * RU: Создаёт запрос в состоянии {@code CREATED}/{@code DOWNLOAD} для указанных метаданных файла. <br>
     * ==================================================================<br>
     * EN: @param fileInfo the static file metadata (path, hash, parts) / RU: @param fileInfo статические метаданные файла (путь, хеш, части) <br>
     **/
    protected AbstractFileRequest(FileInfoHolder fileInfo)
    {
        _fileInfo = fileInfo;
        _state = TaskState.CREATED;
        _stage = TaskStage.DOWNLOAD;
        _attempts = 0;
        _firstAttemptAt = 0L;
        _failure = null;
        _acquiredMB = 0;
        _downloadEpoch = 0L;
    }

    /**
     * EN: Marks the request active (a stage started processing it) and records the first-attempt timestamp
     *     used by the per-file retry deadline. Fails fast on a terminal request.<br>
     * RU: Помечает запрос активным (стадия начала обработку) и фиксирует время первой попытки для дедлайна
     *     повторов по файлу. Бросает исключение на терминальном запросе.<br>
     **/
    public void markActive()
    {
        ensureNotTerminal();
        if (_firstAttemptAt == 0L)
        {
            _firstAttemptAt = System.currentTimeMillis();
        }
        _state = TaskState.ACTIVE;
        // Bump the attempt token BEFORE resetting the storage, so a straggler of the previous attempt that reads
        // the new token (or the reset it precedes) is fenced out of writing into this fresh attempt's storage.
        _downloadEpoch++;
        resetForDownloadAttempt();
    }

    /**
     * EN: Hook invoked at the start of every download attempt (from {@link #markActive()}), so a storage
     *     strategy can discard any partial raw data left by a previous, failed attempt before the retry
     *     streams fresh bytes. The default does nothing — the memory strategy is untouched (its per-part
     *     slots are re-filled by the re-download, byte-identical to before); the temp strategy overrides it
     *     to delete and re-create its half-written raw temp file (Q8: delete + re-create, not truncate).<br>
     * RU: Хук, вызываемый в начале каждой попытки загрузки (из {@link #markActive()}), чтобы стратегия
     *     хранения могла отбросить частичные сырые данные, оставшиеся от предыдущей неудачной попытки, до
     *     того как повтор запишет свежие байты. По умолчанию ничего не делает — стратегия памяти не
     *     затрагивается (её слоты по частям заполняются повторной загрузкой, побайтно как прежде); стратегия
     *     временного файла переопределяет хук, удаляя и заново создавая свой недописанный сырой временный
     *     файл (Q8: удалить и создать заново, а не усекать).<br>
     **/
    protected void resetForDownloadAttempt()
    {
        // default: subclasses that keep cross-attempt raw state (temp files) reset it here
    }

    /**
     * EN: Records a retryable failure and moves the request to {@code RETRY_WAIT}, incrementing the attempt
     *     counter. The same instance is later re-submitted by the retry handler.<br>
     * RU: Фиксирует повторяемый сбой и переводит запрос в {@code RETRY_WAIT}, увеличивая счётчик попыток. Тот
     *     же объект позже переотправляется обработчиком повторов.<br>
     * ==================================================================<br>
     * EN: @param failure the classified failure reason / RU: @param failure классифицированная причина сбоя <br>
     **/
    public void markRetry(DownloadFailureType failure)
    {
        ensureNotTerminal();
        _failure = failure;
        _attempts++;
        _state = TaskState.RETRY_WAIT;
    }

    /**
     * EN: Advances to the next stage, first releasing the resources consumed by the stage just left (via
     *     {@link #freeAfterStage}) to keep the in-flight footprint within budget.<br>
     * RU: Переходит на следующую стадию, предварительно освобождая ресурсы, использованные завершённой
     *     стадией (через {@link #freeAfterStage}), удерживая потребление в рамках бюджета.<br>
     **/
    public void advanceStage()
    {
        ensureNotTerminal();
        freeAfterStage(_stage);
        _stage = _stage.next();
    }

    /**
     * EN: Marks the request successfully completed ({@code DONE}). Called after the store stage advanced it
     *     to {@code COMPLETE}.<br>
     * RU: Помечает запрос успешно завершённым ({@code DONE}). Вызывается после того, как стадия store
     *     перевела его в {@code COMPLETE}.<br>
     **/
    public void markDone()
    {
        ensureNotTerminal();
        _state = TaskState.DONE;
    }

    /**
     * EN: Marks the request terminally failed. Idempotent: a second call on an already-terminal request is a
     *     no-op so accidental double-reporting cannot corrupt counters. Releases every resource still held on
     *     the failure path (via {@link #freeAllResources}) — the summary needs only path + failure.<br>
     * RU: Помечает запрос терминально проваленным. Идемпотентно: повторный вызов на уже терминальном запросе
     *     ничего не делает, чтобы случайный двойной отчёт не портил счётчики. Освобождает все ещё удерживаемые
     *     ресурсы на пути сбоя (через {@link #freeAllResources}) — сводке нужны только путь и причина.<br>
     * ==================================================================<br>
     * EN: @param failure the classified failure reason / RU: @param failure классифицированная причина сбоя <br>
     **/
    public void markFailed(DownloadFailureType failure)
    {
        if (_state.isTerminal())
        {
            return;
        }
        _failure = failure;
        _state = TaskState.FAILED;
        freeAllResources();
    }

    /**
     * EN: Releases the resources the just-finished stage no longer needs. Called from {@link #advanceStage}
     *     with the stage being left. The default frees nothing; storage-strategy subclasses override it (the
     *     memory strategy frees the raw parts after decompress and the decompressed buffer after store).<br>
     * RU: Освобождает ресурсы, которые только что завершённой стадии больше не нужны. Вызывается из
     *     {@link #advanceStage} с покидаемой стадией. По умолчанию ничего не освобождает; подклассы стратегии
     *     хранения переопределяют (память освобождает сырые части после распаковки и распакованный буфер после
     *     сохранения).<br>
     * ==================================================================<br>
     * EN: @param completedStage the stage just finished / RU: @param completedStage только что завершённая стадия <br>
     **/
    protected void freeAfterStage(TaskStage completedStage)
    {
        // default: subclasses release their per-stage data plane
    }

    /**
     * EN: Releases every resource the request still holds on the terminal-failure path. The default frees
     *     nothing; storage-strategy subclasses override it (memory drops its byte buffers, temp deletes its
     *     temp files).<br>
     * RU: Освобождает все ресурсы, ещё удерживаемые запросом на пути терминального сбоя. По умолчанию ничего
     *     не освобождает; подклассы стратегии хранения переопределяют (память сбрасывает свои байтовые буферы,
     *     временный режим удаляет свои временные файлы).<br>
     **/
    protected void freeAllResources()
    {
        // default: subclasses release their data plane / temp files
    }

    /**
     * EN: Throws if the request is already in a terminal state — guards every state transition against
     *     illegal moves.<br>
     * RU: Бросает исключение, если запрос уже в терминальном состоянии, — защищает каждый переход состояния от
     *     недопустимых изменений.<br>
     **/
    private void ensureNotTerminal()
    {
        if (_state.isTerminal())
        {
            throw new IllegalStateException("Illegal transition from terminal state " + _state + " for '" + getLinkPath() + "'.");
        }
    }

    /**
     * EN: The storage strategy backing this request, decided once when the coordinator builds the concrete
     *     implementation ({@code MEMORY} for {@link org.index.patchdownloader.model.pipeline.request.MemoryFileRequest},
     *     {@code TEMPORARY} for the temp-file variant). The decompress stage reads it to choose the fast in-memory
     *     {@code byte[]} path versus the streaming channel path, so the two never diverge in output — only in
     *     footprint. <br>
     * RU: Стратегия хранения, обслуживающая этот запрос, определяется один раз при построении координатором
     *     конкретной реализации ({@code MEMORY} для
     *     {@link org.index.patchdownloader.model.pipeline.request.MemoryFileRequest}, {@code TEMPORARY} для
     *     варианта с временным файлом). Стадия распаковки читает её, чтобы выбрать быстрый путь в памяти по
     *     {@code byte[]} против потокового канального пути, поэтому они никогда не расходятся по выводу — лишь по
     *     объёму потребления. <br>
     * ==================================================================<br>
     * @return <br>
     *         {StorageStrategy} - EN: the storage strategy of this request / RU: стратегия хранения этого запроса <br>
     **/
    public abstract StorageStrategy storageStrategy();

    @Override
    public long downloadEpoch()
    {
        return _downloadEpoch;
    }

    @Override
    public FileInfoHolder fileInfo()
    {
        return _fileInfo;
    }

    @Override
    public int partCount()
    {
        return _fileInfo == null ? 1 : Math.max(_fileInfo.getAllSeparatedParts().length, 1);
    }

    @Override
    public ArchiveType compressType()
    {
        return _fileInfo == null ? ArchiveType.NONE : _fileInfo.getCompressType();
    }

    @Override
    public long expectedLength()
    {
        return _fileInfo == null ? -1 : _fileInfo.getFileLength();
    }

    @Override
    public File target()
    {
        return new File(MainConfig.DOWNLOAD_PATH, getLinkPath());
    }

    public FileInfoHolder getFileInfo()
    {
        return _fileInfo;
    }

    public String getLinkPath()
    {
        return _fileInfo == null ? "" : _fileInfo.getLinkPath();
    }

    public TaskState getState()
    {
        return _state;
    }

    public TaskStage getStage()
    {
        return _stage;
    }

    public int getAttempts()
    {
        return _attempts;
    }

    public long getFirstAttemptAt()
    {
        return _firstAttemptAt;
    }

    public DownloadFailureType getFailure()
    {
        return _failure;
    }

    public void setFailure(DownloadFailureType failure)
    {
        _failure = failure;
    }

    public int getAcquiredMB()
    {
        return _acquiredMB;
    }

    public void setAcquiredMB(int acquiredMB)
    {
        _acquiredMB = acquiredMB;
    }
}
