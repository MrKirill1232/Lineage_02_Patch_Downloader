package org.index.patchdownloader.cli.instances;

import org.index.patchdownloader.cli.CliArgs;
import org.index.patchdownloader.cli.ICliInstance;
import org.index.patchdownloader.config.configs.MainConfig;

/**
 * EN: {@code -log_check}: value-less flag that enables progress logging of the {@code -restore} file check
 *     ({@code MainConfig.LOGGING_FILE_CHECK_IN_CONDITION}).<br>
 * RU: {@code -log_check}: флаг без значения, включающий логирование прогресса проверки файлов при
 *     {@code -restore} ({@code MainConfig.LOGGING_FILE_CHECK_IN_CONDITION}).<br>
 **/
public class CliLogCheckInstance implements ICliInstance
{
    @Override
    public String[] getParsableAttributes()
    {
        return new String[]{"-log_check"};
    }

    @Override
    public String getDescription()
    {
        return "Log the progress of the restore file-check.";
    }

    @Override
    public int parseAttribute(int currIndex, String[] arguments)
    {
        MainConfig.LOGGING_FILE_CHECK_IN_CONDITION = true;
        CliArgs.logApplied(arguments[currIndex], MainConfig.LOGGING_FILE_CHECK_IN_CONDITION);
        return currIndex;
    }
}
