package org.index.patchdownloader.cli.instances;

import java.io.File;

import org.index.patchdownloader.cli.CliArgs;
import org.index.patchdownloader.cli.ICliInstance;
import org.index.patchdownloader.config.configs.MainConfig;

/**
 * EN: {@code -path <folder>}: sets the output folder ({@code MainConfig.DOWNLOAD_PATH}) as a plain
 *     {@link File}. Uses the value directly — the old {@code URI.create(...)} round-trip crashed on Windows
 *     paths with spaces (e.g. {@code C:\Program Files\...}).<br>
 * RU: {@code -path <folder>}: задаёт выходную папку ({@code MainConfig.DOWNLOAD_PATH}) как обычный
 *     {@link File}. Использует значение напрямую — старый прогон через {@code URI.create(...)} падал на
 *     Windows-путях с пробелами (напр. {@code C:\Program Files\...}).<br>
 **/
public class CliDownloadPathInstance implements ICliInstance
{
    @Override
    public String[] getParsableAttributes()
    {
        return new String[]{"-path"};
    }

    @Override
    public String getDescription()
    {
        return "Absolute output folder for downloaded files. Example: -path \"C:/downloads/lineage2/\"";
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
        MainConfig.DOWNLOAD_PATH = new File(value);
        CliArgs.logApplied(arguments[currIndex], MainConfig.DOWNLOAD_PATH);
        return currIndex + 1;
    }
}
