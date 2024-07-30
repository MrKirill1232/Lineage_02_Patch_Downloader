package org.index.patchdownloader.model.linkgenerator;

import org.index.patchdownloader.enums.CDNLink;

public class NcAmericaLinkGenerator extends NcTaiwanLinkGenerator
{
    protected NcAmericaLinkGenerator(int patchVersion)
    {
        super(CDNLink.NC_SOFT_AMERICA, patchVersion);
    }

    @Override
    protected String getFileListFileName()
    {
        return "PatchFileInfo_LINEAGE2_" + _patchVersion + ".dat";
    }

    @Override
    protected String getFileListFileHash()
    {
        return "FileInfoMap_LINEAGE2_" + _patchVersion + ".dat";
    }
}
