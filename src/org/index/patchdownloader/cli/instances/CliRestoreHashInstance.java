package org.index.patchdownloader.cli.instances;

import org.index.patchdownloader.cli.CliArgs;
import org.index.patchdownloader.cli.ICliInstance;
import org.index.patchdownloader.config.configs.MainConfig;

/**
 * EN: {@code -r_hash}: value-less flag; with {@code -restore}, compares existing on-disk files by HASH-SUM
 *     ({@code MainConfig.CHECK_BY_HASH_SUM}) to decide whether to skip them.<br>
 * RU: {@code -r_hash}: флаг без значения; вместе с {@code -restore} сравнивает существующие на диске файлы по
 *     ХЕШ-СУММЕ ({@code MainConfig.CHECK_BY_HASH_SUM}), чтобы решить, пропускать ли их.<br>
 **/
public class CliRestoreHashInstance implements ICliInstance
{
    @Override
    public String[] getParsableAttributes()
    {
        return new String[]{"-r_hash"};
    }

    @Override
    public String getDescription()
    {
        return "Restore check: compare existing files by hash-sum (use with -restore).";
    }

    @Override
    public int parseAttribute(int currIndex, String[] arguments)
    {
        MainConfig.CHECK_BY_HASH_SUM = true;
        CliArgs.logApplied(arguments[currIndex], MainConfig.CHECK_BY_HASH_SUM);
        return currIndex;
    }
}
