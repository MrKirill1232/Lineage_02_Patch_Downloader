package org.index.patchdownloader.cli.instances;

import org.index.patchdownloader.cli.CliArgs;
import org.index.patchdownloader.cli.ICliInstance;
import org.index.patchdownloader.config.configs.MainConfig;

/**
 * EN: {@code -threads_check <n>}: number of concurrent workers used to check existing files during
 *     {@code -restore} ({@code MainConfig.THREAD_COUNT_FOR_FILE_CHECK_IN_CONDITION}). A non-numeric value keeps
 *     the current value.<br>
 * RU: {@code -threads_check <n>}: число одновременных воркеров проверки существующих файлов при
 *     {@code -restore} ({@code MainConfig.THREAD_COUNT_FOR_FILE_CHECK_IN_CONDITION}). Нечисловое значение
 *     сохраняет текущее.<br>
 **/
public class CliThreadsCheckInstance implements ICliInstance
{
    @Override
    public String[] getParsableAttributes()
    {
        return new String[]{"-threads_check"};
    }

    @Override
    public String getDescription()
    {
        return "Concurrent workers for the restore file-check. Example: -threads_check 10";
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
        MainConfig.THREAD_COUNT_FOR_FILE_CHECK_IN_CONDITION = Math.max(1, CliArgs.parseInteger(value, MainConfig.THREAD_COUNT_FOR_FILE_CHECK_IN_CONDITION));
        CliArgs.logApplied(arguments[currIndex], MainConfig.THREAD_COUNT_FOR_FILE_CHECK_IN_CONDITION);
        return currIndex + 1;
    }
}
