package org.index.patchdownloader.interfaces;

import org.index.patchdownloader.model.holders.LinkHolder;

public interface IRequest
{
    void onComplete();

    LinkHolder getLinkHolder();
}
