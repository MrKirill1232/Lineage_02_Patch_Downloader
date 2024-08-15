package org.index.patchdownloader.config.parseutils;

import java.io.Closeable;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Index
 */
public class PropertiesReader implements Closeable
{
    private final   File                _configFile     ;
    private         List<byte[]>        _readLines      ;
    private         Map<String, byte[]> _keyAndByteValue;

    private final static int _bytesPerChar = 1;

    public PropertiesReader(File configFile)
    {
        _configFile     = configFile;
    }

    public void load()
    {
        readLinesInFile();
        assignKeyIntoMap();
    }

    private void readLinesInFile()
    {
        _readLines = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(_configFile, "r"))
        {
            RAFReadParameters parameters = new RAFReadParameters();
            ByteBuffer buffer = ByteBuffer.allocate((int) raf.length());
            byte[] dummyArray = new byte[_bytesPerChar];
            while (raf.read(dummyArray) != -1)
            {
                checkNextAvailableArray(parameters, buffer, dummyArray);
            }
            if (buffer.position() != 0)
            {
                addByteBufferAsArrayIntoList(_readLines, buffer, _bytesPerChar);
                clearBuffer(parameters, buffer);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private void checkNextAvailableArray(RAFReadParameters parameters, ByteBuffer buffer, byte[] inputByteArray)
    {
        char lookingChar = getCharFromByteArray(_bytesPerChar, 0, inputByteArray);
        if ((buffer.position() == 0 || buffer.position() == parameters.getEndOfLinePosition()) && isCommentChar(lookingChar))
        {
            parameters.setComment(true);
        }
        if (parameters.isComment() && parameters.isJumpOnNextLine())
        {
            addByteBufferAsArrayIntoList(_readLines, buffer, _bytesPerChar);
            clearBuffer(parameters, buffer);
            parameters.setComment(true);
            return;
        }
        if (parameters.isComment() && !isEndOfLineChar(lookingChar))
        {
            return;
        }
        if (isAppendNextLineChar(lookingChar))
        {
            parameters.setJumpOnNextLineCharPosition(buffer.position());
        }
        if (isEndOfLineChar(lookingChar))
        {
            if (parameters.isComment())
            {
                clearBuffer(parameters, buffer);
                return;
            }

            if (parameters.getJumpOnNextLineCharPosition() != -1)
            {
                char prevChar = getCharFromBuffer(buffer, _bytesPerChar, buffer.position() - _bytesPerChar);
                if (isAppendNextLineChar(prevChar))
                {
                    parameters.setJumpOnNextLine(true);
                    buffer.position(buffer.position() - _bytesPerChar);
                }
            }
            parameters.setJumpOnNextLineCharPosition(-1);
            parameters.setEndOfLinePosition(buffer.position());
        }
        if (!isIgnoredCharacterByCondition(parameters, buffer, lookingChar))
        {
            buffer.put(inputByteArray);
        }
        if (parameters.getEndOfLinePosition() == buffer.position() && !parameters.isJumpOnNextLine())
        {
            addByteBufferAsArrayIntoList(_readLines, buffer, _bytesPerChar);
            clearBuffer(parameters, buffer);
        }
    }

    private static void clearBuffer(RAFReadParameters parameters, ByteBuffer buffer)
    {
        parameters.setComment(false);
        parameters.setJumpOnNextLine(false);
        parameters.setJumpOnNextLineCharPosition(-1);
        parameters.setEndOfLinePosition(-1);
        buffer.clear();
    }

    private static void addByteBufferAsArrayIntoList(List<byte[]> lines, ByteBuffer buffer, int bytesPerChar)
    {
        if (buffer == null || buffer.position() == 0)
        {
            return;
        }
        lines.add(Arrays.copyOfRange(buffer.array(), 0, buffer.position()));
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

    private static char getCharFromBuffer(ByteBuffer buffer, int bytesPerChar, int offset)
    {
        if (buffer == null || buffer.position() == 0 || bytesPerChar < 0 || buffer.position() < bytesPerChar || offset < 0 || buffer.position() < offset)
        {
            return Character.MIN_VALUE;
        }
        if (bytesPerChar == Byte.BYTES)
        {
            return (char) buffer.get(offset);
        }
        else
        {
            ByteBuffer byteBuffer = ByteBuffer.allocate(bytesPerChar);
            for (int index = 0; index < bytesPerChar; index++)
            {
                byteBuffer.put(buffer.get(offset + index));
            }
            if (bytesPerChar == Short.BYTES)
            {
                return (char) Short.reverseBytes(byteBuffer.getShort(0));
            }
        }
        return Character.MIN_VALUE;
    }

    private static boolean isComment(ByteBuffer buffer, int bytesPerChar)
    {
        return buffer.position() > bytesPerChar && (getCharFromBuffer(buffer, bytesPerChar, (buffer.position() - bytesPerChar)) == '#');
    }

    private static boolean isCommentChar(char lookingCharacter)
    {
        return lookingCharacter == '#';
    }

    private static boolean isEndOfLineChar(char lookingCharacter)
    {
        return lookingCharacter == '\n';
    }

    private static boolean isAppendNextLineChar(char lookingCharacter)
    {
        // '\'
        return lookingCharacter == '\\';
    }

    private static boolean isIgnoredCharacter(char lookingCharacter)
    {
        return lookingCharacter == '\r' || lookingCharacter == '\n';
    }

    private static boolean isIgnoredCharacterByCondition(RAFReadParameters parameters, ByteBuffer buffer, char lookingCharacter)
    {
        if (lookingCharacter == '\\')
        {
            if (parameters.getEndOfLinePosition() == buffer.position())
            {
                return true;
            }
        }
        return isIgnoredCharacter(lookingCharacter);
    }

    private void assignKeyIntoMap()
    {
        if (_readLines == null)
        {
            _keyAndByteValue = Collections.emptyMap();
            return;
        }
        _keyAndByteValue = new HashMap<>();
        for (byte[] array : _readLines)
        {
            CharBuffer charBuffer = CharBuffer.allocate(array.length);
            for (int index = 0; index < array.length; index++)
            {
                char character = getCharFromByteArray(_bytesPerChar, index, array);
                index += _bytesPerChar - 1;
                if (character == '=')
                {
                    break;
                }
                charBuffer.put(character);
            }
            String key = new String(charBuffer.array()).trim();
            byte[] val = Arrays.copyOfRange(array, (charBuffer.position() * _bytesPerChar + 1), array.length);
            _keyAndByteValue.put(key, val);
        }
    }

    private static String getStringAsBuilder(byte[] inputArray, int bytesPerChar)
    {
        StringBuilder builder = new StringBuilder(inputArray.length);
        for (int index = 0; index < inputArray.length; index++)
        {
            char character = getCharFromByteArray(bytesPerChar, index, inputArray);
            index += bytesPerChar - 1;
            if (character == '\\')
            {
                builder.append("\\\\");
            }
            else
            {
                builder.append(character);
            }
        }
        return builder.toString();
    }

    private static String getStringAsCharBuffer(byte[] inputArray, int bytesPerChar)
    {
        CharBuffer charBuffer = CharBuffer.allocate(inputArray.length);
        for (int index = 0; index < inputArray.length; index++)
        {
            char character = getCharFromByteArray(bytesPerChar, index, inputArray);
            index += bytesPerChar - 1;
            charBuffer.put(character);
        }
        return new String(Arrays.copyOfRange(charBuffer.array(), 0, charBuffer.position()));
    }

    public File getConfigFile()
    {
        return _configFile;
    }

    public int getBytesPerChar()
    {
        return _bytesPerChar;
    }

    public List<byte[]> getReadLines()
    {
        return _readLines == null ? Collections.emptyList() : _readLines;
    }

    public Map<String, byte[]> getKeyAndByteValue()
    {
        return _keyAndByteValue == null ? Collections.emptyMap() : _keyAndByteValue;
    }

    @Override
    public void close()
    {
        if (_readLines != null)
        {
            _readLines.clear();
        }
        _readLines = null;
        if (_keyAndByteValue != null)
        {
            _keyAndByteValue.clear();
        }
        _keyAndByteValue = null;
    }

    private final static class RAFReadParameters
    {
        private boolean _isComment;
        private boolean _isJumpOnNextLine;
        private int     _jumpOnNextLineCharPosition;
        private int     _endOfLinePosition;

        private RAFReadParameters()
        {
            _isComment = false;
            _jumpOnNextLineCharPosition = -1;
            _endOfLinePosition = -1;
        }

        public boolean isComment()
        {
            return _isComment;
        }

        public void setComment(boolean isComment)
        {
            _isComment = isComment;
        }

        public boolean isJumpOnNextLine()
        {
            return _isJumpOnNextLine;
        }

        public void setJumpOnNextLine(boolean isJumpOnNextLine)
        {
            _isJumpOnNextLine = isJumpOnNextLine;
        }

        public int getJumpOnNextLineCharPosition()
        {
            return _jumpOnNextLineCharPosition;
        }

        public void setJumpOnNextLineCharPosition(int jumpOnNextLineCharPosition)
        {
            _jumpOnNextLineCharPosition = jumpOnNextLineCharPosition;
        }

        public int getEndOfLinePosition()
        {
            return _endOfLinePosition;
        }

        public void setEndOfLinePosition(int endOfLinePosition)
        {
            _endOfLinePosition = endOfLinePosition;
        }
    }
}
