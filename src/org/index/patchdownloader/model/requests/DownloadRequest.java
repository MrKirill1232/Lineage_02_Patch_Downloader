package org.index.patchdownloader.model.requests;

import org.index.patchdownloader.interfaces.IRequest;
import org.index.patchdownloader.interfaces.IRequestor;
import org.index.patchdownloader.model.holders.FileInfoHolder;

public class DownloadRequest implements IRequest
{
    private IRequestor      _requestor;

    private FileInfoHolder  _fileInfo;
    private byte[][]        _downloadedByteArray;

    private int             _downloadingAttempts;

    public DownloadRequest(IRequestor requestor, FileInfoHolder fileInfo)
    {
        _requestor  = requestor;
        _fileInfo   = fileInfo;
        _downloadedByteArray = new byte[Math.max(fileInfo.getAllSeparatedParts().length, 1)][];
    }

    public void addDownloadedPart(int filePart, byte[] downloadedByteArray)
    {
        _downloadedByteArray[filePart] = downloadedByteArray;
    }

    @Override
    public void onComplete()
    {
        if (_requestor != null)
        {
            _requestor.onDownload(this);
        }
    }

    public int getDownloadingAttempts()
    {
        return _downloadingAttempts;
    }

    public void setDownloadingAttempts(int downloadingAttempts)
    {
        _downloadingAttempts = downloadingAttempts;
    }

    public byte[][] getDownloadedByteArray()
    {
        return _downloadedByteArray;
    }

    public boolean isComplete()
    {
        for (FileInfoHolder fileInfoHolder : getFileInfoHolder().getAllSeparatedParts())
        {
            if (fileInfoHolder.getAccessLink().getHttpStatus() != 200)
            {
                return false;
            }
        }
        return (getFileInfoHolder().getAllSeparatedParts().length != 0 || getFileInfoHolder().getAccessLink().getHttpStatus() == 200) && !(_downloadedByteArray == null || _downloadedByteArray.length == 0);
    }

    @Override
    public FileInfoHolder getFileInfoHolder()
    {
        return _fileInfo;
    }
}
