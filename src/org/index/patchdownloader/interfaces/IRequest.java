package org.index.patchdownloader.interfaces;

import org.index.patchdownloader.model.holders.LinkHolder;

public interface IRequest
{
    void onComplete();

    default String getLinkPath()
    {
        LinkHolder linkHolder = getLinkHolder();
        if (linkHolder == null)
        {
            return "";
        }
        return linkHolder.getLinkPath();
    }

    LinkHolder getLinkHolder();
}
