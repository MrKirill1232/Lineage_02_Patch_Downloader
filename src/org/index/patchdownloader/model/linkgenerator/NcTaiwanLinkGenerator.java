package org.index.patchdownloader.model.linkgenerator;

import org.index.patchdownloader.enums.ArchiveType;
import org.index.patchdownloader.enums.CDNLink;
import org.index.patchdownloader.enums.FileTypeByLink;
import org.index.patchdownloader.enums.HashType;
import org.index.patchdownloader.interfaces.IDummyLogger;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.model.holders.LinkInfoHolder;
import org.index.patchdownloader.util.HttpDownloadUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

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
    public HashType getHashingAlgorithm()
    {
        return HashType.SHA01;
    }

    /**
     * EN: Downloads the file list and the hash list (synchronously) and parses them into the file map.
     *     A non-200 HTTP status aborts the run instead of silently leaving the map empty or unverified. <br>
     * RU: Скачивает (синхронно) список файлов и список хешей и разбирает их в карту файлов. HTTP-статус,
     *     отличный от 200, прерывает загрузку, а не оставляет карту пустой или без проверки хешей. <br>
     **/
    @Override
    public void load()
    {
        String fileListUrl = String.format(_cdnLinkType.getCdnFileListLink(), _patchVersion, _patchVersion);

        FileInfoHolder fileListInfo = new FileInfoHolder(getFileListFileName(), "", ArchiveType.NONE, false, 0);
        fileListInfo.setFileLength(-1);
        fileListInfo.setAccessLink(new LinkInfoHolder(fileListInfo));
        fileListInfo.getAccessLink().setAccessLink(fileListUrl);

        byte[] fileListData = HttpDownloadUtils.download(fileListInfo.getAccessLink());
        if (fileListInfo.getAccessLink().getHttpStatus() != 200)
        {
            throw new NoSuchElementException("File list is unavailable! HTTP status " + fileListInfo.getAccessLink().getHttpStatus() + ". Requested link " + fileListUrl + ";");
        }
        parseFileList(fileListData);

        String fileMapUrl = String.format(_cdnLinkType.getGeneralCdnLink(), _patchVersion, getFileListFileHash());

        FileInfoHolder fileMapInfo = new FileInfoHolder(getFileListFileHash(), "", ArchiveType.NONE, false, 0);
        fileMapInfo.setFileLength(-1);
        fileMapInfo.setAccessLink(new LinkInfoHolder(fileMapInfo));
        fileMapInfo.getAccessLink().setAccessLink(fileMapUrl);

        byte[] fileMapData = HttpDownloadUtils.download(fileMapInfo.getAccessLink());
        if (fileMapInfo.getAccessLink().getHttpStatus() != 200)
        {
            IDummyLogger.log(IDummyLogger.ERROR, "Hash list is unavailable! HTTP status " + fileMapInfo.getAccessLink().getHttpStatus() + ". Requested link " + fileMapUrl + ";");
            throw new NoSuchElementException("Hash list is unavailable! HTTP status " + fileMapInfo.getAccessLink().getHttpStatus() + ". Requested link " + fileMapUrl + ";");
        }
        parseHashList(fileMapData);
    }

    protected String getFileListFileName()
    {
        return "PatchFileInfo_TWLin2EP20_" + _patchVersion + ".dat";
    }

    protected String getFileListFileHash()
    {
        return "FileInfoMap_TWLin2EP20_" + _patchVersion + ".dat";
    }

    /**
     * EN: Parses the UTF-16LE file-list text into per-file holders (path/size/hash/type), grouping
     *     separated parts. <br>
     * RU: Разбирает UTF-16LE текст списка файлов в holder'ы по файлам (путь/размер/хеш/тип), группируя
     *     разделённые части. <br>
     * ==================================================================<br>
     * EN: @param data the downloaded file-list bytes / RU: @param data скачанные байты списка файлов <br>
     **/
    protected void parseFileList(byte[] data)
    {
        if (data == null || data.length == 0)
        {
            return;
        }
        String fileInfo = new String(data, StandardCharsets.UTF_16LE);
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
            boolean typeAllowsSeparation = (typeOfFile == FileTypeByLink.SEPARATED.ordinal() || typeOfFile == FileTypeByLink.UNK_04.ordinal());
            int     digitRunLength = trailingDigitRunLength(pathAndName);
            boolean isSeparated = typeAllowsSeparation && digitRunLength >= 2;
            String  nameOfPart  = isSeparated ? pathAndName.substring(0, pathAndName.length() - digitRunLength) + "%02d" : pathAndName;
            int     countOfSeparatedFiles;
            if (isSeparated)
            {
                int part = Integer.parseInt(pathAndName.substring(pathAndName.length() - digitRunLength));
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
            if (countOfSeparatedFiles > 0)
            {
                // Fill each split part by its 1-based index (%02d) by looking the part line up in the map
                for (int sIndex = 0; sIndex < countOfSeparatedFiles; sIndex++)
                {
                    String lookingInfo = stringMapOfValues.get(String.format(nameOfPart, (sIndex + 1)));
                    fileInfoHolder.setSeparatedPart(sIndex, parseFileInfoFromLine(lookingInfo, false, true, 0));
                }
            }
            _fileMapHolder.put((fileInfoHolder.getLinkPath()).toLowerCase(), fileInfoHolder);
        }
    }

    private static int trailingDigitRunLength(String value)
    {
        int index = value.length();
        while (index > 0 && Character.isDigit(value.charAt(index - 1)))
        {
            index--;
        }
        return value.length() - index;
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
        fileInfoHolder.setDownloadDataLength(Long.parseLong(fileLength));
        fileInfoHolder.setDownloadDataHashSum(hashSum);

        return fileInfoHolder;
    }

    /**
     * EN: Parses the UTF-16LE hash-list text and fills the final (decompressed) length + hash-sum on the
     *     already-parsed file holders. <br>
     * RU: Разбирает UTF-16LE текст списка хешей и заполняет финальную (распакованную) длину + хеш-сумму на
     *     уже разобранных holder'ах файлов. <br>
     * ==================================================================<br>
     * EN: @param data the downloaded hash-list bytes / RU: @param data скачанные байты списка хешей <br>
     **/
    protected void parseHashList(byte[] data)
    {
        if (data == null || data.length == 0)
        {
            return;
        }

        String hashInfo = new String(data, StandardCharsets.UTF_16LE);
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

            fileInfo.setFileLength(Long.parseLong(fileLength));
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
