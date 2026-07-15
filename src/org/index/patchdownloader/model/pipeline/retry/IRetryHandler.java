package org.index.patchdownloader.model.pipeline.retry;

import org.index.patchdownloader.instancemanager.AbstractStageManager;
import org.index.patchdownloader.model.pipeline.request.AbstractFileRequest;
import org.index.patchdownloader.model.pipeline.enums.DownloadFailureType;

/**
 * EN: Per-stage retry policy. A stage that fails hands the task to its retry handler, which either
 *     re-submits it into the owning stage's pool or lets the stage report a terminal failure.<br>
 * RU: Политика повторов конкретной стадии. Упавшая стадия передаёт задачу своему обработчику,
 *     который либо переотправляет её в пул этой стадии, либо позволяет стадии сообщить о
 *     терминальном сбое.<br>
 **/
public interface IRetryHandler
{
    /**
     * EN: Decides whether the given failure should be retried by re-running the owning stage. <br>
     * RU: Решает, стоит ли повторять данный сбой перезапуском текущей стадии. <br>
     * ==================================================================<br>
     * EN: @param task the failed task / RU: @param task проваленная задача <br>
     * EN: @param failure the classified failure reason / RU: @param failure классифицированная причина сбоя <br>
     * @return <br>
     *         {true}  - EN: retry (re-submit) / RU: повторить (переотправить) <br>
     *         {false} - EN: give up (terminal FAILED) / RU: сдаться (терминальный FAILED) <br>
     **/
    boolean shouldRetry(AbstractFileRequest task, DownloadFailureType failure);

    /**
     * EN: Performs the retry: bumps the attempt counter on the task and re-submits it into the
     *     owning stage's pool immediately (no backoff, no scheduler). <br>
     * RU: Выполняет повтор: увеличивает счётчик попыток и немедленно переотправляет задачу в пул
     *     этой стадии (без задержки и планировщика). <br>
     * ==================================================================<br>
     * EN: @param task the task to retry / RU: @param task задача для повтора <br>
     * EN: @param failure the classified failure reason / RU: @param failure классифицированная причина сбоя <br>
     * EN: @param owner the stage that owns the retry / RU: @param owner стадия, владеющая повтором <br>
     **/
    void onRetry(AbstractFileRequest task, DownloadFailureType failure, AbstractStageManager owner);
}
