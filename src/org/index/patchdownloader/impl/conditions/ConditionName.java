package org.index.patchdownloader.impl.conditions;

import org.index.patchdownloader.interfaces.ICondition;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.util.Utils;

public class ConditionName implements ICondition
{
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
            // in case if someone want to write "system/.file_name" (a leading-dot file):
            // empty name + the part after the leading dot as extension, so it matches only
            // that dot-file (e.g. ".options") instead of the whole directory.
            if (nameAndExt.lastIndexOf('.') == 0)
            {
                _checkName = "";
                _checkExtn = nameAndExt.substring(1).toLowerCase();
            }
            else if (nameAndExt.equalsIgnoreCase("*"))
            {
                _checkName = null;
                _checkExtn = null;
            }
            else
            {
                // Split on the FIRST dot only: everything after it is the extension
                // (matches check()'s split semantics), e.g. "files_info.json.zip" -> ext "json.zip".
                // A segment with no dot (bare name / no extension) has no extension part.
                String[] splitNameAndExt = nameAndExt.split("\\.", 2);
                _checkName = splitNameAndExt[0].toLowerCase();
                _checkExtn = (splitNameAndExt.length > 1) ? splitNameAndExt[1].toLowerCase() : null;
            }
        }
    }

    /**
     * EN: Matches a file against the parsed filter (path + name + extension, each optionally wildcarded).
     *     A trailing {@code /*} on the path matches the directory AND all nested subfolders (recursive).
     *     The result is inverted for exclude filters ({@code _include == false}). <br>
     * RU: Сопоставляет файл с разобранным фильтром (путь + имя + расширение, каждый опционально с
     *     подстановкой). Хвостовой {@code /*} в пути соответствует каталогу И всем вложенным подпапкам (рекурсивно).
     *     Для exclude-фильтров ({@code _include == false}) результат инвертируется. <br>
     * ==================================================================<br>
     * EN: @param fileInfoHolder the file to test / RU: @param fileInfoHolder проверяемый файл <br>
     * @return <br>
     *         {true}  - EN: include: matches; exclude: does not match / RU: include: совпал; exclude: не совпал <br>
     *         {false} - EN: include: no match; exclude: matches / RU: include: не совпал; exclude: совпал <br>
     **/
    @Override
    public boolean check(FileInfoHolder fileInfoHolder)
    {
        if (fileInfoHolder == null)
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
        // Split on the FIRST dot only, matching the constructor's split semantics: index 0 is the
        // name, index 1 (if present) is the full extension (e.g. "json.zip" from "files_info.json.zip").
        String[] nameAndExtn = fileInfoHolder.getFileName().split("\\.", 2);
        if (_checkPath != null)
        {
            if (Utils.checkByChar(_checkPath, 1, 0, '*'))
            {
                isCheckPath = true;
            }
            else if (Utils.checkByChar(_checkPath, -1, (_checkPath.length() - 1), '*'))
            {
                String pathOfFile = fileInfoHolder.getFilePath();
                String checkPath = _checkPath.substring(0, _checkPath.length() - 1);
                if (checkPath.charAt(checkPath.length() - 1) == '/')
                {
                    checkPath = checkPath.substring(0, checkPath.length() - 1);
                }
                int lastIndex = pathOfFile.lastIndexOf('/');
                if (lastIndex != - 1 && lastIndex == (pathOfFile.length() - 1))
                {
                    pathOfFile = pathOfFile.substring(0, lastIndex);
                }
                // Trailing "/*" matches the directory itself AND everything nested under it
                // (recursive), e.g. "system/*" also matches "system/plugins/...". The trailing "/"
                // boundary keeps a sibling like "system2" from matching "system".
                String lowerPath = pathOfFile.toLowerCase();
                String lowerCheck = checkPath.toLowerCase();
                isCheckPath = lowerPath.equals(lowerCheck) || lowerPath.startsWith(lowerCheck + "/");
            }
            else
            {
                String pathOfFile = fileInfoHolder.getFilePath();
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
            if (Utils.checkByChar(_checkName, 1, 0, '*'))
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
            if (Utils.checkByChar(_checkExtn, 1, 0, '*'))
            {
                isCheckExtn = true;
            }
            else
            {
                isCheckExtn = (nameAndExtn.length > 1) && nameAndExtn[1].equalsIgnoreCase(_checkExtn);
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

    /**
     * EN: Reports whether this condition is optional. A condition is optional exactly when it is an
     *     include filter ({@code _include == true}): for an include filter a non-match simply means the
     *     file is not selected, which is not an error. Exclude filters are mandatory. <br>
     * RU: Сообщает, является ли условие необязательным. Условие необязательно тогда и только тогда, когда
     *     это include-фильтр ({@code _include == true}): для include-фильтра несовпадение означает лишь, что
     *     файл не выбран, и это не ошибка. Exclude-фильтры обязательны. <br>
     * ==================================================================<br>
     * @return <br>
     *         {true}  - EN: optional (include filter) / RU: необязательное (include-фильтр) <br>
     *         {false} - EN: mandatory (exclude filter) / RU: обязательное (exclude-фильтр) <br>
     **/
    @Override
    public boolean optional()
    {
        return _include;
    }
}
