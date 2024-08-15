package org.index.patchdownloader.util.parsers;

import org.index.patchdownloader.config.annotations.ConfigParameter;
import org.index.patchdownloader.util.Utils;

/**
 * @author Index
 */
public class EnumFieldParser implements IFieldParser
{
    @Override
    public Object parseValue(ConfigParameter configParameter, Class<?> requestClass, String value, Object defaultValue, Object... args)
    {
        if (value == null || requestClass == null)
        {
            logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), requestClass, value, defaultValue,
                    "[Input value] or [Request Class] is null"
            );
            return defaultValue;
        }
        if (!requestClass.isEnum())
        {
            logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), requestClass, value, defaultValue,
                    "[Request Class] is not enum. Found '" + requestClass.getSimpleName() + "'"
            );
            return defaultValue;
        }
        for (Object enumValue : requestClass.getEnumConstants())
        {
            if (enumValue.toString().equalsIgnoreCase(value))
            {
                return enumValue;
            }
        }
        logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), requestClass, value, defaultValue,
                "Cannot parse enum class"
        );
        return defaultValue;
    }

    @Override
    public Object parseValueByObjectValue(Class<?> requestClass, Object value, Object defaultValue, Object... args)
    {
        if (value == null || requestClass == null)
        {
            logParsingError(null, null, requestClass, value, defaultValue,
                    "[Input value] or [Request Class] is null"
            );
            return defaultValue;
        }
        if (value.getClass() == requestClass)
        {
            return value;
        }
        if (value instanceof String)
        {
            return parseValue(null, requestClass, (String) value, defaultValue);
        }
        logParsingError(null, null, requestClass, value, defaultValue,
                "Cannot parse value"
        );
        return defaultValue;
    }

    @Override
    public void logParsingError(ConfigParameter configParameter, String configKey, Class<?> requestClass, Object value, Object defaultValue, String... message)
    {
        IFieldParser.innerLogger(ERROR, getClass(),
                "Cannot parse '" + ("Enum") + ((requestClass == null) ? ("") : (" with requested class '" + requestClass.getSimpleName() + "'")) +"'. " +
                        ((configKey == null)    ? ("") : (  "Config key: '"       + configKey                       + "'. "))   +
                        ((value == null)        ? ("") : (  "Requested value: '"  + String.valueOf(value)           + "'. "))   +
                        (                                   "Used default value '"+ String.valueOf(defaultValue)    + "'. ")    +
                        (Utils.joinStrings(".\n", message)) +
                        (""),
                null);
    }
}
