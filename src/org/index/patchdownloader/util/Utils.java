package org.index.patchdownloader.util;

public class Utils
{
    private Utils()
    {

    }

    public static boolean checkByChar(String checkString, int requiredStringLength, int positionOfCharacter, char lookingCharacter)
    {
        return checkString != null && (requiredStringLength == -1 || checkString.length() == requiredStringLength) && (checkString.charAt(positionOfCharacter) == lookingCharacter);
    }
}
