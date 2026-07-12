package org.index.patchdownloader.util;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.interfaces.IDummyLogger;
import org.index.patchdownloader.model.holders.LinkInfoHolder;

/**
 * EN: Small synchronous HTTP GET helper for the link generators, which fetch the file list / hash list
 *     up-front (outside the async pipeline). It records the HTTP status and length on the given link
 *     holder and never throws: on failure it logs, sets status {@code -1} and returns an empty array so
 *     the caller can simply check {@code getHttpStatus() == 200}.<br>
 * RU: Небольшая синхронная утилита (вспомогательный класс) для HTTP GET, используемая генераторами ссылок, которые заранее (вне асинхронного
 *     конвейера) скачивают список файлов / хешей. Записывает статус и длину HTTP в переданный holder
 *     ссылки и никогда не бросает: при сбое логирует, ставит статус {@code -1} и возвращает пустой
 *     массив, чтобы вызывающий мог просто проверить {@code getHttpStatus() == 200}.<br>
 **/
public final class HttpDownloadUtils
{
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(1);

    /**
     * EN: Hard cap on the buffered response body for these metadata GETs (file list / hash list). The
     *     mirrors are treated as untrusted, so a hostile or misconfigured one could stream a multi-gigabyte
     *     body; without a cap that buffers straight into an Out-Of-Memory (OOM) error instead of the graceful
     *     status {@code -1} failure path. Metadata files are far smaller than this, so 256 MiB is generous. <br>
     * RU: Жёсткий предел на размер буферизуемого тела ответа для этих метаданных (список файлов / список
     *     хешей). Зеркала считаются недоверенными, поэтому враждебное или неправильно настроенное зеркало
     *     может отдать тело в несколько гигабайт; без предела оно буферизуется прямо в ошибку нехватки памяти
     *     (Out-Of-Memory, OOM) вместо аккуратного пути отказа со статусом {@code -1}. Файлы метаданных
     *     гораздо меньше, так что 256 МиБ более чем достаточно. <br>
     **/
    private static final int MAX_BODY_BYTES = 256 * 1024 * 1024;

    private HttpDownloadUtils()
    {
    }

    /**
     * EN: Performs a blocking HTTP GET for the given link, records status/length on the holder, and
     *     returns the body bytes (empty on failure). <br>
     * RU: Выполняет блокирующий HTTP GET по ссылке, записывает статус/длину в holder и возвращает байты
     *     тела (пустые при сбое). <br>
     * ==================================================================<br>
     * EN: @param linkInfo the link holder to download and update / RU: @param linkInfo holder ссылки для загрузки и обновления <br>
     * @return <br>
     *         {byte[]} - EN: the body bytes, or an empty array on failure / RU: байты тела или пустой массив при сбое <br>
     **/
    public static byte[] download(LinkInfoHolder linkInfo)
    {
        try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build())
        {
            HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(linkInfo.getAccessLink())).timeout(REQUEST_TIMEOUT).GET();
            if (MainConfig.REQUESTED_USER_AGENT != null)
            {
                builder.header("User-Agent", MainConfig.REQUESTED_USER_AGENT);
            }
            HttpResponse<InputStream> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] body = readCapped(response.body());
            linkInfo.setHttpStatus(response.statusCode());
            linkInfo.setHttpLength(body.length);
            return body;
        }
        catch (Exception e)
        {
            linkInfo.setHttpStatus(-1);
            IDummyLogger.log(IDummyLogger.ERROR, "Failed to download '" + linkInfo.getAccessLink() + "': " + e);
            return new byte[0];
        }
    }

    /**
     * EN: Drains the response body stream into a byte array, aborting once {@link #MAX_BODY_BYTES} is
     *     exceeded so an oversized (possibly hostile) body cannot exhaust the heap. Throws on overflow so the
     *     caller's catch reports it via the standard status {@code -1} failure path. <br>
     * RU: Считывает поток тела ответа в массив байтов, прерываясь при превышении {@link #MAX_BODY_BYTES},
     *     чтобы чрезмерно большое (возможно, враждебное) тело не исчерпало кучу. При переполнении бросает
     *     исключение, чтобы вызывающий обработал его через стандартный путь отказа со статусом {@code -1}. <br>
     * ==================================================================<br>
     * EN: @param bodyStream the response body stream (may be {@code null}) / RU: @param bodyStream поток тела ответа (может быть {@code null}) <br>
     * @return <br>
     *         {byte[]} - EN: the buffered body bytes / RU: буферизованные байты тела <br>
     **/
    private static byte[] readCapped(InputStream bodyStream) throws java.io.IOException
    {
        if (bodyStream == null)
        {
            return new byte[0];
        }
        try (InputStream in = bodyStream; ByteArrayOutputStream buffer = new ByteArrayOutputStream())
        {
            byte[] chunk = new byte[8192];
            int read;
            int total = 0;
            while ((read = in.read(chunk)) != -1)
            {
                total += read;
                if (total > MAX_BODY_BYTES)
                {
                    throw new java.io.IOException("Response body exceeds the " + MAX_BODY_BYTES + "-byte cap");
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        }
    }
}
