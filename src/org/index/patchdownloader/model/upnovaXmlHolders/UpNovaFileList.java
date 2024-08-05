package org.index.patchdownloader.model.upnovaXmlHolders;

import org.dom4j.Document;
import org.index.patchdownloader.util.xml.IXmlParser;

public class UpNovaFileList implements IXmlParser
{

    @Override
    public void parseDocument(String xmlInfo, Document document)
    {
        // UpNovaUpdateConfig._patchPath + _path + _name + ".zip";
    }
}
