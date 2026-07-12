package org.index.patchdownloader.cli;

import git.index.fieldparser.FieldParserManager;
import git.index.fieldparser.model.FieldClassRef;
import org.index.patchdownloader.interfaces.IDummyLogger;

/**
 * EN: Small shared helpers for the CLI argument handlers: fetching / unquoting the value token that follows a
 *     flag, parsing scalars through the same {@link FieldParserManager} the config uses (so an invalid value
 *     falls back to a default instead of throwing), and a uniform warning log. Kept separate so each
 *     {@link ICliInstance} stays a few lines and only expresses WHICH config field it sets.<br>
 * RU: Небольшие общие помощники для обработчиков CLI-аргументов: получение / снятие кавычек с токена-значения
 *     после флага, разбор скаляров через тот же {@link FieldParserManager}, что и у конфига (поэтому неверное
 *     значение откатывается к умолчанию, а не вызывает исключение), и единообразный лог-предупреждение. Вынесены отдельно,
 *     чтобы каждый {@link ICliInstance} оставался в несколько строк и выражал только то, какое поле конфига задаёт.<br>
 **/
public final class CliArgs
{
    private CliArgs()
    {
    }

    /**
     * EN: Returns the unquoted value token right after {@code currIndex}, or {@code null} when the flag is the
     *     last token (no value supplied) or the following token is itself a registered flag (value omitted, so
     *     the next flag must not be swallowed). <br>
     * RU: Возвращает токен-значение (без кавычек) сразу после {@code currIndex} или {@code null}, если флаг —
     *     последний токен (значение не передано) либо следующий токен сам является зарегистрированным флагом
     *     (значение пропущено, поэтому следующий флаг нельзя поглощать). <br>
     * ==================================================================<br>
     * EN: @param currIndex index of the flag / RU: @param currIndex индекс флага <br>
     * EN: @param arguments the argument array / RU: @param arguments массив аргументов <br>
     * @return <br>
     *         {String} - EN: the next value, or null / RU: следующее значение или null <br>
     **/
    public static String nextValue(int currIndex, String[] arguments)
    {
        if (currIndex + 1 >= arguments.length)
        {
            return null;
        }
        if (CliArguments.isKnownFlag(arguments[currIndex + 1]))
        {
            return null;
        }
        return unquote(arguments[currIndex + 1]);
    }

    /**
     * EN: Strips a single pair of surrounding double-quotes; empty/short strings are returned unchanged
     *     (guards the old {@code charAt(0)} crash on an empty value). <br>
     * RU: Снимает одну пару обрамляющих двойных кавычек; пустые/короткие строки возвращаются без изменений
     *     (защита от старого краша {@code charAt(0)} на пустом значении). <br>
     * ==================================================================<br>
     * EN: @param value the raw token / RU: @param value сырой токен <br>
     * @return <br>
     *         {String} - EN: the unquoted value / RU: значение без кавычек <br>
     **/
    public static String unquote(String value)
    {
        if (value == null || value.length() < 2)
        {
            return value;
        }
        if (value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"')
        {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    /**
     * EN: Parses a string to {@code int} via the shared {@link FieldParserManager}, returning
     *     {@code defaultValue} when it is not a number. <br>
     * RU: Разбирает строку в {@code int} через общий {@link FieldParserManager}, возвращая {@code defaultValue},
     *     если это не число. <br>
     * ==================================================================<br>
     * EN: @param value the raw value / RU: @param value сырое значение <br>
     * EN: @param defaultValue fallback when not a number / RU: @param defaultValue запасное значение, если не число <br>
     * @return <br>
     *         {int} - EN: the parsed int / RU: разобранный int <br>
     **/
    public static int parseInteger(String value, int defaultValue)
    {
        return FieldParserManager.getInstance().applyParserFromClass(Integer.class).parseValue(value, new FieldClassRef<>(Integer.class), defaultValue);
    }

    /**
     * EN: Parses a string to an enum constant of {@code enumClass} via the shared {@link FieldParserManager}
     *     (case-insensitive by name, or by ordinal), returning {@code defaultValue} on no match. <br>
     * RU: Разбирает строку в константу перечисления {@code enumClass} через общий {@link FieldParserManager}
     *     (по имени без учёта регистра или по ordinal), возвращая {@code defaultValue} при отсутствии совпадения. <br>
     * ==================================================================<br>
     * EN: @param value the raw value / RU: @param value сырое значение <br>
     * EN: @param enumClass the enum type / RU: @param enumClass тип перечисления <br>
     * EN: @param defaultValue fallback when no constant matches / RU: @param defaultValue запасное значение при отсутствии совпадения <br>
     * @return <br>
     *         {T} - EN: the parsed enum constant / RU: разобранная константа перечисления <br>
     **/
    public static <T extends Enum<T>> T parseEnum(String value, Class<T> enumClass, T defaultValue)
    {
        return FieldParserManager.getInstance().applyParserFromClass(enumClass).parseValue(value, new FieldClassRef<>(enumClass), defaultValue);
    }

    /**
     * EN: Logs a uniform warning for a start-up argument (missing value, unknown value, …). <br>
     * RU: Логирует единообразное предупреждение для аргумента запуска (нет значения, неизвестное значение, …). <br>
     * ==================================================================<br>
     * EN: @param flag the argument flag / RU: @param flag флаг аргумента <br>
     * EN: @param message the reason / RU: @param message причина <br>
     **/
    public static void warn(String flag, String message)
    {
        IDummyLogger.log(IDummyLogger.WARNING, "Start-up argument '" + flag + "': " + message);
    }

    /**
     * EN: Logs (INFO) that a start-up argument overrode a config value, showing the resulting value — so a run
     *     started with CLI overrides makes the applied changes visible in the log. <br>
     * RU: Логирует (INFO), что аргумент запуска переопределил значение конфига, показывая итоговое значение —
     *     чтобы запуск с CLI-переопределениями показывал применённые изменения в логе. <br>
     * ==================================================================<br>
     * EN: @param flag the argument flag / RU: @param flag флаг аргумента <br>
     * EN: @param value the resulting config value / RU: @param value итоговое значение конфига <br>
     **/
    public static void logApplied(String flag, Object value)
    {
        IDummyLogger.log(IDummyLogger.INFO, "Start-up argument '" + flag + "' overrides config: new value = '" + value + "'.");
    }
}
