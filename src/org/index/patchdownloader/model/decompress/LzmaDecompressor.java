package org.index.patchdownloader.model.decompress;

import org.index.patchdownloader.interfaces.IDecompressor;
import org.index.patchdownloader.interfaces.IDummyLogger;
import org.index.patchdownloader.model.requests.DownloadRequest;
import org.tukaani.xz.LZMAInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class LzmaDecompressor implements IDummyLogger, IDecompressor
{

    @Override
    public byte[] decompress(DownloadRequest request, int totalArrayLength, byte[] compressDataArray)
    {
        try
        {
            LZMAInputStream archiveStream = new LZMAInputStream(new ByteArrayInputStream(compressDataArray));

            ByteBuffer decodeBuffer = ByteBuffer.allocate(getUnCompressSize(compressDataArray));

            while (true)
            {
                byte[] bytes = new byte[1024];
                int status = archiveStream.read(bytes);
                if (status == -1)
                {
                    break;
                }
                decodeBuffer.put(bytes, 0, status);
            }

            return decodeBuffer.array();
        }
        catch (IOException ioException)
        {
            ioException.printStackTrace();
        }
        return new byte[0];
    }

    private static int getPropertiesByte(byte[] input)
    {
        return input[0] & 0xFF;
    }

    private static int getDictionarySize(byte[] input)
    {
        // readByte;        // Properties byte (lc, lp, and pb)
        int dictSize = 0;
        for (int index = 0; index < 4; ++index)
        {
            dictSize |= (input[index + 1] & 255) << (8 * index);
        }
        return dictSize;
    }

    @Override
    public boolean check(DownloadRequest request, int totalArrayLength, byte[] compressDataArray)
    {
        if (request.getFileInfoHolder().getFileLength() == totalArrayLength)
        {
            return false;
        }

        if (getPropertiesByte(compressDataArray) > (4 * 5 + 4) * 9 + 8)
        {
            // look implementation on org/tukaani/xz/LZMAInputStream.initialize
            // int props = propsByte & 0xFF;
            // if (props > (4 * 5 + 4) * 9 + 8)
            //   throw new CorruptedInputException("Invalid LZMA properties byte");
            IDummyLogger.log(IDummyLogger.ERROR, getClass(), "Cannot decode input array by LZMA method. Reason: " + "Properties byte is invalid. File '" + request.getLinkPath() + "';", null);
            return false;
        }
        if (true)
        {
            int dictionarySize = getDictionarySize(compressDataArray);
            // look implementation on org/tukaani/xz/LZMAInputStream.initialize
            // Validate the dictionary size since the other "initialize" throws
            // IllegalArgumentException if dictSize is not supported.
            if (dictionarySize < 0 || dictionarySize > LZMAInputStream.DICT_SIZE_MAX)
            {
                IDummyLogger.log(IDummyLogger.ERROR, getClass(), "Cannot decode input array by LZMA method. Reason: " + "LZMA dictionary is too big for this implementation. File '" + request.getLinkPath() + "';", null);
                return false;
            }
        }

        int allocateBufferSize = getUnCompressSize(compressDataArray);
        if (allocateBufferSize <= 0 || allocateBufferSize == Integer.MAX_VALUE)
        {
            if (request.getDownloadedByteArray().length > 1)
            {
                IDummyLogger.log(IDummyLogger.ERROR, getClass(), "Cannot decode input array by LZMA method. Reason: " + "Uncompressed length is not correct! Next size found: " + allocateBufferSize + ". File '" + request.getLinkPath() + "';", null);
                return false;
            }
            // System.out.println("Cannot decode input array by LZMA method. Reason: " + "File is not a archive. File '" + request.getLinkPath() + "';");
            return false;
        }
        return true;
    }

    @Override
    public int getCompressSize(byte[] compressedDataArray)
    {
        return compressedDataArray.length;
    }

    @Override
    public int getUnCompressSize(byte[] compressedDataArray)
    {
        // readByte;        // Properties byte (lc, lp, and pb)
        // readUnsignedByte // Dictionary size is an unsigned 32-bit little endian integer.
        // readUnsignedByte // Dictionary size is an unsigned 32-bit little endian integer.
        // readUnsignedByte // Dictionary size is an unsigned 32-bit little endian integer.
        // readUnsignedByte // Dictionary size is an unsigned 32-bit little endian integer.
        long uncompSize = 0;
        for (int index = 0; index < 8; ++index)
        {
            uncompSize |= ((long) (compressedDataArray[index + 5] & 255)) << (8 * index);
        }
        return (int) Math.min(Integer.MAX_VALUE, uncompSize);
    }
}
