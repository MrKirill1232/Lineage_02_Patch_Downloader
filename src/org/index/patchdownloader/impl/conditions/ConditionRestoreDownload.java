package org.index.patchdownloader.impl.conditions;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.instancemanager.CheckSumManager;
import org.index.patchdownloader.interfaces.ICondition;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.model.linkgenerator.GeneralLinkGenerator;
import org.index.patchdownloader.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

public class ConditionRestoreDownload implements ICondition
{
    private final GeneralLinkGenerator  _linkGenerator;
    private final Set<String>           _excludeFileList;

    public ConditionRestoreDownload(GeneralLinkGenerator linkGenerator)
    {
        _linkGenerator  = linkGenerator;
        _excludeFileList= new HashSet<>();
        load();
    }

    private void load()
    {
        if (!MainConfig.CHECK_BY_NAME && !MainConfig.CHECK_BY_SIZE && !MainConfig.CHECK_BY_HASH_SUM)
        {
            return;
        }
        System.out.println("Warning! Program is working. Now program check all the files inside '" + MainConfig.DOWNLOAD_PATH + "' folder. It can take some time.");
        File[] fileList = FileUtils.getFileList(MainConfig.DOWNLOAD_PATH, MainConfig.DEPTH_OF_FILE_CHECK);
        for (File file : fileList)
        {
            String pathAndName  = getPathAndName(file).toLowerCase().replaceAll("\\\\", "/");

            FileInfoHolder fileInfoHolder = _linkGenerator.getFileMapHolder().getOrDefault(pathAndName, null);
            if (fileInfoHolder == null)
            {
                continue;
            }

            boolean checkBySize = !MainConfig.CHECK_BY_SIZE;
            boolean checkByHash = !MainConfig.CHECK_BY_HASH_SUM;

            if (MainConfig.CHECK_BY_SIZE)
            {
                checkBySize = fileInfoHolder.getFileLength() == ((int) file.length());
            }
            if (MainConfig.CHECK_BY_HASH_SUM)
            {
                try
                {
                    checkByHash = CheckSumManager.check(_linkGenerator.getHashingAlgorithm(), Files.readAllBytes(file.toPath()), fileInfoHolder.getFileHashSum());
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }
            }
            if (checkBySize && checkByHash)
            {
                _excludeFileList.add(pathAndName);
            }
        }
        System.out.println("Warning! Program is working. Exclude File List formed.");
    }

    private static String getPathAndName(File file)
    {
        String pathToDownload = MainConfig.DOWNLOAD_PATH.getAbsolutePath();
        String pathToCurrFile = file.getAbsolutePath();
        return pathToCurrFile.substring(pathToDownload.length() + 1);
    }

    @Override
    public boolean check(FileInfoHolder fileInfoHolder)
    {
        String filePath = fileInfoHolder.getFilePath();
        if (filePath.endsWith("/"))
        {
            filePath = filePath.substring(0, filePath.length() -1);
        }
        return !_excludeFileList.contains((filePath + "/" + fileInfoHolder.getFileName()).toLowerCase());
    }
}
