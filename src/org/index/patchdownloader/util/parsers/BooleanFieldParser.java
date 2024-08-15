package org.index.patchdownloader.util.parsers;

import org.index.patchdownloader.config.annotations.ConfigParameter;
import org.index.patchdownloader.util.Utils;

public class BooleanFieldParser implements IFieldParser
{
    @Override
    public Object parseValue(ConfigParameter configParameter, Class<?> requestClass, String value, Object defaultValue, Object... args)
    {
        if (value == null)
        {
            logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), requestClass, value, defaultValue,
                    "[Input value] is null"
            );
            return defaultValue;
        }
        String normalizeString = normalizeString(value);
        if (normalizeString.length() == 1)
        {
            if (normalizeString.charAt(0) == 'y' || normalizeString.charAt(0) == '1')
            {
                return true;
            }
            if (normalizeString.charAt(0) == 'n' || normalizeString.charAt(0) == '0')
            {
                return false;
            }
        }
        else
        {
            if (normalizeString.equals("true") || normalizeString.equals("yes") || normalizeString.equals("on"))
            {
                return true;
            }
            if (normalizeString.equals("false") || normalizeString.equals("no") || normalizeString.equals("off"))
            {
                return false;
            }
        }
        logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), requestClass, value, defaultValue,
                "[01] Cannot handle input value"
        );
        return defaultValue;
    }

    @Override
    public Object parseValueByObjectValue(Class<?> requestClass, Object value, Object defaultValue, Object... args)
    {
        if (value == null)
        {
            logParsingError(null, null, requestClass, value, defaultValue,
                    "[Input value] is null"
            );
            return defaultValue;
        }
        if ((value.getClass() == boolean.class || value.getClass() == Boolean.class))
        {
            return (value.getClass().isPrimitive()) ? ((boolean) value) : (((Boolean) value).booleanValue());
        }
        else if (value.getClass() == char.class || value.getClass() == Character.class)
        {
            char charValue = (value.getClass().isPrimitive()) ? ((char) value) : (((Character) value).charValue());
            if (charValue == 'y' || charValue == 'Y' || charValue == '1')
            {
                return true;
            }
            else if (charValue == 'n' || charValue == 'N' || charValue == '0')
            {
                return false;
            }
            logParsingError(null, null, requestClass, value, defaultValue,
                    "[02] Cannot handle input value"
            );
            return defaultValue;
        }
        else if (value.getClass() == String.class)
        {
            return parseValue(null, null, (String) value, defaultValue);
        }
        logParsingError(null, null, requestClass, value, defaultValue,
                "[03] Cannot handle input value"
        );
        return defaultValue;
    }

    private static String normalizeString(String inputString)
    {
        return inputString.toLowerCase().trim();
    }

    @Override
    public void logParsingError(ConfigParameter configParameter, String configKey, Class<?> requestClass, Object value, Object defaultValue, String... message)
    {
        IFieldParser.innerLogger(ERROR, getClass(),
                "Cannot parse '" + ("Boolean") + ((requestClass == null) ? ("") : (" with requested class '" + requestClass.getSimpleName() + "'")) +"'. " +
                        ((configKey == null)    ? ("") : (  "Config key: '"       + configKey                       + "'. "))   +
                        ((value == null)        ? ("") : (  "Requested value: '"  + String.valueOf(value)           + "'. "))   +
                        (                                   "Used default value '"+ String.valueOf(defaultValue)    + "'. ")    +
                        (Utils.joinStrings(".\n", message)) +
                        (""),
                null);
    }
}
