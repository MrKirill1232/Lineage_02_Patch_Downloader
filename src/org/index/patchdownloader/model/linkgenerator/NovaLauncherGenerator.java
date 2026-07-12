package org.index.patchdownloader.model.linkgenerator;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.enums.ArchiveType;
import org.index.patchdownloader.enums.CDNLink;
import org.index.patchdownloader.enums.HashType;
import org.index.patchdownloader.interfaces.IDummyLogger;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.model.holders.LinkInfoHolder;
import org.index.patchdownloader.model.upnovaXmlHolders.UpNovaFileList;
import org.index.patchdownloader.model.upnovaXmlHolders.UpNovaUpdateConfig;
import org.index.patchdownloader.util.HttpDownloadUtils;

public class NovaLauncherGenerator extends GeneralLinkGenerator
{
    private UpNovaUpdateConfig  _upNovaUpdateConfig ;
    private UpNovaFileList      _upNovaFileList     ;

    /**
     * EN: Validates the configured launcher URL. Loading is NOT done here (fixed lifecycle): the
     *     controller drives {@link #load()} once, uniformly with the other generators. <br>
     * RU: Проверяет настроенный URL лаунчера. Загрузка здесь НЕ выполняется (исправленный жизненный
     *     цикл): контроллер вызывает {@link #load()} один раз, единообразно с другими генераторами. <br>
     **/
    public NovaLauncherGenerator()
    {
        super(CDNLink.UP_NOVA_LAUNCHER, -1);
        if (MainConfig.UP_NOVA_LAUNCHER_URL == null)
        {
            throw new NullPointerException("Requested UpNovaLauncher URL Generator. Main.ini - 'up_nova_launcher_url' is not setup.");
        }
    }

    @Override
    public HashType getHashingAlgorithm()
    {
        return HashType.CRC32;
    }

    /**
     * EN: Loads the UpNova update config, then the file list it points to. <br>
     * RU: Загружает конфиг обновления UpNova, затем список файлов, на который он указывает. <br>
     **/
    @Override
    public void load()
    {
        getUpdateConfig();
        getMapOfFile();
    }

    /**
     * EN: Downloads and parses {@code UpdateConfig.xml} (explicit UTF-8). Logs and returns on a non-200. <br>
     * RU: Скачивает и разбирает {@code UpdateConfig.xml} (явный UTF-8). Логирует и выходит при не-200. <br>
     **/
    private void getUpdateConfig()
    {
        String updateConfigUrl = URI.create(MainConfig.UP_NOVA_LAUNCHER_URL + "/UpdateConfig.xml").normalize().toString();

        FileInfoHolder updateConfigInfo = new FileInfoHolder("UpdateConfig.xml", "", ArchiveType.NONE, false, 0);
        updateConfigInfo.setFileLength(-1);
        updateConfigInfo.setAccessLink(new LinkInfoHolder(updateConfigInfo));
        updateConfigInfo.getAccessLink().setAccessLink(updateConfigUrl);

        byte[] data = HttpDownloadUtils.download(updateConfigInfo.getAccessLink());
        if (updateConfigInfo.getAccessLink().getHttpStatus() != 200)
        {
            IDummyLogger.log(IDummyLogger.ERROR, "Cannot get info from 'UpdateConfig.xml'. Response - '" + updateConfigInfo.getAccessLink().getHttpStatus() + "'. Request URL - '" + updateConfigUrl + "'");
            return;
        }
        UpNovaUpdateConfig upNovaUpdateConfig = new UpNovaUpdateConfig();
        upNovaUpdateConfig.parseXmlString(updateConfigUrl, new String(data, StandardCharsets.UTF_8));
        _upNovaUpdateConfig = upNovaUpdateConfig;
    }

    /**
     * EN: Downloads and parses {@code UpdateInfo.xml} (explicit UTF-8) into the file list. Logs and
     *     returns on a non-200 or a missing patch path. <br>
     * RU: Скачивает и разбирает {@code UpdateInfo.xml} (явный UTF-8) в список файлов. Логирует и выходит
     *     при не-200 или при отсутствии пути к патчу (patch path). <br>
     **/
    private void getMapOfFile()
    {
        if (_upNovaUpdateConfig == null || _upNovaUpdateConfig.getPatchPath() == null)
        {
            return;
        }

        String fileListUrl = URI.create(_upNovaUpdateConfig.getPatchPath() + "/UpdateInfo.xml").normalize().toString();

        FileInfoHolder fileListInfo = new FileInfoHolder("UpdateInfo.xml", "", ArchiveType.NONE, false, 0);
        fileListInfo.setFileLength(-1);
        fileListInfo.setAccessLink(new LinkInfoHolder(fileListInfo));
        fileListInfo.getAccessLink().setAccessLink(fileListUrl);

        byte[] data = HttpDownloadUtils.download(fileListInfo.getAccessLink());
        if (fileListInfo.getAccessLink().getHttpStatus() != 200)
        {
            IDummyLogger.log(IDummyLogger.ERROR, "Cannot get info from 'UpdateInfo.xml'. Response - '" + fileListInfo.getAccessLink().getHttpStatus() + "'. Request URL - '" + fileListUrl + "'");
            return;
        }
        UpNovaFileList upNovaFileList = new UpNovaFileList(_upNovaUpdateConfig);
        upNovaFileList.parseXmlString(fileListUrl, new String(data, StandardCharsets.UTF_8));
        _upNovaFileList = upNovaFileList;
    }

    /**
     * EN: Returns the parsed file map, or an empty map when the file list has not been loaded. <br>
     * RU: Возвращает разобранную карту файлов или пустую карту, если список файлов не загружен. <br>
     * ==================================================================<br>
     * @return <br>
     *         {Map} - EN: file map or empty / RU: карта файлов или пустая <br>
     **/
    @Override
    public Map<String, FileInfoHolder> getFileMapHolder()
    {
        return _upNovaFileList == null ? Collections.emptyMap() : _upNovaFileList.getFileMapHolder();
    }
}
