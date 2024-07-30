package org.index.protocoldownloader.interfaces;

import org.index.protocoldownloader.config.configs.MainConfig;
import org.index.protocoldownloader.impl.conditions.ConditionName;
import org.index.protocoldownloader.impl.conditions.ConditionRestoreDownload;
import org.index.protocoldownloader.model.holders.LinkHolder;
import org.index.protocoldownloader.model.linkgenerator.GeneralLinkGenerator;

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
