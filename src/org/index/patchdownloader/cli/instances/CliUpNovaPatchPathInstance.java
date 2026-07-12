package org.index.patchdownloader.cli.instances;

import org.index.patchdownloader.cli.CliArgs;
import org.index.patchdownloader.cli.ICliInstance;
import org.index.patchdownloader.config.configs.MainConfig;

/**
 * EN: {@code -upnova_patch_path <node>}: sets the XML node name that holds the patch path inside
 *     {@code UpdateConfig.xml} ({@code MainConfig.UP_NOVA_LAUNCHER_PATCH_PATH}); used only with
 *     {@code -cdn UP_NOVA_LAUNCHER}. It is a node NAME, not a URL, so it is stored verbatim.<br>
 * RU: {@code -upnova_patch_path <node>}: задаёт имя XML-узла с путём к патчу внутри {@code UpdateConfig.xml}
 *     ({@code MainConfig.UP_NOVA_LAUNCHER_PATCH_PATH}); только с {@code -cdn UP_NOVA_LAUNCHER}. Это ИМЯ узла,
 *     а не URL, поэтому хранится дословно.<br>
 **/
public class CliUpNovaPatchPathInstance implements ICliInstance
{
    @Override
    public String[] getParsableAttributes()
    {
        return new String[]{"-upnova_patch_path"};
    }

    @Override
    public String getDescription()
    {
        return "XML node name holding the patch path in UpdateConfig.xml (only with -cdn UP_NOVA_LAUNCHER). Default: PatchPath";
    }

    @Override
    public int parseAttribute(int currIndex, String[] arguments)
    {
        String value = CliArgs.nextValue(currIndex, arguments);
        if (value == null)
        {
            CliArgs.warn(arguments[currIndex], "requires a value.");
            return currIndex;
        }
        MainConfig.UP_NOVA_LAUNCHER_PATCH_PATH = value.isEmpty() ? null : value;
        CliArgs.logApplied(arguments[currIndex], MainConfig.UP_NOVA_LAUNCHER_PATCH_PATH);
        return currIndex + 1;
    }
}
