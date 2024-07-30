package org.index.patchdownloader.interfaces;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.impl.conditions.ConditionName;
import org.index.patchdownloader.impl.conditions.ConditionRestoreDownload;
import org.index.patchdownloader.model.holders.LinkHolder;
import org.index.patchdownloader.model.linkgenerator.GeneralLinkGenerator;

import java.util.ArrayList;
import java.util.List;

public interface ICondition
{
    default boolean check(LinkHolder linkHolder)
    {
        return true;
    }

    public static boolean checkCondition(List<ICondition> conditionList, LinkHolder linkHolder)
    {
        for (ICondition condition : conditionList)
        {
            if (!condition.check(linkHolder))
            {
                return false;
            }
        }
        return true;
    }

    public static List<ICondition> loadConditions(GeneralLinkGenerator generalLinkGenerator)
    {
        List<ICondition> conditionList = new ArrayList<>();
        if (MainConfig.RESTORE_DOWNLOADING && (MainConfig.CHECK_BY_NAME || MainConfig.CHECK_BY_HASH_SUM || MainConfig.CHECK_BY_SIZE))
        {
            conditionList.add(new ConditionRestoreDownload(generalLinkGenerator));
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
        return conditionList;
    }
}