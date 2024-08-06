package org.index.patchdownloader.model.upnovaXmlHolders;

import org.dom4j.Document;
import org.dom4j.Element;
import org.index.patchdownloader.enums.ArchiveType;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.model.holders.LinkInfoHolder;
import org.index.patchdownloader.util.StatSet;
import org.index.patchdownloader.util.xml.IXmlParser;

import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class UpNovaFileList implements IXmlParser
{
    private final UpNovaUpdateConfig _upNovaUpdateConfig;

    private String _version;
    private String _initialName;

    private final Map<String, FileInfoHolder> _fileMapHolder;

    public UpNovaFileList(UpNovaUpdateConfig upNovaUpdateConfig)
    {
        _upNovaUpdateConfig = upNovaUpdateConfig;
        _fileMapHolder = new HashMap<>();
    }

    @Override
    public void parseDocument(String xmlInfo, Document document)
    {
        for (Element updateInfoElement : IXmlParser.getChildNodes(document, "UpdateInfo"))
        {
            _version = IXmlParser.parseTextToStatSet(updateInfoElement).getString("Version", null);
            for (Element folderElement : IXmlParser.getChildNodes(updateInfoElement, "Folder"))
            {
                _initialName = IXmlParser.parseTextToStatSet(folderElement).getString("Name", null);
                parseFoldersElement(folderElement);
                parseFilesElement(folderElement);
            }
        }
    }

    private void parseFoldersElement(Element element)
    {
        for (Element foldersElement : IXmlParser.getChildNodes(element, "Folders"))
        {
            for (Element folderModelElements : IXmlParser.getChildNodes(foldersElement, "FolderModel"))
            {
                parseFoldersElement(folderModelElements);
                parseFilesElement(folderModelElements);
            }
        }
    }

    private void parseFilesElement(Element element)
    {
        for (Element filesElement : IXmlParser.getChildNodes(element, "Files"))
        {
            for (Element fileModelElements : IXmlParser.getChildNodes(filesElement, "FileModel"))
            {
                StatSet fileModelSet = IXmlParser.parseTextToStatSet(fileModelElements);

                String name = fileModelSet.getString("Name", null);
                String path = fileModelSet.getString("Path", null);
                String size = fileModelSet.getString("Size", null);
                String hash = fileModelSet.getString("Hash", null);

                FileInfoHolder fileInfoHolder = new FileInfoHolder(name, path.substring(_initialName.length() + 1), ArchiveType.ZIP_ARCHIVE, false, 0);
                fileInfoHolder.setFileLength(Integer.parseInt(size));
                fileInfoHolder.setFileHashSum(hash);

                String accessLink = ((_upNovaUpdateConfig == null ? "" : _upNovaUpdateConfig.getPatchPath()) + "/" + path + "/" + name + ".zip").replaceAll("\\\\", "/");

                fileInfoHolder.setAccessLink(new LinkInfoHolder(fileInfoHolder));
                fileInfoHolder.getAccessLink().setAccessLink(URI.create(accessLink).normalize().toString());

                _fileMapHolder.put(fileInfoHolder.getLinkPath().toLowerCase(), fileInfoHolder);
            }
        }
    }

    public String getVersion()
    {
        return _version;
    }

    public String getInitialName()
    {
        return _initialName;
    }

    public Map<String, FileInfoHolder> getFileMapHolder()
    {
        return _fileMapHolder;
    }

    public static void main(String[] args)
    {
        UpNovaFileList fileList = new UpNovaFileList(null);
        fileList.parseFile(new File("E:\\MrKirill1232\\Lineage 2\\IMBA_388_GAMMA_LAUNCHER\\packed\\UpdateInfo.xml"));
    }
}
