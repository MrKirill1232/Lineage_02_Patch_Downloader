package org.index.patchdownloader.model.holders;

public class LinkInfoHolder
{
    private final FileInfoHolder _fileInfo;

    private String _accessLink;
    private int _httpStatus;
    private int _httpLength;

    public LinkInfoHolder(FileInfoHolder fileInfo)
    {
        _fileInfo = fileInfo;
        _httpStatus = -1;
        _httpLength = -1;
    }

    public FileInfoHolder getFileInfo()
    {
        return _fileInfo;
    }

    public String getAccessLink()
    {
        return _accessLink;
    }

    public void setAccessLink(String accessLink)
    {
        _accessLink = accessLink;
    }

    public int getHttpStatus()
    {
        return _httpStatus;
    }

    public void setHttpStatus(int httpStatus)
    {
        _httpStatus = httpStatus;
    }

    public int getHttpLength()
    {
        return _httpLength;
    }

    public void setHttpLength(int httpLength)
    {
        _httpLength = httpLength;
    }
}
