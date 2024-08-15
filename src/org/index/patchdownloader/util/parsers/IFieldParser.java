package org.index.patchdownloader.util.parsers;

import org.index.patchdownloader.config.IConfigDummyLogger;
import org.index.patchdownloader.config.annotations.ConfigParameter;

public interface IFieldParser extends IConfigDummyLogger
{
    public Object parseValue(ConfigParameter configParameter, Class<?> requestClass, String value, Object defaultValue, Object... args);

    public default Object parseValueByObjectValue(Class<?> requestClass, Object value, Object defaultValue, Object... args)
    {
        return defaultValue;
    }

    public default byte[] replaceIllegalSymbols(int bytesPerChar, byte[] input)
    {
        return input;
    }

    public default String replaceIllegalSymbols(String string)
    {
        return string;
    }

    public void logParsingError(ConfigParameter configParameter, String configKey, Class<?> requestClass, Object value, Object defaultValue, String... message);

    public default boolean isDigitValue()
    {
        return false;
    }

    public default Object[] getMainArgumentsArray(ConfigParameter configParameter, Class<?> requestClass, Object defaultValue, Object... arguments)
    {
        return arguments;
    }

    public static void innerLogger(String level, Class<?> requestedClass, String message, Throwable throwable)
    {
        IConfigDummyLogger.log(level, requestedClass, message, throwable);
    }
}
