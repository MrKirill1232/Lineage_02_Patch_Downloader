package org.index.patchdownloader.model.linkgenerator;

import org.index.patchdownloader.enums.CDNLink;
import org.index.patchdownloader.model.holders.FileInfoHolder;

import java.util.HashMap;
import java.util.Map;

public abstract class GeneralLinkGenerator
{
    public static GeneralLinkGenerator generateLinkToFiles(CDNLink cdnLinkType, int patchVersion)
    {
        switch (cdnLinkType)
        {
            case NC_SOFT_TAIWAN:
            {
                return new NcTaiwanLinkGenerator(patchVersion);
            }
            case NC_SOFT_JAPANESE:
            {
                return new NcJapaneseLinkGenerator(patchVersion);
            }
            case NC_SOFT_AMERICA:
            {
                return new NcAmericaLinkGenerator(patchVersion);
            }
            case NC_SOFT_KOREAN:
            {
                return new NcKoreanLinkGenerator(patchVersion);
            }
            default:
            {
                return null;
            }
        }
    }

    protected final CDNLink _cdnLinkType;
    protected final int _patchVersion;

    protected Map<String, FileInfoHolder> _fileMapHolder;

    protected GeneralLinkGenerator(CDNLink cdnLink, int patchVersion)
    {
        _cdnLinkType    = cdnLink;
        _patchVersion   = patchVersion;
        _fileMapHolder  = new HashMap<>();
    }

    public abstract void load();

    public CDNLink getCdnLinkType()
    {
        return _cdnLinkType;
    }

    public int getPatchVersion()
    {
        return _patchVersion;
    }

    public Map<String, FileInfoHolder> getFileMapHolder()
    {
        return _fileMapHolder;
    }
}
