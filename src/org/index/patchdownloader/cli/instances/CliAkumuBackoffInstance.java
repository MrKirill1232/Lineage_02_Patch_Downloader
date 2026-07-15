package org.index.patchdownloader.cli.instances;

import org.index.patchdownloader.cli.CliArgs;
import org.index.patchdownloader.cli.ICliInstance;
import org.index.patchdownloader.config.configs.MainConfig;

/**
 * EN: {@code -akumu_backoff <ms>}: base back-off delay in milliseconds for retrying akumu on HTTP 429
 *     (Too Many Requests) or 5xx (server-error) responses
 *     ({@code MainConfig.AKUMU_RETRY_BACKOFF_MS}); the delay grows exponentially from this base. A non-numeric
 *     value keeps the current value; a negative value is clamped to zero.<br>
 * RU: {@code -akumu_backoff <ms>}: базовая задержка backoff в миллисекундах для повтора akumu на HTTP 429
 *     (слишком много запросов) или 5xx (ошибки сервера)
 *     ({@code MainConfig.AKUMU_RETRY_BACKOFF_MS}); задержка растёт экспоненциально от этой базы. Нечисловое
 *     значение сохраняет текущее; отрицательное значение приводится к нулю.<br>
 **/
public class CliAkumuBackoffInstance implements ICliInstance
{
    @Override
    public String[] getParsableAttributes()
    {
        return new String[]{"-akumu_backoff"};
    }

    @Override
    public String getDescription()
    {
        return "Base back-off (ms) for akumu 429/5xx retries (grows exponentially). Example: -akumu_backoff 2000";
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
        // A backoff base of 0 disables the akumu backoff politeness half; a non-positive value falls back to the
        // default (this override runs AFTER onEndLoad's clamp, so the floor must be re-applied here).
        int parsed = CliArgs.parseInteger(value, MainConfig.AKUMU_RETRY_BACKOFF_MS);
        MainConfig.AKUMU_RETRY_BACKOFF_MS = parsed > 0 ? parsed : 2000;
        CliArgs.logApplied(arguments[currIndex], MainConfig.AKUMU_RETRY_BACKOFF_MS);
        return currIndex + 1;
    }
}
