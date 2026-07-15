package org.index.patchdownloader.model.upnovaXmlHolders;

import org.dom4j.Document;
import org.dom4j.Element;
import org.index.patchdownloader.enums.ArchiveType;
import org.index.patchdownloader.interfaces.IDummyLogger;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.model.holders.LinkInfoHolder;
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

    /**
     * EN: Walks every {@code <UpdateInfo>} block of the parsed launcher document: records the update
     *     {@code Version} and, for each root {@code <Folder>}, remembers its name as the project root
     *     before recursing into its sub-folders and files. <br>
     * RU: Проходит по всем блокам {@code <UpdateInfo>} разобранного документа лаунчера: сохраняет
     *     {@code Version} обновления и для каждой корневой {@code <Folder>} запоминает её имя как корень
     *     проекта, после чего спускается в её подпапки и файлы. <br>
     * ==================================================================<br>
     * EN: @param xmlInfo a label describing the XML source (path or origin), used for diagnostics /
     *     RU: @param xmlInfo метка источника XML (путь или происхождение), используется для диагностики <br>
     * EN: @param document the parsed launcher update document /
     *     RU: @param document разобранный документ обновления лаунчера <br>
     **/
    @Override
    public void parseDocument(String xmlInfo, Document document)
    {
        for (Element updateInfoElement : IXmlParser.getChildNodes(document, "UpdateInfo"))
        {
            _version = IXmlParser.parseString(updateInfoElement, "Version", null);
            for (Element folderElement : IXmlParser.getChildNodes(updateInfoElement, "Folder"))
            {
                _initialName = IXmlParser.parseString(folderElement, "Name", null);
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

    /**
     * EN: For each {@code <FileModel>} inside the given element builds a {@link FileInfoHolder} carrying its
     *     name, path, size and hash: files whose path equals the project root are placed at the project head,
     *     the rest keep their path relative to that root. Assembles the {@code .zip} download link from the
     *     configured patch path and registers the holder in the file map keyed by its lower-cased link path.
     *     The XML comes from an untrusted HTTP source, so malformed entries (a missing name or path, an absent
     *     project root, a path that does not sit under that root, or a link that is not a valid URI) are logged
     *     and skipped instead of aborting the whole update list. <br>
     * RU: Для каждого {@code <FileModel>} внутри переданного элемента создаёт {@link FileInfoHolder} с его
     *     именем, путём, размером и хешем: файлы, чей путь совпадает с корнем проекта, помещаются в корень,
     *     остальные сохраняют путь относительно этого корня. Собирает ссылку на {@code .zip} из настроенного
     *     пути к патчу и регистрирует держатель в карте файлов по ключу — пути ссылки в нижнем регистре.
     *     XML приходит из недоверенного HTTP-источника, поэтому некорректные записи (нет имени или пути, нет
     *     корня проекта, путь лежит вне этого корня либо ссылка не является валидным URI) логируются и
     *     пропускаются, а не обрывают разбор всего списка обновления. <br>
     * ==================================================================<br>
     * EN: @param element the folder element whose {@code <Files>} children are parsed /
     *     RU: @param element элемент папки, чьи дочерние {@code <Files>} разбираются <br>
     **/
    private void parseFilesElement(Element element)
    {
        for (Element filesElement : IXmlParser.getChildNodes(element, "Files"))
        {
            for (Element fileModelElements : IXmlParser.getChildNodes(filesElement, "FileModel"))
            {
                String rawName = IXmlParser.parseString(fileModelElements, "Name", null);
                String rawPath = IXmlParser.parseString(fileModelElements, "Path", null);
                if (rawName == null || rawPath == null)
                {
                    IDummyLogger.log(IDummyLogger.WARNING, "UpNovaFileList: skipping FileModel with missing Name/Path (name='" + rawName + "', path='" + rawPath + "').");
                    continue;
                }
                if (_initialName == null)
                {
                    IDummyLogger.log(IDummyLogger.WARNING, "UpNovaFileList: skipping FileModel '" + rawName + "' because its enclosing Folder has no Name.");
                    continue;
                }
                String name = rawName.replaceAll("\\\\", "/");
                String path = rawPath.replaceAll("\\\\", "/");
                long size = IXmlParser.parseLong(fileModelElements, "Size", -1);
                String hash = IXmlParser.parseString(fileModelElements, "Hash", null);
                FileInfoHolder fileInfoHolder;
                if (path.equalsIgnoreCase(_initialName))
                {   // in head of project
                    fileInfoHolder = new FileInfoHolder(name, "", ArchiveType.ZIP_ARCHIVE, false, 0);
                }
                else if (path.regionMatches(true, 0, _initialName, 0, _initialName.length())
                        && path.length() > _initialName.length() && path.charAt(_initialName.length()) == '/')
                {   // in sub-folder — require a '/' right after the project root so a sibling folder whose name
                    // merely starts with the root ("L2" vs "L2Voice") is NOT mis-parsed into a garbled path.
                    fileInfoHolder = new FileInfoHolder(name, path.substring(_initialName.length() + 1), ArchiveType.ZIP_ARCHIVE, false, 0);
                }
                else
                {
                    IDummyLogger.log(IDummyLogger.WARNING, "UpNovaFileList: skipping FileModel '" + name + "' because its Path '" + path + "' does not start with the project root '" + _initialName + "'.");
                    continue;
                }
                fileInfoHolder.setFileLength(size);
                fileInfoHolder.setFileHashSum(hash);

                String accessLink = ((_upNovaUpdateConfig == null ? "" : _upNovaUpdateConfig.getPatchPath()) + "/" + path + "/" + name + ".zip").replaceAll("\\\\", "/");

                String normalizedLink;
                try
                {
                    normalizedLink = URI.create(accessLink).normalize().toString();
                }
                catch (IllegalArgumentException e)
                {
                    IDummyLogger.log(IDummyLogger.WARNING, "UpNovaFileList: skipping FileModel '" + name + "' because its access link '" + accessLink + "' is not a valid URI: " + e.getMessage());
                    continue;
                }

                fileInfoHolder.setAccessLink(new LinkInfoHolder(fileInfoHolder));
                fileInfoHolder.getAccessLink().setAccessLink(normalizedLink);

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
