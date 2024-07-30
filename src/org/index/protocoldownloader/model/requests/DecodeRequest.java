package org.index.protocoldownloader.model.requests;

import org.index.protocoldownloader.interfaces.IRequest;
import org.index.protocoldownloader.interfaces.IRequestor;

public class DecodeRequest implements IRequest
{
    private final IRequestor _requestor;
    private final DownloadRequest _downloadRequest;
    private final byte[][] _encodeArray;

    private byte[]  _decodedArray;
    private boolean _completed;

    public DecodeRequest(IRequestor requestor, DownloadRequest request)
    {
        _requestor = requestor;
        _downloadRequest = request;
        _encodeArray = null;
    }

    public DecodeRequest(IRequestor requestor, byte[][] encodeArray)
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

    public void setDecodedArray(byte[] decodedArray)
    {
        _decodedArray = decodedArray;
    }

    public byte[] getDecodedArray()
    {
        return _decodedArray;
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
            _requestor.onDecode(this);
        }
    }
}
