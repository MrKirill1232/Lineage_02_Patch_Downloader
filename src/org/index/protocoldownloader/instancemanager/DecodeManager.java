package org.index.protocoldownloader.instancemanager;

import org.index.protocoldownloader.enums.FileTypeByLink;
import org.index.protocoldownloader.model.requests.DecodeRequest;
import org.index.protocoldownloader.model.requests.DownloadRequest;
import org.tukaani.xz.LZMAInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class DecodeManager
{
    private final int _parallelDecodeCount;

    private final Queue<DecodeRequest> _requestQueue;

    private DecodeManager(int parallelDecodeCount)
    {
        _parallelDecodeCount = parallelDecodeCount;
        _requestQueue = new ConcurrentLinkedQueue<>();
    }

    public void addToDecode(DecodeRequest request)
    {
        _requestQueue.add(request);
    }

    public void startDecode()
    {
        DecodeRequest request = _requestQueue.poll();
        if (request == null)
        {
            return;
        }
        request.setDecodedArray(decode(request.getDownloadRequest()));
        request.onComplete();
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

        if (request.getLinkHolder().getTotalLength() == totalArrayLength)
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
