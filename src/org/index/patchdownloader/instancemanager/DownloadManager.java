package org.index.patchdownloader.instancemanager;

import org.index.patchdownloader.interfaces.IRequest;
import org.index.patchdownloader.model.requests.DownloadRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.NoSuchElementException;

public class DownloadManager extends AbstractQueueManager
{
    private final static DownloadManager INSTANCE = new DownloadManager();

    public static DownloadManager getInstance()
    {
        return INSTANCE;
    }

    private DownloadManager()
    {
    }

    @Override
    public void runQueueEntry()
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
        DownloadRequest downloadRequest = (DownloadRequest) request;
        download(downloadRequest);
        downloadRequest.onComplete();
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
