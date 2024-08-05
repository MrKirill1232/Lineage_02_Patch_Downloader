package org.index.patchdownloader.model.linkgenerator;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.enums.CDNLink;
import org.index.patchdownloader.instancemanager.DownloadManager;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.model.holders.LinkInfoHolder;
import org.index.patchdownloader.model.requests.DownloadRequest;
import org.index.patchdownloader.model.upnovaXmlHolders.UpNovaUpdateConfig;

import java.net.URI;
import java.net.http.HttpClient;

public class NovaLauncherGenerator extends GeneralLinkGenerator
{
    private UpNovaUpdateConfig _upNovaUpdateConfig;

    public NovaLauncherGenerator()
    {
        super(CDNLink.UP_NOVA_LAUNCHER, -1);
        MainConfig.UP_NOVA_LAUNCHER_URL = "http://flameria.com/";
        if (MainConfig.UP_NOVA_LAUNCHER_URL == null)
        {
            throw new NullPointerException("Requested UpNovaLauncher URL Generator. Main.ini - 'up_nova_launcher_url' is not setup.");
        }
    }

    @Override
    public void load()
    {
        getUpdateConfig();

    }

    private void getUpdateConfig()
    {
        HttpClient httpClient;
        //------------------------------------------------------------------------------------------------------//
        httpClient = HttpClient.newHttpClient();
        //------------------------------------------------------------------------------------------------------//

        String updateConfigUrl = URI.create(MainConfig.UP_NOVA_LAUNCHER_URL + "/UpdateConfig.xml").normalize().toString();

        FileInfoHolder updateConfigInfo = new FileInfoHolder("UpdateConfig.xml", "", false, 0);
        updateConfigInfo.setFileLength(-1);
        updateConfigInfo.setAccessLink(new LinkInfoHolder(updateConfigInfo));
        updateConfigInfo.getAccessLink().setAccessLink(updateConfigUrl);

        DownloadRequest updateConfigRequest = DownloadManager.download(httpClient, new DownloadRequest(null, updateConfigInfo));
        //------------------------------------------------------------------------------------------------------//
        httpClient.close();
        //------------------------------------------------------------------------------------------------------//
        UpNovaUpdateConfig upNovaUpdateConfig = new UpNovaUpdateConfig();
        upNovaUpdateConfig.parseXmlString(updateConfigInfo.getFilePath(), new String(updateConfigRequest.getDownloadedByteArray()[0]).replace("\uFEFF", ""));
        _upNovaUpdateConfig = upNovaUpdateConfig;
    }

    private void getMapOfFile()
    {

    }

    public static void main(String[] args)
    {
        new NovaLauncherGenerator();
    }
}
