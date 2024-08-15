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

    @ConfigParameter(parameterName = "cdn_source")
    public static CDNLink CDN_SOURCE = null;

    @ConfigParameter(parameterName = "patch_version")
    public static int PATCH_VERSION_SOURCE = -1;

    @ConfigParameter(parameterName = "downloading_path", canBeNull = true)
    public static File DOWNLOAD_PATH = null;

    @ConfigParameter(parameterName = "thread_usage")
    public static boolean THREAD_USAGE = false;

    @ConfigParameter(parameterName = "parallel_downloading")
    public static int PARALLEL_DOWNLOADING = 1;

    @ConfigParameter(parameterName = "parallel_decoding")
    public static int PARALLEL_DECODING = 1;

    @ConfigParameter(parameterName = "parallel_storing")
    public static int PARALLEL_STORING = 1;

    @ConfigParameter(parameterName = "check_hash_sum")
    public static boolean CHECK_HASH_SUM = false;

    @ConfigParameter(parameterName = "check_file_size")
    public static boolean CHECK_FILE_SIZE = false;

    @ConfigParameter(parameterName = "restore_downloading")
    public static boolean RESTORE_DOWNLOADING = false;

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

    @ConfigParameter(parameterName = "acmi_like_logging")
    public static boolean ACMI_LIKE_LOGGING = false;

    @ConfigParameter(parameterName = "max_download_attempts")
    public static int MAX_DOWNLOAD_ATTEMPTS = 2;

    @ConfigParameter(parameterName = "requested_user_agent", canBeNull = true)
    public static String REQUESTED_USER_AGENT = null;

    @ConfigParameter(parameterName = "up_nova_launcher_url", canBeNull = true)
    public static String UP_NOVA_LAUNCHER_URL = null;

    // on null - PatchPath
    @ConfigParameter(parameterName = "up_nova_launcher_patch_path", canBeNull = true)
    public static String UP_NOVA_LAUNCHER_PATCH_PATH = null;

    @ConfigParameter(parameterName = "thread_on_parallel_file_check")
    public static int THREAD_COUNT_FOR_FILE_CHECK_IN_CONDITION = 1;

    @ConfigParameter(parameterName = "logging_progress_of_file_check")
    public static boolean LOGGING_FILE_CHECK_IN_CONDITION = false;

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
        if (CDN_SOURCE == CDNLink.UP_NOVA_LAUNCHER && UP_NOVA_LAUNCHER_PATCH_PATH == null)
        {
            UP_NOVA_LAUNCHER_PATCH_PATH = "PatchPath";
            info += "Upnova Patch Path is not setup. Variable " + "UP_NOVA_LAUNCHER_PATCH_PATH" + " updated. New value is \"" + UP_NOVA_LAUNCHER_PATCH_PATH.toString() + "\";" + "\n";
        }
        System.out.print(info);
    }
}
