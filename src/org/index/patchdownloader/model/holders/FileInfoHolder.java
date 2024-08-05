package org.index.patchdownloader.model.holders;

public class FileInfoHolder
{
    private final static FileInfoHolder[] EMPTY_ARRAY = new FileInfoHolder[0];

    private final String    _fileName   ;
    private final String    _filePath   ;
    private final boolean   _partOfFile ;
    private LinkInfoHolder  _accessLink ;

    private String          _fileHashSum;
    private int             _fileLength ;
    private String          _downloadDataHashSum;
    private int             _downloadDataLength;

    private final FileInfoHolder[] _separatedParts;

    public FileInfoHolder(String fileName, String filePath, boolean separated, int countOfSeparatedParts)
    {
        _fileName   = fileName  ;
        _filePath   = filePath  ;
        _partOfFile = separated ;
        _fileLength = -1;
        _downloadDataLength = -1;
        if (countOfSeparatedParts == 0)
        {
            _separatedParts = EMPTY_ARRAY;
        }
        else
        {
            _separatedParts = new FileInfoHolder[countOfSeparatedParts];
        }
    }

    public String getFileName()
    {
        return _fileName;
    }

    public String getFilePath()
    {
        return _filePath;
    }

    public boolean isPartOfFile()
    {
        return _partOfFile;
    }

    public LinkInfoHolder getAccessLink()
    {
        return _accessLink;
    }

    public void setAccessLink(LinkInfoHolder accessLink)
    {
        _accessLink = accessLink;
    }

    public String getFileHashSum()
    {
        return _fileHashSum;
    }

    public void setFileHashSum(String fileHashSum)
    {
        _fileHashSum = fileHashSum;
    }

    public int getFileLength()
    {
        return _fileLength;
    }

    public void setFileLength(int fileLength)
    {
        _fileLength = fileLength;
    }

    public String getDownloadDataHashSum()
    {
        return _downloadDataHashSum;
    }

    public void setDownloadDataHashSum(String downloadDataHashSum)
    {
        _downloadDataHashSum = downloadDataHashSum;
    }

    public int getDownloadDataLength()
    {
        return _downloadDataLength;
    }

    public void setDownloadDataLength(int downloadDataLength)
    {
        _downloadDataLength = downloadDataLength;
    }

    public FileInfoHolder[] getAllSeparatedParts()
    {
        return _separatedParts;
    }

    public void setSeparatedPart(int index, FileInfoHolder fileInfo)
    {
        if (getAllSeparatedParts().length == 0 || getAllSeparatedParts().length <= index)
        {
            return;
        }
        _separatedParts[index] = fileInfo;
    }

    public String getLinkPath()
    {
        String path = getFilePath();
        String name = getFileName();
        if (!path.isEmpty() && path.charAt(path.length() - 1) == '/')
        {
            return path + name;
        }
        return path + "/" + name;
    }
}
