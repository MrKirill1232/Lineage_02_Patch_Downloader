package org.index.patchdownloader.enums;

public enum CDNLink
{
    // 07/26/2024 - latest is 529
    NC_SOFT_TAIWAN("http://mmorepo.cdn.plaync.com.tw/TWLin2EP20/%d/Patch/%s", "http://mmorepo.cdn.plaync.com.tw/TWLin2EP20/%d/Patch/PatchFileInfo_TWLin2EP20_%d.dat"),
    NC_SOFT_KOREAN("http://l2kor.ncupdate.com/%s", "http://l2kor.ncupdate.com/L2_KOR/%d/Patch/files_info.json.zip"),
    // http://l2-client-cdn.ncsoft.jp/L2_JP/102/Patch/Zip/Maps/24_14.unr.zip
    // 101 - 464
    // 102 - 474
    NC_SOFT_JAPANESE("http://l2-client-cdn.ncsoft.jp/L2_JP/%d/Patch/%s", "http://l2-client-cdn.ncsoft.jp/L2_JP/%d/Patch/PatchFileInfo_L2_JP_%d.dat"),
    // NC-WEST IS DEAD :D
    // http://d35293xeakkyq4.cloudfront.net/LINEAGE2/479/Patch/FileInfoMap_LINEAGE2_479.dat.zip
    // [Request URI: http://d35293xeakkyq4.cloudfront.net/LINEAGE2/479/Patch/PatchFileInfo_LINEAGE2_479.dat.zip]
    NC_SOFT_AMERICA("http://d35293xeakkyq4.cloudfront.net/LINEAGE2/%d/Patch/%s", "http://d35293xeakkyq4.cloudfront.net/LINEAGE2/%d/Patch/PatchFileInfo_LINEAGE2_%d.dat"),
    ;

    private final String _generalCdnLink;
    private final String _cdnFileListLink;

    CDNLink(String generalCdnLink, String cdnFileListLink)
    {
        _generalCdnLink = generalCdnLink;
        _cdnFileListLink = cdnFileListLink;
    }

    public String getGeneralCdnLink()
    {
        return _generalCdnLink;
    }

    public String getCdnFileListLink()
    {
        return _cdnFileListLink;
    }
}
