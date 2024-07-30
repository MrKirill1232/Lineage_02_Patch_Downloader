package org.index.protocoldownloader.impl.conditions;

import org.index.protocoldownloader.config.configs.MainConfig;
import org.index.protocoldownloader.config.parsers.MainConfigParser;
import org.index.protocoldownloader.enums.CDNLink;
import org.index.protocoldownloader.instancemanager.CheckSumManager;
import org.index.protocoldownloader.interfaces.ICondition;
import org.index.protocoldownloader.model.holders.LinkHolder;
import org.index.protocoldownloader.model.linkgenerator.GeneralLinkGenerator;
import org.index.protocoldownloader.util.FileUtils;

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
        File[] fileList = FileUtils.getFileList(MainConfig.DOWNLOAD_PATH, true);
        for (File file : fileList)
        {
            String pathAndName  = getPathAndName(file).toLowerCase().replaceAll("\\\\", "/");

            LinkHolder linkHolder = _linkGenerator.getFileMapHolder().getOrDefault(pathAndName, null);
            if (linkHolder == null)
            {
                continue;
            }

            boolean checkBySize = !MainConfig.CHECK_BY_SIZE;
            boolean checkByHash = !MainConfig.CHECK_BY_HASH_SUM;

            if (MainConfig.CHECK_BY_SIZE)
            {
                checkBySize = linkHolder.getOriginalFileLength() == ((int) file.length());
            }
            if (MainConfig.CHECK_BY_HASH_SUM)
            {
                try
                {
                    checkByHash = CheckSumManager.check(Files.readAllBytes(file.toPath()), linkHolder.getOriginalFileHashsum());
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
    }

    private static String getPathAndName(File file)
    {
        String pathToDownload = MainConfig.DOWNLOAD_PATH.getAbsolutePath();
        String pathToCurrFile = file.getAbsolutePath();
        return pathToCurrFile.substring(pathToDownload.length() + 1);
    }

    @Override
    public boolean check(LinkHolder linkHolder)
    {
        String filePath = linkHolder.getFilePath();
        if (filePath.endsWith("/"))
        {
            filePath = filePath.substring(0, filePath.length() -1);
        }
        return !_excludeFileList.contains((filePath + "/" + linkHolder.getFileName()).toLowerCase());
    }

//    public static void main(String[] args)
//    {
//        MainConfigParser.getInstance().load();
//        MainConfig.CHECK_BY_HASH_SUM = true;
//
//        GeneralLinkGenerator taiwanLinkGenerator = GeneralLinkGenerator.generateLinkToFiles(CDNLink.NC_SOFT_JAPANESE, 101);
//        taiwanLinkGenerator.load();
//
//        ConditionRestoreDownload restoreDownload = new ConditionRestoreDownload(taiwanLinkGenerator);
//
//        System.out.println();
//    }
}
