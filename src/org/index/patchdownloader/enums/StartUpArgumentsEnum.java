package org.index.patchdownloader.enums;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.interfaces.IArgumentHandler;
import org.index.patchdownloader.util.ParseUtils;

import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public enum StartUpArgumentsEnum implements IArgumentHandler
{
    ARGUMENT_CDN("-cdn")
            {
                @Override
                public void handleArgumentsImpl(String requestedArgument, String possibleValue)
                {
                    CDNLink requestCDN = ParseUtils.parseEnum(possibleValue, null, CDNLink.class);
                    if (requestCDN == null)
                    {
                        log("Found start-up argument '" + requestedArgument + ". Cannot handle value, because its null.");
                        return;
                    }
                    MainConfig.CDN_SOURCE = requestCDN;
                    log("Found start-up argument '" + requestedArgument + "'. Variable " + "CDN_SOURCE" + " updated. New value is \"" + String.valueOf(requestCDN) + "\";");
                }

                @Override
                public boolean requiredPossibleValue()
                {
                    return true;
                }
            },
    ARGUMENT_VERSION("-version")
            {
                @Override
                public void handleArgumentsImpl(String requestedArgument, String possibleValue)
                {
                    int requestVersion = ParseUtils.parseInteger(possibleValue, -1);
                    MainConfig.PATCH_VERSION_SOURCE = requestVersion;
                    log("Found start-up argument '" + requestedArgument + "'. Variable " + "PATCH_VERSION_SOURCE" + " updated. New value is \"" + String.valueOf(requestVersion) + "\";");
                }

                @Override
                public boolean requiredPossibleValue()
                {
                    return true;
                }
            },
    ARGUMENT_PATH("-path")
            {
                @Override
                public void handleArgumentsImpl(String requestedArgument, String possibleValue)
                {
                    String valueInArg = replaceIllegalSymbolsInPath(replaceQuoteOnBeginAndEnd(possibleValue));
                    File requestFile = new File(URI.create(valueInArg).normalize().toString());
                    MainConfig.DOWNLOAD_PATH = requestFile;
                    log("Found start-up argument '" + requestedArgument + "'. Variable " + "DOWNLOAD_PATH" + " updated. New value is \"" + String.valueOf(requestFile) + "\";");
                }

                @Override
                public boolean requiredPossibleValue()
                {
                    return true;
                }
            },
    ARGUMENT_INNER_PATH("-inner_path")
            {
                @Override
                public void handleArgumentsImpl(String requestedArgument, String possibleValue)
                {
                    String valueInArg = replaceIllegalSymbolsInPath(replaceQuoteOnBeginAndEnd(possibleValue));
                    File requestFile = new File(MainConfig.PATH_TO_RUNNING, URI.create(valueInArg).normalize().toString());
                    MainConfig.DOWNLOAD_PATH = requestFile;
                    log("Found start-up argument '" + requestedArgument + "'. Variable " + "DOWNLOAD_PATH" + " updated. New value is \"" + String.valueOf(requestFile) + "\";");
                }

                @Override
                public boolean requiredPossibleValue()
                {
                    return true;
                }
            },
    ARGUMENT_INCLUDE_FILTER("-include_filter")
            {
                @Override
                public void handleArgumentsImpl(String requestedArgument, String possibleValue)
                {
                    MainConfig.INCLUDE_FILE_FILTER = replaceQuoteOnBeginAndEnd(possibleValue);
                    log("Found start-up argument '" + requestedArgument + "'. Variable " + "INCLUDE_FILE_FILTER" + " updated. New value is \"" + String.valueOf(MainConfig.INCLUDE_FILE_FILTER) + "\";");
                }

                @Override
                public boolean requiredPossibleValue()
                {
                    return true;
                }
            },
    ARGUMENT_EXCLUDE_FILTER("-exclude_filter")
            {
                @Override
                public void handleArgumentsImpl(String requestedArgument, String possibleValue)
                {
                    MainConfig.EXCLUDE_FILE_FILTER = replaceQuoteOnBeginAndEnd(possibleValue);
                    log("Found start-up argument '" + requestedArgument + "'. Variable " + "EXCLUDE_FILE_FILTER" + " updated. New value is \"" + String.valueOf(MainConfig.EXCLUDE_FILE_FILTER) + "\";");
                }

                @Override
                public boolean requiredPossibleValue()
                {
                    return true;
                }
            },
    ARGUMENT_CHECK_SIZE("-size")
            {
                @Override
                public void handleArgumentsImpl(String requestedArgument, String possibleValue)
                {
                    MainConfig.CHECK_FILE_SIZE = true;
                    log("Found start-up argument '" + requestedArgument + "'. Variable " + "CHECK_FILE_SIZE" + " updated. New value is \"" + String.valueOf(MainConfig.CHECK_FILE_SIZE) + "\";");
                }
            },
    ARGUMENT_CHECK_HASH_SUM("-hash")
            {
                @Override
                public void handleArgumentsImpl(String requestedArgument, String possibleValue)
                {
                    MainConfig.CHECK_HASH_SUM = true;
                    log("Found start-up argument '" + requestedArgument + "'. Variable " + "CHECK_HASH_SUM" + " updated. New value is \"" + String.valueOf(MainConfig.CHECK_HASH_SUM) + "\";");
                }
            },
    ARGUMENT_BROWSER_AGENT("-agent")
            {
                @Override
                public void handleArgumentsImpl(String requestedArgument, String possibleValue)
                {
                    MainConfig.REQUESTED_USER_AGENT = replaceQuoteOnBeginAndEnd(possibleValue);
                    log("Found start-up argument '" + requestedArgument + "'. Variable " + "REQUESTED_USER_AGENT" + " updated. New value is \"" + String.valueOf(MainConfig.REQUESTED_USER_AGENT) + "\";");
                }

                @Override
                public boolean requiredPossibleValue()
                {
                    return true;
                }
            },
    ARGUMENT_UP_NOVA_LAUNCHER_URL("-upnova_url")
            {
                @Override
                public void handleArgumentsImpl(String requestedArgument, String possibleValue)
                {
                    MainConfig.UP_NOVA_LAUNCHER_URL = URI.create(replaceQuoteOnBeginAndEnd(replaceIllegalSymbolsInPath(possibleValue))).normalize().toString();
                    log("Found start-up argument '" + requestedArgument + "'. Variable " + "UP_NOVA_LAUNCHER_URL" + " updated. New value is \"" + String.valueOf(MainConfig.UP_NOVA_LAUNCHER_URL) + "\";");
                }

                @Override
                public boolean requiredPossibleValue()
                {
                    return true;
                }
            },
    ARGUMENT_UP_NOVA_LAUNCHER_PATCH_PATH("-upnova_patch_path")
            {
                @Override
                public void handleArgumentsImpl(String requestedArgument, String possibleValue)
                {
                    MainConfig.UP_NOVA_LAUNCHER_PATCH_PATH = URI.create(replaceIllegalSymbolsInPath(possibleValue)).normalize().toString();
                    log("Found start-up argument '" + requestedArgument + "'. Variable " + "UP_NOVA_LAUNCHER_PATCH_PATH" + " updated. New value is \"" + String.valueOf(MainConfig.UP_NOVA_LAUNCHER_PATCH_PATH) + "\";");
                }

                @Override
                public boolean requiredPossibleValue()
                {
                    return true;
                }
            },
    ARGUMENT_RESTORE_DOWNLOADING("-restore")
            {
                @Override
                public void handleArgumentsImpl(String requestedArgument, String possibleValue)
                {
                    MainConfig.RESTORE_DOWNLOADING = true;
                    log("Found start-up argument '" + requestedArgument + "'. Variable " + "RESTORE_DOWNLOADING" + " updated. New value is \"" + String.valueOf(MainConfig.RESTORE_DOWNLOADING) + "\";");
                }
            },
    ARGUMENT_RESTORE_DOWNLOADING_CHECK_BY_SIZE("-r_size")
            {
                @Override
                public void handleArgumentsImpl(String requestedArgument, String possibleValue)
                {
                    MainConfig.CHECK_BY_SIZE = true;
                    log("Found start-up argument '" + requestedArgument + "'. Variable " + "CHECK_BY_SIZE" + " updated. New value is \"" + String.valueOf(MainConfig.CHECK_BY_SIZE) + "\";");
                }
            },
    ARGUMENT_RESTORE_DOWNLOADING_CHECK_BY_HASH("-r_hash")
            {
                @Override
                public void handleArgumentsImpl(String requestedArgument, String possibleValue)
                {
                    MainConfig.CHECK_BY_HASH_SUM = true;
                    log("Found start-up argument '" + requestedArgument + "'. Variable " + "CHECK_BY_HASH_SUM" + " updated. New value is \"" + String.valueOf(MainConfig.CHECK_BY_HASH_SUM) + "\";");
                }
            },
    ARGUMENT_THREAD_USAGE("-thread")
            {
                @Override
                public void handleArgumentsImpl(String requestedArgument, String possibleValue)
                {
                    MainConfig.THREAD_USAGE = true;
                    log("Found start-up argument '" + requestedArgument + "'. Variable " + "THREAD_USAGE" + " updated. New value is \"" + String.valueOf(MainConfig.THREAD_USAGE) + "\";");
                }
            },
    ARGUMENT_THREADS_ON_DOWNLOADING("-threads_download")
            {
                @Override
                public void handleArgumentsImpl(String requestedArgument, String possibleValue)
                {
                    int requestValue = ParseUtils.parseInteger(possibleValue, -1);
                    MainConfig.PARALLEL_DOWNLOADING = requestValue;
                    log("Found start-up argument '" + requestedArgument + "'. Variable " + "PARALLEL_DOWNLOADING" + " updated. New value is \"" + String.valueOf(requestValue) + "\";");
                }
            },
    ARGUMENT_THREADS_ON_DECOMPRESSING("-threads_decompress")
            {
                @Override
                public void handleArgumentsImpl(String requestedArgument, String possibleValue)
                {
                    int requestValue = ParseUtils.parseInteger(possibleValue, -1);
                    MainConfig.PARALLEL_DECODING = requestValue;
                    log("Found start-up argument '" + requestedArgument + "'. Variable " + "PARALLEL_DECODING" + " updated. New value is \"" + String.valueOf(requestValue) + "\";");
                }
            },
    ARGUMENT_THREADS_ON_SAVING("-threads_saving")
            {
                @Override
                public void handleArgumentsImpl(String requestedArgument, String possibleValue)
                {
                    int requestValue = ParseUtils.parseInteger(possibleValue, -1);
                    MainConfig.PARALLEL_STORING = requestValue;
                    log("Found start-up argument '" + requestedArgument + "'. Variable " + "PARALLEL_STORING" + " updated. New value is \"" + String.valueOf(requestValue) + "\";");
                }
            },
    ARGUMENT_THREADS_ON_FILE_CHECK("-threads_check")
            {
                @Override
                public void handleArgumentsImpl(String requestedArgument, String possibleValue)
                {
                    int requestValue = ParseUtils.parseInteger(possibleValue, -1);
                    MainConfig.THREAD_COUNT_FOR_FILE_CHECK_IN_CONDITION = requestValue;
                    log("Found start-up argument '" + requestedArgument + "'. Variable " + "THREAD_COUNT_FOR_FILE_CHECK_IN_CONDITION" + " updated. New value is \"" + String.valueOf(requestValue) + "\";");
                }

                @Override
                public boolean requiredPossibleValue()
                {
                    return true;
                }
            },
    ARGUMENT_HELP("-h", "-help")
            {
                @Override
                public void handleArgumentsImpl(String requestedArgument, String possibleValue)
                {
                    String str = "";

                    str += "This program is support arguments, and you can use them!";
                    str += "\n";
                    str += "[-cdn] - selecting download channel. Supports a 4 options. [NC_SOFT_TAIWAN] | [NC_SOFT_KOREAN] | [NC_SOFT_JAPANESE] | [NC_SOFT_AMERICA].";
                    str += "\n";
                    str += "Additional option is [UP_NOVA_LAUNCHER] but it required a [up_nova_launcher_url] in .ini or use a [-upnova_url] argument.";
                    str += "\n";
                    str += "Example: -cdn NC_SOFT_TAIWAN";
                    str += "\n";
                    str += "[-version] - selection a patch version. WARNING! THIS OPTION NOT THE SAME VERSION AS \"PROTOCOL VERSION\" OF LINEAGE 2.";
                    str += "\n";
                    str += "Patch version - its a version of installed client files. Example 89 patch is 486 game protocol on Korean.";
                    str += "\n";
                    str += "Latest knows versions (on 07/27/2024):";
                    str += "\n";
                    str += "NC_SOFT_TAIWAN   - 529";
                    str += "NC_SOFT_KOREAN   - 089";
                    str += "NC_SOFT_JAPANESE - 102";
                    str += "NC_SOFT_AMERICA  - 479";
                    str += "UP_NOVA_LAUNCHER - do not support";
                    str += "\n";
                    str += "Example: -version 529";
                    str += "\n";
                    str += "[-path] - output path of downloaded files.";
                    str += "\n";
                    str += "Example: -path \"C://downloads/lineage_02/429/\"";
                    str += "\n";
                    str += "[-inner_path] - output path of downloaded files inside running instance.";
                    str += "\n";
                    str += "Example: -inner_path \"/flameria\"    | Running path - \"C:/l2/custom_l2\" | final outputpath - C:/l2/custom_l2/flameria/";
                    str += "\n";
                    str += "Example: -inner_path \"\"             | Running path - \"C:/l2/custom_l2\" | final outputpath - C:/l2/custom_l2/";
                    str += "\n";
                    str += "[-include_filter] - Filter for downloaded file. If will match - downloading";
                    str += "[-exclude_filter] - Filter for downloaded file. If will match - skipping";
                    str += "\n";
                    str += "Examples: ";
                    str += "\n";
                    str += "01. -include_filter system/*.u";
                    str += "\n";
                    str += "02. -exclude_filter system/*.dat;system/interface.*";
                    str += "\n";
                    str += "[-hash] - will compare original hashsum with downloaded and decompressed file.";
                    str += "\n";
                    str += "[-size] - will compare original size with downloaded and decompressed file.";
                    str += "\n";
                    str += "[-restore] - will restore downloading and compare existed files. In miss-match - will download it again.";
                    str += "\n";
                    str += "[-r_hash] - will compare original hashsum with existed file.";
                    str += "\n";
                    str += "[-r_size] - will compare original size with existed file.";
                    str += "\n";
                    str += "[-agent] - will set requested UserAgent for HttpConnection (used for downloading)";
                    str += "\n";
                    str += "Example: ";
                    str += "\n";
                    str += "-agent \"Mozilla/5.0\"";
                    str += "\n";
                    str += "[-upnova_url] - set URL which contains UpdateConfig.xml. Used only for -cdn UP_NOVA_LAUNCHER";
                    str += "\n";
                    str += "Example: ";
                    str += "\n";
                    str += "http://flameria.com/UpdateConfig.xml";
                    str += "\n";
                    str += "-upnova_url \"http://flameria.com/\"";
                    str += "\n";

                    System.out.println(str);
                }
            },
    ;

    private final String[] _inLineArguments;

    StartUpArgumentsEnum(String... inLineArguments)
    {
        _inLineArguments = inLineArguments;
    }

    private String[] getInLineArguments()
    {
        return _inLineArguments;
    }

    private final static Map<String, StartUpArgumentsEnum> START_UP_ARGUMENT_BY_IN_LINE_ARGUMENT = new HashMap<>();
    static
    {
        for (StartUpArgumentsEnum argument : StartUpArgumentsEnum.values())
        {
            if (argument.getInLineArguments() == null)
            {
                continue;
            }
            for (String inLineArgument : argument.getInLineArguments())
            {
                START_UP_ARGUMENT_BY_IN_LINE_ARGUMENT.put(inLineArgument, argument);
            }
        }
    }

    public static StartUpArgumentsEnum getArgumentHandlerByInLineArgument(String inLineArgument)
    {
        return START_UP_ARGUMENT_BY_IN_LINE_ARGUMENT.getOrDefault(inLineArgument, null);
    }
}
