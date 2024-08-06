package org.index.patchdownloader.interfaces;

import org.index.patchdownloader.model.requests.DownloadRequest;

public interface IDecompressor
{
    byte[] decompress(DownloadRequest request, int totalArrayLength, byte[] compressDataArray);

    boolean check(DownloadRequest request, int totalArrayLength, byte[] compressDataArray);

    int getCompressSize(byte[] compressedDataArray);

    int getUnCompressSize(byte[] compressedDataArray);
}
