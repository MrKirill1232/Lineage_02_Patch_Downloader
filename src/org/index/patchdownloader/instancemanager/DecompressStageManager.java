package org.index.patchdownloader.instancemanager;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.enums.HashType;
import org.index.patchdownloader.interfaces.IDummyLogger;
import org.index.patchdownloader.model.decompress.Decompressors;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.model.pipeline.FileDownloadTask;
import org.index.patchdownloader.model.pipeline.enums.DownloadFailureType;
import org.index.patchdownloader.model.pipeline.enums.TaskStage;
import org.index.patchdownloader.model.pipeline.retry.NoRetryHandler;

/**
 * EN: Decompress stage (CPU-bound; does not use ForkJoinPool (FJP).ManagedBlocker because the work
 *     never blocks). Concatenates the downloaded parts, decodes via the stateless {@link Decompressors},
 *     and optionally verifies size/hash (warn only). Failures are terminal — no retries (uses
 *     {@link NoRetryHandler}). The run-wide hash algorithm is injected by the coordinator.<br>
 * RU: Стадия распаковки (нагружает CPU; не использует ForkJoinPool (FJP).ManagedBlocker, поскольку
 *     операция никогда не блокируется). Склеивает скачанные части, декодирует через stateless
 *     {@link Decompressors} и опционально проверяет размер/хеш (только предупреждение). Сбои
 *     терминальны — повторов нет (используется {@link NoRetryHandler}). Алгоритм хеша на весь
 *     запуск задаётся координатором.<br>
 **/
public class DecompressStageManager extends AbstractStageManager
{
    private volatile HashType _hashType;

    private DecompressStageManager()
    {
        super(NoRetryHandler.INSTANCE);
        _hashType = null;
    }

    /**
     * EN: Sets the hash algorithm used for the optional hash-sum check (same for every file of a run). <br>
     * RU: Задаёт алгоритм хеша для опциональной проверки контрольной суммы (единый на весь запуск). <br>
     * ==================================================================<br>
     * EN: @param hashType the run's hash algorithm / RU: @param hashType алгоритм хеша запуска <br>
     **/
    public void setHashType(HashType hashType)
    {
        _hashType = hashType;
    }

    @Override
    protected TaskStage stage()
    {
        return TaskStage.DECOMPRESS;
    }

    /**
     * EN: Concatenates the raw parts, decompresses them into the task, and runs the optional
     *     size/hash validation. <br>
     * RU: Склеивает сырые части, распаковывает их в задачу и выполняет опциональную проверку
     *     размера/хеша. <br>
     * ==================================================================<br>
     * EN: @param task the task to decompress / RU: @param task задача для распаковки <br>
     **/
    @Override
    protected void processTask(FileDownloadTask task) throws Exception
    {
        FileInfoHolder fileInfo = task.getFileInfo();
        byte[] combined = concatParts(task.getDownloadedParts());
        byte[] out = Decompressors.decompress(fileInfo.getCompressType(), combined, fileInfo.getFileLength());
        validate(fileInfo, out);
        task.setDecompressed(out);
    }

    /**
     * EN: Verifies the decompressed length and hash-sum against the file metadata when the checks are
     *     enabled; a mismatch is logged as a warning (never silently swallowed, never fatal here). <br>
     * RU: Проверяет длину и контрольную сумму распакованных данных по метаданным файла, если проверки
     *     включены; несоответствие логируется как предупреждение (не глотается молча, не фатально). <br>
     * ==================================================================<br>
     * EN: @param fileInfo the file metadata / RU: @param fileInfo метаданные файла <br>
     * EN: @param out the decompressed bytes / RU: @param out распакованные байты <br>
     **/
    private void validate(FileInfoHolder fileInfo, byte[] out)
    {
        if (MainConfig.CHECK_FILE_SIZE)
        {
            int expected = fileInfo.getFileLength() != -1 ? fileInfo.getFileLength() : (fileInfo.getAccessLink() != null ? fileInfo.getAccessLink().getHttpLength() : -1);
            if (expected > 0 && out.length != expected)
            {
                IDummyLogger.log(IDummyLogger.WARNING, "File '" + fileInfo.getLinkPath() + "' has length " + out.length + " but expected " + expected + ".");
            }
        }
        if (MainConfig.CHECK_HASH_SUM)
        {
            String expectedHash = fileInfo.getFileHashSum();
            if (expectedHash == null)
            {
                IDummyLogger.log(IDummyLogger.WARNING, "File '" + fileInfo.getLinkPath() + "' has no original hash-sum; skipping hash check.");
            }
            else if (_hashType == null || !HashingManager.check(_hashType, out, expectedHash))
            {
                IDummyLogger.log(IDummyLogger.WARNING, "File '" + fileInfo.getLinkPath() + "' hash-sum differs from the original.");
            }
        }
    }

    /**
     * EN: Concatenates all non-null downloaded parts into a single contiguous byte array. <br>
     * RU: Склеивает все скачанные части, не равные null, в один непрерывный массив байтов. <br>
     * ==================================================================<br>
     * EN: @param parts the per-part byte arrays / RU: @param parts массивы байтов по частям <br>
     * @return <br>
     *         {byte[]} - EN: the concatenated bytes / RU: склеенные байты <br>
     **/
    private static byte[] concatParts(byte[][] parts)
    {
        if (parts == null)
        {
            return new byte[0];
        }
        int total = 0;
        for (byte[] part : parts)
        {
            if (part != null)
            {
                total += part.length;
            }
        }
        byte[] combined = new byte[total];
        int offset = 0;
        for (int index = 0; index < parts.length; index++)
        {
            byte[] part = parts[index];
            if (part != null)
            {
                System.arraycopy(part, 0, combined, offset, part.length);
                offset += part.length;
                // Drop the source reference as it is copied to reduce the transient compressed-data peak.
                parts[index] = null;
            }
        }
        return combined;
    }

    @Override
    protected DownloadFailureType classify(Throwable throwable)
    {
        return DownloadFailureType.DECOMPRESS_ERROR;
    }

    private static final DecompressStageManager INSTANCE = new DecompressStageManager();

    public static DecompressStageManager getInstance()
    {
        return INSTANCE;
    }
}
