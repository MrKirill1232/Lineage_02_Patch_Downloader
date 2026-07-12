package org.index.patchdownloader.instancemanager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.model.pipeline.FileDownloadTask;
import org.index.patchdownloader.model.pipeline.enums.DownloadFailureType;
import org.index.patchdownloader.model.pipeline.enums.TaskStage;
import org.index.patchdownloader.model.pipeline.retry.NoRetryHandler;
import org.index.patchdownloader.util.FileUtils;
import org.index.patchdownloader.util.concurrent.PipelineExecutors;

/**
 * EN: Store stage (last in the chain). Writes the decompressed bytes to {@code DOWNLOAD_PATH/linkPath}
 *     via try-with-resources, wrapped in {@code managedBlock}. On success the base success handler
 *     marks the task DONE and reports it to the sink. IO errors are terminal ({@link NoRetryHandler})
 *     and are recovered by a later {@code -restore} run.<br>
 * RU: Стадия сохранения (последняя в цепочке). Пишет распакованные байты в
 *     {@code DOWNLOAD_PATH/linkPath} через try-with-resources, обёрнутый в {@code managedBlock}. При
 *     успехе базовый обработчик помечает задачу DONE и сообщает приёмнику. Ошибки IO неустранимы
 *     ({@link NoRetryHandler}) и исправляются последующим запуском {@code -restore}.<br>
 **/
public class StoreStageManager extends AbstractStageManager
{
    private StoreStageManager()
    {
        super(NoRetryHandler.INSTANCE);
    }

    @Override
    protected TaskStage stage()
    {
        return TaskStage.STORE;
    }

    /**
     * EN: Writes the task's decompressed bytes to the output file, wrapped in a {@code ManagedBlocker}
     *     so the dedicated pool stays accounted during the blocking write. <br>
     * RU: Пишет распакованные байты задачи в выходной файл, обёрнуто в {@code ManagedBlocker}, чтобы
     *     выделенный пул корректно учитывался во время блокирующей записи. <br>
     * ==================================================================<br>
     * EN: @param task the task to store / RU: @param task задача для сохранения <br>
     **/
    @Override
    protected void processTask(FileDownloadTask task) throws Exception
    {
        File target = new File(MainConfig.DOWNLOAD_PATH, task.getLinkPath());
        FileUtils.ensureWithinDirectory(MainConfig.DOWNLOAD_PATH, target, task.getLinkPath());
        byte[] data = task.getDecompressed();
        PipelineExecutors.managedBlock(() ->
        {
            writeFile(target, data);
            return Boolean.TRUE;
        });
    }

    /**
     * EN: Writes the bytes to the target file using try-with-resources (no swallowed IO error). <br>
     * RU: Пишет байты в целевой файл через try-with-resources (без проглоченной ошибки IO). <br>
     * ==================================================================<br>
     * EN: @param target the destination file / RU: @param target целевой файл <br>
     * EN: @param data the bytes to write / RU: @param data байты для записи <br>
     **/
    private static void writeFile(File target, byte[] data) throws IOException
    {
        try (FileOutputStream fileOutputStream = new FileOutputStream(target))
        {
            fileOutputStream.write(data == null ? new byte[0] : data);
            fileOutputStream.flush();
        }
    }

    @Override
    protected DownloadFailureType classify(Throwable throwable)
    {
        return DownloadFailureType.STORE_ERROR;
    }

    private static final StoreStageManager INSTANCE = new StoreStageManager();

    public static StoreStageManager getInstance()
    {
        return INSTANCE;
    }
}
