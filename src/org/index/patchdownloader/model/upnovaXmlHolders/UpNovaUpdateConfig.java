package org.index.patchdownloader.model.upnovaXmlHolders;

import org.dom4j.Document;
import org.dom4j.Element;
import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.util.xml.IXmlParser;

public class UpNovaUpdateConfig implements IXmlParser
{
    private String _updaterTitle;
    private String _updaterVersion;
    private String _selfUpdatePath;
    private String _patchPath;

    /**
     * EN: Reads the {@code <UpdateConfig>} node into the updater title and version, the
     * self-update path and the patch path. The patch path element is read from
     * {@code PatchPath} by default, or from the name given by
     * {@link MainConfig#UP_NOVA_LAUNCHER_PATCH_PATH} when that override is set. Prints a
     * warning to {@code stderr} when the patch path stays unset.
     * <br>
     * RU: Считывает узел {@code <UpdateConfig>} в название и версию обновлятора, путь
     * самообновления и путь до патча. Имя элемента с путём до патча берётся из
     * {@code PatchPath} по умолчанию либо из значения
     * {@link MainConfig#UP_NOVA_LAUNCHER_PATCH_PATH}, если задано это переопределение.
     * Выводит предупреждение в {@code stderr}, когда путь до патча так и остаётся не заданным.
     *
     * @param xmlInfo EN: source URL the XML was fetched from; RU: URL-источник, откуда получен XML
     * @param document EN: parsed XML document to read; RU: разобранный XML-документ для чтения
     */
    @Override
    public void parseDocument(String xmlInfo, Document document)
    {
        for (Element updateConfigElement : IXmlParser.getChildNodes(document, "UpdateConfig"))
        {
            _updaterTitle = IXmlParser.parseString(updateConfigElement, "UpdaterTitle", null);
            _updaterVersion = IXmlParser.parseString(updateConfigElement, "UpdaterVersion", null);
            _selfUpdatePath = IXmlParser.parseString(updateConfigElement, "SelfUpdatePath", null);
            _patchPath = IXmlParser.parseString(updateConfigElement, (MainConfig.UP_NOVA_LAUNCHER_PATCH_PATH == null ? "PatchPath" : MainConfig.UP_NOVA_LAUNCHER_PATCH_PATH), null);
            if (_patchPath == null)
            {
                System.err.println("Used a UpNova Launcher URL Generator. Warning! PatchPath is not setup! Use a [-upnova_patch_path] start-up argument with 'PatchPath' variable or set it in Main.ini as [up_nova_launcher_patch_path] variable. UpdateConfig.xml available by next URL: " + xmlInfo + ";");
            }
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
