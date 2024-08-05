package org.index.patchdownloader.model.upnovaXmlHolders;

import org.dom4j.Document;
import org.dom4j.Element;
import org.index.patchdownloader.util.StatSet;
import org.index.patchdownloader.util.xml.IXmlParser;

public class UpNovaUpdateConfig implements IXmlParser
{
    private String _updaterTitle;
    private String _updaterVersion;
    private String _selfUpdatePath;
    private String _patchPath;

    @Override
    public void parseDocument(String xmlInfo, Document document)
    {
        for (Element updateConfigElement : IXmlParser.getChildNodes(document, "UpdateConfig"))
        {
            StatSet statSet = IXmlParser.parseTextToStatSet(updateConfigElement);
            _updaterTitle = statSet.getString("UpdaterTitle", null);
            _updaterVersion = statSet.getString("UpdaterVersion", null);
            _selfUpdatePath = statSet.getString("SelfUpdatePath", null);
            _patchPath = statSet.getString("PatchPath", null);
        }
    }

    public String getUpdaterTitle()
    {
        return _updaterTitle;
    }

    public String getUpdaterVersion()
    {
        return _updaterVersion;
    }

    public String getSelfUpdatePath()
    {
        return _selfUpdatePath;
    }

    public String getPatchPath()
    {
        return _patchPath;
    }
}
