package org.index.patchdownloader.cli.instances;

import org.index.patchdownloader.cli.CliArgs;
import org.index.patchdownloader.cli.ICliInstance;
import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.enums.DownloadMode;

/**
 * EN: {@code -download_mode <mode>}: sets the run-wide storage policy ({@code MainConfig.DOWNLOAD_MODE}). Value
 *     is one of the {@link DownloadMode} constants (case-insensitive): {@code ALL_MEMORY} (default, unchanged
 *     historical behaviour), {@code HYBRID} (large or unknown-size files stream through a temporary file) or
 *     {@code ALL_TEMP} (every file streams through a temporary file). An unknown value is warned and ignored.<br>
 * RU: {@code -download_mode <mode>}: задаёт общую для запуска политику хранения ({@code MainConfig.DOWNLOAD_MODE}).
 *     Значение — одна из констант {@link DownloadMode} (без учёта регистра): {@code ALL_MEMORY} (по умолчанию,
 *     прежнее поведение без изменений), {@code HYBRID} (большие файлы и файлы неизвестного размера идут через
 *     временный файл) или {@code ALL_TEMP} (каждый файл идёт через временный файл). Неизвестное значение
 *     логируется как предупреждение и игнорируется.<br>
 **/
public class CliDownloadModeInstance implements ICliInstance
{
    @Override
    public String[] getParsableAttributes()
    {
        return new String[]{"-download_mode"};
    }

    @Override
    public String getDescription()
    {
        return "Storage policy. One of: ALL_MEMORY | HYBRID | ALL_TEMP. Example: -download_mode HYBRID";
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
        DownloadMode mode = CliArgs.parseEnum(value, DownloadMode.class, null);
        if (mode == null)
        {
            CliArgs.warn(arguments[currIndex], "unknown download mode '" + value + "'.");
            return currIndex + 1;
        }
        MainConfig.DOWNLOAD_MODE = mode;
        CliArgs.logApplied(arguments[currIndex], MainConfig.DOWNLOAD_MODE);
        return currIndex + 1;
    }
}
