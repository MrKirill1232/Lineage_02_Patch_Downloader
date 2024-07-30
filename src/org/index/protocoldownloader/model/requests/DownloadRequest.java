package org.index.protocoldownloader.model.requests;

import org.index.protocoldownloader.interfaces.IRequest;
import org.index.protocoldownloader.interfaces.IRequestor;
import org.index.protocoldownloader.model.holders.LinkHolder;

public class DownloadRequest implements IRequest
{
    private IRequestor _requestor;

    private LinkHolder _linkHolder;
    private int         _httpStatus;
    private byte[][]    _downloadedByteArray;

    public DownloadRequest(IRequestor requestor, LinkHolder linkHolder)
    {
        _requestor  = requestor;
        _linkHolder = linkHolder;
        _httpStatus = -1;

        if (linkHolder == null)
        {
            _downloadedByteArray = new byte[1][];
        }
        else
        {
            _downloadedByteArray = new byte[linkHolder.getTotalFileParts()][];
        }
    }

    public void setHttpStatus(int status)
    {
        _httpStatus = status;
    }

    public void addDownloadedPart(int filePart, byte[] downloadedByteArray)
    {
        _downloadedByteArray[filePart] = downloadedByteArray;
    }

    @Override
    public void onComplete()
    {;
        if (_requestor != null)
        {
            _requestor.onDownload(this);
        }
    }

    public LinkHolder getLinkHolder()
    {
        return _linkHolder;
    }

    public int getHttpStatus()
    {
        return _httpStatus;
    }

    public byte[][] getDownloadedByteArray()
    {
        return _downloadedByteArray;
    }

    public boolean isComplete()
    {
        return _httpStatus == 200 && !(_downloadedByteArray == null || _downloadedByteArray.length == 0);
    }
}
