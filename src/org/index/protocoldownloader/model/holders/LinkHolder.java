package org.index.protocoldownloader.model.holders;

import org.index.protocoldownloader.enums.FileTypeByLink;

public class LinkHolder
{
    private final String _fileName;
    private final String _filePath;
    private final FileTypeByLink _linkType;

    private int         _originalFileLength;
    private String      _originalFileHashsum;

    private String[]    _namesOfFiles;
    private int[]       _fileLength;
    private String[]    _accessLink;
    private String[]    _hashsum;

    public LinkHolder(String fileName, String filePath, FileTypeByLink typeByLink, int totalParts)
    {
        _fileName = fileName;
        _filePath = filePath;
        _linkType = typeByLink;

        _namesOfFiles   = new String[totalParts];
        _fileLength     = new int[totalParts];
        _accessLink     = new String[totalParts];
        _hashsum        = new String[totalParts];
    }

    public String getFileName()
    {
        return _fileName;
    }

    public String getFilePath()
    {
        return _filePath;
    }

    public FileTypeByLink getLinkType()
    {
        return _linkType;
    }

    public int getTotalFileParts()
    {
        return _accessLink.length;
    }

    public String[] getNamesOfFiles()
    {
        return _namesOfFiles;
    }

    public void setNamesOfFiles(int index, String nameOfFile)
    {
        _namesOfFiles[index] = nameOfFile;
    }

    public int[] getFileLength()
    {
        return _fileLength;
    }

    public void setFileLength(int index, int fileLength)
    {
        _fileLength[index] = fileLength;
    }

    public String[] getAccessLink()
    {
        return _accessLink;
    }

    public void setAccessLink(int index, String accessLink)
    {
        _accessLink[index] = accessLink.replace("\\", "/");
    }

    public String[] getHashsum()
    {
        return _hashsum;
    }

    public void setHashsum(int index, String hashsum)
    {
        _hashsum[index] = hashsum;
    }

    public int getTotalLength()
    {
        int length = 0;
        for (int partOfLength : _fileLength)
        {
            length += partOfLength;
        }
        return length;
    }

    public int getOriginalFileLength()
    {
        return _originalFileLength;
    }

    public void setOriginalFileLength(int originalFileLength)
    {
        _originalFileLength = originalFileLength;
    }

    public String getOriginalFileHashsum()
    {
        return _originalFileHashsum;
    }

    public void setOriginalFileHashsum(String originalFileHashsum)
    {
        _originalFileHashsum = originalFileHashsum;
    }
}
