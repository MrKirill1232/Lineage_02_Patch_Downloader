package org.index.patchdownloader.model.pipeline.download;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.enums.CDNLink;
import org.index.patchdownloader.enums.StorageStrategy;
import org.index.patchdownloader.interfaces.IDownloadRequest;
import org.index.patchdownloader.model.akumu.AnubisClient;
import org.index.patchdownloader.model.holders.LinkInfoHolder;

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
     * EN: @param request the request / RU: @param request запрос <br>
     * @return <br>
     *         {true}  - EN: this is an akumu run / RU: это akumu-запуск <br>
     *         {false} - EN: not akumu (leave to the CDN strategies) / RU: не akumu (оставить CDN-стратегиям) <br>
     **/
    @Override
    public boolean supports(IDownloadRequest request)
    {
        return MainConfig.CDN_SOURCE == CDNLink.AKUMU;
    }

    /**
     * EN: Downloads the whole file with one Anubis-authenticated GET into part slot 0, recording the REAL
     *     status / length on the link holder. The storage strategy picks HOW the body lands, never opening more
     *     than the one connection:
     *     <ul>
     *       <li><b>TEMPORARY</b> — the body is streamed straight into the pre-sized temp file chunk-by-chunk
     *           ({@link AnubisClient#fetchStreaming}), so a large file never sits in the heap. This is why a
     *           temp-mode akumu task is charged only the small streaming estimate by the in-flight budget.</li>
     *       <li><b>MEMORY</b> — the payload is meant to live in the heap anyway, so the whole body is fetched as a
     *           {@code byte[]} ({@link AnubisClient#fetch}, which must buffer it to tell the Anubis challenge from
     *           the file) and written in ONE positioned chunk. A single terminal {@link IDownloadRequest#acceptChunk}
     *           means a retry never appends onto a partially-written slot.</li>
     *     </ul>
     *     The status code alone is a weak signal (a 200 can carry an empty or truncated body; a 206 only part of
     *     it), so the authoritative success check is the downloaded byte length against the torrent-declared size:
     *     a mismatch — or an empty body when the size is unknown — throws an {@link IOException} (after any streamed
     *     bytes, which a temp retry discards) so the download stage retries it, instead of an empty/corrupt file
     *     being stored. <br>
     * RU: Скачивает весь файл одним Anubis-авторизованным GET в слот части 0, записывая РЕАЛЬНЫЙ статус / длину в
     *     holder ссылки. Стратегия хранения выбирает, КАК ложится тело, никогда не открывая больше одного
     *     соединения:
     *     <ul>
     *       <li><b>TEMPORARY</b> — тело стримится прямо во временный файл заранее выделенного размера кусками
     *           ({@link AnubisClient#fetchStreaming}), поэтому большой файл никогда не лежит в куче. Поэтому
     *           temp-задача akumu тарифицируется бюджетом лишь небольшой потоковой оценкой.</li>
     *       <li><b>MEMORY</b> — объём и так должен жить в куче, поэтому всё тело берётся как {@code byte[]}
     *           ({@link AnubisClient#fetch}, который обязан его буферизовать, чтобы отличить челлендж Anubis от
     *           файла) и пишется ОДНИМ позиционным куском. Единственный терминальный
     *           {@link IDownloadRequest#acceptChunk} означает, что повтор не допишет в частично заполненный
     *           слот.</li>
     *     </ul>
     *     Один код статуса — слабый сигнал (200 может нести пустое или обрезанное тело; 206 — только часть),
     *     поэтому решающая проверка успеха — сравнение длины скачанного с размером из торрента: несовпадение — или
     *     пустое тело при неизвестном размере — бросает {@link IOException} (после любых переданных байтов, которые
     *     temp-повтор отбрасывает), чтобы стадия загрузки повторила, вместо сохранения пустого/битого файла. <br>
     * ==================================================================<br>
     * EN: @param request the request to download / RU: @param request запрос для загрузки <br>
     **/
    @Override
    protected void doDownload(IDownloadRequest request) throws Exception
    {
        LinkInfoHolder link = request.fileInfo().getAccessLink();
        int status;
        long length;
        if (request.storageStrategy() == StorageStrategy.TEMPORARY)
        {
            // Pass the attempt token so acceptChunk fences a straggler body (still draining after a timeout/
            // interrupt cancel) from writing into the temp file a retry has already reset (checked atomically).
            long epoch = request.downloadEpoch();
            AnubisClient.StreamResult result = AnubisClient.getInstance().fetchStreaming(link.getAccessLink(), (offset, chunk) -> request.acceptChunk(epoch, 0, offset, chunk));
            status = result.statusCode();
            length = result.length();
        }
        else
        {
            HttpResponse<byte[]> response = AnubisClient.getInstance().fetch(link.getAccessLink());
            byte[] body = response.body() == null ? new byte[0] : response.body();
            status = response.statusCode();
            length = body.length;
            request.acceptChunk(request.downloadEpoch(), 0, 0L, ByteBuffer.wrap(body));
        }
        link.setHttpStatus(status);
        link.setHttpLength(length);

        long expected = request.fileInfo().getFileLength();
        // A KNOWN size (>= 0, including a legitimate 0-byte file) must match exactly; only an UNKNOWN size (< 0)
        // falls back to "must be non-empty". Using >= 0 lets an empty torrent entry (expected == 0) succeed with a
        // 0-byte body instead of failing-and-retrying forever.
        if (expected >= 0 ? length != expected : length == 0)
        {
            throw new IOException("Akumu '" + request.fileInfo().getLinkPath() + "': downloaded " + length + " bytes, expected " + (expected >= 0 ? String.valueOf(expected) : "non-empty (torrent size unknown)") + " (HTTP " + status + "); failed download, will retry.");
        }
        request.partComplete(0);
        request.downloadComplete();
    }
}
