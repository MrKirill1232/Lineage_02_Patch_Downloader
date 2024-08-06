package org.index.patchdownloader.model.requests;

import org.index.patchdownloader.interfaces.IRequest;
import org.index.patchdownloader.interfaces.IRequestor;
import org.index.patchdownloader.model.holders.FileInfoHolder;

public class DecompressRequest implements IRequest
{
    private final IRequestor _requestor;
    private final DownloadRequest _downloadRequest;
    private final byte[][] _encodeArray;

    private byte[] _decompressArray;
    private boolean _completed;

    public DecompressRequest(IRequestor requestor, DownloadRequest request)
    {
        _requestor = requestor;
        _downloadRequest = request;
        _encodeArray = null;
    }

    public DecompressRequest(IRequestor requestor, byte[][] encodeArray)
    {
        _requestor = requestor;
        _downloadRequest = null;
        _encodeArray = encodeArray;
    }

    public boolean isCompleted()
    {
        return _completed;
    }

    public byte[][] getEncodeArray()
    {
        return (_downloadRequest == null) ? _encodeArray : (_downloadRequest.getDownloadedByteArray());
    }

    public void setDecompressArray(byte[] decompressArray)
    {
        _decompressArray = decompressArray;
    }

    public byte[] getDecompressArray()
    {
        return _decompressArray;
    }

    public DownloadRequest getDownloadRequest()
    {
        return _downloadRequest;
    }

    @Override
    public void onComplete()
    {
        _completed = true;
        if (_requestor != null)
        {
            _requestor.onDecompress(this);
        }
    }

    @Override
    public FileInfoHolder getFileInfoHolder()
    {
        return getDownloadRequest() == null ? null : getDownloadRequest().getFileInfoHolder();
    }
}
