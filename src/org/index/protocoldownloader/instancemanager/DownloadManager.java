package org.index.protocoldownloader.instancemanager;

import org.index.protocoldownloader.config.configs.MainConfig;
import org.index.protocoldownloader.model.requests.DownloadRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class DownloadManager
{
    private final static DownloadManager INSTANCE = new DownloadManager(1);

    public static DownloadManager getInstance()
    {
        return INSTANCE;
    }

    private final int _parallelDownloadsCount;

    private final Queue<DownloadRequest> _requestQueue;

    private DownloadManager(int parallelDownloadsCount)
    {
        _parallelDownloadsCount = parallelDownloadsCount;
        _requestQueue = new ConcurrentLinkedQueue<>();
    }

    public void addToDownload(DownloadRequest request)
    {
        _requestQueue.add(request);
    }

    public void startDownload()
    {
        DownloadRequest request = _requestQueue.poll();
        if (request == null)
        {
            return;
        }
        download(request);
        request.onComplete();
    }

    public static DownloadRequest download(DownloadRequest request)
    {
        try
        {
            for (int index = 0; index < request.getLinkHolder().getTotalFileParts(); index++)
            {
                String accessLink = request.getLinkHolder().getAccessLink()[index];
                int byteLength = request.getLinkHolder().getFileLength()[index];

                URI uriLink = URI.create(accessLink);
                HttpURLConnection urlConnection = (HttpURLConnection) uriLink.toURL().openConnection();
                urlConnection.setRequestMethod("GET");
                request.setHttpStatus(urlConnection.getResponseCode());

                // if (request.getLinkHolder().getFileName().equalsIgnoreCase("files_info.json.zip"))
                {
                    if (request.getHttpStatus() != 200)
                    {
                        throw new NoSuchElementException("Patch version is not available. Requested link: " + accessLink);
                    }

                    if (byteLength == -1)
                    {
                        byteLength = urlConnection.getContentLength();
                    }

                    InputStream is = urlConnection.getInputStream();
                    ByteBuffer buffer = ByteBuffer.allocate(byteLength == -1 ? 20_971_520 : byteLength);

                    while (true)
                    {
                        byte[] bytes = new byte[1024];
                        int status = is.read(bytes);
                        if (status == -1)
                        {
                            break;
                        }
                        buffer.put(bytes, 0, status);
                    }

                    request.addDownloadedPart(index, Arrays.copyOfRange(buffer.array(), 0, buffer.position()));
                }


                urlConnection.disconnect();
            }
            return request;
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return null;
    }

}
