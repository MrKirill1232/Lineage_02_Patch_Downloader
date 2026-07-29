package org.index.patchdownloader.interfaces;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.impl.conditions.ConditionName;
import org.index.patchdownloader.impl.conditions.ConditionStartupCompare;
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
        // No condition matched. Include everything only when the run has NO effective include filter. Decide from
        // the actual non-blank filter segments (not the raw string): a separator-only value like ";" is non-empty
        // yet builds zero include conditions, so it must be treated as "no filter", not "reject everything".
        return !includeFilterHasEntries();
    }

    private static boolean includeFilterHasEntries()
    {
        String filter = MainConfig.INCLUDE_FILE_FILTER;
        if (filter == null)
        {
            return false;
        }
        for (String segment : filter.split(";"))
        {
            if (!segment.isBlank())
            {
                return true;
            }
        }
        return false;
    }

    public static List<ICondition> loadConditions(GeneralLinkGenerator generalLinkGenerator)
    {
        // Build the include/exclude name filters FIRST, so the start-up comparator can be told which files the run
        // actually wants. Without this it would restore-hash / source-verify EVERY file in the map (gigabytes) even
        // when '-include_filter System/*' selects a handful — the filters were previously appended only AFTER the
        // comparator had already run over the whole map.
        List<ICondition> filterConditions = loadFilterConditions();

        List<ICondition> conditionList = new ArrayList<>();
        // One start-up comparator handles BOTH restore (verify files already in the output folder) and
        // source-compare (copy proven files from a local source). Restore has strict priority internally:
        // it runs first and a restored file is never re-copied. See ConditionStartupCompare.
        boolean restoreEnabled = MainConfig.RESTORE_DOWNLOADING && (MainConfig.CHECK_BY_NAME || MainConfig.CHECK_BY_HASH_SUM || MainConfig.CHECK_BY_SIZE);
        boolean sourceEnabled = MainConfig.SOURCE_COMPARE_PATH != null;
        if (restoreEnabled || sourceEnabled)
        {
            ICondition condition = new ConditionStartupCompare(generalLinkGenerator, filterConditions);
            if (condition instanceof ILoadable)
            {
                ((ILoadable) condition).load();
            }
            if (condition instanceof IThreadResponse)
            {
                ((IThreadResponse) condition).waitCompletion();
            }
            conditionList.add(condition);
        }
        // Same order as before: comparator first, then exclude filters, then include filters (loadFilterConditions
        // builds them in that order). Appending the SAME instances keeps a single source of truth for the filter.
        conditionList.addAll(filterConditions);
        return conditionList;
    }

    /**
     * EN: Builds only the name-based include/exclude filter conditions from the config, in the order
     *     [exclude..., include...]. Shared by {@link #loadConditions(GeneralLinkGenerator)} (which prepends the
     *     start-up comparator) and by the comparator itself (which uses them via
     *     {@link #checkCondition(List, FileInfoHolder)} to skip verifying files the run does not want). A blank or
     *     separator-only segment builds no condition.
     * @return the include/exclude filter conditions (possibly empty)
     */
    public static List<ICondition> loadFilterConditions()
    {
        List<ICondition> filterConditions = new ArrayList<>();
        if (MainConfig.EXCLUDE_FILE_FILTER != null)
        {
            for (String filter : MainConfig.EXCLUDE_FILE_FILTER.split(";"))
            {
                if (filter.isEmpty() || filter.isBlank())
                {
                    continue;
                }
                filterConditions.add(new ConditionName(false, filter));
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
                filterConditions.add(new ConditionName(true, filter));
            }
        }
        return filterConditions;
    }
}
