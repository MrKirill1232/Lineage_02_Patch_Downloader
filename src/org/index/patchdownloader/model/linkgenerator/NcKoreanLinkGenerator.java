package org.index.patchdownloader.model.linkgenerator;

import org.index.patchdownloader.enums.ArchiveType;
import org.index.patchdownloader.enums.CDNLink;
import org.index.patchdownloader.instancemanager.DecompressManager;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.model.holders.LinkInfoHolder;
import org.index.patchdownloader.model.requests.DownloadRequest;
import org.index.patchdownloader.instancemanager.DownloadManager;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.NoSuchElementException;

public class NcKoreanLinkGenerator extends GeneralLinkGenerator
{
    public NcKoreanLinkGenerator(int patchVersion)
    {
        super(CDNLink.NC_SOFT_KOREAN, patchVersion);
    }

    @Override
    public void load()
    {
        HttpClient httpClient = HttpClient.newHttpClient();

        //------------------------------------------------------------------------------------------------------//

        String url = String.format(_cdnLinkType.getCdnFileListLink(), _patchVersion);

        FileInfoHolder fileListInfo = new FileInfoHolder("files_info.json.zip", "", ArchiveType.LZMA_ARCHIVE, false, 0);
        fileListInfo.setFileLength(-1);
        fileListInfo.setAccessLink(new LinkInfoHolder(fileListInfo));
        fileListInfo.getAccessLink().setAccessLink(url);

        DownloadRequest fileListRequest = DownloadManager.download(HttpClient.newHttpClient(), new DownloadRequest(null, fileListInfo));
        parseFileList(fileListRequest);

        //------------------------------------------------------------------------------------------------------//

        httpClient.close();
    }

    private void parseFileList(DownloadRequest request)
    {
        String jsonContent = zipToJson(request);
        if (jsonContent.isEmpty())
        {
            throw new NoSuchElementException("Version is unavailable! Requested version " + _patchVersion + ". Requested link " + request.getFileInfoHolder().getAccessLink() + ";");
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

    private String zipToJson(DownloadRequest request)
    {
        byte[] decodeJSONdata = DecompressManager.decompress(request);
        return decodeJSONdata.length == 0 ? "" : new String(decodeJSONdata, StandardCharsets.UTF_8);
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
        fileInfoHolder.setDownloadDataLength(Integer.parseInt(fileLength));
        fileInfoHolder.setDownloadDataHashSum(hashSum);

        return fileInfoHolder;
    }
}
