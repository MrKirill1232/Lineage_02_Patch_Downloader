package org.index.patchdownloader.interfaces;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Calendar;

public interface IDummyLogger
{
    static boolean SUPPORTED_ANSI = !System.getProperty("os.name", "Windows").contains("Windows");

    public static String INFO = "INFO";
    public static String WARNING = "WARN";
    public static String ERROR = "ERRR";
    public static String FINE = "FINE";

    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLACK = "\u001B[30m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_WHITE = "\u001B[37m";

    static void log(String level, String message)
    {
        log(level, null, message, null);
    }

    static void log(String level, Class<?> requestedClass, String message, Throwable throwable)
    {
        String logMessage = message;
        if (throwable != null)
        {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);
            pw.flush();
            pw.close();
            logMessage += "\n" + sw.toString();
        }
        if (requestedClass != null)
        {
            System.out.println(getColorByLevel(level) + getTime() + " | [" + level + "] | " + "[" + requestedClass.getSimpleName() + "]" + ": " + logMessage + getResetColor());
        }
        else
        {
            System.out.println(getColorByLevel(level) + getTime() + " | [" + level + "]: " + logMessage + getResetColor());
        }
    }

    static String getColorByLevel(String level)
    {
        if (!SUPPORTED_ANSI)
        {
            return "";
        }
        switch (level)
        {
            case INFO:
            {
                return ANSI_RESET;
            }
            case WARNING:
            {
                return ANSI_YELLOW;
            }
            case ERROR:
            {
                return ANSI_RED;
            }
            case FINE:
            {
                return ANSI_GREEN;
            }
        }
        return ANSI_RESET;
    }

    static String getResetColor()
    {
        if (!SUPPORTED_ANSI)
        {
            return "";
        }
        return ANSI_RESET;
    }

    static String getTime()
    {
        Calendar calendar = Calendar.getInstance();
        return String.format("[%02d.%02d.%d | %02d:%02d:%02d]"
                , (calendar.get(Calendar.MONTH) + 1)
                ,  calendar.get(Calendar.DAY_OF_MONTH)
                ,  calendar.get(Calendar.YEAR)

                ,  calendar.get(Calendar.HOUR_OF_DAY)
                ,  calendar.get(Calendar.MINUTE)
                ,  calendar.get(Calendar.SECOND)
        );
    }

    static int getPercentOfCompletion(int currentProgress, int totalElements)
    {
        return ((int) ((double) currentProgress / (double) totalElements * 100d));
    }

    static String getPercentMessage(int percentOfCompletion)
    {
        return String.format("%03d", percentOfCompletion) + "% / 100%";
    }
}
