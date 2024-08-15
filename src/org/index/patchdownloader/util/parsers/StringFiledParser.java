package org.index.patchdownloader.util.parsers;

import org.index.patchdownloader.config.annotations.ConfigParameter;
import org.index.patchdownloader.util.Utils;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * @author Index
 */
public class StringFiledParser implements IFieldParser
{
    @Override
    public Object parseValue(ConfigParameter configParameter, Class<?> classType, String value, Object defaultValue, Object... args)
    {
        if (value == null)
        {
            logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), classType, value, defaultValue,
                    "[Input value] is null"
            );
            return defaultValue;
        }
        return value;
    }

    @Override
    public Object parseValueByObjectValue(Class<?> classType, Object value, Object defaultValue, Object... args)
    {
//        if (value instanceof String)
//        {
//            return (String) value;
//        }
//        else if (value instanceof CharSequence)
//        {
//            return ((CharSequence) value).toString();
//        }
//        else if (value instanceof StringJoiner)
//        {
//            return ((StringJoiner) value).toString();
//        }
        return String.valueOf(value);
    }

    public static String normalizeValue(String inputString)
    {
        return inputString
                .replaceAll("\n", "")
                .replaceAll("\t", " ")
                .replaceAll("\r", "")
                .replaceAll("\0", " ")
                .replaceAll("&nbsp"," ")
                .trim().strip();
    }

    @Override
    public byte[] replaceIllegalSymbols(int bytesPerChar, byte[] input)
    {
        ByteBuffer buffer = ByteBuffer.allocate(input.length * 2);
        for (int index = 0; index < input.length; index++)
        {
            char character = getCharFromByteArray(bytesPerChar, index, input);
            index += bytesPerChar - 1;
            if (character == '\\')
            {
                char nextCharacter = getCharFromByteArray(bytesPerChar, index, input);
                if (nextCharacter != '\\')
                {
                    buffer.put((byte) '\\');
                    buffer.put((byte) '\\');
                }
                else
                {
                    index += bytesPerChar - 1;
                }
            }
            else
            {
                buffer.put((byte) character);
            }
        }
        return Arrays.copyOfRange(buffer.array(), 0, buffer.position());
    }

    @Override
    public void logParsingError(ConfigParameter configParameter, String configKey, Class<?> requestClass, Object value, Object defaultValue, String... message)
    {
        IFieldParser.innerLogger(ERROR, getClass(),
            "Cannot parse '" + ("String") + ((requestClass == null) ? ("") : (" with requested class '" + requestClass.getSimpleName() + "'")) +"'. " +
                    ((configKey == null)    ? ("") : (  "Config key: '"       + configKey                       + "'. "))   +
                    ((value == null)        ? ("") : (  "Requested value: '"  + String.valueOf(value)           + "'. "))   +
                    (                                   "Used default value '"+ String.valueOf(defaultValue)    + "'. ")    +
                    (Utils.joinStrings(".\n", message)) +
                    (""),
            null);
    }

    private static char getCharFromByteArray(int bytesPerChar, int offset, byte... inputByteArray)
    {
        if (inputByteArray == null || inputByteArray.length == 0)
        {
            return Character.MIN_VALUE;
        }
        if (bytesPerChar == Byte.BYTES)
        {
            return (char) inputByteArray[offset];
        }
        else
        {
            ByteBuffer byteBuffer = ByteBuffer.allocate(bytesPerChar);
            for (int index = 0; index < bytesPerChar; index++)
            {
                byteBuffer.put(inputByteArray[offset + index]);
            }
            if (bytesPerChar == Short.BYTES)
            {
                return (char) Short.reverseBytes(byteBuffer.getShort(0));
            }
        }
        return Character.MIN_VALUE;
    }
}
