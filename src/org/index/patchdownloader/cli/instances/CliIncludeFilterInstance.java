package org.index.patchdownloader.cli.instances;

import org.index.patchdownloader.cli.CliArgs;
import org.index.patchdownloader.cli.ICliInstance;
import org.index.patchdownloader.config.configs.MainConfig;

/**
 * EN: {@code -include_filter <pattern>}: sets which files to download ({@code MainConfig.INCLUDE_FILE_FILTER}).
 *     A trailing {@code /*} matches a folder and everything nested under it. Example: {@code system/*}.<br>
 * RU: {@code -include_filter <pattern>}: задаёт, какие файлы качать ({@code MainConfig.INCLUDE_FILE_FILTER}).
 *     Завершающий {@code /*} соответствует папке и всему, что в ней вложено. Пример: {@code system/*}.<br>
 **/
public class CliIncludeFilterInstance implements ICliInstance
{
    @Override
    public String[] getParsableAttributes()
    {
        return new String[]{"-include_filter"};
    }

    @Override
    public String getDescription()
    {
        return "Only download files matching this pattern (trailing /* is recursive). Example: -include_filter system/*";
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
        MainConfig.INCLUDE_FILE_FILTER = value;
        CliArgs.logApplied(arguments[currIndex], MainConfig.INCLUDE_FILE_FILTER);
        return currIndex + 1;
    }
}
