package org.index.patchdownloader.model.decompress;

import org.index.patchdownloader.interfaces.IDecompressor;
import org.index.patchdownloader.model.requests.DownloadRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipDecompressor implements IDecompressor
{

    @Override
    public byte[] decompress(DownloadRequest request, int totalArrayLength, byte[] compressDataArray)
    {
        try
        {
            ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(compressDataArray));
            ZipEntry zipEntry = zipInputStream.getNextEntry();
            ByteBuffer decodeBuffer = ByteBuffer.allocate((int) zipEntry.getSize());

            while (true)
            {
                byte[] bytes = new byte[1024];
                int status = zipInputStream.read(bytes);
                if (status == -1)
                {
                    break;
                }
                decodeBuffer.put(bytes, 0, status);
            }

            if (zipInputStream.getNextEntry() != null)
            {
                System.out.println("WARNING! UNSUPPORTED MULTIPLE ENTRY INSIDE FILE. DOWNLOADING URL: " + request.getFileInfoHolder().getAccessLink().getAccessLink() + " ;");
            }

            return decodeBuffer.array();
        }
        catch (IOException ioException)
        {
            ioException.printStackTrace();
        }

        return new byte[0];
    }

    @Override
    public boolean check(DownloadRequest request, int totalArrayLength, byte[] compressDataArray)
    {
        int allocateBufferSize = getUnCompressSize(compressDataArray);
        if (allocateBufferSize <= 0 || allocateBufferSize == Integer.MAX_VALUE)
        {
            if (request.getDownloadedByteArray().length > 1)
            {
                System.out.println("Cannot decode input array by ZIP method. Reason: " + "Uncompressed length is not correct! Next size found: " + allocateBufferSize + ". File '" + request.getLinkPath() + "';");
                return false;
            }
            // System.out.println("Cannot decode input array by ZIP method. Reason: " + "File is not a archive. File '" + request.getLinkPath() + "';");
            return false;
        }
        return true;
    }

    @Override
    public int getCompressSize(byte[] compressedDataArray)
    {
        // java.util.zip.ZipConstants = static final int LOCSIZ = 18;

        long uncompSize = 0;
        uncompSize |= (compressedDataArray[18] & 0xff) | ((compressedDataArray[18 + 1] & 0xff) << 8);
        uncompSize |= ((long) (compressedDataArray[18 + 2] & 0xff) | ((compressedDataArray[18 + 3] & 0xff) << 8)) << 16;
        uncompSize &= 0xffffffffL;
        return (int) Math.min(Integer.MAX_VALUE, uncompSize);
    }

    @Override
    public int getUnCompressSize(byte[] compressedDataArray)
    {
        // java.util.zip.ZipConstants = static final int LOCLEN = 22;

        long uncompSize = 0;
        uncompSize |= (compressedDataArray[22] & 0xff) | ((compressedDataArray[22 + 1] & 0xff) << 8);
        uncompSize |= ((long) (compressedDataArray[22 + 2] & 0xff) | ((compressedDataArray[22 + 3] & 0xff) << 8)) << 16;
        uncompSize &= 0xffffffffL;
        return (int) Math.min(Integer.MAX_VALUE, uncompSize);
    }
}
