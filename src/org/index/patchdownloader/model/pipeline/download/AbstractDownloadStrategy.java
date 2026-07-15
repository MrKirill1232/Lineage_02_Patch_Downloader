package org.index.patchdownloader.model.pipeline.download;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntPredicate;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.interfaces.IDownloadRequest;
import org.index.patchdownloader.model.holders.LinkInfoHolder;
import org.index.patchdownloader.model.pipeline.HttpStatusException;
import org.index.patchdownloader.util.concurrent.PipelineExecutors;

/**
 * EN: Base of the download-strategy family. A single {@link IDownloadRequest} is fetched by exactly one
 *     strategy, chosen by {@link #supports(IDownloadRequest)} (first match wins). The concrete work lives
 *     in {@link #doDownload(IDownloadRequest)}, which the {@code final} {@link #download} wraps in one
 *     {@link java.util.concurrent.ForkJoinPool.ManagedBlocker} so the stage pool stays accounted while the
 *     worker blocks on I/O. Shared HTTP helpers (plain GET, ranged GET, whole-exchange timeout, bounded
 *     parallel fetch) live here so each strategy only expresses WHAT to fetch. The strategy streams the raw
 *     bytes into the request through {@link IDownloadRequest} instead of returning a whole {@code byte[]}.<br>
 * RU: База семейства стратегий загрузки. Один {@link IDownloadRequest} скачивается ровно одной стратегией,
 *     выбранной {@link #supports(IDownloadRequest)} (побеждает первое совпадение). Конкретная работа — в
 *     {@link #doDownload(IDownloadRequest)}, которую {@code final} {@link #download} оборачивает в один
 *     {@link java.util.concurrent.ForkJoinPool.ManagedBlocker}, чтобы пул стадии корректно учитывался, пока
 *     воркер ждёт I/O. Общие HTTP-хелперы (обычный GET, GET с диапазоном, таймаут всего обмена,
 *     ограниченная параллельная загрузка) — здесь, поэтому стратегия выражает только ЧТО скачивать. Стратегия
 *     потоково пишет сырые байты в запрос через {@link IDownloadRequest}, а не возвращает целый {@code byte[]}.<br>
 **/
public abstract class AbstractDownloadStrategy
{
    protected static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(1);
    // Configurable whole-exchange deadline (default 30 min), captured at class-load — which happens after the
    // config (incl. CLI) is applied — so a legitimately large file is no longer capped at a flat 5 minutes.
    protected static final long EXCHANGE_TIMEOUT_SECONDS = MainConfig.EXCHANGE_TIMEOUT_SECONDS;
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
     * EN: @param request the request to classify / RU: @param request классифицируемый запрос <br>
     * @return <br>
     *         {true}  - EN: this strategy applies / RU: стратегия применима <br>
     *         {false} - EN: try the next strategy / RU: пробовать следующую стратегию <br>
     **/
    public abstract boolean supports(IDownloadRequest request);

    /**
     * EN: Streams the request's raw bytes into its part slots, wrapped in a single {@code ManagedBlocker}. <br>
     * RU: Потоково пишет сырые байты запроса в его слоты частей, обёрнуто в один {@code ManagedBlocker}. <br>
     * ==================================================================<br>
     * EN: @param request the request to download / RU: @param request запрос для загрузки <br>
     **/
    public final void download(IDownloadRequest request) throws Exception
    {
        PipelineExecutors.managedBlock(() ->
        {
            doDownload(request);
            return Boolean.TRUE;
        });
    }

