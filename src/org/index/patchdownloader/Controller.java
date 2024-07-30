package org.index.patchdownloader;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.config.parsers.MainConfigParser;
import org.index.patchdownloader.enums.CDNLink;
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
        MainConfigParser.getInstance().load();
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
            System.out.println("Patch version is unavailable. Version: " + MainConfig.PATCH_VERSION_SOURCE + "; CDN Source:" + MainConfig.CDN_SOURCE + ";");
            return;
        }
        generalLinkGenerator.load();
        if (generalLinkGenerator.getFileMapHolder().isEmpty())
        {
            System.out.println("Patch version is unavailable. Version: " + MainConfig.PATCH_VERSION_SOURCE + "; CDN Source:" + MainConfig.CDN_SOURCE + ";");
            return;
        }

        DownloadFiles downloadFiles = new DownloadFiles(generalLinkGenerator);
        downloadFiles.load();
    }

    private static void printInfo()
    {
        String str = "";

        str += "This program is support arguments, and you can use them!";
        str += "\n";
        str += "[-cdn] - selecting download channel. Supports a 3 options. [NC_SOFT_TAIWAN] | [NC_SOFT_KOREAN] | [NC_SOFT_JAPANESE]";
        str += "\n";
        str += "Example: -cdn NC_SOFT_TAIWAN";
        str += "\n";
        str += "[-version] - selection a patch version. WARNING! THIS OPTION NOT THE SAME VERSION AS \"PROTOCOL VERSION\" OF LINEAGE 2.";
        str += "\n";
        str += "Patch version - its a version of installed client files. Example 89 patch is 486 game protocol on Korean.";
        str += "\n";
        str += "Latest knows versions (on 07/27/2024):";
        str += "\n";
        str += "NC_SOFT_TAIWAN   - 529";
        str += "NC_SOFT_KOREAN   - 089";
        str += "NC_SOFT_JAPANESE - 102";
        str += "\n";
        str += "Example: -version 529";
        str += "\n";
        str += "[-path] - output path of downloaded files.";
        str += "\n";
        str += "Example: -path \"C://downloads/lineage_02/429/\"";
        str += "\n";
        str += "[-request] - request for download path. All requested files will check by \"regex\" patterns.";
        str += "\n";
        str += "Examples: ";
        str += "\n";
        str += "01. -request system/*.u";
        str += "\n";
        str += "02. -request system/*.dat;system/interface.*";
        str += "\n";
        str += "[-sha] - will compare SHA checksum with downloaded file.";
        str += "\n";

        System.out.println(str);
    }
}
