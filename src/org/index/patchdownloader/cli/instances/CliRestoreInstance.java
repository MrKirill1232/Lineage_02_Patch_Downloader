package org.index.patchdownloader.cli.instances;

import org.index.patchdownloader.cli.CliArgs;
import org.index.patchdownloader.cli.ICliInstance;
import org.index.patchdownloader.config.configs.MainConfig;

/**
 * EN: {@code -restore}: a value-less flag that turns on restore mode ({@code MainConfig.RESTORE_DOWNLOADING}),
 *     which skips files already present and valid on disk. Consumes no following token.<br>
 * RU: {@code -restore}: флаг без значения, включающий режим восстановления ({@code MainConfig.RESTORE_DOWNLOADING}),
 *     который пропускает файлы, уже присутствующие и валидные на диске. Не считывает следующий аргумент как значение.<br>
 **/
public class CliRestoreInstance implements ICliInstance
{
    @Override
    public String[] getParsableAttributes()
    {
        return new String[]{"-restore"};
    }

    @Override
    public String getDescription()
    {
        return "Restore mode: skip files already present and valid on disk (pair with -r_size / -r_hash).";
    }

    @Override
    public int parseAttribute(int currIndex, String[] arguments)
    {
        MainConfig.RESTORE_DOWNLOADING = true;
        CliArgs.logApplied(arguments[currIndex], MainConfig.RESTORE_DOWNLOADING);
        return currIndex;
    }
}
