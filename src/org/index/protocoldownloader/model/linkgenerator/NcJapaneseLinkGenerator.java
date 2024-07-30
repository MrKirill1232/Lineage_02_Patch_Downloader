package org.index.protocoldownloader.model.linkgenerator;

import org.index.protocoldownloader.enums.CDNLink;

public class NcJapaneseLinkGenerator extends NcTaiwanLinkGenerator
{
    protected NcJapaneseLinkGenerator(int patchVersion)
    {
        super(CDNLink.NC_SOFT_JAPANESE, patchVersion);
    }



    @Override
    protected String getFileListFileName()
    {
        return "PatchFileInfo_L2_JP_" + _patchVersion + ".dat";
    }

    @Override
    protected String getFileListFileHash()
    {
        return "FileInfoMap_L2_JP_" + _patchVersion + ".dat";
    }
}
