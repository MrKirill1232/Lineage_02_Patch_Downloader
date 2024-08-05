package org.index.patchdownloader.instancemanager;

import org.index.patchdownloader.interfaces.IRequest;
import org.index.patchdownloader.model.requests.DecodeRequest;
import org.index.patchdownloader.model.requests.DownloadRequest;
import org.tukaani.xz.LZMAInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class DecodeManager extends AbstractQueueManager
{
    private final static DecodeManager INSTANCE = new DecodeManager();

    public static DecodeManager getInstance()
    {
        return INSTANCE;
    }

    private DecodeManager()
    {

    }

    @Override
    public void runQueueEntry(int threadId)
    {
        IRequest request = _requestQueue.poll();
        if (request == null)
        {
            return;
        }
        if (!request.getClass().equals(DecodeRequest.class))
        {
            return;
        }
        DecodeRequest decodeRequest = (DecodeRequest) request;
        byte[] decoded = decode(decodeRequest.getDownloadRequest());
        decodeRequest.setDecodedArray(decoded);
        decodeRequest.onComplete();
    }

    public static byte[] decode(DownloadRequest request)
    {
        if (request == null || !request.isComplete())
        {
            return new byte[0];
        }

        byte[][] encodeArray = request.getDownloadedByteArray();

        int totalArrayLength = 0;
        for (int fIndex = 0; fIndex < encodeArray.length; fIndex++)
        {
            totalArrayLength += encodeArray[fIndex].length;
        }

        ByteBuffer encodeBuffer = ByteBuffer.allocate(totalArrayLength);

        for (int fIndex = 0; fIndex < encodeArray.length; fIndex++)
        {
            encodeBuffer.put(encodeArray[fIndex]);
        }

        if (request.getFileInfoHolder().getFileLength() == totalArrayLength)
        {
            return encodeBuffer.array();
        }

        if (getPropertiesByte(encodeArray[0]) > (4 * 5 + 4) * 9 + 8)
        {
            // look implementation on org/tukaani/xz/LZMAInputStream.initialize
            // int props = propsByte & 0xFF;
            // if (props > (4 * 5 + 4) * 9 + 8)
            //   throw new CorruptedInputException("Invalid LZMA properties byte");
            System.out.println("Cannot decode input array by LZMA method. Reason: " + "Properties byte is invalid. File '" + request.getLinkPath() + "';");
            return encodeBuffer.array();
        }
        if (true)
        {
            int dictionarySize = getDictionarySize(encodeArray[0]);
            // look implementation on org/tukaani/xz/LZMAInputStream.initialize
            // Validate the dictionary size since the other "initialize" throws
            // IllegalArgumentException if dictSize is not supported.
            if (dictionarySize < 0 || dictionarySize > LZMAInputStream.DICT_SIZE_MAX)
            {
                System.out.println("Cannot decode input array by LZMA method. Reason: " + "LZMA dictionary is too big for this implementation. File '" + request.getLinkPath() + "';");
                return encodeBuffer.array();
            }
        }

        int allocateBufferSize = getUnCompressSize(encodeArray[0]);
        if (allocateBufferSize <= 0 || allocateBufferSize == Integer.MAX_VALUE)
        {
            if (encodeArray.length > 1)
            {
                System.out.println("Cannot decode input array by LZMA method. Reason: " + "Uncompressed length is not correct! Next size found: " + allocateBufferSize + ". File '" + request.getLinkPath() + "';");
                return encodeBuffer.array();
            }
            // System.out.println("Cannot decode input array by LZMA method. Reason: " + "File is not a archive. File '" + request.getLinkPath() + "';");
            return encodeBuffer.array();
        }

        return decode(encodeBuffer.array());
    }

    public static byte[] decode(byte[] encodeArray)
    {
        try
        {
            LZMAInputStream archiveStream = new LZMAInputStream(new ByteArrayInputStream(encodeArray));

            ByteBuffer decodeBuffer = ByteBuffer.allocate(getUnCompressSize(encodeArray));

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

    private static int getUnCompressSize(byte[] input)
    {
        // readByte;        // Properties byte (lc, lp, and pb)
        // readUnsignedByte // Dictionary size is an unsigned 32-bit little endian integer.
        // readUnsignedByte // Dictionary size is an unsigned 32-bit little endian integer.
        // readUnsignedByte // Dictionary size is an unsigned 32-bit little endian integer.
        // readUnsignedByte // Dictionary size is an unsigned 32-bit little endian integer.
        long uncompSize = 0;
        for (int index = 0; index < 8; ++index)
        {
            uncompSize |= ((long) (input[index + 5] & 255)) << (8 * index);
        }
        return (int) Math.min(Integer.MAX_VALUE, uncompSize);
    }

}
