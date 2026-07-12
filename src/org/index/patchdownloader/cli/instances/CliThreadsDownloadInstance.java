package org.index.patchdownloader.cli.instances;

import org.index.patchdownloader.cli.CliArgs;
import org.index.patchdownloader.cli.ICliInstance;
import org.index.patchdownloader.config.configs.MainConfig;

/**
 * EN: {@code -threads_download <n>}: number of concurrent download workers
 *     ({@code MainConfig.PARALLEL_DOWNLOADING}). A non-numeric value keeps the current value.<br>
 * RU: {@code -threads_download <n>}: число одновременных воркеров загрузки
 *     ({@code MainConfig.PARALLEL_DOWNLOADING}). Нечисловое значение сохраняет текущее.<br>
 **/
public class CliThreadsDownloadInstance implements ICliInstance
{
    @Override
    public String[] getParsableAttributes()
    {
        return new String[]{"-threads_download"};
    }

    @Override
    public String getDescription()
    {
        return "Concurrent download workers (do not use more than 3). Example: -threads_download 3";
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
        MainConfig.PARALLEL_DOWNLOADING = CliArgs.parseInteger(value, MainConfig.PARALLEL_DOWNLOADING);
        CliArgs.logApplied(arguments[currIndex], MainConfig.PARALLEL_DOWNLOADING);
        return currIndex + 1;
    }
}
