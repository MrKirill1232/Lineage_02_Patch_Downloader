package org.index.patchdownloader.cli.instances;

import java.io.File;

import org.index.patchdownloader.cli.CliArgs;
import org.index.patchdownloader.cli.ICliInstance;
import org.index.patchdownloader.config.configs.MainConfig;

/**
 * EN: {@code -temp_file_dir <folder>}: directory for the raw temporary files used by the {@code HYBRID} and
 *     {@code ALL_TEMP} modes ({@code MainConfig.TEMP_FILE_DIR}). The value is taken as a plain {@link File};
 *     keep it on the same volume as the output folder so finalising a file is a rename rather than a
 *     cross-volume copy. When omitted, the default is {@code DOWNLOAD_PATH/.tmp}.<br>
 * RU: {@code -temp_file_dir <folder>}: каталог для сырых временных файлов, используемых режимами {@code HYBRID}
 *     и {@code ALL_TEMP} ({@code MainConfig.TEMP_FILE_DIR}). Значение берётся как обычный {@link File}; держите
 *     его на том же томе, что и папка вывода, чтобы завершение файла было переименованием, а не копированием
 *     между томами. Если не задан, по умолчанию {@code DOWNLOAD_PATH/.tmp}.<br>
 **/
public class CliTempDirInstance implements ICliInstance
{
    @Override
    public String[] getParsableAttributes()
    {
        return new String[]{"-temp_file_dir"};
    }

    @Override
    public String getDescription()
    {
        return "Directory for raw temporary files (HYBRID/ALL_TEMP); keep on the output volume. Example: -temp_file_dir \"C:/downloads/lineage2/.tmp\"";
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
        MainConfig.TEMP_FILE_DIR = new File(value);
        CliArgs.logApplied(arguments[currIndex], MainConfig.TEMP_FILE_DIR);
        return currIndex + 1;
    }
}
