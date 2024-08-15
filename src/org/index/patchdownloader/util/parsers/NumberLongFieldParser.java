package org.index.patchdownloader.util.parsers;

import org.index.patchdownloader.config.annotations.ConfigParameter;
import org.index.patchdownloader.util.Utils;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * @author Index
 */
public class NumberLongFieldParser implements IFieldParser
{
    private final static BigDecimal BIG_DECIMAL_MIN_VALUE = new BigDecimal(Long.MIN_VALUE);
    private final static BigDecimal BIG_DECIMAL_MAX_VALUE = new BigDecimal(Long.MAX_VALUE);
    private final static BigInteger BIG_INTEGER_MIN_VALUE = BigInteger.valueOf(Long.MIN_VALUE);
    private final static BigInteger BIG_INTEGER_MAX_VALUE = BigInteger.valueOf(Long.MAX_VALUE);

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
            logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), classType, value, defaultValue,
                    "[Input value] is out of bounds"
            );
            return defaultValue;
        }
        return Long.parseLong(value);
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
                return bigDecimal.longValue();
            }
            else if (value instanceof BigInteger)
            {
                BigInteger bigInteger = (BigInteger) value;
                if ((bigInteger.compareTo(BIG_INTEGER_MAX_VALUE) > 0) || (bigInteger.compareTo(BIG_INTEGER_MIN_VALUE) < 0))
                {
                    logParsingError(null, null, classType, value, defaultValue,
                            "[Input value] is out of bounds"
                    );
                    return defaultValue;
                }
                return bigInteger.longValue();
            }
            else
            {
                return ((Number) value).longValue();
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
            return (long) Character.getNumericValue(character);
        }
        else if (value.getClass().isPrimitive())
        {
            Class<?> primitiveClass = value.getClass();
            if (primitiveClass == (byte.class))
            {
                return (long) ((byte) value);
            }
            else if (primitiveClass == (short.class))
            {
                return (long) ((short) value);
            }
            else if (primitiveClass == (int.class))
            {
                return (long) ((int) value);
            }
            else if (primitiveClass == (long.class))
            {
                return (long) value;
            }
            else if (primitiveClass == (float.class))
            {
                return (long) ((float) value);
            }
            else if (primitiveClass == (double.class))
            {
                return (long) ((double) value);
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
                "Cannot parse '" + ("Long") + ((requestClass == null) ? ("") : (" with requested class '" + requestClass.getSimpleName() + "'")) +"'. " +
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
                .trim().strip();
    }

    private static boolean isNumber(String inputValue)
    {
        for (int index = 0; index < inputValue.toCharArray().length; index++)
        {
            char requestCharacter = inputValue.charAt(index);
            if (index == 0 && requestCharacter == '-')
            {
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
        // 9223372036854775807
        // -9223372036854775808
        if (inputValue.length() < 19)
        {
            return true;
        }
        if (inputValue.length() > 19)
        {
            return false;
        }
        // -9223372036854775808
        if (inputValue.charAt(0) != '-')
        {
            return false;
        }
        BigDecimal bigDecimal = new BigDecimal(inputValue);
        if ((bigDecimal.compareTo(BIG_DECIMAL_MAX_VALUE) > 0) || (bigDecimal.compareTo(BIG_DECIMAL_MIN_VALUE) < 0))
        {
            return false;
        }
        return true;
    }

    @Override
    public boolean isDigitValue()
    {
        return true;
    }
}
