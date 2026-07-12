package org.index.patchdownloader.model.pipeline.download;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.interfaces.IDummyLogger;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.model.holders.LinkInfoHolder;
import org.index.patchdownloader.model.pipeline.FileDownloadTask;
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
     * EN: @param task the task / RU: @param task задача <br>
     * @return <br>
     *         {true}  - EN: file has CDN parts / RU: у файла есть части CDN <br>
     *         {false} - EN: not a multi-part file / RU: не многочастный файл <br>
     **/
    @Override
    public boolean supports(FileDownloadTask task)
    {
        return task.getFileInfo().getAllSeparatedParts().length > 0;
    }

    /**
     * EN: Fetches all CDN parts concurrently and stores each into its slot. <br>
     * RU: Качает все части CDN параллельно и кладёт каждую в свой слот. <br>
     * ==================================================================<br>
     * EN: @param task the task to download / RU: @param task задача для загрузки <br>
     **/
    @Override
    protected void doDownload(FileDownloadTask task) throws Exception
    {
        FileInfoHolder[] parts = task.getFileInfo().getAllSeparatedParts();
        List<HttpRequest> requests = new ArrayList<>(parts.length);
        for (FileInfoHolder part : parts)
        {
            requests.add(buildRequest(part.getAccessLink()));
        }
        IDummyLogger.log(IDummyLogger.INFO, "CDN parts: " + parts.length + " parts (cap " + MainConfig.PARALLEL_PARTS_PER_FILE + ") for '" + task.getLinkPath() + "'.");
        fetchConcurrently(requests, MainConfig.PARALLEL_PARTS_PER_FILE, (index, response) ->
        {
            LinkInfoHolder link = parts[index].getAccessLink();
            byte[] body = response.body() == null ? new byte[0] : response.body();
            link.setHttpStatus(response.statusCode());
            link.setHttpLength(body.length);
            if (response.statusCode() != 200)
            {
                throw new HttpStatusException(response.statusCode());
            }
            task.addDownloadedPart(index, body);
        });
    }
}
