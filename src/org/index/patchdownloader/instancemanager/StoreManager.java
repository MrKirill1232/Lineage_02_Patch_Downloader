package org.index.patchdownloader.instancemanager;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.interfaces.IRequest;
import org.index.patchdownloader.model.requests.StoreRequest;

import java.io.File;
import java.io.FileOutputStream;

public class StoreManager extends AbstractQueueManager
{
    private final static StoreManager INSTANCE = new StoreManager();

    public static StoreManager getInstance()
    {
        return INSTANCE;
    }

    private StoreManager()
    {
    }

    @Override
    public void runQueueEntry(int threadId)
    {
        IRequest request = _requestQueue.poll();
        if (request == null)
        {
            return;
        }
        if (!request.getClass().equals(StoreRequest.class))
        {
            return;
        }
        StoreRequest storeRequest = (StoreRequest) request;
        File storeFile = new File(MainConfig.DOWNLOAD_PATH, storeRequest.getLinkPath());
        store(storeFile, storeRequest.getStorableByteArray());
        request.onComplete();
    }

    public static void store(File storeAsFile, byte[] storeByteArray)
    {
        try
        {
            FileOutputStream fileOutputStream = new FileOutputStream(storeAsFile);
            fileOutputStream.write(storeByteArray);
            fileOutputStream.flush();
            fileOutputStream.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
}
