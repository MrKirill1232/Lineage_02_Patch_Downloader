package org.index.patchdownloader.util.parsers;

import org.index.patchdownloader.config.annotations.ConfigParameter;
import org.index.patchdownloader.util.Utils;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * @author Index
 */
public class PatternFieldParser implements IFieldParser
{
    private static final Pattern DUMMY_PATTERN = Pattern.compile(".*");

    @Override
    public Object parseValue(ConfigParameter configParameter, Class<?> requestClass, String value, Object defaultValue, Object... args)
    {
        if (value == null || value.isEmpty() || value.isBlank())
        {
            logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), requestClass, value, defaultValue,
                    "[Input value] is null or [Input value] is not valid"
            );
            return defaultValue;
        }
        if (DUMMY_PATTERN.pattern().equals(value))
        {
            return DUMMY_PATTERN;
        }
        if (defaultValue instanceof Pattern)
        {
            Pattern defaultPattern = (Pattern) defaultValue;
            if (defaultPattern.pattern().equals(value))
            {
                return defaultPattern;
            }
        }
        try
        {
            return Pattern.compile(value);
        }
        catch (PatternSyntaxException e)
        {
            logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), requestClass, value, defaultValue,
                    "Error while compiling a pattern"
            );
        }
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
        if (value instanceof String)
        {
            return parseValue(null, null, (String) value, defaultValue);
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
            "Cannot parse '" + ("Pattern") + ((requestClass == null) ? ("") : (" with requested class '" + requestClass.getSimpleName() + "'")) +"'. " +
                    ((configKey == null)    ? ("") : (  "Config key: '"       + configKey                       + "'. "))   +
                    ((value == null)        ? ("") : (  "Requested value: '"  + String.valueOf(value)           + "'. "))   +
                    (                                   "Used default value '"+ String.valueOf(defaultValue)    + "'. ")    +
                    (Utils.joinStrings(".\n", message)) +
                    (""),
            null);
    }
}
