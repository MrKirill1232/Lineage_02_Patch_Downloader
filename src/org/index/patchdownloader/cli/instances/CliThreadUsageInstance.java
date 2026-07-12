package org.index.patchdownloader.cli.instances;

import org.index.patchdownloader.cli.CliArgs;
import org.index.patchdownloader.cli.ICliInstance;
import org.index.patchdownloader.config.configs.MainConfig;

/**
 * EN: {@code -thread}: value-less flag that enables multi-threaded downloading
 *     ({@code MainConfig.THREAD_USAGE}); off means files are processed one by one.<br>
 * RU: {@code -thread}: флаг без значения, включающий многопоточную загрузку
 *     ({@code MainConfig.THREAD_USAGE}); выкл. означает обработку файлов по одному.<br>
 **/
public class CliThreadUsageInstance implements ICliInstance
{
    @Override
    public String[] getParsableAttributes()
    {
        return new String[]{"-thread"};
    }

    @Override
    public String getDescription()
    {
        return "Enable multi-threaded downloading (off = one file at a time).";
    }

    @Override
    public int parseAttribute(int currIndex, String[] arguments)
    {
        MainConfig.THREAD_USAGE = true;
        CliArgs.logApplied(arguments[currIndex], MainConfig.THREAD_USAGE);
        return currIndex;
    }
}
