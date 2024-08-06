package org.index.patchdownloader.instancemanager;

import org.index.patchdownloader.enums.ArchiveType;
import org.index.patchdownloader.interfaces.IDecompressor;
import org.index.patchdownloader.interfaces.IRequest;
import org.index.patchdownloader.model.decompress.LzmaDecompressor;
import org.index.patchdownloader.model.decompress.ZipDecompressor;
import org.index.patchdownloader.model.requests.DecompressRequest;
import org.index.patchdownloader.model.requests.DownloadRequest;

import java.nio.ByteBuffer;

public class DecompressManager extends AbstractQueueManager
{
    private final static DecompressManager INSTANCE = new DecompressManager();

    public static DecompressManager getInstance()
    {
        return INSTANCE;
    }

    private final static IDecompressor[] DECOMPRESSOR_CLASSES = new IDecompressor[ArchiveType.values().length];
    static
    {
        DECOMPRESSOR_CLASSES[ArchiveType.LZMA_ARCHIVE.ordinal()] = new LzmaDecompressor();
        DECOMPRESSOR_CLASSES[ArchiveType.ZIP_ARCHIVE.ordinal()] = new ZipDecompressor();
    }


    private DecompressManager()
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
        if (!request.getClass().equals(DecompressRequest.class))
        {
            return;
        }
        DecompressRequest decompressRequest = (DecompressRequest) request;
        byte[] decompressed = decompress(decompressRequest.getDownloadRequest());
        decompressRequest.setDecompressArray(decompressed);
        decompressRequest.onComplete();
    }

    public static byte[] decompress(DownloadRequest request)
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

        ByteBuffer compressedDataBuffer = ByteBuffer.allocate(totalArrayLength);

        for (int fIndex = 0; fIndex < encodeArray.length; fIndex++)
        {
            compressedDataBuffer.put(encodeArray[fIndex]);
        }

        IDecompressor decompressor = DECOMPRESSOR_CLASSES[request.getFileInfoHolder().getCompressType().ordinal()];
        if (decompressor == null || !decompressor.check(request, totalArrayLength, compressedDataBuffer.array()))
        {
            return compressedDataBuffer.array();
        }
        return decompressor.decompress(request, totalArrayLength, compressedDataBuffer.array());
    }
}
