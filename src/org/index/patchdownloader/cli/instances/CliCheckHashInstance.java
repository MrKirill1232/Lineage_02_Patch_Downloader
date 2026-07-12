package org.index.patchdownloader.cli.instances;

import org.index.patchdownloader.cli.CliArgs;
import org.index.patchdownloader.cli.ICliInstance;
import org.index.patchdownloader.config.configs.MainConfig;

/**
 * EN: {@code -hash}: value-less flag that enables the AFTER-download hash-sum check
 *     ({@code MainConfig.CHECK_HASH_SUM}) — compares the downloaded file hash with the expected one.<br>
 * RU: {@code -hash}: флаг без значения, включающий проверку хеш-суммы ПОСЛЕ загрузки
 *     ({@code MainConfig.CHECK_HASH_SUM}) — сравнивает хеш скачанного файла с ожидаемым.<br>
 **/
public class CliCheckHashInstance implements ICliInstance
{
    @Override
    public String[] getParsableAttributes()
    {
        return new String[]{"-hash"};
    }

    @Override
    public String getDescription()
    {
        return "After downloading, verify each file's hash-sum against the expected hash.";
    }

    @Override
    public int parseAttribute(int currIndex, String[] arguments)
    {
        MainConfig.CHECK_HASH_SUM = true;
        CliArgs.logApplied(arguments[currIndex], MainConfig.CHECK_HASH_SUM);
        return currIndex;
    }
}
