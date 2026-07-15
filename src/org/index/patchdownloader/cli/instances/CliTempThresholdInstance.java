package org.index.patchdownloader.cli.instances;

import org.index.patchdownloader.cli.CliArgs;
import org.index.patchdownloader.cli.ICliInstance;
import org.index.patchdownloader.config.configs.MainConfig;

/**
 * EN: {@code -temp_file_threshold_mb <n>}: in {@code HYBRID} mode, the size in megabytes at or below which a
 *     file stays in memory ({@code MainConfig.TEMP_FILE_THRESHOLD_MB}); a strictly larger file streams through
 *     a temporary file. Clamped to at least 1. A non-numeric value keeps the current value.<br>
 * RU: {@code -temp_file_threshold_mb <n>}: в режиме {@code HYBRID} — размер в мегабайтах, при котором и ниже
 *     которого файл остаётся в оперативной памяти ({@code MainConfig.TEMP_FILE_THRESHOLD_MB}); строго больший
 *     файл идёт через временный файл. Ограничивается минимумом 1. Нечисловое значение оставляет текущее без
 *     изменений.<br>
 **/
public class CliTempThresholdInstance implements ICliInstance
{
    @Override
    public String[] getParsableAttributes()
    {
        return new String[]{"-temp_file_threshold_mb"};
    }

    @Override
    public String getDescription()
    {
        return "HYBRID mode: file size (MB) up to which a file stays in memory; larger streams to a temp file. Example: -temp_file_threshold_mb 256";
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
        MainConfig.TEMP_FILE_THRESHOLD_MB = Math.max(1, CliArgs.parseInteger(value, MainConfig.TEMP_FILE_THRESHOLD_MB));
        CliArgs.logApplied(arguments[currIndex], MainConfig.TEMP_FILE_THRESHOLD_MB);
        return currIndex + 1;
    }
}
