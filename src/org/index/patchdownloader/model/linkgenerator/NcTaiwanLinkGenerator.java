package org.index.patchdownloader.model.linkgenerator;

import org.index.patchdownloader.enums.CDNLink;
import org.index.patchdownloader.enums.FileTypeByLink;
import org.index.patchdownloader.model.requests.DownloadRequest;
import org.index.patchdownloader.instancemanager.DownloadManager;
import org.index.patchdownloader.model.holders.LinkHolder;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class NcTaiwanLinkGenerator extends GeneralLinkGenerator
{
    protected NcTaiwanLinkGenerator(int patchVersion)
    {
        super(CDNLink.NC_SOFT_TAIWAN, patchVersion);
    }

    protected NcTaiwanLinkGenerator(CDNLink cdnLink, int patchVersion)
    {
        super(cdnLink, patchVersion);
    }

    @Override
    public void load()
    {
        String listUrl = String.format(_cdnLinkType.getCdnFileListLink(), _patchVersion, _patchVersion);

        LinkHolder listLinkHolder = new LinkHolder(getFileListFileName(), "", FileTypeByLink.NORMAL_FILE, 1);
        listLinkHolder.setNamesOfFiles(0, listLinkHolder.getFileName());
        listLinkHolder.setFileLength(0, -1);
        listLinkHolder.setAccessLink(0, listUrl);

        DownloadRequest listrequest = DownloadManager.download(new DownloadRequest(null, listLinkHolder));
        parseFileList(listrequest);

        String hashUrl = String.format(_cdnLinkType.getGeneralCdnLink(), _patchVersion, getFileListFileHash());

        LinkHolder hashLinkHolder = new LinkHolder(getFileListFileHash(), "", FileTypeByLink.NORMAL_FILE, 1);
        hashLinkHolder.setNamesOfFiles(0, hashLinkHolder.getFileName());
        hashLinkHolder.setFileLength(0, -1);
        hashLinkHolder.setAccessLink(0, hashUrl);

        DownloadRequest hashrequest = DownloadManager.download(new DownloadRequest(null, hashLinkHolder));
        parseHashList(hashrequest);
    }

    protected String getFileListFileName()
    {
        return "PatchFileInfo_TWLin2EP20_" + _patchVersion + ".dat";
    }

    protected String getFileListFileHash()
    {
        return "FileInfoMap_TWLin2EP20_" + _patchVersion + ".dat";
    }

    protected void parseFileList(DownloadRequest request)
    {
        if (request == null || !request.isComplete())
        {
            return;
        }
        String fileInfo = new String(request.getDownloadedByteArray()[0], StandardCharsets.UTF_16LE);
        String[] lines = fileInfo.split("\r\n");
        Map<String, String> stringMapOfValues = new HashMap<>();
        for (String line : lines)
        {
            line = line.replace("\uFEFF", "").replaceAll("\\\\", "/");
            String pathAndName = line.split(":", 2)[0];
            stringMapOfValues.put(pathAndName, line);
        }
        for (String line : stringMapOfValues.values())
        {
            String  pathAndName = line.split(":", 2)[0];
            int     typeOfFile  = Integer.parseInt(line.substring(line.length() - 1));
            boolean isSeparated = (typeOfFile == FileTypeByLink.SEPARATED.ordinal() || typeOfFile == FileTypeByLink.UNK_04.ordinal()) && Character.isDigit(pathAndName.charAt(pathAndName.length() - 1));
            String  nameOfPart  = isSeparated ? pathAndName.substring(0, pathAndName.length() - 2) + "%02d" : pathAndName;
            int     countOfSeparatedFiles;
            if (isSeparated)
            {
                int part = Integer.parseInt(pathAndName.substring(pathAndName.length() - 2));
                if (part > 1)
                {
                    continue;
                }
                int separateCounter = 0;
                while (true)
                {
                    String checkPart = String.format(nameOfPart, ((separateCounter) + 1));
                    if (!stringMapOfValues.containsKey(checkPart))
                    {
                        break;
                    }
                    separateCounter += 1;
                }
                countOfSeparatedFiles = separateCounter;
            }
            else
            {
                countOfSeparatedFiles = 1;
            }

            LinkHolder linkHolder = new LinkHolder(getNameOfFile(pathAndName, (isSeparated && countOfSeparatedFiles > 1), false), getPathOfFile(line), FileTypeByLink.values()[typeOfFile], countOfSeparatedFiles);

            for (int sIndex = 0; sIndex < countOfSeparatedFiles; sIndex++)
            {
                if (line.contains("HighElf"))
                {
                    System.out.println();
                }
                String lookingInfo = isSeparated && countOfSeparatedFiles > 1 ? stringMapOfValues.get(String.format(nameOfPart, (sIndex + 1))) : line;
                String[] splitLineInfo = lookingInfo.split(":", 5);
                if (splitLineInfo.length != 4)
                {
                    throw new IllegalArgumentException("[path]:[size]:[sha-1 hash]:[file_type]. Structure is not full!");
                }
                String  pathUndName = splitLineInfo[0];
                String  fileLength  = splitLineInfo[1];
                String  hashCode    = splitLineInfo[2];
                linkHolder.setNamesOfFiles(sIndex, getNameOfFile(pathUndName, false, false));
                linkHolder.setAccessLink(sIndex, formatGetUrl(pathUndName));
                linkHolder.setFileLength(sIndex, Integer.parseInt(fileLength));
                _fileMapHolder.put((linkHolder.getLinkPath()).toLowerCase(), linkHolder);
            }
        }
    }

    protected void parseHashList(DownloadRequest request)
    {
        if (request == null || !request.isComplete())
        {
            return;
        }

        String hashInfo = new String(request.getDownloadedByteArray()[0], StandardCharsets.UTF_16LE);
        String[] lines = hashInfo.split("\r\n");

        for (String line : lines)
        {
            String[] splitLineInfo = line.replace("\uFEFF", "").replaceAll("\\\\", "/").split(":");

            String pathAndName = splitLineInfo[0].toLowerCase();

            LinkHolder linkHolder = _fileMapHolder.getOrDefault(pathAndName, null);
            if (linkHolder == null)
            {
                continue;
            }

            String  fileLength  = splitLineInfo[1];
            String  hashCode    = splitLineInfo[2];

            linkHolder.setOriginalFileLength(Integer.parseInt(fileLength));
            linkHolder.setOriginalFileHashsum(hashCode);
        }
    }

    private String getNameOfFile(String pathAndName, boolean separated, boolean ignoreExtension)
    {
        String[] splitPathByFolders
                = pathAndName.split("/");
        String nameOfFile
                = splitPathByFolders[splitPathByFolders.length - 1];
        if (!ignoreExtension && (nameOfFile.endsWith(".zip") || separated))
        {
            nameOfFile = nameOfFile.substring(0, nameOfFile.length() - 4);
        }
        return nameOfFile;
    }

    private String getPathOfFile(String pathAndName)
    {
        String[] splitPathByFolders
                = pathAndName.split("/");

        if (splitPathByFolders.length <= 1)
        {
            return "";
        }

        StringBuilder returnName = new StringBuilder();

        boolean isZipHeader = splitPathByFolders[0].length() == 3 && splitPathByFolders[0].equalsIgnoreCase("zip");
        boolean isPatchVersionHeader = splitPathByFolders[0].equalsIgnoreCase(String.valueOf(getPatchVersion(pathAndName)));

        if (!(isZipHeader || isPatchVersionHeader))
        {
            returnName.append(splitPathByFolders[0]).append("/");
        }

        if (splitPathByFolders.length - 1 <= 0)
        {
            return returnName.toString();
        }

        for (int index = 1; index < splitPathByFolders.length - 1; index++)
        {
            returnName.append(splitPathByFolders[index]).append("/");
        }
        return returnName.toString();
    }

    // input 528\system\LineageMonster12.u.dlt.zip
    private int getPatchVersion(String pathUndName)
    {
        String[] splitThePath = pathUndName.split("/", 2);
        for (char digitCheck : splitThePath[0].toCharArray())
        {
            if (!Character.isDigit(digitCheck))
            {
                return _patchVersion;
            }
        }
        return Integer.parseInt(splitThePath[0]);
    }

    private String formatGetUrl(String pathAndName)
    {
        String path = getPathOfFile(pathAndName);
        String name = getNameOfFile(pathAndName, false, true);
        int patchVer= getPatchVersion(pathAndName);
        if (path.isEmpty())
        {
            return String.format(_cdnLinkType.getGeneralCdnLink(), _patchVersion, pathAndName);
        }
        if (patchVer != _patchVersion)
        {
            return String.format(_cdnLinkType.getGeneralCdnLink(), _patchVersion, (patchVer + "/" + path + name));
        }
        return String.format(_cdnLinkType.getGeneralCdnLink(), patchVer, (pathAndName));
    }
}
