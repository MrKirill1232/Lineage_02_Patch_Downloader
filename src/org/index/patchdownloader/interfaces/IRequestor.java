package org.index.patchdownloader.interfaces;

public interface IRequestor
{
    void onDownload(IRequest request);

    void onDecompress(IRequest request);

    void onStore(IRequest request);
}
