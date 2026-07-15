package org.index.patchdownloader.model.pipeline.download;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.interfaces.IDownloadRequest;
import org.index.patchdownloader.interfaces.IDummyLogger;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.model.holders.LinkInfoHolder;
import org.index.patchdownloader.model.pipeline.HttpStatusException;

/**
 * EN: Download of a file the Content-Delivery-Network (CDN) already split into parts ({@code getAllSeparatedParts()}).
 *     Fetches the N part URLs CONCURRENTLY, bounded by {@code parallel_parts_per_file}, into the task's per-part slots.
 *     Any part failing (non-200 / transport) fails the whole file so it is retried as a unit.<br>
 * RU: Загрузка файла, который сеть доставки контента (Content-Delivery-Network, CDN) уже разбила на части
 *     ({@code getAllSeparatedParts()}). Качает N URL-частей
 *     ПАРАЛЛЕЛЬНО, ограничиваясь {@code parallel_parts_per_file}, в слоты частей задачи. Сбой любой части
 *     (не-200 / транспорт) проваливает весь файл, чтобы он повторялся целиком.<br>
 **/
public class CdnPartsDownloadStrategy extends AbstractDownloadStrategy
{
    public CdnPartsDownloadStrategy(HttpClient httpClient)
    {
        super(httpClient);
    }

    /**
     * EN: Applies when the CDN split the file into parts. <br>
     * RU: Применяется, когда CDN разбил файл на части. <br>
     * ==================================================================<br>
     * EN: @param request the request / RU: @param request запрос <br>
     * @return <br>
     *         {true}  - EN: file has CDN parts / RU: у файла есть части CDN <br>
     *         {false} - EN: not a multi-part file / RU: не многочастный файл <br>
     **/
    @Override
    public boolean supports(IDownloadRequest request)
    {
        return request.fileInfo().getAllSeparatedParts().length > 0;
    }

    /**
     * EN: Fetches all CDN parts concurrently, streaming each part's body straight into its own slot as it
     *     arrives (never buffering a whole part into a single {@code byte[]}), then signals the whole raw
     *     payload received. Each part's chunks arrive in order over its one connection, so an in-memory slot's
     *     sequential append reconstructs the part exactly; a non-200 part fails the whole file so it retries as
     *     a unit. <br>
     * RU: Качает все части CDN параллельно, потоково записывая тело каждой части прямо в её слот по мере
     *     поступления (никогда не буферизуя целую часть в один {@code byte[]}), затем сигнализирует о полном
     *     получении сырых данных. Куски каждой части приходят по порядку по её единственному соединению,
     *     поэтому последовательная дозапись в слот в памяти точно восстанавливает часть; не-200 часть
     *     проваливает весь файл, чтобы он повторился целиком. <br>
     * ==================================================================<br>
     * EN: @param request the request to download / RU: @param request запрос для загрузки <br>
     **/
    @Override
    protected void doDownload(IDownloadRequest request) throws Exception
    {
        FileInfoHolder[] parts = request.fileInfo().getAllSeparatedParts();
        List<HttpRequest> requests = new ArrayList<>(parts.length);
        for (FileInfoHolder part : parts)
        {
            requests.add(buildRequest(part.getAccessLink()));
        }
        IDummyLogger.log(IDummyLogger.INFO, "CDN parts: " + parts.length + " parts (cap " + MainConfig.PARALLEL_PARTS_PER_FILE + ") for '" + request.fileInfo().getLinkPath() + "'.");
        long epoch = request.downloadEpoch();
        fetchConcurrently(requests, MainConfig.PARALLEL_PARTS_PER_FILE, new StreamHandler()
        {
            @Override
            public ChunkSink sink(int index)
            {
                return (offset, chunk) -> request.acceptChunk(epoch, index, offset, chunk);
            }

            @Override
            public boolean streamStatus(int index, int statusCode)
            {
                return statusCode == 200;
            }

            @Override
            public void settled(int index, int statusCode, long bytesStreamed) throws Exception
            {
                LinkInfoHolder link = parts[index].getAccessLink();
                link.setHttpStatus(statusCode);
                link.setHttpLength(bytesStreamed);
                if (statusCode != 200)
                {
                    throw new HttpStatusException(statusCode);
                }
                // Reconcile received vs the list-declared part length: a stale list where the server serves a
                // different size (its own Content-Length matches, so HTTP is happy) would place the next part at the
                // wrong offset in temp mode. Fail (retryable) instead of storing a shifted/gapped file.
                long declared = parts[index].getDownloadDataLength();
                if (declared > 0 && bytesStreamed != declared)
                {
                    throw new IOException("CDN part " + index + " of '" + request.fileInfo().getLinkPath() + "' returned " + bytesStreamed + " bytes, list declared " + declared + " (stale file list); failing to avoid a shifted file, will retry.");
                }
                request.partComplete(index);
            }
        });
        request.downloadComplete();
    }
}
