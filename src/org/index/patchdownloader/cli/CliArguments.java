package org.index.patchdownloader.cli;

import org.index.patchdownloader.interfaces.IDummyLogger;

/**
 * EN: Start-up argument dispatcher. Walks the raw {@code String[]} once; for each token it finds the
 *     {@link CliArg} whose instance answers to that flag and hands control to its
 *     {@link ICliInstance#parseAttribute(int, String[])}, which returns the index of the last token it
 *     consumed so the walk resumes right after. Unknown tokens are skipped. Replaces the old
 *     enum-of-handlers + manual index bookkeeping in the controller.<br>
 * RU: Диспетчер аргументов запуска. Проходит сырой {@code String[]} один раз; для каждого токена находит
 *     {@link CliArg}, чей экземпляр отвечает на этот флаг, и передаёт управление его
 *     {@link ICliInstance#parseAttribute(int, String[])}, который возвращает индекс последнего поглощённого
 *     токена, чтобы проход продолжился сразу за ним. Неизвестные токены пропускаются. Заменяет старый
 *     enum-обработчиков + ручной учёт индекса в контроллере.<br>
 **/
public final class CliArguments
{
    private CliArguments()
    {
    }

    /**
     * EN: Parses every recognised start-up argument, applying it to {@code MainConfig}. Null / empty arrays are
     *     a no-op. <br>
     * RU: Разбирает каждый распознанный аргумент запуска, применяя его к {@code MainConfig}. Null / пустой
     *     массив — ничего не делает. <br>
     * ==================================================================<br>
     * EN: @param arguments the JVM start-up arguments / RU: @param arguments аргументы запуска JVM <br>
     **/
    public static void parse(String[] arguments)
    {
        if (arguments == null)
        {
            return;
        }
        for (int index = 0; index < arguments.length; index++)
        {
            ICliInstance instance = findInstance(arguments[index]);
            if (instance == null)
            {
                if (arguments[index] != null && arguments[index].startsWith("-"))
                {
                    IDummyLogger.log(IDummyLogger.WARNING, "Unknown start-up argument '" + arguments[index] + "' ignored. Use -help for the list.");
                }
                continue;
            }
            index = instance.parseAttribute(index, arguments);
        }
    }

    /**
     * EN: Finds the argument instance that answers to the given flag (case-insensitive), or {@code null}. <br>
     * RU: Находит экземпляр аргумента, отвечающий на данный флаг (без учёта регистра), или {@code null}. <br>
     * ==================================================================<br>
     * EN: @param flag the token to match / RU: @param flag сопоставляемый токен <br>
     * @return <br>
     *         {ICliInstance} - EN: the matching instance, or null / RU: подходящий экземпляр или null <br>
     **/
    private static ICliInstance findInstance(String flag)
    {
        for (CliArg argument : CliArg.values())
        {
            for (String attribute : argument.getInstance().getParsableAttributes())
            {
                if (attribute.equalsIgnoreCase(flag))
                {
                    return argument.getInstance();
                }
            }
        }
        return null;
    }

    /**
     * EN: Tells whether the given token is a registered start-up flag (case-insensitive). Used so a value
     *     fetch does not swallow the following flag when its own value was omitted. <br>
     * RU: Сообщает, является ли данный токен зарегистрированным флагом запуска (без учёта регистра).
     *     Используется, чтобы получение значения не поглощало следующий флаг, когда собственное значение
     *     пропущено. <br>
     * ==================================================================<br>
     * EN: @param flag the token to test / RU: @param flag проверяемый токен <br>
     * @return <br>
     *         {boolean} - EN: true if a handler answers to this flag / RU: true, если на этот флаг отвечает обработчик <br>
     **/
    public static boolean isKnownFlag(String flag)
    {
        return findInstance(flag) != null;
    }
}
