package org.index.patchdownloader.model.pipeline.download;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.enums.CDNLink;
import org.index.patchdownloader.model.akumu.AnubisClient;
import org.index.patchdownloader.model.holders.LinkInfoHolder;
import org.index.patchdownloader.model.pipeline.FileDownloadTask;

/**
 * EN: Download strategy for the akumu HTTP mirror. Every akumu file is fetched with ONE connection through the
 *     shared {@link AnubisClient}, which passes the Anubis antibot, re-authenticates transparently when the
 *     cookie expires (the challenge is served as a 200 page, so a plain GET would silently store HTML) and
 *     bounds concurrency + backs off on rate limits. It therefore does NOT self-split or parallelise parts —
 *     that would open many connections against a source that limits them. Placed first in the strategy list,
 *     it is active only on an akumu run ({@code cdn_source = AKUMU}) and inert otherwise, so the CDN strategies
 *     are unaffected.<br>
 * RU: Стратегия загрузки для HTTP-зеркала akumu. Каждый файл akumu качается ОДНИМ соединением через общий
 *     {@link AnubisClient}, который проходит антибот Anubis, прозрачно переавторизуется при истечении cookie
 *     (челлендж отдаётся как 200-страница, поэтому обычный GET молча сохранил бы HTML), ограничивает
 *     конкурентность и делает backoff на rate-лимитах. Поэтому стратегия НЕ разбивает файл сама и НЕ качает
 *     части параллельно — это открыло бы много соединений к источнику, который их ограничивает. Стоит первой в
 *     списке стратегий, активна только на akumu-запуске ({@code cdn_source = AKUMU}) и иначе бездействует,
 *     поэтому CDN-стратегии не затрагиваются.<br>
 **/
public class AkumuDownloadStrategy extends AbstractDownloadStrategy
{
    public AkumuDownloadStrategy(HttpClient httpClient)
    {
        super(httpClient);
    }

    /**
     * EN: Applies only on an akumu run — a single-source run selected via {@code cdn_source = AKUMU}. <br>
     * RU: Применяется только на akumu-запуске — запуске одного источника, выбранного через
     *     {@code cdn_source = AKUMU}. <br>
     * ==================================================================<br>
     * EN: @param task the task / RU: @param task задача <br>
     * @return <br>
     *         {true}  - EN: this is an akumu run / RU: это akumu-запуск <br>
     *         {false} - EN: not akumu (leave to the CDN strategies) / RU: не akumu (оставить CDN-стратегиям) <br>
     **/
    @Override
    public boolean supports(FileDownloadTask task)
    {
        return MainConfig.CDN_SOURCE == CDNLink.AKUMU;
    }

    /**
     * EN: Downloads the whole file with one Anubis-authenticated GET into part slot 0, recording the REAL
     *     status / length on the link holder. The status code alone is a weak signal (a 200 can carry an empty
     *     or truncated body; a 206 only part of it), so the authoritative success check is the downloaded byte
     *     length against the torrent-declared size: a mismatch — or an empty body when the size is unknown —
     *     throws an {@link IOException} so the download stage retries it, instead of an empty/corrupt file being
     *     stored. <br>
     * RU: Скачивает весь файл одним Anubis-авторизованным GET в слот части 0, записывая РЕАЛЬНЫЙ статус / длину
     *     в holder ссылки. Один код статуса — слабый сигнал (200 может нести пустое или обрезанное тело; 206 —
     *     только часть), поэтому решающая (окончательная) проверка успеха — сравнение длины скачанного с размером из торрента:
     *     несовпадение — или пустое тело при неизвестном размере — бросает {@link IOException}, чтобы стадия
     *     загрузки повторила, вместо сохранения пустого/битого файла. <br>
     * ==================================================================<br>
     * EN: @param task the task to download / RU: @param task задача для загрузки <br>
     **/
    @Override
    protected void doDownload(FileDownloadTask task) throws Exception
    {
        LinkInfoHolder link = task.getFileInfo().getAccessLink();
        HttpResponse<byte[]> response = AnubisClient.getInstance().fetch(link.getAccessLink());
        byte[] body = response.body() == null ? new byte[0] : response.body();
        link.setHttpStatus(response.statusCode());
        link.setHttpLength(body.length);

        int expected = task.getFileInfo().getFileLength();
        if (expected > 0 ? body.length != expected : body.length == 0)
        {
            throw new IOException("Akumu '" + task.getLinkPath() + "': downloaded " + body.length + " bytes, expected " + (expected > 0 ? String.valueOf(expected) : "non-empty (torrent size unknown)") + " (HTTP " + response.statusCode() + "); failed download, will retry.");
        }
        task.addDownloadedPart(0, body);
    }
}
