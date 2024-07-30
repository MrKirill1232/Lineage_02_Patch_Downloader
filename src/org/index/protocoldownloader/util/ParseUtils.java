package org.index.protocoldownloader.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * @author Index
 */
public class ParseUtils
{
    public static String parseString(Object lookingObject)
    {
        return parseString(lookingObject, null);
    }

    public static String parseString(Object lookingObject, String defaultValue)
    {
        if (lookingObject == null)
        {
            return defaultValue;
        }
        try
        {
            if (lookingObject instanceof final String inputString)
            {
                return inputString;
            }
            else if (lookingObject instanceof final CharSequence inputCharSequence)
            {
                return inputCharSequence.toString();
            }
            else if (lookingObject instanceof final StringJoiner inputJoiner)
            {
                return inputJoiner.toString();
            }
            return String.valueOf(lookingObject);
        }
        catch (Exception e)
        {
            return defaultOrThrow("String value required, but found: ", lookingObject, defaultValue);
        }
    }

    public static boolean parseBoolean(Object lookingObject)
    {
        return parseBoolean(lookingObject, null);
    }

    public static boolean parseBoolean(Object lookingObject, Boolean defaultValue)
    {
        try
        {
            if (lookingObject instanceof final Boolean booleanValue)
            {
                return booleanValue.booleanValue();
            }
            final String lookingObjectAsString = normilizeString(String.valueOf(lookingObject));
            if (lookingObjectAsString.equals("0"))
            {
                return false;
            }
            else if (lookingObjectAsString.equals("1"))
            {
                return true;
            }
            else if (lookingObjectAsString.equals("false"))
            {
                return false;
            }
            else if (lookingObjectAsString.equals("true"))
            {
                return true;
            }
            else
            {
                return defaultOrThrow("Boolean value required, but found: ", lookingObject, defaultValue);
            }
        }
        catch (Exception e)
        {
            return defaultOrThrow("Boolean value required, but found: ", lookingObject, defaultValue);
        }
    }

    public static int parseInteger(Object lookingObject)
    {
        return parseInteger(lookingObject, null);
    }

    public static int parseInteger(Object lookingObject, Integer defaultValue)
    {
        try
        {
            if (lookingObject instanceof final Byte byteValue)
            {
                return byteValue.intValue();
            }
            else if (lookingObject instanceof final Short shortValue)
            {
                return shortValue.intValue();
            }
            else if (lookingObject instanceof final Integer integerValue)
            {
                return integerValue.intValue();
            }
            final String lookingObjectAsString = normilizeString(String.valueOf(lookingObject));
            return Integer.parseInt(lookingObjectAsString);
        }
        catch (Exception e)
        {
            return defaultOrThrow("Integer value required, but found: ", lookingObject, defaultValue);
        }
    }

    public static long parseLong(Object lookingObject)
    {
        return parseLong(lookingObject, null);
    }

    public static long parseLong(Object lookingObject, Long defaultValue)
    {
        try
        {
            if (lookingObject instanceof final Byte byteValue)
            {
                return byteValue.longValue();
            }
            else if (lookingObject instanceof final Short shortValue)
            {
                return shortValue.longValue();
            }
            else if (lookingObject instanceof final Integer integerValue)
            {
                return integerValue.longValue();
            }
            else if (lookingObject instanceof final Long longValue)
            {
                return longValue.longValue();
            }
            final String lookingObjectAsString = normilizeString(String.valueOf(lookingObject));
            return Long.parseLong(lookingObjectAsString);
        }
        catch (Exception e)
        {
            return defaultOrThrow("Long value required, but found: ", lookingObject, defaultValue);
        }
    }

    public static float parseFloat(Object lookingObject)
    {
        return parseFloat(lookingObject, null);
    }

    public static float parseFloat(Object lookingObject, Float defaultValue)
    {
        try
        {
            if (lookingObject instanceof final Float floatValue)
            {
                return floatValue;
            }
            else if (lookingObject instanceof final Double doubleValue)
            {
                if (defaultValue > Float.MAX_VALUE)
                {
                    throw new IllegalArgumentException("Float value required, but found double: " + String.valueOf(lookingObject));
                }
                return doubleValue.floatValue();
            }
            final BigDecimal bigDecimalValue = parseValue(lookingObject, (defaultValue == null ? null : new BigDecimal(defaultValue)));
            if (bigDecimalValue.doubleValue() > Float.MAX_VALUE)
            {
                throw new IllegalArgumentException("Float value required, but found double: " + String.valueOf(lookingObject));
            }
            return bigDecimalValue.floatValue();
        }
        catch (Exception e)
        {
            return defaultOrThrow("Float value required, but found: ", lookingObject, defaultValue);
        }
    }

    public static double parseDouble(Object lookingObject)
    {
        return parseDouble(lookingObject, null);
    }

