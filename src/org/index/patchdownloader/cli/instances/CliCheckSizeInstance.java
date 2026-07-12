package org.index.patchdownloader.cli.instances;

import org.index.patchdownloader.cli.CliArgs;
import org.index.patchdownloader.cli.ICliInstance;
import org.index.patchdownloader.config.configs.MainConfig;

/**
 * EN: {@code -size}: value-less flag that enables the AFTER-download size check
 *     ({@code MainConfig.CHECK_FILE_SIZE}) — compares the downloaded file length with the expected one.<br>
 * RU: {@code -size}: флаг без значения, включающий проверку размера ПОСЛЕ загрузки
 *     ({@code MainConfig.CHECK_FILE_SIZE}) — сравнивает длину скачанного файла с ожидаемой.<br>
 **/
public class CliCheckSizeInstance implements ICliInstance
{
    @Override
    public String[] getParsableAttributes()
    {
        return new String[]{"-size"};
    }

    @Override
    public String getDescription()
    {
        return "After downloading, verify each file's size against the expected size.";
    }

    @Override
    public int parseAttribute(int currIndex, String[] arguments)
    {
        MainConfig.CHECK_FILE_SIZE = true;
        CliArgs.logApplied(arguments[currIndex], MainConfig.CHECK_FILE_SIZE);
        return currIndex;
    }
}
