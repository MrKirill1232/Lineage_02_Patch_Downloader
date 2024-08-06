package org.index.patchdownloader.impl.downloader;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.instancemanager.CheckSumManager;
import org.index.patchdownloader.instancemanager.DecompressManager;
import org.index.patchdownloader.instancemanager.DownloadManager;
import org.index.patchdownloader.instancemanager.StoreManager;
import org.index.patchdownloader.interfaces.ICondition;
import org.index.patchdownloader.interfaces.IRequest;
import org.index.patchdownloader.interfaces.IRequestor;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.model.linkgenerator.GeneralLinkGenerator;
import org.index.patchdownloader.model.requests.DecompressRequest;
import org.index.patchdownloader.model.requests.DownloadRequest;
import org.index.patchdownloader.model.requests.StoreRequest;
import org.index.patchdownloader.util.FileUtils;
import org.index.patchdownloader.util.concurrent.NamedExecutorServiceUtils;

import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class DownloadFiles implements IRequestor
{
    private final AtomicInteger _simpleCounter = new AtomicInteger();

    private final GeneralLinkGenerator  _linkGenerator;
    private final ScheduledThreadPoolExecutor _executor;
    private ScheduledFuture<?> _checkStatus;

    public DownloadFiles(GeneralLinkGenerator linkGenerator)
    {
        _linkGenerator = linkGenerator;
        _executor = NamedExecutorServiceUtils.scheduledThreadPoolExecutor(0, "Check status of Downloading.");
    }

    private void checkStatus()
    {
        boolean isCompleted = (_simpleCounter.get() >= _linkGenerator.getFileMapHolder().size());

        boolean isLinksGenerated = !_linkGenerator.getFileMapHolder().isEmpty();
        boolean isQueueClear = DownloadManager.getInstance().getCountOfTaskInQueue() == 0 && DecompressManager.getInstance().getCountOfTaskInQueue() == 0 && StoreManager.getInstance().getCountOfTaskInQueue() == 0;
        boolean isHardTaskRunning = DownloadManager.getInstance().isRunning() || DecompressManager.getInstance().isRunning() || StoreManager.getInstance().isRunning();

        if ((isCompleted || !isLinksGenerated) && (isQueueClear && !isHardTaskRunning))
        {
            System.out.println("Success!");
            System.exit(0);
        }
    }


    public void load()
    {
        DownloadManager.getInstance();
        DecompressManager.getInstance();
        StoreManager.getInstance();

        if (MainConfig.THREAD_USAGE)
        {
            DownloadManager.getInstance().initThreadPool(MainConfig.PARALLEL_DOWNLOADING, MainConfig.PARALLEL_DOWNLOADING);
            DecompressManager.getInstance().initThreadPool(MainConfig.PARALLEL_DECODING, MainConfig.PARALLEL_DECODING);
            StoreManager.getInstance().initThreadPool(MainConfig.PARALLEL_STORING, MainConfig.PARALLEL_STORING);
        }

        List<ICondition> conditionList = ICondition.loadConditions(_linkGenerator);
        for (FileInfoHolder fileInfoHolder : _linkGenerator.getFileMapHolder().values())
        {
            if (!ICondition.checkCondition(conditionList, fileInfoHolder))
            {
                _simpleCounter.addAndGet(1);
                continue;
            }
            if (!FileUtils.createSubFolders(MainConfig.DOWNLOAD_PATH, fileInfoHolder))
            {
                _simpleCounter.addAndGet(1);
                System.out.println("Cannot create a " + (fileInfoHolder.getLinkPath()) + ". Ignoring.");
                continue;
            }

            DownloadManager.getInstance().addRequestToQueue(new DownloadRequest(this, fileInfoHolder));
            if (!MainConfig.THREAD_USAGE)
            {
                DownloadManager.getInstance().runQueueEntry(-1);
            }
        }

        if (MainConfig.THREAD_USAGE)
        {
            _checkStatus = _executor.scheduleWithFixedDelay(() -> checkStatus(), 5_000, 1_000, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void onDownload(IRequest request)
    {
        DecompressManager.getInstance().addRequestToQueue(new DecompressRequest(this, (DownloadRequest) request));
        if (!MainConfig.THREAD_USAGE)
        {
            DecompressManager.getInstance().runQueueEntry(-1);
        }
    }

    @Override
    public void onDecompress(IRequest request)
    {
        DecompressRequest decompressRequest = (DecompressRequest) request;

        if (MainConfig.CHECK_FILE_SIZE)
        {
            int fileLength = decompressRequest.getFileInfoHolder().getFileLength() == -1 ? decompressRequest.getFileInfoHolder().getAccessLink().getHttpLength() : decompressRequest.getFileInfoHolder().getFileLength();
            if (fileLength != -1 && decompressRequest.getDecompressArray().length != fileLength)
            {
                System.out.println("File " + (decompressRequest.getLinkPath()) + " have different length than expected!");
            }
        }
        if (MainConfig.CHECK_HASH_SUM)
        {
            if (decompressRequest.getFileInfoHolder().getFileHashSum() == null)
            {
                System.out.println("File " + (decompressRequest.getLinkPath()) + " do not have hash sum in file map.");
            }
            if (!CheckSumManager.check(_linkGenerator.getHashingAlgorithm(), decompressRequest.getDecompressArray(), decompressRequest.getFileInfoHolder().getFileHashSum()))
            {
                System.out.println("File " + (decompressRequest.getLinkPath()) + " not match hash sum with original file.");
            }
        }

        StoreManager.getInstance().addRequestToQueue(new StoreRequest(this, decompressRequest.getDownloadRequest(), decompressRequest.getDecompressArray()));
        if (!MainConfig.THREAD_USAGE)
        {
            StoreManager.getInstance().runQueueEntry(-1);
        }
    }

    @Override
    public void onStore(IRequest request)
    {
        _simpleCounter.addAndGet(1);
        int percentCounter = (int) ((double) _simpleCounter.get() / (double) _linkGenerator.getFileMapHolder().size() * 100d);
        logStoring(MainConfig.ACMI_LIKE_LOGGING, request.getFileInfoHolder(), percentCounter);
    }

    private static void logStoring(boolean acmiLike, FileInfoHolder fileInfoHolder, int percentCounter)
    {
        if (fileInfoHolder == null)
        {
            System.out.println("??: " + "FAIL");
        }
        else
        {
            if (acmiLike)
            {
                System.out.println(fileInfoHolder.getLinkPath() + ": OK");
            }
            else
            {
                System.out.println("Progress " + percentCounter + "% / 100%" + " | " + "Storing '" + (fileInfoHolder.getLinkPath()) + "'...");
            }
        }
    }
}
