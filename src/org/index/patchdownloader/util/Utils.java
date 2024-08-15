package org.index.patchdownloader.util;

import java.lang.reflect.Array;

public class Utils
{
    private Utils()
    {

    }

    public static boolean checkByChar(String checkString, int requiredStringLength, int positionOfCharacter, char lookingCharacter)
    {
        return checkString != null && (requiredStringLength == -1 || checkString.length() == requiredStringLength) && (checkString.charAt(positionOfCharacter) == lookingCharacter);
    }

    public static String joinStrings(String glueStr, Object array)
    {
        return joinStrings(glueStr, array, 0, -1);
    }

    public static String joinStrings(String glueStr, Object array, int startIdx)
    {
        return joinStrings(glueStr, array, startIdx, -1);
    }

    public static String joinStrings(String glueStr, Object array, int startIdx, int maxCount)
    {
        if (array == null || !array.getClass().isArray() || glueStr == null || glueStr.isEmpty())
        {
            return "";
        }
        int length = Array.getLength(array);
        StringBuilder result = new StringBuilder();
        if (startIdx < 0)
        {
            startIdx += length;
            if (startIdx < 0)
            {
                return "";
            }
        }
        while(startIdx < length && maxCount != 0)
        {
            if(!result.isEmpty() && glueStr != null && !glueStr.isEmpty())
            {
                result.append(glueStr);
            }
            result.append(Array.get(array, startIdx++));
            maxCount--;
        }
        return result.toString();
    }

    public static int getDimensions(Object array)
    {
        int dimensions = 0;
        while (array.getClass().isArray())
        {
            dimensions++;
            array = Array.get(array, 0);
        }
        return dimensions;
    }
}
