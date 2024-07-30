package org.index.protocoldownloader.impl.downloader;

import org.index.protocoldownloader.config.configs.MainConfig;
import org.index.protocoldownloader.instancemanager.CheckSumManager;
import org.index.protocoldownloader.instancemanager.DecodeManager;
import org.index.protocoldownloader.instancemanager.DownloadManager;
import org.index.protocoldownloader.instancemanager.StoreManager;
import org.index.protocoldownloader.interfaces.ICondition;
import org.index.protocoldownloader.interfaces.IRequest;
import org.index.protocoldownloader.interfaces.IRequestor;
import org.index.protocoldownloader.model.holders.LinkHolder;
import org.index.protocoldownloader.model.linkgenerator.GeneralLinkGenerator;
import org.index.protocoldownloader.model.requests.DownloadRequest;
import org.index.protocoldownloader.util.FileUtils;

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
