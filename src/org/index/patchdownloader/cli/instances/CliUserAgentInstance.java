package org.index.patchdownloader.cli.instances;

import org.index.patchdownloader.cli.CliArgs;
import org.index.patchdownloader.cli.ICliInstance;
import org.index.patchdownloader.config.configs.MainConfig;

/**
 * EN: {@code -agent <ua>}: sets the HTTP {@code User-Agent} header used for downloads
 *     ({@code MainConfig.REQUESTED_USER_AGENT}).<br>
 * RU: {@code -agent <ua>}: задаёт HTTP-заголовок {@code User-Agent} для загрузок
 *     ({@code MainConfig.REQUESTED_USER_AGENT}).<br>
 **/
public class CliUserAgentInstance implements ICliInstance
{
    /**
     * EN: The single flag this handler answers to: {@code -agent}.<br>
     * RU: Единственный флаг, на который отвечает этот обработчик: {@code -agent}.<br>
     * @return <br>
     *         {String[]} - EN: the recognised flag / RU: распознаваемый флаг <br>
     **/
    @Override
    public String[] getParsableAttributes()
    {
        return new String[]{"-agent"};
    }

    /**
     * EN: The {@code -help} line describing the {@code -agent} flag.<br>
     * RU: Строка для {@code -help}, описывающая флаг {@code -agent}.<br>
     * @return <br>
     *         {String} - EN: the help text / RU: текст справки <br>
     **/
    @Override
    public String getDescription()
    {
        return "HTTP User-Agent used for downloading. Example: -agent \"Mozilla/5.0\"";
    }

    /**
     * EN: Reads the value token right after {@code -agent} and stores it in
     *     {@code MainConfig.REQUESTED_USER_AGENT}. If no value follows the flag, logs a warning and leaves
     *     the config unchanged.<br>
     * RU: Читает токен-значение сразу после {@code -agent} и сохраняет его в
     *     {@code MainConfig.REQUESTED_USER_AGENT}. Если за флагом нет значения, пишет предупреждение и
     *     оставляет конфиг без изменений.<br>
     * ==================================================================<br>
     * EN: @param currIndex index of the {@code -agent} flag in the array / RU: @param currIndex индекс флага {@code -agent} в массиве <br>
     * EN: @param arguments the full start-up argument array / RU: @param arguments полный массив аргументов запуска <br>
     * @return <br>
     *         {int} - EN: index of the last consumed token: {@code currIndex + 1} on success, {@code currIndex} when no value was given /
     *                 RU: индекс последнего поглощённого токена: {@code currIndex + 1} при успехе, {@code currIndex}, если значение не задано <br>
     **/
    @Override
    public int parseAttribute(int currIndex, String[] arguments)
    {
        String value = CliArgs.nextValue(currIndex, arguments);
        if (value == null)
        {
            CliArgs.warn(arguments[currIndex], "requires a value.");
            return currIndex;
        }
        MainConfig.REQUESTED_USER_AGENT = value;
        CliArgs.logApplied(arguments[currIndex], MainConfig.REQUESTED_USER_AGENT);
        return currIndex + 1;
    }
}
