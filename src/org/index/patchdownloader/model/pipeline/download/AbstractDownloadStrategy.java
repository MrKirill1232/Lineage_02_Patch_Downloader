package org.index.patchdownloader.model.pipeline.download;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.model.holders.LinkInfoHolder;
import org.index.patchdownloader.model.pipeline.FileDownloadTask;
import org.index.patchdownloader.model.pipeline.HttpStatusException;
import org.index.patchdownloader.util.concurrent.PipelineExecutors;

/**
 * EN: Base of the download-strategy family. A single {@link FileDownloadTask} is fetched by exactly one
 *     strategy, chosen by {@link #supports(FileDownloadTask)} (first match wins). The concrete work lives
 *     in {@link #doDownload(FileDownloadTask)}, which the {@code final} {@link #download} wraps in one
 *     {@link java.util.concurrent.ForkJoinPool.ManagedBlocker} so the stage pool stays accounted while the
 *     worker blocks on I/O. Shared HTTP helpers (plain GET, ranged GET, whole-exchange timeout, bounded
 *     parallel fetch) live here so each strategy only expresses WHAT to fetch.<br>
 * RU: База семейства стратегий загрузки. Одна {@link FileDownloadTask} скачивается ровно одной стратегией,
 *     выбранной {@link #supports(FileDownloadTask)} (побеждает первое совпадение). Конкретная работа — в
 *     {@link #doDownload(FileDownloadTask)}, которую {@code final} {@link #download} оборачивает в один
 *     {@link java.util.concurrent.ForkJoinPool.ManagedBlocker}, чтобы пул стадии корректно учитывался, пока
 *     воркер ждёт I/O. Общие HTTP-хелперы (обычный GET, GET с диапазоном, таймаут всего обмена,
 *     ограниченная параллельная загрузка) — здесь, поэтому стратегия выражает только ЧТО скачивать.<br>
 **/
public abstract class AbstractDownloadStrategy
{
    protected static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(1);
    protected static final long EXCHANGE_TIMEOUT_SECONDS = 300;
    protected static final long ONE_MB = 1024L * 1024L;

    protected final HttpClient _httpClient;

    protected AbstractDownloadStrategy(HttpClient httpClient)
    {
        _httpClient = httpClient;
    }

    /**
     * EN: Whether this strategy can handle the given task (checked in priority order; the single-GET
     *     strategy is the always-true fallback). <br>
     * RU: Может ли эта стратегия обработать задачу (проверяется по приоритету; стратегия одиночного GET —
     *     всегда-истинный запасной вариант). <br>
     * ==================================================================<br>
     * EN: @param task the task to classify / RU: @param task классифицируемая задача <br>
     * @return <br>
     *         {true}  - EN: this strategy applies / RU: стратегия применима <br>
     *         {false} - EN: try the next strategy / RU: пробовать следующую стратегию <br>
     **/
    public abstract boolean supports(FileDownloadTask task);

    /**
     * EN: Downloads the task's bytes into its part slots, wrapped in a single {@code ManagedBlocker}. <br>
     * RU: Скачивает байты задачи в её слоты частей, обёрнуто в один {@code ManagedBlocker}. <br>
     * ==================================================================<br>
     * EN: @param task the task to download / RU: @param task задача для загрузки <br>
     **/
    public final void download(FileDownloadTask task) throws Exception
    {
        PipelineExecutors.managedBlock(() ->
        {
            doDownload(task);
            return Boolean.TRUE;
        });
    }

    /**
     * EN: Strategy-specific download body (already inside a {@code ManagedBlocker}); fills the task's parts. <br>
     * RU: Специфичное тело загрузки стратегии (уже внутри {@code ManagedBlocker}); заполняет части задачи. <br>
     * ==================================================================<br>
     * EN: @param task the task to download / RU: @param task задача для загрузки <br>
     **/
    protected abstract void doDownload(FileDownloadTask task) throws Exception;

