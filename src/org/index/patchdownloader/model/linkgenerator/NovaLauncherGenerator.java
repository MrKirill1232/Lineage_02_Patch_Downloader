package org.index.patchdownloader.model.linkgenerator;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.enums.ArchiveType;
import org.index.patchdownloader.enums.CDNLink;
import org.index.patchdownloader.enums.HashType;
import org.index.patchdownloader.instancemanager.DownloadManager;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.model.holders.LinkInfoHolder;
import org.index.patchdownloader.model.requests.DownloadRequest;
import org.index.patchdownloader.model.upnovaXmlHolders.UpNovaFileList;
import org.index.patchdownloader.model.upnovaXmlHolders.UpNovaUpdateConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.Collections;
import java.util.Map;

public class NovaLauncherGenerator extends GeneralLinkGenerator
{
    private UpNovaUpdateConfig  _upNovaUpdateConfig ;
    private UpNovaFileList      _upNovaFileList     ;

    public NovaLauncherGenerator()
    {
        super(CDNLink.UP_NOVA_LAUNCHER, -1);
        // MainConfig.UP_NOVA_LAUNCHER_URL = "http://flameria.com/";
        // MainConfig.UP_NOVA_LAUNCHER_URL = "https://files.imbadon.com/updater/essence/";
        if (MainConfig.UP_NOVA_LAUNCHER_URL == null)
        {
            throw new NullPointerException("Requested UpNovaLauncher URL Generator. Main.ini - 'up_nova_launcher_url' is not setup.");
        }
        load();
        _fileMapHolder = Collections.emptyMap();
    }

    @Override
    public HashType getHashingAlgorithm()
    {
        return HashType.CRC32;
    }

    @Override
    public void load()
    {
        getUpdateConfig();
        getMapOfFile();
    }

    private void getUpdateConfig()
    {
        HttpClient httpClient;
        //------------------------------------------------------------------------------------------------------//
        httpClient = HttpClient.newHttpClient();
        //------------------------------------------------------------------------------------------------------//

        String updateConfigUrl = URI.create(MainConfig.UP_NOVA_LAUNCHER_URL + "/UpdateConfig.xml").normalize().toString();

        FileInfoHolder updateConfigInfo = new FileInfoHolder("UpdateConfig.xml", "", ArchiveType.NONE, false, 0);
        updateConfigInfo.setFileLength(-1);
        updateConfigInfo.setAccessLink(new LinkInfoHolder(updateConfigInfo));
        updateConfigInfo.getAccessLink().setAccessLink(updateConfigUrl);

        DownloadRequest updateConfigRequest = DownloadManager.download(httpClient, new DownloadRequest(null, updateConfigInfo));
        //------------------------------------------------------------------------------------------------------//
        httpClient.close();
        //------------------------------------------------------------------------------------------------------//
        UpNovaUpdateConfig upNovaUpdateConfig = new UpNovaUpdateConfig();
        upNovaUpdateConfig.parseXmlString(updateConfigInfo.getAccessLink().getAccessLink(), new String(updateConfigRequest.getDownloadedByteArray()[0]));
        _upNovaUpdateConfig = upNovaUpdateConfig;
    }

    private void getMapOfFile()
    {
        if (_upNovaUpdateConfig == null || _upNovaUpdateConfig.getPatchPath() == null)
        {
            return;
        }

        HttpClient httpClient;
        //------------------------------------------------------------------------------------------------------//
        httpClient = HttpClient.newHttpClient();
        //------------------------------------------------------------------------------------------------------//

        String fileListUrl = URI.create(_upNovaUpdateConfig.getPatchPath() + "/UpdateInfo.xml").normalize().toString();

        FileInfoHolder fileListInfo = new FileInfoHolder("UpdateInfo.xml", "", ArchiveType.NONE, false, 0);
        fileListInfo.setFileLength(-1);
        fileListInfo.setAccessLink(new LinkInfoHolder(fileListInfo));
        fileListInfo.getAccessLink().setAccessLink(fileListUrl);

        DownloadRequest fileListRequest = DownloadManager.download(httpClient, new DownloadRequest(null, fileListInfo));
        //------------------------------------------------------------------------------------------------------//
        httpClient.close();
        //------------------------------------------------------------------------------------------------------//
        UpNovaFileList upNovaFileList = new UpNovaFileList(_upNovaUpdateConfig);
        upNovaFileList.parseXmlString(fileListInfo.getFilePath(), new String(fileListRequest.getDownloadedByteArray()[0]));
        _upNovaFileList = upNovaFileList;
    }

    @Override
    public Map<String, FileInfoHolder> getFileMapHolder()
    {
        return _upNovaFileList == null ? Collections.emptyMap() : _upNovaFileList.getFileMapHolder();
    }
}
