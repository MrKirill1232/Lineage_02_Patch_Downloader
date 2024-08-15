package org.index.patchdownloader.util.parsers;

import org.index.patchdownloader.config.annotations.ConfigParameter;
import org.index.patchdownloader.util.Utils;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * @author Index
 */
public class CharFieldParser implements IFieldParser
{
    private final static BigDecimal BIG_DECIMAL_MIN_VALUE = new BigDecimal(0);
    private final static BigDecimal BIG_DECIMAL_MAX_VALUE = new BigDecimal(9);
    private final static BigInteger BIG_INTEGER_MIN_VALUE = BigInteger.valueOf(0);
    private final static BigInteger BIG_INTEGER_MAX_VALUE = BigInteger.valueOf(9);

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
        if (value.toCharArray().length > 1)
        {
            logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), requestClass, value, defaultValue,
                    "[Input value] contains more than 1 characters"
            );
            return defaultValue;
        }
        return value.toCharArray()[0];
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
        if (value.getClass() == String.class)
        {
            return parseValue(null, null, (String) value, defaultValue);
        }
        if (value.getClass() == char.class || value.getClass() == Character.class)
        {
            return (value.getClass().isPrimitive()) ? ((char) value) : (((Character) value).charValue());
        }
//        if (value instanceof Number)
//        {
//            if (value instanceof BigDecimal)
//            {
//                BigDecimal bigDecimal = (BigDecimal) value;
//                if ((bigDecimal.compareTo(BIG_DECIMAL_MAX_VALUE) > 0) || (bigDecimal.compareTo(BIG_DECIMAL_MIN_VALUE) < 0))
//                {
//                    logParsingError(null, null, requestClass, value, defaultValue,
//                            "[Input value] is out of bounds"
//                    );
//                    return defaultValue;
//                }
//                return Character.toChars(bigDecimal.intValue())[0];
//            }
//            else if (value instanceof BigInteger)
//            {
//                BigInteger bigInteger = (BigInteger) value;
//                if ((bigInteger.compareTo(BIG_INTEGER_MAX_VALUE) > 0) || (bigInteger.compareTo(BIG_INTEGER_MIN_VALUE) < 0))
//                {
//                    logParsingError(null, null, requestClass, value, defaultValue,
//                            "[Input value] is out of bounds"
//                    );
//                    return defaultValue;
//                }
//                return Character.toChars(bigInteger.intValue())[0];
//            }
//            else
//            {
//                Number number = (Number) value;
//                long maxAvailableNumber = number.longValue();
//                if (maxAvailableNumber > 9 || maxAvailableNumber < 0)
//                {
//                    logParsingError(null, null, requestClass, value, defaultValue,
//                            "[Input value] is out of bounds"
//                    );
//                    return defaultValue;
//                }
//                return Character.toChars(number.intValue())[0];
//            }
//        }
//        if (value.getClass().isPrimitive())
//        {
//            Class<?> primitiveClass = value.getClass();
//            if (primitiveClass == (byte.class))
//            {
//                return (int) ((byte) value);
//            }
//            else if (primitiveClass == (short.class))
//            {
//                return (int) ((short) value);
//            }
//            else if (primitiveClass == (int.class))
//            {
//                return (int) (value);
//            }
//            else if (primitiveClass == (long.class))
//            {
//                long longValue = (long) value;
//                if (longValue > 9 || longValue < 0)
//                {
//                    logParsingError(null, null, requestClass, value, defaultValue,
//                            "[Input value] is out of bounds"
//                    );
//                    return defaultValue;
//                }
//                return (int) longValue;
//            }
//            else if (primitiveClass == (float.class))
//            {
//                return (int) ((float) value);
//            }
//            else if (primitiveClass == (double.class))
//            {
//                double doubleValue = (double) value;
//                if (doubleValue > Integer.MAX_VALUE || doubleValue < Integer.MIN_VALUE)
//                {
//                    logParsingError(null, null, requestClass, value, defaultValue,
//                            "[Input value] is out of bounds"
//                    );
//                    return defaultValue;
//                }
//                return (int) doubleValue;
//            }
//        }
        return defaultValue;
    }

    @Override
    public void logParsingError(ConfigParameter configParameter, String configKey, Class<?> requestClass, Object value, Object defaultValue, String... message)
    {
        IFieldParser.innerLogger(ERROR, getClass(),
                "Cannot parse '" + ("Character") + ((requestClass == null) ? ("") : (" with requested class '" + requestClass.getSimpleName() + "'")) +"'. " +
                        ((configKey == null)    ? ("") : (  "Config key: '"       + configKey                       + "'. "))   +
                        ((value == null)        ? ("") : (  "Requested value: '"  + String.valueOf(value)           + "'. "))   +
                        (                                   "Used default value '"+ String.valueOf(defaultValue)    + "'. ")    +
                        (Utils.joinStrings(".\n", message)) +
                        (""),
                null);
    }
}
