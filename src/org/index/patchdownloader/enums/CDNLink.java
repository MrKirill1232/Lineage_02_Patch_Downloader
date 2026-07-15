package org.index.patchdownloader.enums;

public enum CDNLink
{
    // The two trailing params are the NC "Purple" update-protocol coordinates (TCP:27500) used by -last_version:
    // the service id (baked into the CDN path) and the update host. A null update host = that region's update
    // server is not known yet (its version cannot be live-resolved; see NcUpdateVersionClient / the version brief).
    // 07/26/2024 - latest is 529
    NC_SOFT_TAIWAN("http://mmorepo.cdn.plaync.com.tw/TWLin2EP20/%d/Patch/%s", "http://mmorepo.cdn.plaync.com.tw/TWLin2EP20/%d/Patch/PatchFileInfo_TWLin2EP20_%d.dat", "TWLin2EP20", "up4svr.plaync.com.tw"),
    NC_SOFT_KOREAN("http://l2kor.ncupdate.com/%s", "http://l2kor.ncupdate.com/L2_KOR/%d/Patch/files_info.json.zip", "L2_KOR", "up4svr.ncupdate.com"),
    // http://l2-client-cdn.ncsoft.jp/L2_JP/102/Patch/Zip/Maps/24_14.unr.zip
    // http://ncj-L2-asset.ncsoft.jp/L2_JP/212/Patch/Zip/Maps/24_14.unr.zip
    // 101 - 464
    // 102 - 474
    NC_SOFT_JAPANESE("http://ncj-L2-asset.ncsoft.jp/L2_JP/%d/Patch/%s", "http://ncj-L2-asset.ncsoft.jp/L2_JP/%d/Patch/PatchFileInfo_L2_JP_%d.dat", "L2_JP", "l2update.ncsoft.jp"),
    // NC-WEST IS DEAD :D
    // http://d35293xeakkyq4.cloudfront.net/LINEAGE2/479/Patch/FileInfoMap_LINEAGE2_479.dat.zip
    // [Request URI: http://d35293xeakkyq4.cloudfront.net/LINEAGE2/479/Patch/PatchFileInfo_LINEAGE2_479.dat.zip]
    NC_SOFT_AMERICA("http://d35293xeakkyq4.cloudfront.net/LINEAGE2/%d/Patch/%s", "http://d35293xeakkyq4.cloudfront.net/LINEAGE2/%d/Patch/PatchFileInfo_LINEAGE2_%d.dat", "LINEAGE2", "updater.nclauncher.ncsoft.com"),

    // Akumu HTTP mirror: the file list comes from a .torrent under the configured folder (not a format string);
    // links are built as folder + relative path. See AkumuLinkGenerator / AnubisClient. No NC update protocol.
    AKUMU(null, null, null, null),

    UP_NOVA_LAUNCHER(null, null, null, null);

    private final String _generalCdnLink;
    private final String _cdnFileListLink;
    private final String _updateServiceId;
    private final String _updateHost;

    CDNLink(String generalCdnLink, String cdnFileListLink, String updateServiceId, String updateHost)
    {
        _generalCdnLink = generalCdnLink;
        _cdnFileListLink = cdnFileListLink;
        _updateServiceId = updateServiceId;
        _updateHost = updateHost;
    }

    public String getGeneralCdnLink()
    {
        return _generalCdnLink;
    }

    public String getCdnFileListLink()
    {
        return _cdnFileListLink;
    }

    /**
     * EN: The NC update-protocol service id for this region (e.g. {@code L2_JP}), or {@code null} for a source
     *     without an NC update server (Akumu / UpNova). <br>
     * RU: Идентификатор сервиса update-протокола NC для этого региона (напр. {@code L2_JP}) или {@code null} для
     *     источника без update-сервера NC (Akumu / UpNova). <br>
     * @return <br>
     *         {String} - EN: the service id, or null / RU: идентификатор сервиса или null <br>
     **/
    public String getUpdateServiceId()
    {
        return _updateServiceId;
    }

    /**
     * EN: The NC update server host (TCP:27500) for this region, or {@code null} when it is not known yet — then
     *     the live version cannot be resolved for this source. <br>
     * RU: Хост update-сервера NC (TCP:27500) для этого региона или {@code null}, если он ещё не известен — тогда
     *     живую версию для этого источника узнать нельзя. <br>
     * @return <br>
     *         {String} - EN: the update host, or null / RU: хост update-сервера или null <br>
     **/
    public String getUpdateHost()
    {
        return _updateHost;
    }
}
