package org.index.patchdownloader.instancemanager;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.interfaces.IRequest;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.model.holders.LinkInfoHolder;
import org.index.patchdownloader.model.requests.DownloadRequest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class DownloadManager extends AbstractQueueManager
{
    private final static DownloadManager INSTANCE = new DownloadManager();

    public static DownloadManager getInstance()
    {
        return INSTANCE;
    }

    private HttpClient[] _clients = null;

    private DownloadManager()
    {
    }

    @Override
    public void initThreadPool(int corePoolSize, int threadCount)
    {
        if (_clients != null)
        {
            for (HttpClient httpClient : _clients)
            {
                httpClient.close();
            }
        }
        _clients = new HttpClient[threadCount];
        for (int index = 0; index < threadCount; index++)
        {
            _clients[index] = HttpClient.newHttpClient();
        }
        super.initThreadPool(corePoolSize, threadCount);
    }

    @Override
    public void runQueueEntry(int threadId)
    {
        IRequest request = _requestQueue.poll();
        if (request == null)
        {
            return;
        }
        if (!request.getClass().equals(DownloadRequest.class))
        {
            return;
        }
        HttpClient httpClient = getHttpClient(threadId);
        DownloadRequest downloadRequest = (DownloadRequest) request;
        DownloadRequest downloaded = download(httpClient, downloadRequest);
        if (downloaded == null && (MainConfig.MAX_DOWNLOAD_ATTEMPTS == -1 || downloadRequest.getDownloadingAttempts() <= MainConfig.MAX_DOWNLOAD_ATTEMPTS))
        {
            downloadRequest.getFileInfoHolder().getAccessLink().setHttpStatus(-1);
            downloadRequest.setDownloadingAttempts(downloadRequest.getDownloadingAttempts() + 1);
            System.out.println("Trying to re-start downloading for file: '" + downloadRequest.getLinkPath() + "';");
            addRequestToQueue(downloadRequest);
            return;
        }
        downloadRequest.onComplete();
    }

    private HttpClient getHttpClient(int threadId)
    {
        HttpClient httpClient = null;
        if (_clients == null && _executor == null)
        {
            _clients = new HttpClient[1];
            httpClient = (_clients[0] = HttpClient.newHttpClient());
        }
        else if (_executor == null && threadId == -1)
        {
            httpClient = _clients[0];
            if (httpClient == null || httpClient.isTerminated())
            {
                httpClient = (_clients[0] = HttpClient.newHttpClient());
            }
        }
        else if (_clients != null)
        {
            if (threadId == -1 || _clients.length <= threadId)
            {
                httpClient = null;
            }
            else
            {
                httpClient = _clients[threadId];
                if (httpClient == null || httpClient.isTerminated())
                {
                    httpClient = (_clients[threadId] = HttpClient.newHttpClient());
                }
            }
        }
        return httpClient;
    }

    public static DownloadRequest download(HttpClient client, DownloadRequest request)
    {
        try
        {
            if (request.getFileInfoHolder().getAllSeparatedParts().length == 0)
            {
                FileInfoHolder infoHolder = request.getFileInfoHolder();
                String accessLink = infoHolder.getAccessLink().getAccessLink();
                byte[] downloaded = downloadImplByClient(infoHolder.getAccessLink(), client, accessLink);
                request.addDownloadedPart(0, downloaded);
            }
            else
            {
                for (int index = 0; index < request.getFileInfoHolder().getAllSeparatedParts().length; index++)
                {
                    FileInfoHolder infoHolder = request.getFileInfoHolder().getAllSeparatedParts()[index];
                    String accessLink = infoHolder.getAccessLink().getAccessLink();
                    byte[] downloaded = downloadImplByClient(infoHolder.getAccessLink(), client, accessLink);
                    request.addDownloadedPart(index, downloaded);
                }
            }
            return request;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return null;
    }

    private static byte[] downloadImplByClient(LinkInfoHolder linkInfo, HttpClient client, String accessLink) throws IOException, InterruptedException
    {
        HttpRequest.Builder builder = HttpRequest.newBuilder();
        builder.uri(URI.create(accessLink));
        builder.GET();
        if (MainConfig.REQUESTED_USER_AGENT != null)
        {
            builder.header("User-Agent", MainConfig.REQUESTED_USER_AGENT);
        }
        HttpRequest request = builder.build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        byte[] downloadedArray = response.body();
        if (linkInfo != null)
        {
            linkInfo.setHttpStatus(response.statusCode());
            // linkInfo.setHttpLength((int) response.headers().firstValueAsLong("content-length").orElse(-1));
            linkInfo.setHttpLength(downloadedArray.length);
        }
        return downloadedArray;
    }
}
