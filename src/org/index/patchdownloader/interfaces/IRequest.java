package org.index.patchdownloader.interfaces;

import org.index.patchdownloader.model.holders.FileInfoHolder;

public interface IRequest
{
    void onComplete();

    default String getLinkPath()
    {
        FileInfoHolder fileInfoHolder = getFileInfoHolder();
        if (fileInfoHolder == null)
        {
            return "";
        }
        return fileInfoHolder.getLinkPath();
    }

    FileInfoHolder getFileInfoHolder();
}
