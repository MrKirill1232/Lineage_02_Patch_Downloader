package org.index.patchdownloader.cli.instances;

import org.index.patchdownloader.cli.CliArgs;
import org.index.patchdownloader.cli.ICliInstance;
import org.index.patchdownloader.config.configs.MainConfig;

/**
 * EN: {@code -akumu_connections <n>}: hard cap on concurrent connections to the akumu mirror
 *     ({@code MainConfig.AKUMU_MAX_CONNECTIONS}); keep it low to stay polite to a source that limits
 *     connections. A non-numeric value keeps the current value.<br>
 * RU: {@code -akumu_connections <n>}: жёсткий лимит одновременных соединений к зеркалу akumu
 *     ({@code MainConfig.AKUMU_MAX_CONNECTIONS}); задавайте небольшое значение, чтобы не перегружать источник,
 *     ограничивающий число соединений. Нечисловое значение оставляет текущее без изменений.<br>
 **/
public class CliAkumuConnectionsInstance implements ICliInstance
{
    @Override
    public String[] getParsableAttributes()
    {
        return new String[]{"-akumu_connections"};
    }

    @Override
    public String getDescription()
    {
        return "Max concurrent connections to akumu (keep low, e.g. 2). Example: -akumu_connections 2";
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
        MainConfig.AKUMU_MAX_CONNECTIONS = Math.max(1, CliArgs.parseInteger(value, MainConfig.AKUMU_MAX_CONNECTIONS));
        CliArgs.logApplied(arguments[currIndex], MainConfig.AKUMU_MAX_CONNECTIONS);
        return currIndex + 1;
    }
}
