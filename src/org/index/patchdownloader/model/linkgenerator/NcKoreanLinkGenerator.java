package org.index.patchdownloader.model.linkgenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;

import org.index.patchdownloader.enums.ArchiveType;
import org.index.patchdownloader.enums.CDNLink;
import org.index.patchdownloader.enums.HashType;
import org.index.patchdownloader.interfaces.IDummyLogger;
import org.index.patchdownloader.model.decompress.Decompressors;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.model.holders.LinkInfoHolder;
import org.index.patchdownloader.util.HttpDownloadUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class NcKoreanLinkGenerator extends GeneralLinkGenerator
{
    public NcKoreanLinkGenerator(int patchVersion)
    {
        super(CDNLink.NC_SOFT_KOREAN, patchVersion);
    }

    @Override
    public HashType getHashingAlgorithm()
    {
        return HashType.SHA01;
    }

    /**
     * EN: Downloads the compressed JSON file list (one synchronous request, no leaked client) and parses
     *     it into the file map. <br>
     * RU: Скачивает сжатый JSON-список файлов (один синхронный запрос, без утечки клиента) и разбирает его
     *     в карту файлов. <br>
     **/
    @Override
    public void load()
    {
        String url = String.format(_cdnLinkType.getCdnFileListLink(), _patchVersion);

        FileInfoHolder fileListInfo = new FileInfoHolder("files_info.json.zip", "", ArchiveType.LZMA_ARCHIVE, false, 0);
        fileListInfo.setFileLength(-1);
        fileListInfo.setAccessLink(new LinkInfoHolder(fileListInfo));
        fileListInfo.getAccessLink().setAccessLink(url);

        byte[] fileListData = HttpDownloadUtils.download(fileListInfo.getAccessLink());
        if (fileListInfo.getAccessLink().getHttpStatus() != 200)
        {
            throw new NoSuchElementException("Version is unavailable! Requested version " + _patchVersion + ". Requested link " + fileListInfo.getAccessLink().getAccessLink() + ";");
        }
        parseFileList(fileListInfo, fileListData);
    }

    /**
     * EN: Decompresses the downloaded data to JSON and builds one holder per file entry (original +
     *     encoded info + separated parts). <br>
     * RU: Распаковывает скачанные данные в JSON и строит объект-держатель (holder) на каждую запись
     *     файла (оригинал + закодированные данные (encodedInfo) + разделённые части). <br>
     * ==================================================================<br>
     * EN: @param fileListInfo the file-list holder (archive type + link) / RU: @param fileListInfo holder списка файлов (тип архива + ссылка) <br>
     * EN: @param data the downloaded compressed bytes / RU: @param data скачанные сжатые байты <br>
     **/
    private void parseFileList(FileInfoHolder fileListInfo, byte[] data)
    {
        String jsonContent = zipToJson(fileListInfo.getCompressType(), data);
        if (jsonContent.isEmpty())
        {
            throw new NoSuchElementException("Version is unavailable! Requested version " + _patchVersion + ". Requested link " + fileListInfo.getAccessLink().getAccessLink() + ";");
        }
        JSONObject jsonTable;
        try
        {
            jsonTable = (JSONObject) new JSONParser().parse(jsonContent);
        }
        catch (ParseException e)
        {
            throw new RuntimeException(e);
        }
        JSONArray files = (JSONArray) jsonTable.get("files");
        if (files == null)
        {
            throw new NoSuchElementException("Version is unavailable (no 'files' entry)! Requested version " + _patchVersion + ".");
        }
        for (int index = 0; index < files.size(); index++)
        {
            JSONObject fileInfo = (JSONObject) files.get(index);
            int patchVersion    = Integer.parseInt(String.valueOf(fileInfo.get("version")));
            JSONObject encodedInfo = (JSONObject) fileInfo.get("encodedInfo");
            JSONArray separatedFileList = (JSONArray) encodedInfo.get("separates");
            FileInfoHolder originalFileInfo = parseFileInfoFromJSONObject(fileInfo, patchVersion, false, false, (separatedFileList == null ? 0 : separatedFileList.size()));
            FileInfoHolder encodedFileInfo = parseFileInfoFromJSONObject(encodedInfo, patchVersion, true, false, 0);

            originalFileInfo.setFileHashSum(originalFileInfo.getDownloadDataHashSum());
            originalFileInfo.setFileLength(originalFileInfo.getDownloadDataLength());

            originalFileInfo.setDownloadDataHashSum(encodedFileInfo.getDownloadDataHashSum());
            originalFileInfo.setDownloadDataLength(encodedFileInfo.getDownloadDataLength());

            originalFileInfo.setAccessLink(new LinkInfoHolder(originalFileInfo));
            originalFileInfo.getAccessLink().setAccessLink(encodedFileInfo.getAccessLink().getAccessLink());

            for (int separateIndex = 0; separateIndex < originalFileInfo.getAllSeparatedParts().length; separateIndex++)
            {
                JSONObject separatedFileInfo = (JSONObject) separatedFileList.get(separateIndex);
                originalFileInfo.setSeparatedPart(separateIndex, parseFileInfoFromJSONObject(separatedFileInfo, patchVersion, false, true, 0));
            }
            _fileMapHolder.put(originalFileInfo.getLinkPath().toLowerCase(), originalFileInfo);
        }
    }

    /**
     * EN: Decompresses the file-list bytes (via the shared {@link Decompressors}) into a UTF-8 JSON
     *     string; returns an empty string on failure. <br>
     * RU: Распаковывает байты списка файлов (через общий {@link Decompressors}) в UTF-8 JSON-строку;
     *     возвращает пустую строку при сбое. <br>
     * ==================================================================<br>
     * EN: @param type the archive type / RU: @param type тип архива <br>
     * EN: @param data the compressed bytes / RU: @param data сжатые байты <br>
     * @return <br>
     *         {String} - EN: the JSON content, or empty on failure / RU: JSON-содержимое или пусто при сбое <br>
     **/
    private String zipToJson(ArchiveType type, byte[] data)
    {
        try
        {
            byte[] decoded = Decompressors.decompress(type, data, -1);
            return decoded.length == 0 ? "" : new String(decoded, StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            IDummyLogger.log(IDummyLogger.ERROR, "Failed to decompress Korean file list: " + e);
            return "";
        }
    }

    private String getName(String pathAndName, int patchVersion, boolean separated, boolean ignoreExtension)
    {
        String[] splitPathByFolders
                = pathAndName.split("/");
        String nameOfFile
                = splitPathByFolders[splitPathByFolders.length - 1];
        if (nameOfFile.endsWith(String.valueOf(patchVersion)))
        {
            nameOfFile = nameOfFile.substring(0, (nameOfFile.length() - (String.valueOf(patchVersion).length() + 1)));
        }
        if (!ignoreExtension && (nameOfFile.endsWith(".zip") || separated))
        {
            nameOfFile = nameOfFile.substring(0, nameOfFile.length() - 4);
        }
        return nameOfFile;
    }

    private FileInfoHolder parseFileInfoFromJSONObject(JSONObject jsonObject, int patchVersion, boolean original, boolean isSeparated, int countOfSeparatedParts)
    {
        String pathAndName = String.valueOf(jsonObject.get("path"));
        String fileLength = String.valueOf(jsonObject.get("size"));
        String hashSum = String.valueOf(jsonObject.get("hash"));

        String fileName = getName(pathAndName, patchVersion, !isSeparated, !original);
        String filePath = pathAndName.substring(0, (pathAndName.length() - fileName.length()));

        FileInfoHolder fileInfoHolder = new FileInfoHolder(fileName, filePath, ArchiveType.NONE, isSeparated, countOfSeparatedParts);
        if ((original && countOfSeparatedParts == 0) || isSeparated)
        {
            fileInfoHolder.setAccessLink(new LinkInfoHolder(fileInfoHolder));
            fileInfoHolder.getAccessLink().setAccessLink(String.format(_cdnLinkType.getGeneralCdnLink(), pathAndName));
        }
        fileInfoHolder.setDownloadDataLength(Long.parseLong(fileLength));
        fileInfoHolder.setDownloadDataHashSum(hashSum);

        return fileInfoHolder;
    }
}
