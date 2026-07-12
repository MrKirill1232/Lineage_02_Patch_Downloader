package org.index.patchdownloader.model.akumu;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Semaphore;

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

    private static volatile AnubisClient _instance;

    private final HttpClient _client;
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
        _client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(CONNECT_TIMEOUT).cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
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
        return _client.send(request, HttpResponse.BodyHandlers.ofByteArray());
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
            HttpResponse<byte[]> pass = send(_origin + PASS_CHALLENGE_PATH + "?" + query);
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
}
