package org.index.patchdownloader.cli.instances;

import org.index.patchdownloader.cli.ICliInstance;
import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.model.versioncheck.LastVersionCheck;

/**
 * EN: {@code -last_version}: a value-less action flag (like {@code -help}) that resolves the CDN source's current
 *     patch version through ONE live NC update-protocol query, prints it (with the content hash), then terminates
 *     the program with the query's exit code — a version check is a query, not a run, so it exits right here during
 *     parsing. It downloads nothing and needs no config, so pass {@code -cdn <source>} BEFORE it to pick the region
 *     (a source set in {@code Main.ini} also works). Consumes no following token.<br>
 * RU: {@code -last_version}: флаг-действие без значения (как {@code -help}), который узнаёт текущую версию патча
 *     источника CDN одним живым запросом по update-протоколу NC, печатает её (с хешем контента) и завершает
 *     программу кодом выхода запроса — проверка версии это запрос, а не запуск, поэтому выход происходит прямо
 *     здесь, при разборе. Она ничего не скачивает и не требует конфига, поэтому передавайте {@code -cdn <source>}
 *     ПЕРЕД ней для выбора региона (источник из {@code Main.ini} тоже подходит). Не считывает следующий аргумент
 *     как значение.<br>
 **/
public class CliLastVersionInstance implements ICliInstance
{
    @Override
    public String[] getParsableAttributes()
    {
        return new String[]{"-last_version"};
    }

    @Override
    public String getDescription()
    {
        return "Resolve and print the CDN source's current patch version (live NC update query), then exit. Put -cdn <source> before it. Works for the NC_SOFT_* regions (TW / KR / JP / NA).";
    }

    @Override
    public int parseAttribute(int currIndex, String[] arguments)
    {
        // Act-and-exit during parsing, exactly like -help: -cdn (if any) has already been applied by this point, so
        // MainConfig.CDN_SOURCE reflects the chosen region.
        System.exit(LastVersionCheck.run(MainConfig.CDN_SOURCE));
        return currIndex;
    }
}