package org.index.patchdownloader.interfaces;

public interface IArgumentHandler
{
    default void handleArguments(String requestedArgument, String possibleValue)
    {
        if (requiredPossibleValue() && possibleValue == null)
        {
            log("Found start-up argument '" + requestedArgument + ". Cannot handle value, because its null.");
            return;
        }
        handleArgumentsImpl(requestedArgument, possibleValue);
    }

    void handleArgumentsImpl(String requestedArgument, String possibleValue);

    default boolean requiredPossibleValue()
    {
        return false;
    }

    default String replaceQuoteOnBeginAndEnd(String value)
    {
        if (value.charAt(0) == '"')
        {
            value = value.substring(1, value.length());
        }
        if (value.charAt(value.length() - 1) == '"')
        {
            value = value.substring(0, (value.length() - 1));
        }
        return value;
    }

    default String replaceIllegalSymbolsInPath(String value)
    {
        return value.replaceAll("\\\\", "/");
    }

    default void log(String logString)
    {
        IDummyLogger.log(IDummyLogger.INFO, logString);
    }
}
