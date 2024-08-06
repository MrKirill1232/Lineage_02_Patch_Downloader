package org.index.patchdownloader.model.linkgenerator;

import org.index.patchdownloader.enums.ArchiveType;
import org.index.patchdownloader.enums.CDNLink;
import org.index.patchdownloader.enums.FileTypeByLink;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.model.holders.LinkInfoHolder;
import org.index.patchdownloader.model.requests.DownloadRequest;
import org.index.patchdownloader.instancemanager.DownloadManager;

import java.net.http.HttpClient;
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
        HttpClient httpClient;
        //------------------------------------------------------------------------------------------------------//
        httpClient = HttpClient.newHttpClient();
        //------------------------------------------------------------------------------------------------------//

        String fileListUrl = String.format(_cdnLinkType.getCdnFileListLink(), _patchVersion, _patchVersion);

        FileInfoHolder fileListInfo = new FileInfoHolder(getFileListFileName(), "", ArchiveType.NONE, false, 0);
        fileListInfo.setFileLength(-1);
        fileListInfo.setAccessLink(new LinkInfoHolder(fileListInfo));
        fileListInfo.getAccessLink().setAccessLink(fileListUrl);

        DownloadRequest fileListRequest = DownloadManager.download(httpClient, new DownloadRequest(null, fileListInfo));

        //------------------------------------------------------------------------------------------------------//
        httpClient.close();
        //------------------------------------------------------------------------------------------------------//

        parseFileList(fileListRequest);

        //------------------------------------------------------------------------------------------------------//
        httpClient = HttpClient.newHttpClient();
        //------------------------------------------------------------------------------------------------------//

        String fileMapUrl = String.format(_cdnLinkType.getGeneralCdnLink(), _patchVersion, getFileListFileHash());

        FileInfoHolder fileMapInfo = new FileInfoHolder(getFileListFileHash(), "", ArchiveType.NONE, false, 0);
        fileMapInfo.setFileLength(-1);
        fileMapInfo.setAccessLink(new LinkInfoHolder(fileMapInfo));
        fileMapInfo.getAccessLink().setAccessLink(fileMapUrl);

        DownloadRequest fileMapRequest = DownloadManager.download(httpClient, new DownloadRequest(null, fileMapInfo));

        //------------------------------------------------------------------------------------------------------//
        httpClient.close();
        //------------------------------------------------------------------------------------------------------//

        parseHashList(fileMapRequest);
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
                countOfSeparatedFiles = getCountOfSeparatedFiles(stringMapOfValues, nameOfPart);
            }
            else
            {
                countOfSeparatedFiles = 0;
            }

            FileInfoHolder fileInfoHolder = parseFileInfoFromLine(line, true, false, countOfSeparatedFiles);
            if (countOfSeparatedFiles > 1)
            {
                // separate index :D
                for (int sIndex = 0; sIndex < countOfSeparatedFiles; sIndex++)
                {
                    String lookingInfo = stringMapOfValues.get(String.format(nameOfPart, (sIndex + 1)));
                    fileInfoHolder.setSeparatedPart(sIndex, parseFileInfoFromLine(lookingInfo, false, true, 0));
                }
            }
            _fileMapHolder.put((fileInfoHolder.getLinkPath()).toLowerCase(), fileInfoHolder);
        }
    }

    private static int getCountOfSeparatedFiles(Map<String, String> stringMapOfValues, String nameOfPart)
    {
        int separateCounter = 0;
        while (true)
        {
            String checkPart = String.format(nameOfPart, ((separateCounter) + 1));
            if (!stringMapOfValues.containsKey(checkPart))
            {
                return separateCounter;
            }
            separateCounter += 1;
        }
    }

    private FileInfoHolder parseFileInfoFromLine(String line, boolean original, boolean isSeparated, int countOfSeparatedParts)
    {
        String[] splitLineInfo = line.split(":", 5);
        if (splitLineInfo.length != 4)
        {
            throw new IllegalArgumentException("[path]:[size]:[sha-1 hash]:[file_type]. Structure is not full!");
        }
        String  pathUndName = splitLineInfo[0];
        String  fileLength  = splitLineInfo[1];
        String  hashSum     = splitLineInfo[2];

        String filePath = getPathOfFile(pathUndName);
        String fileName = getNameOfFile(pathUndName, !isSeparated, !original);

        FileInfoHolder fileInfoHolder = new FileInfoHolder(fileName, filePath, (original ? ArchiveType.LZMA_ARCHIVE : ArchiveType.NONE), isSeparated, countOfSeparatedParts);
        if ((original && countOfSeparatedParts == 0) || isSeparated)
        {
            fileInfoHolder.setAccessLink(new LinkInfoHolder(fileInfoHolder));
            fileInfoHolder.getAccessLink().setAccessLink(formatGetUrl(pathUndName));
        }
        fileInfoHolder.setDownloadDataLength(Integer.parseInt(fileLength));
        fileInfoHolder.setDownloadDataHashSum(hashSum);

        return fileInfoHolder;
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

            FileInfoHolder fileInfo = _fileMapHolder.getOrDefault(pathAndName, null);
            if (fileInfo == null)
            {
                continue;
            }

            String  fileLength  = splitLineInfo[1];
            String  hashSum     = splitLineInfo[2];

            fileInfo.setFileLength(Integer.parseInt(fileLength));
            fileInfo.setFileHashSum(hashSum);
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
