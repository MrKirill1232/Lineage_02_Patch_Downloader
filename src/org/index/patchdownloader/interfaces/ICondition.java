package org.index.patchdownloader.interfaces;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.impl.conditions.ConditionName;
import org.index.patchdownloader.impl.conditions.ConditionRestoreDownload;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.model.linkgenerator.GeneralLinkGenerator;

import java.util.ArrayList;
import java.util.List;

public interface ICondition
{
    default boolean check(FileInfoHolder fileInfoHolder)
    {
        return true;
    }

    /**
     * @return "condition" can be skipped from total filter list. In case where all "conditions" methods will return "false" - check method will return "false" as well.
     */
    default boolean optional()
    {
        return false;
    }

    public static boolean checkCondition(List<ICondition> conditionList, FileInfoHolder fileInfoHolder)
    {
        for (ICondition condition : conditionList)
        {
            if (condition.optional())
            {
                if (condition.check(fileInfoHolder))
                {
                    return true;
                }
            }
            else if (!condition.check(fileInfoHolder))
            {
                return false;
            }
        }
        return false;
    }

    public static List<ICondition> loadConditions(GeneralLinkGenerator generalLinkGenerator)
    {
        List<ICondition> conditionList = new ArrayList<>();
        if (MainConfig.RESTORE_DOWNLOADING && (MainConfig.CHECK_BY_NAME || MainConfig.CHECK_BY_HASH_SUM || MainConfig.CHECK_BY_SIZE))
        {
            conditionList.add(new ConditionRestoreDownload(generalLinkGenerator));
        }
        if (MainConfig.EXCLUDE_FILE_FILTER != null)
        {
            for (String filter : MainConfig.EXCLUDE_FILE_FILTER.split(";"))
            {
                if (filter.isEmpty() || filter.isBlank())
                {
                    continue;
                }
                conditionList.add(new ConditionName(false, filter));
            }
        }
        if (MainConfig.INCLUDE_FILE_FILTER != null)
        {
            for (String filter : MainConfig.INCLUDE_FILE_FILTER.split(";"))
            {
                if (filter.isEmpty() || filter.isBlank())
                {
                    continue;
                }
                conditionList.add(new ConditionName(true, filter));
            }
        }
        return conditionList;
    }
}
