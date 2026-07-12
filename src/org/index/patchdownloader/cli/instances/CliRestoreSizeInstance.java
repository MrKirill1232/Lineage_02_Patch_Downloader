package org.index.patchdownloader.cli.instances;

import org.index.patchdownloader.cli.CliArgs;
import org.index.patchdownloader.cli.ICliInstance;
import org.index.patchdownloader.config.configs.MainConfig;

/**
 * EN: {@code -r_size}: value-less flag; with {@code -restore}, compares existing on-disk files by SIZE
 *     ({@code MainConfig.CHECK_BY_SIZE}) to decide whether to skip them.<br>
 * RU: {@code -r_size}: флаг без значения; вместе с {@code -restore} сравнивает существующие на диске файлы по
 *     РАЗМЕРУ ({@code MainConfig.CHECK_BY_SIZE}), чтобы решить, пропускать ли их.<br>
 **/
public class CliRestoreSizeInstance implements ICliInstance
{
    @Override
    public String[] getParsableAttributes()
    {
        return new String[]{"-r_size"};
    }

    @Override
    public String getDescription()
    {
        return "Restore check: compare existing files by size (use with -restore).";
    }

    @Override
    public int parseAttribute(int currIndex, String[] arguments)
    {
        MainConfig.CHECK_BY_SIZE = true;
        CliArgs.logApplied(arguments[currIndex], MainConfig.CHECK_BY_SIZE);
        return currIndex;
    }
}
