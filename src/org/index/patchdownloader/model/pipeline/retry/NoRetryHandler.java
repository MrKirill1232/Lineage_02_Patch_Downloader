package org.index.patchdownloader.model.pipeline.retry;

import org.index.patchdownloader.instancemanager.AbstractStageManager;
import org.index.patchdownloader.model.pipeline.FileDownloadTask;
import org.index.patchdownloader.model.pipeline.enums.DownloadFailureType;

/**
 * EN: A retry handler that never retries — used by the decompress and store stages, where a failure
 *     is treated as terminal by design ({@code FAILED}); such files are recovered by a later
 *     {@code -restore} run. Stateless singleton.<br>
 * RU: Обработчик повторов, который никогда не повторяет — используется стадиями decompress и store,
 *     где сбой по своей природе считается терминальным ({@code FAILED}); такие файлы
 *     восстанавливаются последующим запуском {@code -restore}. Синглтон без состояния.<br>
 **/
public final class NoRetryHandler implements IRetryHandler
{
    public static final NoRetryHandler INSTANCE = new NoRetryHandler();

    private NoRetryHandler()
    {
    }

    /**
     * EN: Always returns {@code false} — decompress/store failures are never retried. <br>
     * RU: Всегда возвращает {@code false} — сбои decompress/store не повторяются. <br>
     * ==================================================================<br>
     * EN: @param task the failed task / RU: @param task проваленная задача <br>
     * EN: @param failure the classified failure reason / RU: @param failure классифицированная причина сбоя <br>
     * @return <br>
     *         {false} - EN: never retry / RU: никогда не повторять <br>
     **/
    @Override
    public boolean shouldRetry(FileDownloadTask task, DownloadFailureType failure)
    {
        return false;
    }

    /**
     * EN: Unreachable no-op (kept to satisfy the interface); {@link #shouldRetry} is always false. <br>
     * RU: Недостижимая пустая реализация (для контракта интерфейса); {@link #shouldRetry} всегда false. <br>
     * ==================================================================<br>
     * EN: @param task the task / RU: @param task задача <br>
     * EN: @param failure the classified failure reason / RU: @param failure классифицированная причина сбоя <br>
     * EN: @param owner the owning stage / RU: @param owner владеющая стадия <br>
     **/
    @Override
    public void onRetry(FileDownloadTask task, DownloadFailureType failure, AbstractStageManager owner)
    {
        // no-op: shouldRetry() is always false for this handler
    }
}