    public static double parseDouble(Object lookingObject, Double defaultValue)
    {
        try
        {
            if (lookingObject instanceof final Float floatValue)
            {
                return floatValue;
            }
            else if (lookingObject instanceof final Double doubleValue)
            {
                return doubleValue;
            }
            final BigDecimal bigDecimalValue = parseValue(lookingObject, (defaultValue == null ? null : new BigDecimal(defaultValue)));
            return bigDecimalValue.doubleValue();
        }
        catch (Exception e)
        {
            return defaultOrThrow("Double value required, but found: ", lookingObject, defaultValue);
        }
    }

    public static BigDecimal parseValue(Object lookingObject, BigDecimal defaultValue)
    {
        try
        {
            if (lookingObject instanceof final BigDecimal bigDecimal)
            {
                return bigDecimal;
            }
            else if (lookingObject instanceof final BigInteger bigInteger)
            {
                return new BigDecimal(bigInteger);
            }
            final String lookingObjectAsString = normilizeString(String.valueOf(lookingObject)).replaceAll(",", ".");
            return new BigDecimal(lookingObjectAsString);
        }
        catch (Exception e)
        {
            return defaultOrThrow("Big Decimal value required, but found: ", lookingObject, defaultValue);
        }
    }

    public static String[] parseStringArray(Object lookingObject, String[] defaultValue)
    {
        try
        {
            if (lookingObject.getClass() == String.class)
            {
                String lookingObjectAsString = normilizeString(String.valueOf(lookingObject), false, false);
                if (lookingObjectAsString.startsWith("[") && lookingObjectAsString.endsWith("]"))
                {
                    lookingObjectAsString = lookingObjectAsString.substring(1, lookingObjectAsString.length() - 1);
                }
                return lookingObjectAsString.replaceAll(";", ",").split(",");
            }
            if (lookingObject instanceof final String[] inputArray)
            {
                return inputArray;
            }
            if (lookingObject instanceof final Object[] inputArray)
            {
                final String[] outputArray = new String[inputArray.length];
                for (int index = 0; index < outputArray.length; index++)
                {
                    outputArray[index] = String.valueOf(inputArray[index]);
                }
                return outputArray;
            }
            if (lookingObject instanceof final Collection<?> inputList)
            {
                final String[] outputArray = new String[inputList.size()];
                int counter = 0;
                for (Object listElement : inputList)
                {
                    outputArray[counter++] = String.valueOf(listElement);
                }
                return outputArray;
            }
            return defaultOrThrow("String array required, but found: ", lookingObject, defaultValue);
        }
        catch (Exception e)
        {
            return defaultOrThrow("String array required, but found: ", lookingObject, defaultValue);
        }
    }

    public static <T extends Enum<T>> T parseEnum(Object lookingObject, T defaultValue, Class<T> enumClass)
    {
        try
        {
            final String lookingObjectAsString = String.valueOf(lookingObject);
            return Enum.valueOf(enumClass, lookingObjectAsString);
        }
        catch (Exception ignored)
        {
            return defaultValue;
        }
    }

    public static int[] parseIntArray(Object lookingObject, int[] defaultValue)
    {
        final String[] splited = parseStringArray(lookingObject, null);
        if (splited == null)
        {
            return defaultOrThrow("Integer array required, but found: ", lookingObject, defaultValue);
        }
        final int[] returnArray = new int[splited.length];
        for (int index = 0; index < splited.length; index++)
        {
            int value;
            try
            {
                value = parseInteger(splited[index], null);
            }
            catch (IllegalArgumentException npe)
            {
                continue;
            }
            returnArray[index] = value;
        }
        return returnArray;
    }

    private static <T> T defaultOrThrow(String throwString, Object lookingObject, T defaultValue)
    {
        if (defaultValue == null)
        {
            throw new IllegalArgumentException(throwString + String.valueOf(lookingObject));
        }
        return defaultValue;
    }

    public static String normilizeString(String inputString)
    {
        return normilizeString(inputString, true, true);
    }

    public static String normilizeString(String inputString, boolean replaceSpace, boolean toLowerCase)
    {
        if (inputString == null || inputString.isEmpty() || inputString.isBlank())
        {
            return "";
        }
        final String returnString = inputString
                .replaceAll("\n", "")
                .replaceAll("\t", "")
                .replaceAll("\r", "")
                .replaceAll("_", "")
                .replaceAll("-", "")
                .replaceAll(replaceSpace ? " " : "", "")
                .replaceAll("\0", replaceSpace ? "" : " ")   // replace all null values
                .replaceAll("&nbsp", replaceSpace ? "" : " ")
                .trim().strip();
        return toLowerCase
                ? returnString.toLowerCase(Locale.ROOT)
                : returnString;
    }
}
