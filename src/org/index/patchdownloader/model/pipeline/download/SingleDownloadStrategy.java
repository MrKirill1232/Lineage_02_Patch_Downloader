package org.index.patchdownloader.model.pipeline.download;

import java.net.http.HttpClient;

import org.index.patchdownloader.model.pipeline.FileDownloadTask;

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
     * EN: @param task the task / RU: @param task задача <br>
     * @return <br>
     *         {true} - EN: always / RU: всегда <br>
     **/
    @Override
    public boolean supports(FileDownloadTask task)
    {
        return true;
    }

    /**
     * EN: Downloads the whole file with one GET into part slot 0. <br>
     * RU: Скачивает весь файл одним GET в слот части 0. <br>
     * ==================================================================<br>
     * EN: @param task the task to download / RU: @param task задача для загрузки <br>
     **/
    @Override
    protected void doDownload(FileDownloadTask task) throws Exception
    {
        task.addDownloadedPart(0, fetchOne(task.getFileInfo().getAccessLink()));
    }
}
