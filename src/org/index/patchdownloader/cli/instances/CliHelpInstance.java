package org.index.patchdownloader.cli.instances;

import org.index.patchdownloader.cli.CliArg;
import org.index.patchdownloader.cli.ICliInstance;

/**
 * EN: {@code -help} / {@code -h}: prints every registered argument (its flags + description) by iterating
 *     {@link CliArg#values()}, then terminates the program with exit code 0 — help is a query, not a run.<br>
 * RU: {@code -help} / {@code -h}: печатает каждый зарегистрированный аргумент (его флаги + описание), обходя
 *     {@link CliArg#values()}, затем завершает программу с кодом 0 — справка это запрос, а не запуск.<br>
 **/
public class CliHelpInstance implements ICliInstance
{
    @Override
    public String[] getParsableAttributes()
    {
        return new String[]{"-help", "-h"};
    }

    @Override
    public String getDescription()
    {
        return "Prints this list of arguments and exits.";
    }

    @Override
    public int parseAttribute(int currIndex, String[] arguments)
    {
        StringBuilder builder = new StringBuilder("Available start-up arguments:").append(System.lineSeparator());
        for (CliArg argument : CliArg.values())
        {
            ICliInstance instance = argument.getInstance();
            builder.append("  ").append(String.join(" | ", instance.getParsableAttributes())).append(System.lineSeparator());
            builder.append("      ").append(instance.getDescription()).append(System.lineSeparator());
        }
        System.out.println(builder);
        System.exit(0);
        return currIndex;
    }
}
