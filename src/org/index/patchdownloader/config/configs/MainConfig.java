package org.index.patchdownloader.config.configs;

import java.io.File;

import git.index.configparser.annotations.ConfigParameterVariable;
import org.index.patchdownloader.enums.CDNLink;

/**
 * EN: Configuration holder — a POJO of public static fields annotated for the {@code ConfigFieldParser}
 *     library. Existing {@code .ini} keys are kept 1:1. Derived defaults and clamps live in
 *     {@link #onEndLoad()} (the library does not clamp: out-of-range values fall back to the default).
 *     Loaded through {@code MainConfigHolder}.<br>
 * RU: Хранилище конфигурации — POJO из публичных статических полей с аннотациями для библиотеки
 *     {@code ConfigFieldParser}. Существующие ключи {@code .ini} сохранены 1:1. Производные значения по
 *     умолчанию и ограничения — в {@link #onEndLoad()} (библиотека не ограничивает: значения вне
 *     диапазона откатываются к default). Загружается через {@code MainConfigHolder}.<br>
 *
 * @author Index
 **/
public class MainConfig
{
    @ConfigParameterVariable(ignoredParameter = true, notPresentedInConfig = true)
    public static File PATH_TO_RUNNING = null;

    @ConfigParameterVariable(parameterName = "cdn_source")
    public static CDNLink CDN_SOURCE = null;

    @ConfigParameterVariable(parameterName = "patch_version", defaultValue = "-1")
    public static int PATCH_VERSION_SOURCE = -1;

    @ConfigParameterVariable(parameterName = "downloading_path", defaultValue = "output")
    public static File DOWNLOAD_PATH = null;

    @ConfigParameterVariable(parameterName = "thread_usage", defaultValue = "false")
    public static boolean THREAD_USAGE = false;

    @ConfigParameterVariable(parameterName = "parallel_downloading", defaultValue = "1")
    public static int PARALLEL_DOWNLOADING = 1;

    @ConfigParameterVariable(parameterName = "parallel_decoding", defaultValue = "1")
    public static int PARALLEL_DECODING = 1;

    @ConfigParameterVariable(parameterName = "parallel_storing", defaultValue = "1")
    public static int PARALLEL_STORING = 1;

    @ConfigParameterVariable(parameterName = "check_hash_sum", defaultValue = "false")
    public static boolean CHECK_HASH_SUM = false;

    @ConfigParameterVariable(parameterName = "check_file_size", defaultValue = "false")
    public static boolean CHECK_FILE_SIZE = false;

    @ConfigParameterVariable(parameterName = "restore_downloading", defaultValue = "false")
    public static boolean RESTORE_DOWNLOADING = false;

    @ConfigParameterVariable(parameterName = "check_files_by_name", defaultValue = "false")
    public static boolean CHECK_BY_NAME = false;

    @ConfigParameterVariable(parameterName = "check_files_by_size", defaultValue = "false")
    public static boolean CHECK_BY_SIZE = false;

    @ConfigParameterVariable(parameterName = "check_files_by_hashsum", defaultValue = "false")
    public static boolean CHECK_BY_HASH_SUM = false;

    @ConfigParameterVariable(parameterName = "include_file_filter")
    public static String INCLUDE_FILE_FILTER = "";

    @ConfigParameterVariable(parameterName = "exclude_file_filter")
    public static String EXCLUDE_FILE_FILTER = "";

    @ConfigParameterVariable(parameterName = "depth_of_file_check", defaultValue = "3")
    public static int DEPTH_OF_FILE_CHECK = 3;

    @ConfigParameterVariable(parameterName = "acmi_like_logging", defaultValue = "false")
    public static boolean ACMI_LIKE_LOGGING = false;

    @ConfigParameterVariable(parameterName = "max_download_attempts", defaultValue = "2")
    public static int MAX_DOWNLOAD_ATTEMPTS = 2;

    @ConfigParameterVariable(parameterName = "requested_user_agent")
    public static String REQUESTED_USER_AGENT = null;

