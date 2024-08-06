package org.index.patchdownloader;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.config.parsers.MainConfigParser;
import org.index.patchdownloader.enums.CDNLink;
import org.index.patchdownloader.enums.StartUpArgumentsEnum;
import org.index.patchdownloader.impl.downloader.DownloadFiles;
import org.index.patchdownloader.model.linkgenerator.GeneralLinkGenerator;
import org.index.patchdownloader.util.FileUtils;

public class Controller
{
    private final CDNLink _cdnType;
    private final int _patchVersion;

    private final GeneralLinkGenerator _fileLinkGenerator;

    public Controller(CDNLink cdnType, int patchVersion)
    {
        _cdnType = cdnType;
        _patchVersion = patchVersion;
        _fileLinkGenerator = GeneralLinkGenerator.generateLinkToFiles(_cdnType, _patchVersion);
        if (_fileLinkGenerator == null)
        {
            throw new IllegalArgumentException("CDN Link is unsupported!");
        }
        _fileLinkGenerator.load();
        if (_fileLinkGenerator.getFileMapHolder().isEmpty())
        {
            throw new IllegalArgumentException("Cannot fetch file info about protocol from requested CDN. Try another CDN or Patch version.");
        }
    }

    public static void main(String[] args)
    {
        synchronized (MainConfig.class)
        {
            MainConfigParser.getInstance().load();
        }
        parseStartUpArguments(args);
        if (!FileUtils.canGetAccessToFolder(MainConfig.DOWNLOAD_PATH))
        {
            System.out.println("Folder '" + MainConfig.DOWNLOAD_PATH + "' is closed for writing.");
            return;
        }
        if (MainConfig.CDN_SOURCE == null)
        {
            System.out.println("CDN Source is Undefined.");
            return;
        }
        GeneralLinkGenerator generalLinkGenerator = GeneralLinkGenerator.generateLinkToFiles(MainConfig.CDN_SOURCE, MainConfig.PATCH_VERSION_SOURCE);
        if (generalLinkGenerator == null)
        {
            System.out.println("Patch version is unavailable. Version: " + MainConfig.PATCH_VERSION_SOURCE + "; CDN Source: " + MainConfig.CDN_SOURCE + ";");
            return;
        }
        generalLinkGenerator.load();
        if (generalLinkGenerator.getFileMapHolder().isEmpty())
        {
            System.out.println("Patch version is unavailable. Version: " + MainConfig.PATCH_VERSION_SOURCE + "; CDN Source: " + MainConfig.CDN_SOURCE + ";");
            return;
        }

        DownloadFiles downloadFiles = new DownloadFiles(generalLinkGenerator);
        downloadFiles.load();
    }

    private static void parseStartUpArguments(String[] args)
    {
        if (args == null)
        {
            return;
        }
        for (int index = 0; index < args.length; index++)
        {
            StartUpArgumentsEnum argumentImpl = StartUpArgumentsEnum.getArgumentHandlerByInLineArgument(args[index]);
            if (argumentImpl == null)
            {
                continue;
            }
            argumentImpl.handleArguments(args[index], (args.length <= (index + 1) ? null : args[index + 1]));
            if (argumentImpl.requiredPossibleValue())
            {
                index += 1;
            }
        }
    }
}
