package org.index.patchdownloader.model.pipeline;

import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.model.pipeline.enums.DownloadFailureType;
import org.index.patchdownloader.model.pipeline.enums.TaskState;
import org.index.patchdownloader.model.pipeline.enums.TaskStage;

/**
 * EN: The single runtime instance that flows through all three pipeline stages, replacing the old
 *     wrapping request objects. It carries the raw downloaded bytes, the decompressed bytes, an
 *     explicit lifecycle {@link TaskState}, a {@link TaskStage} position, the retry counter and the
 *     memory permits it holds. State transitions are guarded (no public {@code setState}).<br>
 * RU: Единственный рантайм-объект, проходящий через все три стадии конвейера, вместо старых
 *     обёрток-запросов. Несёт сырые скачанные байты, распакованные байты, явное состояние
 *     {@link TaskState}, позицию {@link TaskStage}, счётчик повторов и удерживаемые квоты памяти.
 *     Переходы состояний защищены (нет публичного {@code setState}).<br>
 **/
public class FileDownloadTask
{
    private final FileInfoHolder _fileInfo;

    private byte[][] _downloadedParts;
    private byte[] _decompressed;

    private TaskState _state;
    private TaskStage _stage;

    private int _attempts;
    private long _firstAttemptAt;
    private DownloadFailureType _failure;
    private int _acquiredMB;

    /**
     * EN: Creates a task in {@code CREATED}/{@code DOWNLOAD} state and allocates the per-part byte
     *     array sized by the file's separated parts (or a single slot for non-separated files).<br>
     * RU: Создаёт задачу в состоянии {@code CREATED}/{@code DOWNLOAD} и выделяет массив байтов по
     *     частям файла (или один слот для не разделённого файла).<br>
     * ==================================================================<br>
     * EN: @param fileInfo the static file metadata (path, hash, parts) / RU: @param fileInfo статические метаданные файла (путь, хеш, части) <br>
     **/
    public FileDownloadTask(FileInfoHolder fileInfo)
    {
        _fileInfo = fileInfo;
        _downloadedParts = new byte[Math.max(fileInfo.getAllSeparatedParts().length, 1)][];
        _decompressed = null;
        _state = TaskState.CREATED;
        _stage = TaskStage.DOWNLOAD;
        _attempts = 0;
        _firstAttemptAt = 0L;
        _failure = null;
        _acquiredMB = 0;
    }

    /**
     * EN: Marks the task active (a stage started processing it) and records the first-attempt
     *     timestamp used by the per-file retry deadline. Fails fast on a terminal task.<br>
     * RU: Помечает задачу активной (стадия начала обработку) и фиксирует время первой попытки для
     *     дедлайна повторов по файлу. Бросает исключение на терминальной задаче.<br>
     **/
    public void markActive()
    {
        ensureNotTerminal();
        if (_firstAttemptAt == 0L)
        {
            _firstAttemptAt = System.currentTimeMillis();
        }
        _state = TaskState.ACTIVE;
    }

    /**
     * EN: Records a retryable failure and moves the task to {@code RETRY_WAIT}, incrementing the
     *     attempt counter. The same instance is later re-submitted by the retry handler.<br>
     * RU: Фиксирует повторяемый сбой и переводит задачу в {@code RETRY_WAIT}, увеличивая счётчик
     *     попыток. Тот же объект позже переотправляется обработчиком повторов.<br>
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
     * EN: Advances to the next stage and frees the buffer consumed by the stage just left
     *     ({@code _downloadedParts} after decompress, {@code _decompressed} after store) to keep the
     *     in-memory footprint within budget.<br>
     * RU: Переходит на следующую стадию и освобождает буфер, использованный завершённой стадией
     *     ({@code _downloadedParts} после decompress, {@code _decompressed} после store), удерживая
     *     потребление памяти в рамках бюджета.<br>
     **/
    public void advanceStage()
    {
        ensureNotTerminal();
        if (_stage == TaskStage.DECOMPRESS)
        {
            _downloadedParts = null;
        }
        else if (_stage == TaskStage.STORE)
        {
            _decompressed = null;
        }
        _stage = _stage.next();
    }

    /**
     * EN: Marks the task as successfully completed ({@code DONE}). Called after the store stage
     *     advanced the task to {@code COMPLETE}.<br>
     * RU: Помечает задачу успешно завершённой ({@code DONE}). Вызывается после того, как стадия
     *     store перевела задачу в {@code COMPLETE}.<br>
     **/
    public void markDone()
    {
        ensureNotTerminal();
        _state = TaskState.DONE;
    }

    /**
     * EN: Marks the task terminally failed. Idempotent: a second call on an already-terminal task is
     *     a no-op so accidental double-reporting cannot corrupt counters.<br>
     * RU: Помечает задачу терминально проваленной. Идемпотентно: повторный вызов на уже терминальной
     *     задаче ничего не делает, чтобы случайный двойной отчёт не портил счётчики.<br>
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
        // Free any buffers still held on the failure path — the summary needs only path + failure.
        _downloadedParts = null;
        _decompressed = null;
    }

    /**
     * EN: Stores one downloaded part's raw bytes at the given index. Out-of-range indices are
     *     ignored defensively. <br>
     * RU: Сохраняет сырые байты одной части по указанному индексу. Выход за границы игнорируется
     *     защитно. <br>
     * ==================================================================<br>
     * EN: @param index the part index / RU: @param index индекс части <br>
     * EN: @param data the raw downloaded bytes / RU: @param data сырые скачанные байты <br>
     **/
    public void addDownloadedPart(int index, byte[] data)
    {
        if (_downloadedParts == null || index < 0 || index >= _downloadedParts.length)
        {
            return;
        }
        _downloadedParts[index] = data;
    }

    /**
     * EN: Throws if the task is already in a terminal state — guards every state transition against
     *     illegal moves.<br>
     * RU: Бросает исключение, если задача уже в терминальном состоянии, — защищает каждый переход
     *     состояния от недопустимых изменений.<br>
     **/
    private void ensureNotTerminal()
    {
        if (_state.isTerminal())
        {
            throw new IllegalStateException("Illegal transition from terminal state " + _state + " for '" + getLinkPath() + "'.");
        }
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

    public byte[][] getDownloadedParts()
    {
        return _downloadedParts;
    }

    public byte[] getDecompressed()
    {
        return _decompressed;
    }

    public void setDecompressed(byte[] decompressed)
    {
        _decompressed = decompressed;
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
