package org.index.patchdownloader.cli.instances;

import org.index.patchdownloader.cli.CliArgs;
import org.index.patchdownloader.cli.ICliInstance;
import org.index.patchdownloader.config.configs.MainConfig;

/**
 * EN: {@code -threads_saving <n>}: number of concurrent store (file-write) workers
 *     ({@code MainConfig.PARALLEL_STORING}). A non-numeric value keeps the current value.<br>
 * RU: {@code -threads_saving <n>}: число одновременных воркеров сохранения (записи файлов)
 *     ({@code MainConfig.PARALLEL_STORING}). Нечисловое значение сохраняет текущее.<br>
 **/
public class CliThreadsStoreInstance implements ICliInstance
{
    @Override
    public String[] getParsableAttributes()
    {
        return new String[]{"-threads_saving"};
    }

    @Override
    public String getDescription()
    {
        return "Concurrent file-saving workers (recommended 1). Example: -threads_saving 1";
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
        MainConfig.PARALLEL_STORING = CliArgs.parseInteger(value, MainConfig.PARALLEL_STORING);
        CliArgs.logApplied(arguments[currIndex], MainConfig.PARALLEL_STORING);
        return currIndex + 1;
    }
}
