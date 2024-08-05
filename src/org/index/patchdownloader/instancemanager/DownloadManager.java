package org.index.patchdownloader.instancemanager;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.interfaces.IRequest;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.model.holders.LinkInfoHolder;
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
        DownloadRequest downloaded = download(downloadRequest);
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

    public static DownloadRequest download(DownloadRequest request)
    {
        try
        {
            if (request.getFileInfoHolder().getAllSeparatedParts().length == 0)
            {
                FileInfoHolder infoHolder = request.getFileInfoHolder();
                String accessLink = infoHolder.getAccessLink().getAccessLink();
                int byteLength = infoHolder.getDownloadDataLength();
                byte[] downloaded = downloadImpl(infoHolder.getAccessLink(), accessLink, byteLength);
                request.addDownloadedPart(0, downloaded);
            }
            else
            {
                for (int index = 0; index < request.getFileInfoHolder().getAllSeparatedParts().length; index++)
                {
                    FileInfoHolder infoHolder = request.getFileInfoHolder().getAllSeparatedParts()[index];
                    String accessLink = infoHolder.getAccessLink().getAccessLink();
                    int byteLength = infoHolder.getDownloadDataLength();
                    byte[] downloaded = downloadImpl(infoHolder.getAccessLink(), accessLink, byteLength);
                    request.addDownloadedPart(index, downloaded);
                }
            }
            return request;
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return null;
    }

    private static byte[] downloadImpl(LinkInfoHolder linkInfo, String accessLink, int byteLength) throws IOException
    {
        URI uriLink = URI.create(accessLink);
        HttpURLConnection urlConnection = (HttpURLConnection) uriLink.toURL().openConnection();
        urlConnection.setRequestMethod("GET");
        linkInfo.setHttpStatus(urlConnection.getResponseCode());
        if (linkInfo.getHttpStatus() != 200)
        {
            throw new NoSuchElementException("Patch version is not available. Requested link: " + accessLink);
        }

        linkInfo.setHttpLength(urlConnection.getContentLength());

        int finalLength;
        if (byteLength == -1)
        {
            if (linkInfo.getHttpLength() == -1)
            {
                // finalLength = 20_971_520;
                finalLength = 20_975_368;
            }
            else
            {
                finalLength = linkInfo.getHttpLength();
            }
        }
        else
        {
            finalLength = byteLength;
        }

        InputStream is = urlConnection.getInputStream();
        ByteBuffer buffer = ByteBuffer.allocate(finalLength + 1);

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

        urlConnection.disconnect();

        return Arrays.copyOfRange(buffer.array(), 0, buffer.position());
    }
}