    /**
     * EN: Strategy-specific download body (already inside a {@code ManagedBlocker}); streams into the request's parts. <br>
     * RU: Специфичное тело загрузки стратегии (уже внутри {@code ManagedBlocker}); пишет в части запроса потоково. <br>
     * ==================================================================<br>
     * EN: @param request the request to download / RU: @param request запрос для загрузки <br>
     **/
    protected abstract void doDownload(IDownloadRequest request) throws Exception;

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
     * EN: Sends a request asynchronously with the given body handler and blocks up to a whole-exchange
     *     deadline (headers AND body). Because the body handler here is the streaming subscriber, whose body
     *     future only completes once every chunk has been consumed, the deadline still bounds the WHOLE
     *     exchange, not merely the headers. Any NON-success exit cancels the future so we never abandon a
     *     still-running exchange that keeps pulling bytes: a <b>timeout</b> (a stalled body — surfaces as a
     *     retryable {@code TimeoutException}) and an <b>interrupt</b> ({@code cancel} aborts the exchange and
     *     the interrupt flag is restored). A mid-request <b>disconnect / transport error</b> instead completes
     *     the future exceptionally (an {@code ExecutionException} wrapping the {@code IOException}); that is
     *     already memory-safe — the errored subscriber released its buffers — and is classified downstream as a
     *     retryable transport failure, so here {@code cancel} is a harmless no-op on an already-finished
     *     future. <br>
     * RU: Отправляет запрос асинхронно с указанным обработчиком тела и блокируется до дедлайна всего обмена
     *     (заголовки И тело). Поскольку обработчик тела здесь — потоковый подписчик, чей future тела
     *     завершается только после потребления каждого куска, дедлайн по-прежнему ограничивает ВЕСЬ обмен, а
     *     не только заголовки. Любой НЕ-успешный выход отменяет future, чтобы мы никогда не бросали ещё
     *     выполняющийся обмен, продолжающий тянуть байты: <b>таймаут</b> (зависшее тело — становится
     *     повторяемым {@code TimeoutException}) и <b>прерывание</b> ({@code cancel} отменяет обмен, флаг
     *     прерывания восстанавливается). Разрыв соединения / <b>ошибка транспорта</b> в середине запроса вместо
     *     этого завершает future с исключением ({@code ExecutionException}, оборачивающим {@code IOException});
     *     это уже безопасно по памяти — ошибочный подписчик освободил буферы — и классифицируется ниже как
     *     повторяемый сбой транспорта, поэтому здесь {@code cancel} — безвредный no-op на уже завершённом
     *     future. <br>
     * ==================================================================<br>
     * EN: @param request the request / RU: @param request запрос <br>
     * EN: @param bodyHandler the body handler / RU: @param bodyHandler обработчик тела <br>
     * @return <br>
     *         {HttpResponse} - EN: the settled response / RU: завершённый ответ <br>
     **/
    protected <T> HttpResponse<T> sendWithExchangeTimeout(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler) throws Exception
    {
        CompletableFuture<HttpResponse<T>> future = _httpClient.sendAsync(request, bodyHandler);
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
     * EN: A body handler that streams the response body straight into a {@link ChunkSink} as it arrives, instead
     *     of buffering a whole {@code byte[]}. The status decides the mode: when {@code streamStatus} accepts it,
     *     the body feeds the sink; otherwise the body is REJECTED, and {@code cancelOnReject} chooses how — either
     *     CANCEL it (abort before pulling any body: a large unwanted body, e.g. a whole-file 200 whose Range the
     *     server ignored, is never dragged over the network — at the cost of tearing the connection down) or DRAIN
     *     it (read and discard, so a small error body still frees the connection for keep-alive). The resolved body
     *     value is the number of body bytes seen. <br>
     * RU: Обработчик тела, который потоково пишет тело ответа прямо в {@link ChunkSink} по мере поступления, а не
     *     буферизует целый {@code byte[]}. Статус выбирает режим: когда {@code streamStatus} его принимает, тело
     *     кормит приёмник; иначе тело ОТКЛОНЯЕТСЯ, и {@code cancelOnReject} решает как — либо ОТМЕНИТЬ его
     *     (оборвать до вытягивания какого-либо тела: большое ненужное тело, напр. файл целиком по 200, чей Range
     *     сервер проигнорировал, ни разу не тянется по сети — ценой разрыва соединения), либо ВЫЧИТАТЬ (прочитать и
     *     отбросить, чтобы маленькое тело ошибки всё же освободило соединение для keep-alive). Итоговое значение
     *     тела — число увиденных байтов. <br>
     * ==================================================================<br>
     * EN: @param streamStatus which statuses feed the sink / RU: @param streamStatus какие статусы кормят приёмник <br>
     * EN: @param cancelOnReject for a rejected status, true = cancel (do not pull the body), false = drain / RU: @param cancelOnReject для отклонённого статуса: true = отменить (не тянуть тело), false = вычитать <br>
     * EN: @param sink the destination of the streamed chunks / RU: @param sink назначение потоковых кусков <br>
     * @return <br>
     *         {HttpResponse.BodyHandler} - EN: the streaming body handler / RU: потоковый обработчик тела <br>
     **/
    protected HttpResponse.BodyHandler<Long> streamingHandler(IntPredicate streamStatus, IntPredicate cancelOnReject, ChunkSink sink)
    {
        return responseInfo ->
        {
            int status = responseInfo.statusCode();
            if (streamStatus.test(status))
            {
                return new StreamingBodySubscriber(sink, false);
            }
            return new StreamingBodySubscriber(null, cancelOnReject.test(status));
        };
    }

    /**
     * EN: Single blocking GET streamed into the given sink: records the real status/length on the link holder
     *     and raises {@link HttpStatusException} on a non-200. Only a {@code 200} body is fed to the sink; a
     *     non-200 body is drained and discarded (its bytes never reach the sink) before the status is thrown. <br>
     * RU: Один блокирующий GET, потоково записанный в приёмник: фиксирует реальный статус/длину в holder ссылки
     *     и бросает {@link HttpStatusException} на не-200. В приёмник попадает только тело {@code 200}; тело
     *     не-200 вычитывается и отбрасывается (его байты не доходят до приёмника) до того, как бросается
     *     статус. <br>
     * ==================================================================<br>
     * EN: @param linkInfo the link holder / RU: @param linkInfo holder ссылки <br>
     * EN: @param sink the destination of the streamed bytes / RU: @param sink назначение потоковых байтов <br>
     * @return <br>
     *         {long} - EN: the number of body bytes / RU: число байтов тела <br>
     **/
    protected long streamOne(LinkInfoHolder linkInfo, ChunkSink sink) throws Exception
    {
        // A non-200 body here is a small error page: drain it (cancelOnReject = false) so the connection stays
        // reusable rather than being torn down.
        HttpResponse<Long> response = sendWithExchangeTimeout(buildRequest(linkInfo), streamingHandler(status -> status == 200, status -> false, sink));
        long count = response.body() == null ? 0L : response.body();
        linkInfo.setHttpStatus(response.statusCode());
        linkInfo.setHttpLength(count);
        if (response.statusCode() != 200)
        {
            throw new HttpStatusException(response.statusCode());
        }
        return count;
    }

    /**
     * EN: Fetches all requests concurrently, at most {@code cap} in flight — a {@link Semaphore} acquired
     *     before each launch and released when that response settles (via {@code handle}, so the permit is
     *     freed on a transport error too, not only on success). Each response's body is streamed straight into
     *     the {@link StreamHandler}'s per-index {@link ChunkSink} on the HTTP-client thread (so the sink must be
     *     thread-safe for disjoint targets), and {@link StreamHandler#settled} is invoked once it has fully
     *     drained (also on the client thread). The batch is awaited within one whole-exchange deadline; the
     *     FIRST transport or handler error is captured (into an {@link AtomicReference}, so every request still
     *     settles and frees its permit) and rethrown once the batch has drained. Any NON-success exit — a
     *     deadline {@code TimeoutException} OR an interrupt while submitting/awaiting — cancels every underlying
     *     exchange (the raw {@code sendAsync} futures, NOT the derived {@code handle} stages:
     *     {@link CompletableFuture} cancellation does not propagate upstream, so cancelling the handle stage
     *     would leave the download running and streaming) so none keeps pulling its body in the background, and
     *     restores the interrupt flag. This is non-blocking (cancel never blocks; the gate is a local per-call
     *     semaphore nothing else waits on), so the caller unwinds immediately — no deadlock. <br>
     * RU: Качает все запросы параллельно, не более {@code cap} одновременно — {@link Semaphore} занимается
     *     перед каждым запуском и освобождается, когда этот ответ завершился (через {@code handle}, поэтому
     *     квота освобождается и при ошибке транспорта, а не только при успехе). Тело каждого ответа потоково
     *     пишется прямо в {@link ChunkSink} нужного индекса из {@link StreamHandler} в потоке HTTP-клиента
     *     (поэтому приёмник должен быть потокобезопасен для непересекающихся целей), а {@link StreamHandler#settled}
     *     вызывается после полного вычитывания (тоже в потоке клиента). Партия ожидается в пределах одного
     *     дедлайна всего обмена; ПЕРВАЯ ошибка транспорта или обработчика захватывается (в
     *     {@link AtomicReference}, поэтому каждый запрос всё равно завершается и освобождает квоту) и
     *     перебрасывается после осушения партии. Любой НЕ-успешный выход — {@code TimeoutException} по дедлайну
     *     ИЛИ прерывание во время отправки/ожидания — отменяет каждый нижележащий обмен (сырые
     *     {@code sendAsync}-futures, а НЕ производные стадии {@code handle}: отмена {@link CompletableFuture} не
     *     распространяется вверх, поэтому отмена стадии handle оставила бы загрузку работающей и потоково
     *     тянущей тело), чтобы ни один не тянул тело в фоне, и восстанавливает флаг прерывания. Это неблокирующе
     *     (cancel никогда не блокирует; gate — локальный семафор на вызов, которого никто больше не ждёт),
     *     поэтому вызывающий код сразу возвращает управление (раскрутка стека) — без deadlock. <br>
     * ==================================================================<br>
     * EN: @param requests the requests to run / RU: @param requests запросы для выполнения <br>
     * EN: @param cap max concurrent requests / RU: @param cap максимум одновременных запросов <br>
     * EN: @param handler per-index sink + settle callback / RU: @param handler приёмник по индексу + колбэк завершения <br>
     **/
    protected void fetchConcurrently(List<HttpRequest> requests, int cap, StreamHandler handler) throws Exception
    {
        Semaphore gate = new Semaphore(Math.max(1, cap));
        AtomicReference<Throwable> firstError = new AtomicReference<>();
        List<CompletableFuture<HttpResponse<Long>>> exchanges = new ArrayList<>(requests.size());
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
                CompletableFuture<HttpResponse<Long>> exchange = _httpClient.sendAsync(requests.get(index), streamingHandler(status -> handler.streamStatus(requestIndex, status), status -> handler.cancelOnReject(requestIndex, status), handler.sink(requestIndex)));
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
                        handler.settled(requestIndex, response.statusCode(), response.body() == null ? 0L : response.body());
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
            for (CompletableFuture<HttpResponse<Long>> exchange : exchanges)
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
     * EN: Destination for streamed raw bytes: one call per chunk as the body arrives, with the chunk's
     *     absolute {@code offset} within its part (or range). An in-memory request appends the chunk sequentially
     *     and ignores the offset; a temp-file request writes it at that offset. The sink consumes the buffer's
     *     remaining bytes. <br>
     * RU: Назначение для потоковых сырых байтов: один вызов на кусок по мере поступления тела, с абсолютным
     *     {@code offset} куска внутри его части (или диапазона). Запрос в памяти дописывает кусок
     *     последовательно и игнорирует смещение; запрос с временным файлом пишет его по этому смещению.
     *     Приёмник потребляет оставшиеся байты буфера. <br>
     **/
    @FunctionalInterface
    protected interface ChunkSink
    {
        void accept(long offset, ByteBuffer chunk) throws IOException;
    }

    /**
     * EN: Per-index control for a concurrent streaming batch: the {@link ChunkSink} a request's chunks land in,
     *     which statuses actually feed that sink ({@link #streamStatus}), and a post-drain hook
     *     ({@link #settled}) that records status/length and may throw to fail the whole batch. <br>
     * RU: Поиндексное управление параллельной потоковой партией: {@link ChunkSink}, куда ложатся куски запроса,
     *     какие статусы реально кормят этот приёмник ({@link #streamStatus}), и хук после осушения
     *     ({@link #settled}), который фиксирует статус/длину и может бросить, чтобы провалить всю партию. <br>
     **/
    protected interface StreamHandler
    {
        /**
         * EN: The sink the given request's streamed chunks are written into. <br>
         * RU: Приёмник, в который пишутся потоковые куски указанного запроса. <br>
         * ==================================================================<br>
         * EN: @param index the request index / RU: @param index индекс запроса <br>
         * @return <br>
         *         {ChunkSink} - EN: the destination sink / RU: приёмник-назначение <br>
         **/
        ChunkSink sink(int index);

        /**
         * EN: Whether a response with the given status should be streamed into the sink (otherwise its body is
         *     rejected — drained or, per {@link #cancelOnReject}, cancelled — leaving the sink untouched). <br>
         * RU: Нужно ли ответ с данным статусом писать в приёмник (иначе его тело отклоняется — вычитывается либо, по
         *     {@link #cancelOnReject}, отменяется — не трогая приёмник). <br>
         * ==================================================================<br>
         * EN: @param index the request index / RU: @param index индекс запроса <br>
         * EN: @param statusCode the response status / RU: @param statusCode статус ответа <br>
         * @return <br>
         *         {true}  - EN: stream into the sink / RU: писать в приёмник <br>
         *         {false} - EN: reject (drain or cancel) / RU: отклонить (вычитать или отменить) <br>
         **/
        boolean streamStatus(int index, int statusCode);

        /**
         * EN: For a status that is NOT streamed (rejected by {@link #streamStatus}), whether to CANCEL the body
         *     immediately (never pull it) instead of draining-and-discarding it. Cancelling avoids dragging a large
         *     unwanted body over the network (a whole-file {@code 200} whose Range the server ignored) but tears the
         *     connection down; draining keeps the connection alive and suits a small error body. Default
         *     {@code false} (drain). <br>
         * RU: Для статуса, который НЕ пишется в приёмник (отклонён {@link #streamStatus}) — отменять ли тело сразу
         *     (не вытягивая его вовсе), вместо вычитывания-и-отбрасывания. Отмена избегает протаскивания большого
         *     ненужного тела по сети (файл целиком по {@code 200}, чей Range сервер проигнорировал), но рвёт
         *     соединение; вычитывание сохраняет соединение и подходит для маленького тела ошибки. По умолчанию
         *     {@code false} (вычитывать). <br>
         * ==================================================================<br>
         * EN: @param index the request index / RU: @param index индекс запроса <br>
         * EN: @param statusCode the response status / RU: @param statusCode статус ответа <br>
         * @return <br>
         *         {true}  - EN: cancel the body (do not pull it) / RU: отменить тело (не тянуть его) <br>
         *         {false} - EN: drain and discard / RU: вычитать и отбросить <br>
         **/
        default boolean cancelOnReject(int index, int statusCode)
        {
            return false;
        }

        /**
         * EN: Called once the given request has fully drained; records status/length and may throw to fail the
         *     batch (e.g. a non-success status or an ignored Range). <br>
         * RU: Вызывается после полного вычитывания запроса; фиксирует статус/длину и может бросить, чтобы
         *     провалить партию (например, не-успешный статус или проигнорированный Range). <br>
         * ==================================================================<br>
         * EN: @param index the request index / RU: @param index индекс запроса <br>
         * EN: @param statusCode the response status / RU: @param statusCode статус ответа <br>
         * EN: @param bytesStreamed the body byte count / RU: @param bytesStreamed число байтов тела <br>
         **/
        void settled(int index, int statusCode, long bytesStreamed) throws Exception;
    }

    /**
     * EN: A backpressured {@link HttpResponse.BodySubscriber} that pushes each incoming {@link ByteBuffer}
     *     straight into a {@link ChunkSink} (or discards it when the sink is {@code null}), tracking a running
     *     offset within the part and the total byte count that becomes the resolved body value. It requests one
     *     item at a time so at most a small number of buffers are ever in flight — the memory bound that
     *     replaces the whole-{@code byte[]} body. A sink failure cancels the subscription and completes the body
     *     future exceptionally, surfacing as a retryable transport-style error. <br>
     * RU: Потоковый {@link HttpResponse.BodySubscriber} с обратным давлением, который проталкивает каждый
     *     входящий {@link ByteBuffer} прямо в {@link ChunkSink} (или отбрасывает при {@code null}-приёмнике),
     *     ведя текущее смещение внутри части и суммарное число байтов, которое становится итоговым значением
     *     тела. Он запрашивает по одному элементу за раз, поэтому в полёте всегда лишь небольшое число буферов —
     *     ограничение памяти, заменяющее тело в виде целого {@code byte[]}. Сбой приёмника отменяет подписку и
     *     завершает future тела с исключением, проявляясь как повторяемая ошибка транспортного рода. <br>
     **/
    private static final class StreamingBodySubscriber implements HttpResponse.BodySubscriber<Long>
    {
        private final ChunkSink _sink;
        private final boolean _cancelImmediately;
        private final CompletableFuture<Long> _result;
        private Flow.Subscription _subscription;
        private long _offset;
        private long _count;

        private StreamingBodySubscriber(ChunkSink sink, boolean cancelImmediately)
        {
            _sink = sink;
            _cancelImmediately = cancelImmediately;
            _result = new CompletableFuture<>();
            _offset = 0L;
            _count = 0L;
        }

        @Override
        public CompletionStage<Long> getBody()
        {
            return _result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription)
        {
            _subscription = subscription;
            if (_cancelImmediately)
            {
                // Reject-and-abort: cancel BEFORE requesting any body, so a large unwanted body (a whole-file 200
                // whose Range the server ignored) is never pulled over the network. This tears the connection down
                // (no keep-alive) — the right trade only for a large body; a small error body is drained instead
                // (decided in streamingHandler). Complete the body future so the exchange settles and the caller's
                // settled() hook still runs.
                subscription.cancel();
                _result.complete(_count);
                return;
            }
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> items)
        {
            try
            {
                for (ByteBuffer buffer : items)
                {
                    int length = buffer.remaining();
                    if (length == 0)
                    {
                        continue;
                    }
                    if (_sink != null)
                    {
                        _sink.accept(_offset, buffer);
                    }
                    _offset += length;
                    _count += length;
                }
                _subscription.request(1);
            }
            catch (Throwable t)
            {
                _subscription.cancel();
                _result.completeExceptionally(t);
            }
        }

        @Override
        public void onError(Throwable throwable)
        {
            _result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete()
        {
            _result.complete(_count);
        }
    }
}