    @ConfigParameterVariable(parameterName = "up_nova_launcher_url")
    public static String UP_NOVA_LAUNCHER_URL = null;

    @ConfigParameterVariable(parameterName = "up_nova_launcher_patch_path")
    public static String UP_NOVA_LAUNCHER_PATCH_PATH = null;

    @ConfigParameterVariable(parameterName = "thread_on_parallel_file_check", defaultValue = "1")
    public static int THREAD_COUNT_FOR_FILE_CHECK_IN_CONDITION = 1;

    @ConfigParameterVariable(parameterName = "logging_progress_of_file_check", defaultValue = "false")
    public static boolean LOGGING_FILE_CHECK_IN_CONDITION = false;

    @ConfigParameterVariable(parameterName = "max_inflight_memory_mb", defaultValue = "512")
    public static int MAX_INFLIGHT_MEMORY_MB = 512;

    @ConfigParameterVariable(parameterName = "max_inflight_files", defaultValue = "16")
    public static int MAX_INFLIGHT_FILES = 16;

    @ConfigParameterVariable(parameterName = "retry_hard_cap", defaultValue = "50")
    public static int RETRY_HARD_CAP = 50;

    @ConfigParameterVariable(parameterName = "file_retry_deadline_seconds", defaultValue = "300")
    public static int FILE_RETRY_DEADLINE_SECONDS = 300;

    @ConfigParameterVariable(parameterName = "parallel_parts_per_file", defaultValue = "4")
    public static int PARALLEL_PARTS_PER_FILE = 4;

    @ConfigParameterVariable(parameterName = "self_split_min_mb", defaultValue = "10")
    public static int SELF_SPLIT_MIN_MB = 10;

    @ConfigParameterVariable(parameterName = "self_split_chunk_mb", defaultValue = "5")
    public static int SELF_SPLIT_CHUNK_MB = 5;

    @ConfigParameterVariable(parameterName = "akumu_folder_url")
    public static String AKUMU_FOLDER_URL = null;

    @ConfigParameterVariable(parameterName = "akumu_max_connections", defaultValue = "2")
    public static int AKUMU_MAX_CONNECTIONS = 2;

    @ConfigParameterVariable(parameterName = "akumu_retry_backoff_ms", defaultValue = "2000")
    public static int AKUMU_RETRY_BACKOFF_MS = 2000;

    @ConfigParameterVariable(parameterName = "source_compare_path")
    public static File SOURCE_COMPARE_PATH = null;

    @ConfigParameterVariable(parameterName = "sc_check_size", defaultValue = "true")
    public static boolean SC_CHECK_SIZE = true;

    @ConfigParameterVariable(parameterName = "sc_check_hash", defaultValue = "true")
    public static boolean SC_CHECK_HASH = true;

    @ConfigParameterVariable(parameterName = "sc_trust_partial", defaultValue = "false")
    public static boolean SC_TRUST_PARTIAL = false;

