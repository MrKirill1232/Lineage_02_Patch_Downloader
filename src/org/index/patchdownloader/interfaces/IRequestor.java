package org.index.patchdownloader.interfaces;

public interface IRequestor
{
    void onDownload(IRequest request);

    void onDecode(IRequest request);

    void onStore(IRequest request);
}
