package org.index.patchdownloader.util.parsers;

import org.index.patchdownloader.config.annotations.ConfigParameter;
import org.index.patchdownloader.util.Utils;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * @author Index
 */
public class NumberFloatFieldParser implements IFieldParser
{
    private final static BigDecimal BIG_DECIMAL_MIN_VALUE = new BigDecimal(Float.MIN_VALUE);
    private final static BigDecimal BIG_DECIMAL_MAX_VALUE = new BigDecimal(Float.MAX_VALUE);

    @Override
    public Object parseValue(ConfigParameter configParameter, Class<?> classType, String value, Object defaultValue, Object... args)
    {
        if (value == null || value.isEmpty() || value.isBlank())
        {
            logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), classType, value, defaultValue,
                    "[Input value] is null or [Input value] is not valid"
            );
            return defaultValue;
        }
        if (!isNumber(value))
        {
            logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), classType, value, defaultValue,
                    "[Input value] is not a number"
            );
            return defaultValue;
        }
        if (!isValidDigit(value))
        {
            return defaultValue;
        }
        return Float.parseFloat(value);
    }

    @Override
    public Object parseValueByObjectValue(Class<?> classType, Object value, Object defaultValue, Object... args)
    {
        if (value == null)
        {
            logParsingError(null, null, classType, value, defaultValue,
                    "[Input value] is null"
            );
            return defaultValue;
        }
        if (value instanceof String)
        {
            return parseValue(null, null, normalizeValue((String) value), defaultValue);
        }
        else if (value instanceof Number)
        {
            if (value instanceof BigDecimal)
            {
                BigDecimal bigDecimal = (BigDecimal) value;
                if ((bigDecimal.compareTo(BIG_DECIMAL_MAX_VALUE) > 0) || (bigDecimal.compareTo(BIG_DECIMAL_MIN_VALUE) < 0))
                {
                    logParsingError(null, null, classType, value, defaultValue,
                            "[Input value] is out of bounds"
                    );
                    return defaultValue;
                }
                return bigDecimal.floatValue();
            }
            else if (value instanceof BigInteger)
            {
                logParsingError(null, null, classType, value, defaultValue,
                        "[Input value] cannot parse from BigInteger"
                );
                return defaultValue;
            }
            else
            {
                return ((Number) value).floatValue();
            }
        }
        else if (value.getClass() == char.class || value.getClass() == Character.class)
        {
            char character = (char) value;
            if (!Character.isDigit(character))
            {
                logParsingError(null, null, classType, value, defaultValue,
                        "[Input value] is out of bounds"
                );
                return defaultValue;
            }
            return (float) Character.getNumericValue(character);
        }
        else if (value.getClass().isPrimitive())
        {
            Class<?> primitiveClass = value.getClass();
            if (primitiveClass == (byte.class))
            {
                return (float) ((byte) value);
            }
            else if (primitiveClass == (short.class))
            {
                return (float) ((short) value);
            }
            else if (primitiveClass == (int.class))
            {
                return (float) ((int) value);
            }
            else if (primitiveClass == (long.class))
            {
                long longValue = (long) value;
                if (longValue > Float.MAX_VALUE || longValue < Float.MIN_VALUE)
                {
                    logParsingError(null, null, classType, value, defaultValue,
                            "[Input value] is out of bounds"
                    );
                    return defaultValue;
                }
                return (float) longValue;
            }
            else if (primitiveClass == (float.class))
            {
                return (double) ((float) value);
            }
            else if (primitiveClass == (double.class))
            {
                double doubleValue = (double) value;
                if (doubleValue > Float.MAX_VALUE || doubleValue < Float.MIN_VALUE)
                {
                    logParsingError(null, null, classType, value, defaultValue,
                            "[Input value] is out of bounds"
                    );
                    return defaultValue;
                }
                return (float) doubleValue;
            }
        }
        logParsingError(null, null, classType, value, defaultValue,
                "Cannot parse value"
        );
        return defaultValue;
    }

    @Override
    public String replaceIllegalSymbols(String string)
    {
        return normalizeValue(string);
    }

    @Override
    public void logParsingError(ConfigParameter configParameter, String configKey, Class<?> requestClass, Object value, Object defaultValue, String... message)
    {
        IFieldParser.innerLogger(ERROR, getClass(),
                "Cannot parse '" + ("Float") + ((requestClass == null) ? ("") : (" with requested class '" + requestClass.getSimpleName() + "'")) +"'. " +
                        ((configKey == null)    ? ("") : (  "Config key: '"       + configKey                       + "'. "))   +
                        ((value == null)        ? ("") : (  "Requested value: '"  + String.valueOf(value)           + "'. "))   +
                        (                                   "Used default value '"+ String.valueOf(defaultValue)    + "'. ")    +
                        (Utils.joinStrings(".\n", message)) +
                        (""),
                null);
    }

    private static String normalizeValue(String inputString)
    {
        return inputString
                .replaceAll("\n", "")
                .replaceAll("\t", "")
                .replaceAll("\r", "")
                .replaceAll("_", "")
                .replaceAll(" ", "")
                .replaceAll("\0", "")
                .replaceAll("&nbsp","")

                .replaceAll(",", ".")

                .trim().strip();
    }

    private static boolean isNumber(String inputValue)
    {
        boolean isDotFound = false;
        for (int index = 0; index < inputValue.toCharArray().length; index++)
        {
            char requestCharacter = inputValue.charAt(index);
            if (index == 0 && requestCharacter == '-')
            {
                continue;
            }
            if (requestCharacter == '.')
            {
                if (index == 0)
                {
                    return false;
                }
                if (isDotFound)
                {
                    return false;
                }
                isDotFound = true;
                continue;
            }
            if (!Character.isDigit(requestCharacter))
            {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidDigit(String inputValue)
    {
        // TODO: FIND ANOTHER WAY TO VALIDATE
        try
        {
            Float.parseFloat(inputValue);
            return true;
        }
        catch (Exception e)
        {
            IFieldParser.innerLogger(ERROR, NumberFloatFieldParser.class,
                    "Input number not a valid! Value '" + inputValue + "'.", null);
        }
        return false;
    }

    @Override
    public boolean isDigitValue()
    {
        return true;
    }
}
