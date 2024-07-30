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
import org.index.patchdownloader.model.requests.DownloadRequest;
import org.index.patchdownloader.util.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DownloadFiles implements IRequestor
{
    private final GeneralLinkGenerator  _linkGenerator;
    private final List<ICondition>      _conditionList;

    public DownloadFiles(GeneralLinkGenerator linkGenerator)
    {
        _linkGenerator      = linkGenerator     ;
        _conditionList      = new ArrayList<>() ;
    }

    public void load()
    {
        List<ICondition> conditionList = ICondition.loadConditions(_linkGenerator);
        for (LinkHolder linkHolder : _linkGenerator.getFileMapHolder().values())
        {
            if (!ICondition.checkCondition(conditionList, linkHolder))
            {
                continue;
            }
            if (!FileUtils.createSubFolders(MainConfig.DOWNLOAD_PATH, linkHolder))
            {
                System.out.println("Cannot create a " + ("/" + linkHolder.getFilePath() + "/" + linkHolder.getFileName()) + ". Ignoring.");
                continue;
            }
            DownloadRequest downloadRequest = DownloadManager.download(new DownloadRequest(this, linkHolder));
            byte[] decodedByteArray = DecodeManager.decode(downloadRequest);

            if (MainConfig.CHECK_HASH_SUM && linkHolder.getOriginalFileHashsum() == null)
            {
                System.out.println("Cannnot get HASHSUM of file " + linkHolder.getFileName());
                continue;
            }

            if (MainConfig.CHECK_HASH_SUM && !CheckSumManager.check(decodedByteArray, linkHolder.getOriginalFileHashsum()))
            {
                System.out.println("HASHSUM is WRONG " + linkHolder.getFileName());
                continue;
            }

            File storeFile = new File(MainConfig.DOWNLOAD_PATH, ("/" + linkHolder.getFilePath() + "/" + linkHolder.getFileName()));

            StoreManager.store(storeFile, decodedByteArray);
            System.out.println("Storing " + ("/" + linkHolder.getFilePath() + "/" + linkHolder.getFileName()) + "...");
        }
    }

    @Override
    public void onDownload(IRequest request)
    {

    }

    @Override
    public void onDecode(IRequest request)
    {

    }

    @Override
    public void onStore(IRequest request)
    {

    }
}
