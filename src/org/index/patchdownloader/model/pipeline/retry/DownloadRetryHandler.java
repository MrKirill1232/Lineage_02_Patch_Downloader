package org.index.patchdownloader.model.pipeline.retry;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.instancemanager.AbstractStageManager;
import org.index.patchdownloader.model.pipeline.request.AbstractFileRequest;
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
    private static final long BASE_BACKOFF_MS = 1000L;
    private static final long MAX_BACKOFF_MS = 60_000L;

    /**
     * EN: Shared daemon scheduler that defers a rate-limited/server-error resubmit by an exponential delay, so a
     *     server that returned 429/5xx is not immediately hammered again (the pipeline previously resubmitted with
     *     zero delay). Single-thread: it only sleeps and re-submits, never does real work. <br>
     * RU: Общий демон-планировщик, откладывающий повтор при rate-limit/ошибке сервера на экспоненциальную задержку,
     *     чтобы сервер, вернувший 429/5xx, не долбили сразу заново (раньше конвейер переотправлял без задержки).
     *     Однопоточный: только спит и переотправляет, реальной работы не делает. <br>
     **/
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(backoffThreadFactory());

    private static ThreadFactory backoffThreadFactory()
    {
        AtomicInteger counter = new AtomicInteger(0);
        return runnable ->
        {
            Thread thread = new Thread(runnable, "download-retry-backoff-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

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
    public boolean shouldRetry(AbstractFileRequest task, DownloadFailureType failure)
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
    public void onRetry(AbstractFileRequest task, DownloadFailureType failure, AbstractStageManager owner)
    {
        task.markRetry(failure);
        if (failure == DownloadFailureType.RATE_LIMIT || failure == DownloadFailureType.SERVER_ERROR)
        {
            // The server asked us to slow down (429) or is failing (5xx): defer the resubmit by an exponential
            // backoff instead of hammering it immediately. The budget stays held during the wait (backpressure).
            long delay = Math.min(MAX_BACKOFF_MS, BASE_BACKOFF_MS << Math.min(Math.max(task.getAttempts() - 1, 0), 16));
            SCHEDULER.schedule(() -> owner.submit(task), delay, TimeUnit.MILLISECONDS);
        }
        else
        {
            // A plain transport hiccup: resubmit immediately (no server asked us to wait).
            owner.submit(task);
        }
    }
}
