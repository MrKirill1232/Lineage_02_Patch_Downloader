package org.index.patchdownloader.impl.conditions;

import org.index.patchdownloader.interfaces.ICondition;
import org.index.patchdownloader.model.holders.LinkHolder;

public class ConditionName implements ICondition
{
    @SuppressWarnings("unused")
    private final boolean _include;
    private final String _checkPath;
    private final String _checkName;
    private final String _checkExtn;

    public ConditionName(boolean include, String filter)
    {
        _include = include;
        if (filter == null || filter.equalsIgnoreCase("*"))
        {
            _checkPath = null;
            _checkName = null;
            _checkExtn = null;
        }
        else
        {
            String[] splitPath = filter.split("/");
            String nameAndExt = splitPath[splitPath.length - 1];
            if (nameAndExt.equalsIgnoreCase("*"))
            {
                // system/*
                _checkPath = filter.toLowerCase();
            }
            else
            {
                if (splitPath.length == 1)
                {
                    _checkPath = null;
                }
                // system/interface.u
                // remove interface.u and +1 is a "/" character
                else
                {
                    _checkPath = filter.substring(0, (filter.length() - nameAndExt.length()) - 1).toLowerCase();
                }
            }
            // in case if someone want to write "system/.file_name"
            if (nameAndExt.lastIndexOf('.') == 0)
            {
                _checkName = null;
                _checkExtn = null;
            }
            else if (nameAndExt.equalsIgnoreCase("*"))
            {
                _checkName = null;
                _checkExtn = null;
            }
            else
            {
                String[] splitNameAndExt = nameAndExt.split("\\.", 2);
                _checkName = splitNameAndExt[0].toLowerCase();
                _checkExtn = splitNameAndExt[1].toLowerCase();
            }
        }
    }

    @Override
    public boolean check(LinkHolder linkHolder)
    {
        if (linkHolder == null)
        {
            return false;
        }
        if (_checkPath == null && _checkName == null && _checkExtn == null)
        {
            return true;
        }
        boolean isCheckPath = (_checkPath == null);
        boolean isCheckName = (_checkName == null);
        boolean isCheckExtn = (_checkExtn == null);
        String[] nameAndExtn = linkHolder.getFileName().split("\\.");
        if (_checkPath != null)
        {
            if (_checkPath.equalsIgnoreCase("*"))
            {
                isCheckPath = true;
            }
            else if (_checkPath.endsWith("*"))
            {
                String pathOfFile = linkHolder.getFilePath();
                String checkPath = _checkPath.substring(0, _checkPath.length() - 1);
                if (checkPath.endsWith("/"))
                {
                    checkPath = checkPath.substring(0, checkPath.length() - 1);
                }
                int lastIndex = pathOfFile.lastIndexOf('/');
                if (lastIndex != - 1 && lastIndex == (pathOfFile.length() - 1))
                {
                    pathOfFile = pathOfFile.substring(0, lastIndex);
                }
                isCheckPath = pathOfFile.startsWith(checkPath);
            }
            else
            {
                String pathOfFile = linkHolder.getFilePath();
                int lastIndex = pathOfFile.lastIndexOf('/');
                if (lastIndex != -1 && lastIndex == (pathOfFile.length() - 1))
                {
                    pathOfFile = pathOfFile.substring(0, lastIndex);
                }
                isCheckPath = pathOfFile.equalsIgnoreCase(_checkPath);
            }
        }
        if (_checkName != null)
        {
            if (_checkName.equalsIgnoreCase("*"))
            {
                isCheckName = true;
            }
            else
            {
                isCheckName = nameAndExtn[0].equalsIgnoreCase(_checkName);
            }
        }
        if (_checkExtn != null)
        {
            if (_checkExtn.equalsIgnoreCase("*"))
            {
                isCheckExtn = true;
            }
            else
            {
                isCheckExtn = nameAndExtn[nameAndExtn.length - 1].equalsIgnoreCase(_checkExtn);
            }
        }
        if (_include)
        {
            return (isCheckPath && isCheckName && isCheckExtn);
        }
        else
        {
            return !(isCheckPath && isCheckName && isCheckExtn);
        }
    }
}
