package org.index.patchdownloader.cli.instances;

import org.index.patchdownloader.cli.CliArgs;
import org.index.patchdownloader.cli.ICliInstance;
import org.index.patchdownloader.config.configs.MainConfig;

/**
 * EN: {@code -version <n>}: sets the patch version ({@code MainConfig.PATCH_VERSION_SOURCE}). A non-numeric
 *     value (or a following token that is actually another flag) is rejected with a warning and leaves the
 *     config untouched, so the {@code -1} "unset" sentinel is preserved.<br>
 * RU: {@code -version <n>}: задаёт версию патча ({@code MainConfig.PATCH_VERSION_SOURCE}). Нечисловое
 *     значение (или следующий токен, который на деле является другим флагом) отклоняется с предупреждением и
 *     не изменяет конфиг, поэтому сохраняется маркер «не задано» — {@code -1}.<br>
 **/
public class CliPatchVersionInstance implements ICliInstance
{
    @Override
    public String[] getParsableAttributes()
    {
        return new String[]{"-version"};
    }

    @Override
    public String getDescription()
    {
        return "Patch version (client file-list version, NOT the L2 protocol version). Example: -version 101";
    }

    @Override
    public int parseAttribute(int currIndex, String[] arguments)
    {
        String value = CliArgs.nextValue(currIndex, arguments);
        if (value == null)
        {
            CliArgs.warn(arguments[currIndex], "requires a value.");
            return currIndex;
        }
        // Detect whether the token is genuinely an integer: parse it twice with two distinct sentinels.
        // Equal results mean the value parsed; differing results mean it fell back to the default, i.e. it
        // is not a number. This avoids silently coercing a non-numeric value (e.g. a mistyped flag) to -1
        // and logging it as if it were applied.
        int parsed = CliArgs.parseInteger(value, Integer.MIN_VALUE);
        if (parsed != CliArgs.parseInteger(value, Integer.MIN_VALUE + 1))
        {
            if (value.startsWith("-"))
            {
                // Looks like the user omitted the number and the next token is really another flag: leave it
                // unconsumed so the argument walker can parse it on its own.
                CliArgs.warn(arguments[currIndex], "requires a numeric value; next token '" + value + "' looks like another flag and was left unconsumed.");
                return currIndex;
            }
            CliArgs.warn(arguments[currIndex], "requires a numeric value; '" + value + "' is not a number and was ignored.");
            return currIndex + 1;
        }
        MainConfig.PATCH_VERSION_SOURCE = parsed;
        CliArgs.logApplied(arguments[currIndex], MainConfig.PATCH_VERSION_SOURCE);
        return currIndex + 1;
    }
}
