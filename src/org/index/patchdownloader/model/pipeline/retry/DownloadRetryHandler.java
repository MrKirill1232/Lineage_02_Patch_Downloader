package org.index.patchdownloader.model.pipeline.retry;

import java.util.concurrent.TimeUnit;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.instancemanager.AbstractStageManager;
import org.index.patchdownloader.model.pipeline.FileDownloadTask;
import org.index.patchdownloader.model.pipeline.enums.DownloadFailureType;

/**
 * EN: Retry policy for the download stage. Retries only retryable failures and is bounded by a hard
 *     attempt cap AND a per-file wall-clock deadline — both apply even when
 *     {@code MAX_DOWNLOAD_ATTEMPTS == -1}, so a persistently-retryable URL can never spin forever.
 *     Re-submit is immediate (no backoff, no scheduler).<br>
 * RU: Политика повторов стадии загрузки. Повторяет только повторяемые сбои и ограничена жёстким
 *     потолком попыток И дедлайном по файлу — оба действуют даже при
 *     {@code MAX_DOWNLOAD_ATTEMPTS == -1}, поэтому постоянно повторяемый URL не может крутиться
 *     вечно. Переотправка немедленная (без задержки и планировщика).<br>
 **/
public class DownloadRetryHandler implements IRetryHandler
{
    /**
     * EN: Retryable AND under the soft cap (unless -1) AND under the absolute hard cap AND within
     *     the per-file wall-clock deadline. <br>
     * RU: Повторяемый И в пределах мягкого потолка (если не -1) И жёсткого потолка И дедлайна по
     *     файлу. <br>
     * ==================================================================<br>
     * EN: @param task the failed task / RU: @param task проваленная задача <br>
     * EN: @param failure the classified failure reason / RU: @param failure классифицированная причина сбоя <br>
     * @return <br>
     *         {true}  - EN: retry allowed / RU: повтор разрешён <br>
     *         {false} - EN: give up (terminal FAILED) / RU: сдаться (терминальный FAILED) <br>
     **/
    @Override
    public boolean shouldRetry(FileDownloadTask task, DownloadFailureType failure)
    {
        if (failure == null || !failure.isRetryable())
        {
            return false;
        }
        if (task.getAttempts() >= MainConfig.RETRY_HARD_CAP)
        {
            return false;
        }
        long deadlineMs = TimeUnit.SECONDS.toMillis(MainConfig.FILE_RETRY_DEADLINE_SECONDS);
        if (task.getFirstAttemptAt() != 0L && (System.currentTimeMillis() - task.getFirstAttemptAt()) >= deadlineMs)
        {
            return false;
        }
        if (MainConfig.MAX_DOWNLOAD_ATTEMPTS != -1 && task.getAttempts() >= MainConfig.MAX_DOWNLOAD_ATTEMPTS)
        {
            return false;
        }
        return true;
    }

    /**
     * EN: Bumps the attempt counter (via {@code markRetry}) and immediately re-submits the same task
     *     into the download pool. Budget stays held because there is no wait window. <br>
     * RU: Увеличивает счётчик попыток (через {@code markRetry}) и немедленно переотправляет ту же
     *     задачу в пул загрузки. Бюджет остаётся удержанным, так как окна ожидания нет. <br>
     * ==================================================================<br>
     * EN: @param task the task to retry / RU: @param task задача для повтора <br>
     * EN: @param failure the classified failure reason / RU: @param failure классифицированная причина сбоя <br>
     * EN: @param owner the download stage / RU: @param owner стадия загрузки <br>
     **/
    @Override
    public void onRetry(FileDownloadTask task, DownloadFailureType failure, AbstractStageManager owner)
    {
        task.markRetry(failure);
        owner.submit(task);
    }
}