    /**
     * EN: Post-load hook invoked by the library after all fields are parsed. Resolves the running path,
     *     fills derived defaults (download path, UpNova patch path), normalises empty nullable strings to
     *     {@code null}, and clamps numeric fields that must be at least 1 (the library itself does not
     *     clamp). <br>
     * RU: Хук, вызываемый библиотекой после разбора всех полей. Определяет путь запуска,
     *     заполняет производные значения (путь загрузки, UpNova patch path), нормализует пустые nullable
     *     строки в {@code null} и ограничивает числовые поля минимумом 1 (сама библиотека не ограничивает). <br>
     **/
    private void onEndLoad()
    {
        try
        {
            PATH_TO_RUNNING = new File("").getCanonicalFile();
        }
        catch (Exception e)
        {
            PATH_TO_RUNNING = new File(System.getProperty("user.dir"));
        }

        REQUESTED_USER_AGENT = emptyToNull(REQUESTED_USER_AGENT);
        UP_NOVA_LAUNCHER_URL = emptyToNull(UP_NOVA_LAUNCHER_URL);
        UP_NOVA_LAUNCHER_PATCH_PATH = emptyToNull(UP_NOVA_LAUNCHER_PATCH_PATH);
        AKUMU_FOLDER_URL = emptyToNull(AKUMU_FOLDER_URL);

        if (DOWNLOAD_PATH == null || DOWNLOAD_PATH.getPath().isEmpty())
        {
            DOWNLOAD_PATH = new File(PATH_TO_RUNNING, "output");
        }
        else if (!DOWNLOAD_PATH.isAbsolute())
        {
            DOWNLOAD_PATH = new File(PATH_TO_RUNNING, DOWNLOAD_PATH.getPath());
        }
        if (CDN_SOURCE == CDNLink.UP_NOVA_LAUNCHER && UP_NOVA_LAUNCHER_PATCH_PATH == null)
        {
            UP_NOVA_LAUNCHER_PATCH_PATH = "PatchPath";
        }

        // source-compare root: an empty path disables the feature; a relative path is resolved against the run dir.
        if (SOURCE_COMPARE_PATH != null && SOURCE_COMPARE_PATH.getPath().isEmpty())
        {
            SOURCE_COMPARE_PATH = null;
        }
        else if (SOURCE_COMPARE_PATH != null && !SOURCE_COMPARE_PATH.isAbsolute())
        {
            SOURCE_COMPARE_PATH = new File(PATH_TO_RUNNING, SOURCE_COMPARE_PATH.getPath());
        }

        PARALLEL_DOWNLOADING = Math.max(1, PARALLEL_DOWNLOADING);
        PARALLEL_DECODING = Math.max(1, PARALLEL_DECODING);
        PARALLEL_STORING = Math.max(1, PARALLEL_STORING);
        THREAD_COUNT_FOR_FILE_CHECK_IN_CONDITION = Math.max(1, THREAD_COUNT_FOR_FILE_CHECK_IN_CONDITION);
        MAX_INFLIGHT_MEMORY_MB = Math.max(1, MAX_INFLIGHT_MEMORY_MB);
        MAX_INFLIGHT_FILES = Math.max(1, MAX_INFLIGHT_FILES);
        RETRY_HARD_CAP = Math.max(1, RETRY_HARD_CAP);
        FILE_RETRY_DEADLINE_SECONDS = Math.max(1, FILE_RETRY_DEADLINE_SECONDS);
        PARALLEL_PARTS_PER_FILE = Math.max(1, PARALLEL_PARTS_PER_FILE);
        SELF_SPLIT_MIN_MB = Math.max(0, SELF_SPLIT_MIN_MB);
        SELF_SPLIT_CHUNK_MB = Math.max(1, SELF_SPLIT_CHUNK_MB);

        AKUMU_MAX_CONNECTIONS = Math.max(1, AKUMU_MAX_CONNECTIONS);
        AKUMU_RETRY_BACKOFF_MS = Math.max(0, AKUMU_RETRY_BACKOFF_MS);
        if (CDN_SOURCE == CDNLink.AKUMU)
        {
            // Akumu limits concurrent connections and runs an antibot: behave like one browser session.
            // Keep the download stage no wider than the connection budget and never open extra part-connections.
            PARALLEL_DOWNLOADING = Math.min(PARALLEL_DOWNLOADING, AKUMU_MAX_CONNECTIONS);
            PARALLEL_PARTS_PER_FILE = 1;
        }
    }

    /**
     * EN: Returns {@code null} for a null or empty string, otherwise the string unchanged. <br>
     * RU: Возвращает {@code null} для null или пустой строки, иначе строку без изменений. <br>
     * ==================================================================<br>
     * EN: @param value the string to normalise / RU: @param value нормализуемая строка <br>
     * @return <br>
     *         {String} - EN: null if empty/null, else the value / RU: null если пусто/null, иначе значение <br>
     **/
    private static String emptyToNull(String value)
    {
        if (value == null || value.isEmpty())
        {
            return null;
        }
        return value;
    }
}