    /**
     * EN: Builds a plain GET for the given link with the configured timeout and optional User-Agent. <br>
     * RU: Строит обычный GET для ссылки с настроенным таймаутом и опциональным User-Agent. <br>
     * ==================================================================<br>
     * EN: @param linkInfo the link holder / RU: @param linkInfo holder ссылки <br>
     * @return <br>
     *         {HttpRequest} - EN: the prepared request / RU: подготовленный запрос <br>
     **/
    protected HttpRequest buildRequest(LinkInfoHolder linkInfo)
    {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(linkInfo.getAccessLink())).timeout(REQUEST_TIMEOUT).GET();
        if (MainConfig.REQUESTED_USER_AGENT != null)
        {
            builder.header("User-Agent", MainConfig.REQUESTED_USER_AGENT);
        }
        return builder.build();
    }

    /**
     * EN: Builds a ranged GET ({@code Range: bytes=start-end}) with the configured timeout and User-Agent. <br>
     * RU: Строит GET с диапазоном ({@code Range: bytes=start-end}) с настроенным таймаутом и User-Agent. <br>
     * ==================================================================<br>
     * EN: @param linkInfo the link holder / RU: @param linkInfo holder ссылки <br>
     * EN: @param start first byte offset / RU: @param start смещение первого байта <br>
     * EN: @param end last byte offset inclusive / RU: @param end смещение последнего байта включительно <br>
     * @return <br>
     *         {HttpRequest} - EN: the prepared ranged request / RU: подготовленный запрос с диапазоном <br>
     **/
    protected HttpRequest rangeRequest(LinkInfoHolder linkInfo, long start, long end)
    {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(linkInfo.getAccessLink())).timeout(REQUEST_TIMEOUT).header("Range", "bytes=" + start + "-" + end).GET();
        if (MainConfig.REQUESTED_USER_AGENT != null)
        {
            builder.header("User-Agent", MainConfig.REQUESTED_USER_AGENT);
        }
        return builder.build();
    }

    /**
     * EN: Sends a request asynchronously and blocks up to a whole-exchange deadline (headers AND body). Any
     *     NON-success exit cancels the future so we never abandon a still-running exchange that keeps buffering
     *     the body into memory. Two exit paths reach a still-running future: a <b>timeout</b> (a stalled body —
     *     surfaces as a retryable {@code TimeoutException}) and an <b>interrupt</b> ({@code cancel} aborts the
     *     exchange and the interrupt flag is restored). A mid-request <b>disconnect / transport error</b>
     *     instead completes the future exceptionally (an {@code ExecutionException} wrapping the
     *     {@code IOException}); that is already memory-safe — the errored body subscriber has released its
     *     buffers — and is classified downstream as a retryable transport failure, so here {@code cancel} is a
     *     harmless no-op on an already-finished future. <br>
     * RU: Отправляет запрос асинхронно и блокируется до дедлайна всего обмена (заголовки И тело). Любой
     *     НЕ-успешный выход отменяет future, чтобы мы никогда не бросали ещё выполняющийся обмен, который
     *     продолжает копить тело в память. Ещё выполняющийся future дают два пути: <b>таймаут</b> (зависшее
     *     тело — становится повторяемым {@code TimeoutException}) и <b>прерывание</b> ({@code cancel} отменяет
     *     обмен, флаг прерывания восстанавливается). Разрыв соединения / <b>ошибка транспорта</b> в середине
     *     запроса вместо этого завершает future с исключением ({@code ExecutionException}, оборачивающим
     *     {@code IOException}); это уже безопасно по памяти — ошибочный подписчик тела освободил буферы — и
     *     классифицируется ниже как повторяемый сбой транспорта, поэтому здесь {@code cancel} — безвредный
     *     no-op на уже завершённом future. <br>
     * ==================================================================<br>
     * EN: @param request the request / RU: @param request запрос <br>
     * @return <br>
     *         {HttpResponse} - EN: the response with byte-array body / RU: ответ с телом-массивом байтов <br>
     **/
    protected HttpResponse<byte[]> sendWithExchangeTimeout(HttpRequest request) throws Exception
    {
        CompletableFuture<HttpResponse<byte[]>> future = _httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
        try
        {
            return future.get(EXCHANGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        catch (Exception e)
        {
            future.cancel(true);
            if (e instanceof InterruptedException)
            {
                Thread.currentThread().interrupt();
            }
            throw e;
        }
    }

    /**
     * EN: Single blocking GET: records status/length on the link holder and returns the body; raises
     *     {@link HttpStatusException} on a non-200. <br>
     * RU: Один блокирующий GET: записывает статус/длину в holder ссылки и возвращает тело; бросает
     *     {@link HttpStatusException} на не-200. <br>
     * ==================================================================<br>
     * EN: @param linkInfo the link holder / RU: @param linkInfo holder ссылки <br>
     * @return <br>
     *         {byte[]} - EN: the body bytes / RU: байты тела <br>
     **/
    protected byte[] fetchOne(LinkInfoHolder linkInfo) throws Exception
    {
        HttpResponse<byte[]> response = sendWithExchangeTimeout(buildRequest(linkInfo));
        byte[] body = response.body() == null ? new byte[0] : response.body();
        linkInfo.setHttpStatus(response.statusCode());
        linkInfo.setHttpLength(body.length);
        if (response.statusCode() != 200)
        {
            throw new HttpStatusException(response.statusCode());
        }
        return body;
    }

    /**
     * EN: Fetches all requests concurrently, at most {@code cap} in flight — a {@link Semaphore} acquired
     *     before each launch and released when that response settles (via {@code handle}, so the permit is
     *     freed on a transport error too, not only on success). Each response invokes {@code handler} on the
     *     HTTP-client thread (must be thread-safe). The batch is awaited within one whole-exchange deadline;
     *     the FIRST transport or handler error is captured (into an {@link AtomicReference}, so every request
     *     still settles and frees its permit) and rethrown once the batch has drained. Any NON-success exit —
     *     a deadline {@code TimeoutException} OR an interrupt while submitting/awaiting — cancels every
     *     underlying exchange (the raw {@code sendAsync} futures, NOT the derived {@code handle} stages:
     *     {@link CompletableFuture} cancellation does not propagate upstream, so cancelling the handle stage
     *     would leave the download running and buffering) so none keeps buffering its body in the background,
     *     and restores the interrupt flag. This is non-blocking (cancel never blocks; the gate is a local
     *     per-call semaphore nothing else waits on), so the caller unwinds immediately — no deadlock. <br>
     * RU: Качает все запросы параллельно, не более {@code cap} одновременно — {@link Semaphore} занимается
     *     перед каждым запуском и освобождается, когда этот ответ завершился (через {@code handle}, поэтому
     *     квота освобождается и при ошибке транспорта, а не только при успехе). Каждый ответ вызывает
     *     {@code handler} в потоке HTTP-клиента (должен быть потокобезопасным). Партия ожидается в пределах
     *     одного дедлайна всего обмена; ПЕРВАЯ ошибка транспорта или обработчика захватывается (в
     *     {@link AtomicReference}, поэтому каждый запрос всё равно завершается и освобождает квоту) и
     *     перебрасывается после осушения партии. Любой НЕ-успешный выход — {@code TimeoutException} по дедлайну
     *     ИЛИ прерывание во время отправки/ожидания — отменяет каждый нижележащий обмен (сырые
     *     {@code sendAsync}-futures, а НЕ производные стадии {@code handle}: отмена {@link CompletableFuture} не
     *     распространяется вверх, поэтому отмена стадии handle оставила бы загрузку работающей и копящей тело),
     *     чтобы ни один не копил тело в фоне, и восстанавливает флаг прерывания. Это неблокирующе (cancel
     *     никогда не блокирует; gate — локальный семафор на вызов, которого никто больше не ждёт), поэтому
     *     вызывающий код сразу возвращает управление (раскрутка стека) — без deadlock. <br>
     * ==================================================================<br>
     * EN: @param requests the requests to run / RU: @param requests запросы для выполнения <br>
     * EN: @param cap max concurrent requests / RU: @param cap максимум одновременных запросов <br>
     * EN: @param handler per-response callback / RU: @param handler колбэк на каждый ответ <br>
     **/
    protected void fetchConcurrently(List<HttpRequest> requests, int cap, PartHandler handler) throws Exception
    {
        Semaphore gate = new Semaphore(Math.max(1, cap));
        AtomicReference<Throwable> firstError = new AtomicReference<>();
        List<CompletableFuture<HttpResponse<byte[]>>> exchanges = new ArrayList<>(requests.size());
        List<CompletableFuture<Void>> completions = new ArrayList<>(requests.size());
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(EXCHANGE_TIMEOUT_SECONDS);
        try
        {
            for (int index = 0; index < requests.size(); index++)
            {
                if (firstError.get() != null)
                {
                    break;
                }
                final int requestIndex = index;
                if (!gate.tryAcquire(deadlineNanos - System.nanoTime(), TimeUnit.NANOSECONDS))
                {
                    throw new TimeoutException("batch stalled during submission");
                }
                CompletableFuture<HttpResponse<byte[]>> exchange = _httpClient.sendAsync(requests.get(index), HttpResponse.BodyHandlers.ofByteArray());
                exchanges.add(exchange);
                completions.add(exchange.handle((response, error) ->
                {
                    gate.release();
                    if (error != null)
                    {
                        firstError.compareAndSet(null, error);
                        return null;
                    }
                    try
                    {
                        handler.handle(requestIndex, response);
                    }
                    catch (Throwable t)
                    {
                        firstError.compareAndSet(null, t);
                    }
                    return null;
                }));
            }
            CompletableFuture.allOf(completions.toArray(new CompletableFuture[0])).get(Math.max(0L, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS);
        }
        catch (Exception e)
        {
            for (CompletableFuture<HttpResponse<byte[]>> exchange : exchanges)
            {
                exchange.cancel(true);
            }
            if (e instanceof InterruptedException)
            {
                Thread.currentThread().interrupt();
            }
            throw e;
        }
        Throwable error = firstError.get();
        if (error != null)
        {
            if (error instanceof Exception exception)
            {
                throw exception;
            }
            throw new ExecutionException(error);
        }
    }

    /**
     * EN: Callback invoked for each response of a concurrent batch; may throw to fail the batch. <br>
     * RU: Колбэк на каждый ответ параллельной партии; может бросить, чтобы провалить партию. <br>
     **/
    @FunctionalInterface
    protected interface PartHandler
    {
        void handle(int index, HttpResponse<byte[]> response) throws Exception;
    }
}
