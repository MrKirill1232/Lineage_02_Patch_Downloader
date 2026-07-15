package org.index.patchdownloader.model.versioncheck;

import org.index.patchdownloader.enums.CDNLink;
import org.index.patchdownloader.interfaces.IDummyLogger;

/**
 * EN: Drives the {@code -last_version} action: resolves the configured CDN source's current patch version through
 *     a single {@link NcUpdateVersionClient} query (NC update protocol, TCP:27500) and logs it. Every NC region
 *     with a known update host works (TW / KR / JP / NA); a source without one (Akumu / UpNova) logs a clear
 *     "no NC update host" line. Returns a process exit code so the caller can exit straight after — this action
 *     never downloads anything.<br>
 * RU: Выполняет действие {@code -last_version}: узнаёт текущую версию патча настроенного источника CDN одним
 *     запросом {@link NcUpdateVersionClient} (update-протокол NC, TCP:27500) и логирует её. Работает для каждого
 *     NC-региона с известным update-хостом (TW / KR / JP / NA); источник без него (Akumu / UpNova) пишет понятную
 *     строку «нет update-хоста NC». Возвращает код выхода процесса, чтобы вызывающий сразу завершился — это
 *     действие ничего не скачивает.<br>
 **/
public final class LastVersionCheck
{
    private static final int UPDATE_PORT = 27500;
    private static final int TIMEOUT_MS = 5000;

    private LastVersionCheck()
    {
    }

    /**
     * EN: Resolves and logs the current version for {@code cdn}. A missing source, a source without a known
     *     update host, or a failed/timed-out query all log an error and return a non-zero exit code; a successful
     *     resolve logs the version and hash and returns {@code 0}. Never throws — the query error is logged. <br>
     * RU: Узнаёт и логирует текущую версию для {@code cdn}. Отсутствующий источник, источник без известного
     *     update-хоста или неудавшийся/просроченный запрос — все пишут ошибку и возвращают ненулевой код выхода;
     *     удачное разрешение логирует версию и хеш и возвращает {@code 0}. Не бросает — ошибка запроса
     *     логируется. <br>
     * ==================================================================<br>
     * EN: @param cdn the configured CDN source / RU: @param cdn настроенный источник CDN <br>
     * @return <br>
     *         {int} - EN: process exit code (0 = resolved, 1 = unavailable/failed) / RU: код выхода процесса (0 = получено, 1 = недоступно/сбой) <br>
     **/
    public static int run(CDNLink cdn)
    {
        if (cdn == null)
        {
            IDummyLogger.log(IDummyLogger.ERROR, "-last_version: CDN source is undefined. Pass -cdn <source> BEFORE -last_version, or set 'cdn_source' in Main.ini.");
            return 1;
        }
        String service = cdn.getUpdateServiceId();
        String host = cdn.getUpdateHost();
        if (service == null || host == null)
        {
            IDummyLogger.log(IDummyLogger.ERROR, "-last_version: " + cdn + " has no NC update host - live version-check works only for the NC_SOFT_* regions (TW / KR / JP / NA).");
            return 1;
        }
        try
        {
            NcUpdateVersionClient.VersionInfo info = NcUpdateVersionClient.query(host, UPDATE_PORT, service, TIMEOUT_MS);
            IDummyLogger.log(IDummyLogger.INFO, "Latest version for " + cdn + " (" + service + " @ " + host + ":" + UPDATE_PORT + "): " + info.version() + (info.hash() == null ? "" : " (hash " + info.hash() + ")") + ".");
            return 0;
        }
        catch (Exception e)
        {
            IDummyLogger.log(IDummyLogger.ERROR, "-last_version: version-check failed for " + cdn + " (" + service + " @ " + host + ":" + UPDATE_PORT + "): " + e);
            return 1;
        }
    }
}
