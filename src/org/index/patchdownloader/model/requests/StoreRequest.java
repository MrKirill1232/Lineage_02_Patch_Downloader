package org.index.patchdownloader.model.requests;

import org.index.patchdownloader.interfaces.IRequest;
import org.index.patchdownloader.interfaces.IRequestor;
import org.index.patchdownloader.model.holders.FileInfoHolder;

public class StoreRequest implements IRequest
{
    private final IRequestor        _requestor;
    private final DownloadRequest   _downloadRequest;
    private final byte[]            _storableByteArray;

    public StoreRequest(IRequestor requestor, DownloadRequest downloadRequest, byte[] storableByteArray)
    {
        _requestor          = requestor;
        _downloadRequest    = downloadRequest;
        _storableByteArray  = storableByteArray;
    }

    public DownloadRequest getDownloadRequest()
    {
        return _downloadRequest;
    }

    public byte[] getStorableByteArray()
    {
        return _storableByteArray;
    }

    @Override
    public void onComplete()
    {
        if (_requestor != null)
        {
            _requestor.onStore(this);
        }
    }

    @Override
    public FileInfoHolder getFileInfoHolder()
    {
        return getDownloadRequest() == null ? null : getDownloadRequest().getFileInfoHolder();
    }
}
