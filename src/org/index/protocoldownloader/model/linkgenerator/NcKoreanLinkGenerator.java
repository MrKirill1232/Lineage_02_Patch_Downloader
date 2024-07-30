package org.index.protocoldownloader.model.linkgenerator;

import org.index.protocoldownloader.enums.CDNLink;
import org.index.protocoldownloader.enums.FileTypeByLink;
import org.index.protocoldownloader.instancemanager.DecodeManager;
import org.index.protocoldownloader.model.requests.DownloadRequest;
import org.index.protocoldownloader.instancemanager.DownloadManager;
import org.index.protocoldownloader.model.holders.LinkHolder;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

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
        String url = String.format(_cdnLinkType.getCdnFileListLink(), _patchVersion);

        LinkHolder linkHolder = new LinkHolder("files_info.json.zip", "", FileTypeByLink.NORMAL_FILE, 1);
        linkHolder.setNamesOfFiles(0, linkHolder.getFileName());
        linkHolder.setFileLength(0, -1);
        linkHolder.setAccessLink(0, url);

        DownloadRequest request = DownloadManager.download(new DownloadRequest(null, linkHolder));
        parseFileList(request);
    }

    private void parseFileList(DownloadRequest request)
    {
        String jsonContent = zipToJson(request);
        if (jsonContent.isEmpty())
        {
            throw new NoSuchElementException("Version is unavailable! Requested version " + _patchVersion + ". Requested link " + request.getLinkHolder().getAccessLink()[0] + ";");
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
            int originalFileSize= Integer.parseInt(String.valueOf(fileInfo.get("size")));
            String hashSum = String.valueOf(fileInfo.get("hash"));
            String pathInClient = String.valueOf(fileInfo.get("path"));
            String nameOfFile = getName(pathInClient, patchVersion, false, false);
            JSONObject encodedInfo = (JSONObject) fileInfo.get("encodedInfo");
            LinkHolder linkHolder = null;
            String filePath = pathInClient.substring(0, (pathInClient.length() - nameOfFile.length()));
            if (encodedInfo.get("separates") != null)
            {
                JSONArray separatedFileList = (JSONArray) encodedInfo.get("separates");
                linkHolder = new LinkHolder(nameOfFile, filePath, FileTypeByLink.SEPARATED, separatedFileList.size());
                for (int separateIndex = 0; separateIndex < separatedFileList.size(); separateIndex++)
                {
                    JSONObject separatedFileInfo = (JSONObject) separatedFileList.get(separateIndex);

                    String pathAndName = String.valueOf(separatedFileInfo.get("path"));
                    int size = Integer.parseInt(String.valueOf(separatedFileInfo.get("size")));
                    String separatedHashSum = String.valueOf(separatedFileInfo.get("hash"));

                    linkHolder.setNamesOfFiles(separateIndex, getName(pathAndName, patchVersion, true, true));
                    linkHolder.setFileLength(separateIndex, size);
                    linkHolder.setAccessLink(separateIndex, String.format(_cdnLinkType.getGeneralCdnLink(), pathAndName));
                    linkHolder.setHashsum(separateIndex, separatedHashSum);
                }
            }
            else
            {
                String pathAndName = String.valueOf(encodedInfo.get("path"));
                int size = Integer.parseInt(String.valueOf(encodedInfo.get("size")));
                linkHolder = new LinkHolder(nameOfFile, filePath, FileTypeByLink.NORMAL_FILE, 1);
                linkHolder.setNamesOfFiles(0, nameOfFile);
                linkHolder.setFileLength(0, size);
                linkHolder.setAccessLink(0, String.format(_cdnLinkType.getGeneralCdnLink(), pathAndName));
            }
            linkHolder.setOriginalFileHashsum(hashSum);
            linkHolder.setOriginalFileLength(originalFileSize);
            _fileMapHolder .put(pathInClient.toLowerCase(), linkHolder  );
        }
    }

    private String zipToJson(DownloadRequest request)
    {
        byte[] decodeJSONdata = DecodeManager.decode(request);
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
}
