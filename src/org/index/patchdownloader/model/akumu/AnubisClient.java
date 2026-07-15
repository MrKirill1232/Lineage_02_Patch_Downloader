package org.index.patchdownloader.model.akumu;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.interfaces.IDummyLogger;
import org.index.patchdownloader.model.pipeline.HttpStatusException;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 * EN: A single, shared, cookie-backed HTTP session for the akumu mirror that transparently passes the Anubis
 *     antibot. One instance per run (singleton) is deliberate: the tool must behave like ONE browser tab —
 *     one cookie jar, one User-Agent, and a small hard cap on concurrent connections — so it is polite to a
 *     source that limits connections, not a scraper. {@link #fetch(String)} performs a GET and:
 *     <ul>
 *       <li>detects the Anubis challenge even when it arrives with HTTP 200 (the interstitial is served as a
 *           200 {@code text/html} page, so a naive client would silently save the challenge HTML instead of
 *           the file), solves the proof-of-work once ({@link AnubisSolver}) and retries;</li>
 *       <li>backs off (exponential) on 429 / 5xx and retries a bounded number of times, then surfaces the
 *           status so the pipeline's own retry can take over;</li>
 *       <li>bounds real concurrency with a {@link Semaphore} ({@code akumu_max_connections}) regardless of the
 *           download stage's parallelism.</li>
 *     </ul>
 *     Solving is single-flight (only one thread solves; others within a short window just retry with the fresh
 *     cookie), so a burst of expiries does not trigger a burst of proofs.<br>
 * RU: Единая общая HTTP-сессия с cookie для зеркала akumu, прозрачно проходящая антибот Anubis. По одному
 *     экземпляру на запуск (синглтон) — намеренно: инструмент должен вести себя как ОДНА вкладка браузера —
 *     одно хранилище cookie, один User-Agent и небольшой жёсткий лимит одновременных соединений — чтобы быть
 *     вежливым к источнику, который ограничивает соединения, а не скрапером. {@link #fetch(String)} делает GET и:
 *     <ul>
 *       <li>детектирует челлендж Anubis, даже когда он приходит с HTTP 200 (заглушка отдаётся как 200
 *           {@code text/html}, поэтому наивный клиент молча сохранил бы HTML челленджа вместо файла), один раз
 *           решает proof-of-work ({@link AnubisSolver}) и повторяет запрос;</li>
 *       <li>делает экспоненциальный backoff на 429 / 5xx и повторяет ограниченное число раз, затем отдаёт
 *           статус, чтобы подключился собственный повтор конвейера;</li>
 *       <li>ограничивает реальную конкурентность {@link Semaphore} ({@code akumu_max_connections}) независимо от
 *           параллелизма стадии загрузки.</li>
 *     </ul>
 *     Решение PoW — single-flight (решает только один поток; остальные в течение короткого окна просто повторяют
 *     запрос с уже обновлённой cookie), поэтому массовое истечение срока cookie не приводит к массовому пересчёту
 *     proof-of-work.<br>
 **/
public final class AnubisClient
{
    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Safari/537.36";
    private static final String CHALLENGE_MARKER = "id=\"anubis_challenge\"";
    private static final String PASS_CHALLENGE_PATH = "/.within.website/x/cmd/anubis/api/pass-challenge";
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(5);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_CHALLENGE_RETRIES = 3;
    private static final int MAX_BACKOFF_RETRIES = 6;
    private static final long BACKOFF_CAP_MS = 60_000L;
    private static final long AUTH_REUSE_WINDOW_MS = 5_000L;
    private static final int CHALLENGE_MAX_BYTES = 256 * 1024;
    private static final long EXCHANGE_TIMEOUT_SECONDS = MainConfig.EXCHANGE_TIMEOUT_SECONDS;

    private static volatile AnubisClient _instance;

    private final HttpClient _client;
    private final HttpClient _passChallengeClient;
    private final String _origin;
    private final String _userAgent;
    private final long _backoffBaseMs;
    private final Semaphore _gate;
    private final Object _authLock;

    private volatile long _lastAuthAt;

    private AnubisClient(String origin, int maxConnections, long backoffBaseMs, String userAgent)
    {
        _origin = origin;
        _userAgent = userAgent;
        _backoffBaseMs = backoffBaseMs;
        _gate = new Semaphore(Math.max(1, maxConnections));
        _authLock = new Object();
        _lastAuthAt = 0L;
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        _client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(CONNECT_TIMEOUT).cookieHandler(cookies).build();
        // Pass-challenge goes on a NON-redirect-following client that SHARES the same cookie jar: the Anubis 302
        // back to the target file is captured for its Set-Cookie but NOT auto-followed — otherwise the whole file
        // would be fetched (and buffered in the heap) a second time just to pass the challenge, blowing memory on a
        // large file and re-pulling it over the rate-limited mirror.
        _passChallengeClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(CONNECT_TIMEOUT).cookieHandler(cookies).build();
    }

    /**
     * EN: Lazily builds the single run-wide session from config ({@code akumu_folder_url} host,
     *     {@code akumu_max_connections}, {@code akumu_retry_backoff_ms}, {@code requested_user_agent}).
     *     Only ever called on an akumu run. <br>
     * RU: Лениво строит единую сессию на запуск из конфига ({@code akumu_folder_url} host,
     *     {@code akumu_max_connections}, {@code akumu_retry_backoff_ms}, {@code requested_user_agent}).
     *     Вызывается только на akumu-запуске. <br>
     * ==================================================================<br>
     * @return <br>
     *         {AnubisClient} - EN: the shared session / RU: общая сессия <br>
     **/
    public static AnubisClient getInstance()
    {
        AnubisClient local = _instance;
        if (local != null)
        {
            return local;
        }
        synchronized (AnubisClient.class)
        {
            if (_instance == null)
            {
                if (MainConfig.AKUMU_FOLDER_URL == null)
                {
                    throw new IllegalStateException("Akumu source requested but 'akumu_folder_url' is not set in Main.ini.");
                }
                URI base = URI.create(MainConfig.AKUMU_FOLDER_URL);
                String origin = base.getScheme() + "://" + base.getAuthority();
                String userAgent = MainConfig.REQUESTED_USER_AGENT != null ? MainConfig.REQUESTED_USER_AGENT : DEFAULT_USER_AGENT;
                _instance = new AnubisClient(origin, MainConfig.AKUMU_MAX_CONNECTIONS, MainConfig.AKUMU_RETRY_BACKOFF_MS, userAgent);
            }
            return _instance;
        }
    }

    /**
     * EN: Fetches the URL over the shared session, transparently passing Anubis and backing off on rate/server
     *     errors. Bounded by the connection gate. Returns the successful {@link HttpResponse} so the caller can
     *     record the REAL status/length instead of assuming 200: a <b>200 or 206</b> (the content-bearing
     *     statuses) is success, while other codes — including other 2xx like 201/204 that carry no file body —
     *     are not. The Anubis challenge is always a 200 {@code text/html} interstitial, so it is detected on 200
     *     only and re-solved. Throws {@link HttpStatusException} on any other status (a permanent one, or an
     *     exhausted retryable 429/5xx) and other exceptions on transport failure. <br>
     * RU: Качает URL через общую сессию, прозрачно проходя Anubis и делая backoff на rate/серверных ошибках.
     *     Ограничено воротами соединений. Возвращает успешный {@link HttpResponse}, чтобы вызывающий записал
     *     РЕАЛЬНЫЙ статус/длину, а не считал, что это 200: успех — это <b>200 или 206</b> (коды с телом), а
     *     прочие коды — включая иные 2xx вроде 201/204 без тела файла — нет. Челлендж Anubis всегда 200
     *     {@code text/html} заглушка, поэтому детектируется только на 200 и перерешается. Бросает
     *     {@link HttpStatusException} на любом другом статусе (постоянном или исчерпанном повторяемом 429/5xx) и
     *     другие исключения на сбое транспорта. <br>
     * ==================================================================<br>
     * EN: @param url the absolute akumu URL to fetch / RU: @param url абсолютный akumu-URL для загрузки <br>
     * @return <br>
     *         {HttpResponse} - EN: the successful (200/206) response / RU: успешный (200/206) ответ <br>
     **/
    public HttpResponse<byte[]> fetch(String url) throws Exception
    {
        _gate.acquire();
        try
        {
            return fetchWithAuth(url);
        }
        finally
        {
            _gate.release();
        }
    }

    /**
     * EN: Streaming counterpart of {@link #fetch(String)}: passes Anubis exactly the same way but pushes the file
     *     body into {@code sink} chunk-by-chunk instead of buffering a whole {@code byte[]}, so a temp-mode akumu
     *     download never holds the payload in the heap. Classification stays cheap: the Anubis challenge is always a
     *     small {@code text/html} 200 page, so a non-html body streams straight through untouched, while an html
     *     body is peeked (bounded to {@code CHALLENGE_MAX_BYTES}) only long enough to tell a challenge from a real
     *     file. Same connection gate, User-Agent, cookie jar and back-off as {@link #fetch(String)} — one
     *     browser-like session, never a parallel scraper. The sink is written ONLY for a content-bearing 200/206;
     *     a detected challenge never touches it (it is solved and the request retried), so a retry always streams
     *     onto a clean sink. <br>
     * RU: Потоковый аналог {@link #fetch(String)}: проходит Anubis точно так же, но проталкивает тело файла в
     *     {@code sink} кусками, а не буферизует целый {@code byte[]}, поэтому temp-загрузка akumu никогда не держит
     *     объём в куче. Классификация остаётся дешёвой: челлендж Anubis всегда небольшая {@code text/html}
     *     200-страница, поэтому не-html тело стримится напрямую нетронутым, а html-тело подглядывается (не более
     *     {@code CHALLENGE_MAX_BYTES}) ровно настолько, чтобы отличить челлендж от реального файла. Те же ворота
     *     соединений, User-Agent, банка cookie и backoff, что и у {@link #fetch(String)}, — одна браузероподобная
     *     сессия, а не параллельный скрапер. В приёмник пишется ТОЛЬКО тело 200/206; обнаруженный челлендж его не
     *     трогает (он решается и запрос повторяется), поэтому повтор всегда стримит на чистый приёмник. <br>
     * ==================================================================<br>
     * EN: @param url the absolute akumu URL to fetch / RU: @param url абсолютный akumu-URL для загрузки <br>
     * EN: @param sink the destination the file body is streamed into / RU: @param sink назначение, куда стримится тело файла <br>
     * @return <br>
     *         {StreamResult} - EN: the success status + streamed byte count / RU: статус успеха + число переданных байтов <br>
     **/
    public StreamResult fetchStreaming(String url, BodySink sink) throws Exception
    {
        _gate.acquire();
        try
        {
            return fetchStreamingWithAuth(url, sink);
        }
        finally
        {
            _gate.release();
        }
    }

    /**
     * EN: The core send-then-classify retry loop. Sends the request and classifies the status: a 200 that is the
     *     Anubis interstitial passes the antibot Proof-of-Work (PoW) and retries; a 200/206 with a file body
     *     returns; a 429 or 5xx backs off (exponential) and retries; any other status throws immediately. Retries
     *     are bounded — a challenge that will not pass or an exhausted 429/5xx cap throws a retryable
     *     {@link HttpStatusException} so the pipeline can try the file again. <br>
     * RU: Основной цикл «отправить и классифицировать» с повторами. Отправляет запрос и классифицирует статус:
     *     200, оказавшийся заглушкой Anubis, проходит антибот Proof-of-Work (PoW) и повторяет запрос; 200/206 с
     *     телом файла возвращается; 429 или 5xx делают экспоненциальный backoff и повторяют; любой другой статус
     *     сразу бросает исключение. Число повторов ограничено — непройденный челлендж или исчерпанный лимит
     *     429/5xx бросает повторяемый {@link HttpStatusException}, чтобы конвейер мог загрузить файл заново. <br>
     * ==================================================================<br>
     * EN: @param url the absolute akumu URL to fetch / RU: @param url абсолютный akumu-URL для загрузки <br>
     * @return <br>
     *         {HttpResponse} - EN: the first successful (200/206) response / RU: первый успешный (200/206) ответ <br>
     **/
    private HttpResponse<byte[]> fetchWithAuth(String url) throws Exception
    {
        int challengeRetries = 0;
        int backoffRetries = 0;
        while (true)
        {
            HttpResponse<byte[]> response = send(url);
            int status = response.statusCode();
            byte[] body = response.body() == null ? new byte[0] : response.body();
            if (status == 200 || status == 206)
            {
                if (status == 200 && isChallenge(response, body))
                {
                    if (++challengeRetries > MAX_CHALLENGE_RETRIES)
                    {
                        // Failing to pass the antibot is TRANSIENT (rate limiting), not a permanent 403:
                        // throw a retryable status so the pipeline retries the file later instead of losing it.
                        throw new HttpStatusException(429);
                    }
                    passChallenge(body, url);
                    continue;
                }
                return response;
            }
            if (status == 429 || (status >= 500 && status <= 599))
            {
                if (++backoffRetries > MAX_BACKOFF_RETRIES)
                {
                    throw new HttpStatusException(status);
                }
                backoffSleep(backoffRetries, status, url);
                continue;
            }
            throw new HttpStatusException(status);
        }
    }

    private HttpResponse<byte[]> send(String url) throws Exception
    {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(REQUEST_TIMEOUT).header("User-Agent", _userAgent).GET().build();
        // Whole-exchange deadline (not the plain blocking send whose body was unbounded): a body that stalls after
        // headers can no longer hang the worker forever while holding a connection gate permit.
        return sendWithDeadline(_client, request, HttpResponse.BodyHandlers.ofByteArray());
    }

    /**
     * EN: The streaming send-then-classify retry loop (streaming twin of {@link #fetchWithAuth(String)}), bounded by
     *     a WHOLE-EXCHANGE deadline ({@link #sendWithDeadline}) so a stalled body read can NEVER hold a
     *     connection permit forever — the failure the plain blocking {@code ofInputStream} read would allow. The
     *     body handler picks the subscriber from the response head, so a content-bearing 200/206 with a non-html
     *     body is STREAMED straight into the sink (never buffered), while an html 200 (the Anubis interstitial, or a
     *     rare html file) and any non-2xx body are buffered as a small {@code byte[]}. Then: a 200 html body
     *     carrying the challenge marker passes the Proof-of-Work and retries WITHOUT touching the sink; a buffered
     *     html file is emitted to the sink; a streamed body returns its count; a 429/5xx backs off and retries; any
     *     other status throws. The challenge is html and classified from the head, so the streaming sink only ever
     *     receives real file bytes and a retry re-streams cleanly. <br>
     * RU: Потоковый цикл «отправить и классифицировать» с повторами (потоковый близнец
     *     {@link #fetchWithAuth(String)}), ограниченный дедлайном ВСЕГО обмена ({@link #sendWithDeadline}),
     *     чтобы зависшее чтение тела НИКОГДА не держало квоту соединения вечно — сбой, который допускало бы обычное
     *     блокирующее чтение {@code ofInputStream}. Обработчик тела выбирает подписчика по «голове» ответа, поэтому
     *     200/206 с не-html телом СТРИМИТСЯ прямо в приёмник (без буферизации), а html-200 (заглушка Anubis либо
     *     редкий html-файл) и любое не-2xx тело буферизуются небольшим {@code byte[]}. Далее: html-тело 200 с
     *     маркером челленджа проходит Proof-of-Work и повторяет запрос, НЕ трогая приёмник; буферизованный html-файл
     *     отдаётся в приёмник; потоковое тело возвращает свой счёт; 429/5xx делает backoff и повторяет; любой другой
     *     статус бросает исключение. Челлендж — html и классифицируется по «голове», поэтому потоковый приёмник
     *     получает только реальные байты файла, а повтор стримит заново с чистого листа. <br>
     * ==================================================================<br>
     * EN: @param url the absolute akumu URL to fetch / RU: @param url абсолютный akumu-URL для загрузки <br>
     * EN: @param sink the destination the file body is streamed into / RU: @param sink назначение, куда стримится тело файла <br>
     * @return <br>
     *         {StreamResult} - EN: the first successful (200/206) status + byte count / RU: первый успешный (200/206) статус + число байтов <br>
     **/
    private StreamResult fetchStreamingWithAuth(String url, BodySink sink) throws Exception
    {
        int challengeRetries = 0;
        int backoffRetries = 0;
        while (true)
        {
            HttpResponse<AnubisBody> response = sendWithDeadline(_client, streamRequest(url), anubisBodyHandler(sink));
            int status = response.statusCode();
            AnubisBody body = response.body();
            if (status == 200 || status == 206)
            {
                byte[] buffered = body.buffered();
                if (status == 200 && buffered != null && isChallengeBytes(buffered))
                {
                    if (++challengeRetries > MAX_CHALLENGE_RETRIES)
                    {
                        // Failing the antibot is TRANSIENT (rate limiting), not a permanent 403: throw a retryable
                        // status so the pipeline retries the file later instead of losing it.
                        throw new HttpStatusException(429);
                    }
                    passChallenge(buffered, url);
                    continue;
                }
                if (buffered != null)
                {
                    // A real (rare) html file, or an empty body: emit the buffered bytes to the sink.
                    if (buffered.length > 0)
                    {
                        sink.accept(0L, ByteBuffer.wrap(buffered));
                    }
                    return new StreamResult(status, buffered.length);
                }
                // The file was streamed straight into the sink.
                return new StreamResult(status, body.streamedCount());
            }
            if (status == 429 || (status >= 500 && status <= 599))
            {
                if (++backoffRetries > MAX_BACKOFF_RETRIES)
                {
                    throw new HttpStatusException(status);
                }
                backoffSleep(backoffRetries, status, url);
                continue;
            }
            throw new HttpStatusException(status);
        }
    }

    private HttpRequest streamRequest(String url)
    {
        return HttpRequest.newBuilder().uri(URI.create(url)).timeout(REQUEST_TIMEOUT).header("User-Agent", _userAgent).GET().build();
    }

    /**
     * EN: Sends the request asynchronously and blocks up to a whole-exchange deadline (headers AND body) via
     *     {@code future.get}. Because the streaming subscriber's body future only completes once every chunk has
     *     been consumed, the deadline bounds the WHOLE exchange, not just the headers — the fix for the plain
     *     blocking read whose body was unbounded. Any non-success exit (timeout / interrupt) cancels the exchange so
     *     it stops pulling bytes and releases the connection, and the interrupt flag is restored; a mid-body
     *     transport error completes the future exceptionally and is rethrown as a retryable failure. This is the
     *     Content-Delivery-Network path's exchange-timeout, applied on THIS client's cookie jar. <br>
     * RU: Отправляет запрос асинхронно и блокируется до дедлайна всего обмена (заголовки И тело) через
     *     {@code future.get}. Поскольку future тела потокового подписчика завершается лишь после потребления
     *     каждого куска, дедлайн ограничивает ВЕСЬ обмен, а не только заголовки — исправление для обычного
     *     блокирующего чтения, чьё тело было неограниченным. Любой не-успешный выход (таймаут / прерывание) отменяет
     *     обмен, чтобы он перестал тянуть байты и освободил соединение, а флаг прерывания восстанавливается; ошибка
     *     транспорта в середине тела завершает future исключением и перебрасывается как повторяемый сбой. Это
     *     таймаут всего обмена пути сети доставки контента, применённый к банке cookie ЭТОГО клиента. <br>
     * ==================================================================<br>
     * EN: @param request the prepared request / RU: @param request подготовленный запрос <br>
     * EN: @param handler the body handler / RU: @param handler обработчик тела <br>
     * @return <br>
     *         {HttpResponse} - EN: the settled response / RU: завершённый ответ <br>
     **/
    private static <T> HttpResponse<T> sendWithDeadline(HttpClient client, HttpRequest request, HttpResponse.BodyHandler<T> handler) throws Exception
    {
        CompletableFuture<HttpResponse<T>> future = client.sendAsync(request, handler);
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
     * EN: Chooses the body subscriber from the response head: a content-bearing 200/206 with a NON-html body streams
     *     straight into the sink (a bounded, backpressured subscriber that never buffers the whole file); everything
     *     else — an html 200 (the Anubis challenge or a rare html file) and any non-2xx body — is buffered as a
     *     small {@code byte[]} so it can be classified / drained. That is exactly the classification the retry loop
     *     needs, made before a single file byte is streamed. <br>
     * RU: Выбирает подписчика тела по «голове» ответа: 200/206 с телом и НЕ-html типом стримится прямо в приёмник
     *     (ограниченный подписчик с обратным давлением, никогда не буферизующий весь файл); всё остальное — html-200
     *     (челлендж Anubis или редкий html-файл) и любое не-2xx тело — буферизуется небольшим {@code byte[]}, чтобы
     *     его можно было классифицировать / вычитать. Это ровно та классификация, что нужна циклу повторов, сделанная
     *     до стрима хоть одного байта файла. <br>
     * ==================================================================<br>
     * EN: @param sink the destination for a streamed file body / RU: @param sink назначение для потокового тела файла <br>
     * @return <br>
     *         {HttpResponse.BodyHandler} - EN: the classifying body handler / RU: классифицирующий обработчик тела <br>
     **/
    private HttpResponse.BodyHandler<AnubisBody> anubisBodyHandler(BodySink sink)
    {
        return responseInfo ->
        {
            int status = responseInfo.statusCode();
            boolean contentBearing = status == 200 || status == 206;
            boolean html = responseInfo.headers().firstValue("content-type").orElse("").toLowerCase().contains("text/html");
            if (contentBearing && !html)
            {
                return new StreamingSinkSubscriber(sink);
            }
            return HttpResponse.BodySubscribers.mapping(HttpResponse.BodySubscribers.ofByteArray(), bytes -> new AnubisBody(bytes, 0L));
        };
    }

    /**
     * EN: Whether the buffered body is the Anubis challenge: non-empty, no larger than {@code CHALLENGE_MAX_BYTES},
     *     and carrying the challenge marker. A larger body (or a marker-less one) is a real file, never a challenge. <br>
     * RU: Является ли буферизованное тело челленджем Anubis: непустое, не больше {@code CHALLENGE_MAX_BYTES} и несёт
     *     маркер челленджа. Большее тело (или без маркера) — реальный файл, никогда не челлендж. <br>
     * ==================================================================<br>
     * EN: @param body the buffered body bytes / RU: @param body буферизованные байты тела <br>
     * @return <br>
     *         {true}  - EN: this is the challenge page / RU: это страница челленджа <br>
     *         {false} - EN: this is a real file / RU: это реальный файл <br>
     **/
    private static boolean isChallengeBytes(byte[] body)
    {
        return body.length > 0 && body.length <= CHALLENGE_MAX_BYTES && new String(body, StandardCharsets.ISO_8859_1).contains(CHALLENGE_MARKER);
    }

    /**
     * EN: A backpressured {@link HttpResponse.BodySubscriber} that pushes each incoming {@link ByteBuffer} straight
     *     into the {@link BodySink} at its running absolute offset, requesting one item at a time so at most a small
     *     number of buffers are ever in flight — the memory bound that replaces a whole-{@code byte[]} body. A sink
     *     failure cancels the subscription and completes the body future exceptionally, surfacing as a retryable
     *     transport-style error. The resolved value is an {@link AnubisBody} with a {@code null} buffer and the
     *     streamed byte count. Single akumu connection per file, so the sink is fed by one thread. <br>
     * RU: Потоковый {@link HttpResponse.BodySubscriber} с обратным давлением, проталкивающий каждый входящий
     *     {@link ByteBuffer} прямо в {@link BodySink} по его текущему абсолютному смещению, запрашивая по одному
     *     элементу за раз, поэтому в полёте всегда лишь небольшое число буферов — ограничение памяти, заменяющее
     *     тело в виде целого {@code byte[]}. Сбой приёмника отменяет подписку и завершает future тела исключением,
     *     проявляясь как повторяемая ошибка транспортного рода. Итог — {@link AnubisBody} с {@code null}-буфером и
     *     числом переданных байтов. Одно соединение akumu на файл, поэтому приёмник кормит один поток. <br>
     **/
    private static final class StreamingSinkSubscriber implements HttpResponse.BodySubscriber<AnubisBody>
    {
        private final BodySink _sink;
        private final CompletableFuture<AnubisBody> _result;
        private Flow.Subscription _subscription;
        private long _offset;
        private long _count;

        private StreamingSinkSubscriber(BodySink sink)
        {
            _sink = sink;
            _result = new CompletableFuture<>();
            _offset = 0L;
            _count = 0L;
        }

        @Override
        public CompletionStage<AnubisBody> getBody()
        {
            return _result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription)
        {
            _subscription = subscription;
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
                    _sink.accept(_offset, buffer);
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
            _result.complete(new AnubisBody(null, _count));
        }
    }

    /**
     * EN: The resolved streaming body: either a BUFFERED small body ({@code buffered != null} — the challenge, a
     *     rare html file, or a non-2xx body to classify/drain) or a STREAMED file ({@code buffered == null},
     *     {@code streamedCount} = the bytes pushed to the sink). <br>
     * RU: Итоговое потоковое тело: либо БУФЕРИЗОВАННОЕ небольшое тело ({@code buffered != null} — челлендж, редкий
     *     html-файл или не-2xx тело для классификации/вычитывания), либо ПОТОКОВЫЙ файл ({@code buffered == null},
     *     {@code streamedCount} = байты, переданные в приёмник). <br>
     **/
    private record AnubisBody(byte[] buffered, long streamedCount)
    {
    }

    /**
     * EN: Whether a 200 response is actually the Anubis interstitial rather than the file: an HTML body,
     *     small enough to be the challenge page, that carries the challenge marker. Binary files (octet-stream,
     *     or simply large) are never stringified. <br>
     * RU: Является ли 200-ответ на самом деле заглушкой Anubis, а не файлом: HTML-тело, достаточно маленькое,
     *     чтобы быть страницей челленджа, несущее маркер челленджа. Бинарные файлы (octet-stream или просто
     *     большие) никогда не превращаются в строку. <br>
     * ==================================================================<br>
     * EN: @param response the HTTP response / RU: @param response HTTP-ответ <br>
     * EN: @param body the response body / RU: @param body тело ответа <br>
     * @return <br>
     *         {true}  - EN: this is the Anubis challenge page / RU: это страница челленджа Anubis <br>
     *         {false} - EN: this is the real file / RU: это реальный файл <br>
     **/
    private boolean isChallenge(HttpResponse<byte[]> response, byte[] body)
    {
        if (body.length == 0 || body.length > CHALLENGE_MAX_BYTES)
        {
            return false;
        }
        String contentType = response.headers().firstValue("content-type").orElse("");
        if (!contentType.toLowerCase().contains("text/html"))
        {
            return false;
        }
        return new String(body, StandardCharsets.ISO_8859_1).contains(CHALLENGE_MARKER);
    }

    /**
     * EN: Solves the challenge embedded in the given page and submits it to the pass-challenge endpoint so the
     *     cookie jar receives the auth cookie. Single-flight: if another thread already authed within the reuse
     *     window, this returns immediately and the caller simply retries with the now-valid cookie. The reuse
     *     window is armed ONLY when the pass-challenge succeeds (status &lt; 400 = cookie set); a rejected pass
     *     (e.g. a transient 429) leaves it un-armed — so a needed re-solve is never suppressed — and backs off
     *     briefly. <br>
     * RU: Решает челлендж из переданной страницы и отправляет его на endpoint pass-challenge, чтобы хранилище
     *     cookie получило auth-cookie. Single-flight: если другой поток уже авторизовался в окне повторного
     *     использования, метод сразу возвращается, а вызывающий просто повторяет запрос со свежей cookie. Окно
     *     повторного использования взводится ТОЛЬКО при успешном pass-challenge (статус &lt; 400 = cookie
     *     установлена); отклонённый pass (напр. транзиентный 429) оставляет его невзведённым — поэтому нужный
     *     перерасчёт никогда не подавляется — и делает короткий backoff. <br>
     * ==================================================================<br>
     * EN: @param challengePage the challenge HTML bytes / RU: @param challengePage байты HTML челленджа <br>
     * EN: @param targetUrl the file URL we were fetching (its path becomes the redir) / RU: @param targetUrl URL файла (его путь — redir) <br>
     **/
    private void passChallenge(byte[] challengePage, String targetUrl) throws Exception
    {
        synchronized (_authLock)
        {
            long now = System.currentTimeMillis();
            if (now - _lastAuthAt < AUTH_REUSE_WINDOW_MS)
            {
                return;
            }
            JSONObject challenge = extractChallenge(new String(challengePage, StandardCharsets.ISO_8859_1));
            String randomData = String.valueOf(challenge.get("randomData"));
            int difficulty = (int) longValue(challenge.get("difficulty"));
            String id = String.valueOf(challenge.get("id"));

            AnubisSolver.Solution solution = AnubisSolver.solve(randomData, difficulty);
            String redir = URI.create(targetUrl).getRawPath();
            String query = "id=" + encode(id) + "&response=" + encode(solution.getHash()) + "&nonce=" + solution.getNonce() + "&redir=" + encode(redir) + "&elapsedTime=" + solution.getElapsedMs();
            // On the non-redirect-following client with a discarding body: the auth cookie arrives on the 302
            // itself (captured in the shared jar), and we do NOT follow it (which would re-download the file). A
            // 2xx OR 3xx is success. Bounded by the whole-exchange deadline.
            HttpRequest passRequest = HttpRequest.newBuilder().uri(URI.create(_origin + PASS_CHALLENGE_PATH + "?" + query)).timeout(REQUEST_TIMEOUT).header("User-Agent", _userAgent).GET().build();
            HttpResponse<Void> pass = sendWithDeadline(_passChallengeClient, passRequest, HttpResponse.BodyHandlers.discarding());
            int passStatus = pass.statusCode();
            IDummyLogger.log(IDummyLogger.INFO, "Anubis PoW solved (difficulty " + difficulty + ", nonce " + solution.getNonce() + ", " + solution.getElapsedMs() + "ms); pass-challenge -> HTTP " + passStatus + ".");
            if (passStatus >= 200 && passStatus < 400)
            {
                // Authenticated (auth cookie set): arm the reuse window so concurrent threads skip a redundant re-solve.
                _lastAuthAt = System.currentTimeMillis();
            }
            else
            {
                // The pass-challenge itself was rejected — typically a transient 429 from this rate-limited
                // source, meaning NO auth cookie was set. Leave the reuse window UN-armed so the next attempt
                // re-solves (never suppress a needed re-solve), and back off briefly to stay polite.
                Thread.sleep(Math.min(BACKOFF_CAP_MS, _backoffBaseMs));
            }
        }
    }

    /**
     * EN: Extracts the {@code challenge} JSON object from the {@code <script id="anubis_challenge">} block. <br>
     * RU: Извлекает JSON-объект {@code challenge} из блока {@code <script id="anubis_challenge">}. <br>
     * ==================================================================<br>
     * EN: @param html the challenge page HTML / RU: @param html HTML страницы челленджа <br>
     * @return <br>
     *         {JSONObject} - EN: the inner challenge object / RU: внутренний объект challenge <br>
     **/
    private static JSONObject extractChallenge(String html) throws Exception
    {
        int marker = html.indexOf(CHALLENGE_MARKER);
        if (marker < 0)
        {
            throw new IllegalStateException("Anubis challenge marker not found in page.");
        }
        int open = html.indexOf('>', marker);
        int close = html.indexOf("</script>", open);
        if (open < 0 || close < 0)
        {
            throw new IllegalStateException("Anubis challenge script block is malformed.");
        }
        String json = html.substring(open + 1, close).trim();
        JSONObject root = (JSONObject) new JSONParser().parse(json);
        Object challenge = root.get("challenge");
        if (!(challenge instanceof JSONObject))
        {
            throw new IllegalStateException("Anubis challenge JSON has no 'challenge' object.");
        }
        return (JSONObject) challenge;
    }

    private void backoffSleep(int attempt, int status, String url) throws InterruptedException
    {
        long delay = Math.min(BACKOFF_CAP_MS, _backoffBaseMs * (1L << (attempt - 1)));
        IDummyLogger.log(IDummyLogger.WARNING, "Akumu returned HTTP " + status + " for '" + url + "'; backing off " + delay + "ms (attempt " + attempt + "/" + MAX_BACKOFF_RETRIES + ").");
        Thread.sleep(delay);
    }

    private static long longValue(Object value)
    {
        if (value instanceof Number number)
        {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static String encode(String value)
    {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * EN: Destination for the streamed file body: one call per chunk with the chunk's absolute {@code offset}
     *     within the file, consuming the buffer's remaining bytes. A temp-file request writes it at that offset; an
     *     in-memory request appends it (the akumu strategy only streams in temp mode). <br>
     * RU: Назначение для потокового тела файла: один вызов на кусок с абсолютным {@code offset} куска в файле,
     *     потребляющий оставшиеся байты буфера. Запрос с временным файлом пишет его по этому смещению; запрос в
     *     памяти дописывает (стратегия akumu стримит только в temp-режиме). <br>
     **/
    @FunctionalInterface
    public interface BodySink
    {
        void accept(long offset, ByteBuffer chunk) throws IOException;
    }

    /**
     * EN: The settled result of a streaming fetch: the REAL success status (200 or 206) and the number of body
     *     bytes streamed into the sink, so the caller records the true status/length and can validate it against
     *     the expected size. <br>
     * RU: Итог потоковой загрузки: РЕАЛЬНЫЙ статус успеха (200 или 206) и число байтов тела, переданных в приёмник,
     *     чтобы вызывающий записал истинные статус/длину и мог сверить их с ожидаемым размером. <br>
     **/
    public static final class StreamResult
    {
        private final int _statusCode;
        private final long _length;

        private StreamResult(int statusCode, long length)
        {
            _statusCode = statusCode;
            _length = length;
        }

        public int statusCode()
        {
            return _statusCode;
        }

        public long length()
        {
            return _length;
        }
    }
}
