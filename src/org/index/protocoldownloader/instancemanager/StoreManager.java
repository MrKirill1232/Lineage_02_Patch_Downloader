package org.index.protocoldownloader.instancemanager;

import org.index.protocoldownloader.config.configs.MainConfig;
import org.index.protocoldownloader.model.requests.StoreRequest;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class StoreManager
{
    private final int _parallelStoreCount;

    private final Queue<StoreRequest> _requestQueue;

    private StoreManager(int parallelStoreCount)
    {
        _parallelStoreCount = parallelStoreCount;
        _requestQueue = new ConcurrentLinkedQueue<>();
    }

    public void addToQueue(StoreRequest storeRequest)
    {
        _requestQueue.add(storeRequest);
    }

    public void startQueue()
    {
        StoreRequest request = _requestQueue.poll();
        if (request == null)
        {
            return;
        }
        File storeFile = new File(MainConfig.DOWNLOAD_PATH, request.getSavePath());
        store(storeFile, request.getStorableByteArray());
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
