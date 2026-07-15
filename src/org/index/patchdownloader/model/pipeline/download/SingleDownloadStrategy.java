package org.index.patchdownloader.model.pipeline.download;

import java.net.http.HttpClient;

import org.index.patchdownloader.interfaces.IDownloadRequest;

/**
 * EN: Plain single-connection download: one GET for the whole file. The always-applicable fallback used
 *     for non-separated files that are small or when the server does not support range downloads.<br>
 * RU: Обычная загрузка одним соединением: один GET на весь файл. Всегда применимый запасной вариант для
 *     не разделённых файлов, которые малы, или когда сервер не поддерживает загрузку по диапазонам.<br>
 **/
public class SingleDownloadStrategy extends AbstractDownloadStrategy
{
    public SingleDownloadStrategy(HttpClient httpClient)
    {
        super(httpClient);
    }

    /**
     * EN: Always applies — this is the fallback strategy. <br>
     * RU: Применяется всегда — это запасная стратегия. <br>
     * ==================================================================<br>
     * EN: @param request the request / RU: @param request запрос <br>
     * @return <br>
     *         {true} - EN: always / RU: всегда <br>
     **/
    @Override
    public boolean supports(IDownloadRequest request)
    {
        return true;
    }

    /**
     * EN: Downloads the whole file with one GET, streaming each body chunk into part slot 0 as it arrives
     *     (never buffering the whole file into a single {@code byte[]}), then signals the raw payload
     *     received. <br>
     * RU: Скачивает весь файл одним GET, потоково записывая каждый кусок тела в слот части 0 по мере
     *     поступления (никогда не буферизуя весь файл в один {@code byte[]}), затем сигнализирует о получении
     *     сырых данных. <br>
     * ==================================================================<br>
     * EN: @param request the request to download / RU: @param request запрос для загрузки <br>
     **/
    @Override
    protected void doDownload(IDownloadRequest request) throws Exception
    {
        long epoch = request.downloadEpoch();
        streamOne(request.fileInfo().getAccessLink(), (offset, chunk) -> request.acceptChunk(epoch, 0, offset, chunk));
        request.partComplete(0);
        request.downloadComplete();
    }
}
