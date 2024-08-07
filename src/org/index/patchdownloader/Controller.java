package org.index.patchdownloader;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.config.parsers.MainConfigParser;
import org.index.patchdownloader.enums.CDNLink;
import org.index.patchdownloader.enums.StartUpArgumentsEnum;
import org.index.patchdownloader.impl.downloader.DownloadFiles;
import org.index.patchdownloader.interfaces.IDummyLogger;
import org.index.patchdownloader.model.linkgenerator.GeneralLinkGenerator;
import org.index.patchdownloader.util.FileUtils;

public class Controller implements IDummyLogger
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
            IDummyLogger.log(IDummyLogger.ERROR, "Patch version is unavailable. Version: " + MainConfig.PATCH_VERSION_SOURCE + "; CDN Source: " + MainConfig.CDN_SOURCE + ";");
            return;
        }
        _fileLinkGenerator.load();
        if (_fileLinkGenerator.getFileMapHolder().isEmpty())
        {
            IDummyLogger.log(IDummyLogger.ERROR, "Patch version is unavailable. Version: " + MainConfig.PATCH_VERSION_SOURCE + "; CDN Source: " + MainConfig.CDN_SOURCE + ";");
        }
        IDummyLogger.log(IDummyLogger.INFO, "Version: " + MainConfig.PATCH_VERSION_SOURCE + "; CDN Source: " + MainConfig.CDN_SOURCE + "; Total available files: " + _fileLinkGenerator.getFileMapHolder().size() + ";");
        IDummyLogger.log(IDummyLogger.INFO, "File list obtained. Program continue working...");

        DownloadFiles downloadFiles = new DownloadFiles(_fileLinkGenerator);
        downloadFiles.load();
    }

    public static void main(String[] args)
    {
        MainConfigParser.getInstance().load();
        parseStartUpArguments(args);
        if (!FileUtils.canGetAccessToFolder(MainConfig.DOWNLOAD_PATH))
        {
            IDummyLogger.log(IDummyLogger.ERROR, "Folder '" + MainConfig.DOWNLOAD_PATH + "' is closed for writing.");
            return;
        }
        if (MainConfig.CDN_SOURCE == null)
        {
            IDummyLogger.log(IDummyLogger.ERROR, "CDN Source is Undefined.");
            return;
        }
        new Controller(MainConfig.CDN_SOURCE, MainConfig.PATCH_VERSION_SOURCE);
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
