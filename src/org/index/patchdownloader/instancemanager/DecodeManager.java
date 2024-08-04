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
    public void runQueueEntry()
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
        decodeRequest.setDecodedArray(decode(decodeRequest.getDownloadRequest()));
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

        if (request.getLinkHolder().getOriginalFileLength() == totalArrayLength)
        {
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
