package org.index.patchdownloader.config.configs;

import org.index.patchdownloader.config.IConfig;
import org.index.patchdownloader.config.annotations.ConfigParameter;
import org.index.patchdownloader.enums.CDNLink;

import java.io.File;

/**
 * @author Index
 */
public class MainConfig implements IConfig
{
    @ConfigParameter(ignoredParameter = true)
    public static File PATH_TO_RUNNING = null;

    @ConfigParameter(parameterName = "cdn_source", setParameterMethod = "setCdnSource")
    public static CDNLink CDN_SOURCE = null;

    @ConfigParameter(parameterName = "patch_version")
    public static int PATCH_VERSION_SOURCE = -1;

    @ConfigParameter(parameterName = "downloading_path", canBeNull = true)
    public static File DOWNLOAD_PATH = null;

    @ConfigParameter(parameterName = "developer")
    public static boolean DEVELOPER = false;

    @ConfigParameter(parameterName = "parallel_downloading")
    public static int PARALLEL_DOWNLOADING = 1;

    @ConfigParameter(parameterName = "parallel_decoding")
    public static int PARALLEL_DECODING = 1;

    @ConfigParameter(parameterName = "parallel_storing")
    public static int PARALLEL_STORING = 1;

    @ConfigParameter(parameterName = "check_hash_sum")
    public static boolean CHECK_HASH_SUM = false;

    @ConfigParameter(parameterName = "restore_downloading")
    public static boolean RESTORE_DOWNLOADING = false;

    @ConfigParameter(parameterName = "check_files_before_downloading")
    public static boolean CHECK_BEFORE_DOWNLOADING = false;

    @ConfigParameter(parameterName = "check_files_by_name")
    public static boolean CHECK_BY_NAME = false;

    @ConfigParameter(parameterName = "check_files_by_size")
    public static boolean CHECK_BY_SIZE = false;

    @ConfigParameter(parameterName = "check_files_by_hashsum")
    public static boolean CHECK_BY_HASH_SUM = false;

    @ConfigParameter(parameterName = "include_file_filter", canBeNull = true)
    public static String INCLUDE_FILE_FILTER = "";

    @ConfigParameter(parameterName = "exclude_file_filter", canBeNull = true)
    public static String EXCLUDE_FILE_FILTER = "";

    // C://dummy01//dummy02//lineage_2//system//locales//plugins
    // DOWNLOAD_PATH = C://dummy01//dummy02//lineage_2//
    // DEPTH_OF_FILE_CHECK = 2
    // lineage_2    - depth 0 [include in file list]
    // system       - depth 1 [include in file list]
    // locales      - depth 2 [include in file list]
    // plugins      - depth 3 [NOT include in file list]
    // -1 - ignore depth
    @ConfigParameter(parameterName = "depth_of_file_check")
    public static int DEPTH_OF_FILE_CHECK = 3;

    @Override
    public void onLoad()
    {
        try
        {
            PATH_TO_RUNNING = new File("").getCanonicalFile();
        }
        catch (Exception e)
        {
            /**
             * @apiNote https://docs.oracle.com/javase/tutorial/essential/environment/sysprop.html
             */
            PATH_TO_RUNNING = new File(System.getProperty("user.dir"));
        }
        String info = "";

        if (DOWNLOAD_PATH == null)
        {
            DOWNLOAD_PATH = new File(PATH_TO_RUNNING + "/output");
            info += "Downloading path is not setup. Variable " + "DOWNLOAD_PATH" + " updated. New value is \"" + DOWNLOAD_PATH.toString() + "\";" + "\n";
        }
        if (DEVELOPER)
        {
            PARALLEL_DOWNLOADING = 1;
            info += "Developer option enabled. Variable " + "PARALLEL_DOWNLOADING" + " updated. New value is \"" + PARALLEL_DOWNLOADING + "\";" + "\n";
            PARALLEL_DECODING = 1;
            info += "Developer option enabled. Variable " + "PARALLEL_DECODING" + " updated. New value is \"" + PARALLEL_DECODING + "\";" + "\n";
            PARALLEL_STORING = 1;
            info += "Developer option enabled. Variable " + "PARALLEL_STORING" + " updated. New value is \"" + PARALLEL_STORING + "\";" + "\n";
            CHECK_HASH_SUM = false;
            info += "Developer option enabled. Variable " + "CHECK_HASH_SUM" + " updated. New value is \"" + CHECK_HASH_SUM + "\";" + "\n";
            RESTORE_DOWNLOADING = false;
            info += "Developer option enabled. Variable " + "RESTORE_DOWNLOADING" + " updated. New value is \"" + RESTORE_DOWNLOADING + "\";" + "\n";
            CHECK_BEFORE_DOWNLOADING = false;
            info += "Developer option enabled. Variable " + "CHECK_BEFORE_DOWNLOADING" + " updated. New value is \"" + CHECK_BEFORE_DOWNLOADING + "\";" + "\n";
        }
        System.out.print(info);
    }

    public static void setCdnSource(String value)
    {
        if (value == null)
        {
            CDN_SOURCE = null;
            System.out.print("CDN Source is not setup. Variable " + "CDN_SOURCE" + " updated. New value is \"" + String.valueOf(CDN_SOURCE) + "\";" + "\n");
        }
        else
        {
            CDN_SOURCE = CDNLink.valueOf(value.toUpperCase());
        }
    }
}
