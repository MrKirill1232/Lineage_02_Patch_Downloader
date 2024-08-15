package org.index.patchdownloader.util.parsers;

import org.index.patchdownloader.config.annotations.ConfigParameter;
import org.index.patchdownloader.util.Utils;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * @author Index
 */
public class FileFieldParser implements IFieldParser
{

    @Override
    public Object parseValue(ConfigParameter configParameter, Class<?> requestClass, String value, Object defaultValue, Object... args)
    {
        if (value == null)
        {
            logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), requestClass, value, defaultValue,
                    "[Input value] or [Request Class] is null"
            );
            return defaultValue;
        }
        try
        {
            return new File(value).getCanonicalFile();
        }
        catch (Exception e)
        {
            logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), requestClass, value, defaultValue,
                    "Cannot parse file."
            );
            return defaultValue;
        }
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
        if (value.getClass() == File.class)
        {
            return new File(((File) value).getPath());
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
                "Cannot parse '" + ("File") + ((requestClass == null) ? ("") : (" with requested class '" + requestClass.getSimpleName() + "'")) +"'. " +
                        ((configKey == null)    ? ("") : (  "Config key: '"       + configKey                       + "'. "))   +
                        ((value == null)        ? ("") : (  "Requested value: '"  + String.valueOf(value)           + "'. "))   +
                        (                                   "Used default value '"+ String.valueOf(defaultValue)    + "'. ")    +
                        (Utils.joinStrings(".\n", message)) +
                        (""),
                null);
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
                if (nextCharacter == '\\')
                {
                    index += bytesPerChar - 1;
                }
                buffer.put((byte) '/');
            }
            else
            {
                buffer.put((byte) character);
            }
        }
        return Arrays.copyOfRange(buffer.array(), 0, buffer.position());
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
