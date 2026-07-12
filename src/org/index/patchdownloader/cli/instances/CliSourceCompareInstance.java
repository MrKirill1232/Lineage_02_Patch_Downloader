package org.index.patchdownloader.cli.instances;

import java.io.File;

import org.index.patchdownloader.cli.CliArgs;
import org.index.patchdownloader.cli.ICliInstance;
import org.index.patchdownloader.config.configs.MainConfig;

/**
 * EN: {@code -source_compare <folder>}: enables source-compare and sets the local source root
 *     ({@code MainConfig.SOURCE_COMPARE_PATH}). Files that verify against the torrent/Content-Delivery-Network (CDN)
 *     hashes are COPIED from this folder instead of downloaded. Size + hash checks are on by default (hash is what
 *     makes the reuse safe — some {@code .dat} files are RSA (Rivest-Shamir-Adleman)-packed and keep their size
 *     while their content differs).<br>
 * RU: {@code -source_compare <folder>}: включает source-compare и задаёт корень локального источника
 *     ({@code MainConfig.SOURCE_COMPARE_PATH}). Файлы, прошедшие проверку по хешам торрента/сети доставки контента
 *     (CDN), КОПИРУЮТСЯ из этой папки вместо загрузки. Проверки размера + хеша включены по умолчанию (именно хеш
 *     делает переиспользование безопасным — часть {@code .dat} упакована алгоритмом RSA (Rivest-Shamir-Adleman)
 *     и сохраняет размер при ином контенте).<br>
 **/
public class CliSourceCompareInstance implements ICliInstance
{
    @Override
    public String[] getParsableAttributes()
    {
        return new String[]{"-source_compare"};
    }

    @Override
    public String getDescription()
    {
        return "Reuse files from a local source folder (copy instead of download) when they verify against the torrent/CDN hashes. Example: -source_compare \"C:/existing_client/\"";
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
        MainConfig.SOURCE_COMPARE_PATH = new File(value);
        CliArgs.logApplied(arguments[currIndex], MainConfig.SOURCE_COMPARE_PATH);
        return currIndex + 1;
    }
}
