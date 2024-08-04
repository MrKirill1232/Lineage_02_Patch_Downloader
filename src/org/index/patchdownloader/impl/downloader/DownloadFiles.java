package org.index.patchdownloader.impl.downloader;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.instancemanager.CheckSumManager;
import org.index.patchdownloader.instancemanager.DecodeManager;
import org.index.patchdownloader.instancemanager.DownloadManager;
import org.index.patchdownloader.instancemanager.StoreManager;
import org.index.patchdownloader.interfaces.ICondition;
import org.index.patchdownloader.interfaces.IRequest;
import org.index.patchdownloader.interfaces.IRequestor;
import org.index.patchdownloader.model.holders.LinkHolder;
import org.index.patchdownloader.model.linkgenerator.GeneralLinkGenerator;
import org.index.patchdownloader.model.requests.DecodeRequest;
import org.index.patchdownloader.model.requests.DownloadRequest;
import org.index.patchdownloader.model.requests.StoreRequest;
import org.index.patchdownloader.util.FileUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class DownloadFiles implements IRequestor
{
    private AtomicInteger _simpleCounter = new AtomicInteger();

    private final GeneralLinkGenerator  _linkGenerator;
    private final List<ICondition>      _conditionList;

    public DownloadFiles(GeneralLinkGenerator linkGenerator)
    {
        _linkGenerator      = linkGenerator     ;
        _conditionList      = new ArrayList<>() ;
    }

    public void load()
    {
        DownloadManager.getInstance();
        DecodeManager.getInstance();
        StoreManager.getInstance();

        if (MainConfig.THREAD_USAGE)
        {
            DownloadManager.getInstance().initThreadPool(MainConfig.PARALLEL_DOWNLOADING, MainConfig.PARALLEL_DOWNLOADING);
            DecodeManager.getInstance().initThreadPool(MainConfig.PARALLEL_DECODING, MainConfig.PARALLEL_DECODING);
            StoreManager.getInstance().initThreadPool(MainConfig.PARALLEL_STORING, MainConfig.PARALLEL_STORING);
        }

        List<ICondition> conditionList = ICondition.loadConditions(_linkGenerator);
        for (LinkHolder linkHolder : _linkGenerator.getFileMapHolder().values())
        {
            if (!ICondition.checkCondition(conditionList, linkHolder))
            {
                _simpleCounter.addAndGet(1);
                continue;
            }
            if (!FileUtils.createSubFolders(MainConfig.DOWNLOAD_PATH, linkHolder))
            {
                _simpleCounter.addAndGet(1);
                System.out.println("Cannot create a " + ("/" + linkHolder.getFilePath() + "/" + linkHolder.getFileName()) + ". Ignoring.");
                continue;
            }

            DownloadManager.getInstance().addRequestToQueue(new DownloadRequest(this, linkHolder));
            if (!MainConfig.THREAD_USAGE)
            {
                DownloadManager.getInstance().runQueueEntry();
            }
        }
    }

    @Override
    public void onDownload(IRequest request)
    {
        DecodeManager.getInstance().addRequestToQueue(new DecodeRequest(this, (DownloadRequest) request));
        if (!MainConfig.THREAD_USAGE)
        {
            DecodeManager.getInstance().runQueueEntry();
        }
    }

    @Override
    public void onDecode(IRequest request)
    {
        DecodeRequest decodeRequest = (DecodeRequest) request;

        if (MainConfig.CHECK_FILE_SIZE &&  decodeRequest.getLinkHolder().getOriginalFileLength() != 0 && (decodeRequest.getDecodedArray().length != decodeRequest.getLinkHolder().getOriginalFileLength()))
        {
            System.out.println("File " + (decodeRequest.getLinkHolder().getFilePath() + "/" + decodeRequest.getLinkHolder().getFileName()) + " have different length than expected!");
        }
        if (MainConfig.CHECK_HASH_SUM)
        {
            if (decodeRequest.getLinkHolder().getOriginalFileHashsum() == null)
            {
                System.out.println("File " + (decodeRequest.getLinkHolder().getFilePath() + "/" + decodeRequest.getLinkHolder().getFileName()) + " do not have hashsum in file map.");
            }
            if (!CheckSumManager.check(decodeRequest.getDecodedArray(), decodeRequest.getLinkHolder().getOriginalFileHashsum()))
            {
                System.out.println("File " + (decodeRequest.getLinkHolder().getFilePath() + "/" + decodeRequest.getLinkHolder().getFileName()) + " not match hash sum with original file.");
            }
        }

        StoreManager.getInstance().addRequestToQueue(new StoreRequest(this, decodeRequest.getDownloadRequest(), decodeRequest.getDecodedArray()));
        if (!MainConfig.THREAD_USAGE)
        {
            StoreManager.getInstance().runQueueEntry();
        }
    }

    @Override
    public void onStore(IRequest request)
    {
        _simpleCounter.addAndGet(1);
        int percentCounter = (int) ((double) _simpleCounter.get() / (double) _linkGenerator.getFileMapHolder().size() * 100d);
        logStoring(MainConfig.ACMI_LIKE_LOGGING, request.getLinkHolder(), percentCounter);
        if (MainConfig.THREAD_USAGE)
        {
            if (DownloadManager.getInstance().getCountOfTaskInQueue() == 0 && DecodeManager.getInstance().getCountOfTaskInQueue() == 0 && StoreManager.getInstance().getCountOfTaskInQueue() == 0)
            {
                System.out.println("Success!");
                System.exit(0);
            }
        }
    }

    private static void logStoring(boolean acmiLike, LinkHolder linkHolder, int percentCounter)
    {
        if (linkHolder == null)
        {
            System.out.println("??: " + "FAIL");
        }
        else
        {
            if (acmiLike)
            {
                System.out.println(linkHolder.getFilePath() + "/" + linkHolder.getFileName() + ": OK");
            }
            else
            {
                System.out.println("Progress " + percentCounter + "% / 100%" + "Storing " + ("/" + linkHolder.getFilePath() + "/" + linkHolder.getFileName()) + "...");
            }
        }
    }
}
