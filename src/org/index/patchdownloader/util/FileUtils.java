package org.index.patchdownloader.util;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.interfaces.IDummyLogger;
import org.index.patchdownloader.model.holders.FileInfoHolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class FileUtils
{
    public static final File[] EMPTY_FILE_ARRAY = new File[0];

    /**
     * EN: Suffix of the temp-file pipeline's in-progress final file. A {@code .part} holds a fully decompressed
     *     file that has not yet been fsynced and renamed onto its target; an orphan one is a crash leftover. <br>
     * RU: Суффикс промежуточного итогового файла конвейера временных файлов. {@code .part} содержит полностью
     *     распакованный файл, ещё не сброшенный на диск и не переименованный в цель; сиротский — след от сбоя. <br>
     **/
    public static final String PART_FILE_SUFFIX = ".part";

    /**
     * EN: Name pattern of the raw temp files the temp-file pipeline creates ({@code dl-<uuid>.tmp}). The startup
     *     sweep deletes ONLY files matching this pattern, so a misconfigured {@code temp_file_dir} can never take
     *     user data with it. <br>
     * RU: Шаблон имени сырых временных файлов конвейера ({@code dl-<uuid>.tmp}). Стартовая очистка удаляет ТОЛЬКО
     *     файлы по этому шаблону, поэтому неверно заданный {@code temp_file_dir} не может унести пользовательские
     *     данные. <br>
     **/
    public static final String RAW_TEMP_PREFIX = "dl-";
    public static final String RAW_TEMP_SUFFIX = ".tmp";

    /**
     * EN: Defence-in-depth for an untrusted target path, used by the store stage and the source-compare copy /
     *     verify. Fails when either:
     *     <ul>
     *       <li><b>path traversal (zip-slip)</b> — the canonical {@code target} escapes {@code baseDir}. File lists
     *           come from untrusted sources (a crafted torrent from a mirror, a Content-Delivery-Network (CDN) file
     *           list) and their paths flow into {@code new File(baseDir, relativePath)}; a {@code ..} or absolute
     *           path would otherwise write/read an arbitrary file on disk.</li>
     *       <li><b>reserved Windows device name</b> — a segment of the relative path is {@code NUL / CON / COM1 …}
     *           (see {@link #isReservedDeviceName(String)}). Such a name stays INSIDE {@code baseDir}, so the
     *           traversal check passes, yet it resolves to a device rather than a file: a write silently succeeds
     *           while nothing lands on disk. Checked on the raw {@code label} segments (not the canonical path,
     *           whose normalisation could hide the device name), so every source is guarded — not just the akumu
     *           torrent path where the check first lived.</li>
     *     </ul><br>
     * RU: Эшелонированная защита недоверенного целевого пути, применяется стадией сохранения и копированием /
     *     проверкой source-compare. Падает, когда:
     *     <ul>
     *       <li><b>обход пути (zip-slip)</b> — канонический {@code target} выходит за {@code baseDir}. Списки файлов
     *           приходят из недоверенных источников (поддельный торрент с зеркала, список файлов сети доставки
     *           контента (CDN)), а их пути попадают в {@code new File(baseDir, relativePath)}; без проверки
     *           {@code ..} или абсолютный путь записал бы или прочитал произвольный файл на диске.</li>
     *       <li><b>зарезервированное имя устройства Windows</b> — сегмент относительного пути равен
     *           {@code NUL / CON / COM1 …} (см. {@link #isReservedDeviceName(String)}). Такое имя остаётся ВНУТРИ
     *           {@code baseDir}, то есть проверку обхода проходит, но указывает на устройство, а не на файл: запись
     *           «успешна», а на диск ничего не попадает. Проверяются сырые сегменты {@code label} (а не канонический
     *           путь, чья нормализация могла бы скрыть имя устройства), чтобы защищёнными были все источники — не
     *           только путь торрента akumu, где проверка появилась впервые.</li>
     *     </ul><br>
     * ==================================================================<br>
     * EN: @param baseDir the directory the target must stay within / RU: @param baseDir каталог, за который цель не должна выходить <br>
     * EN: @param target the resolved target file / RU: @param target вычисленный целевой файл <br>
     * EN: @param label the raw relative path — validated for device names AND shown in the message / RU: @param label сырой относительный путь — проверяется на имена устройств И показывается в сообщении <br>
     **/
    public static void ensureWithinDirectory(File baseDir, File target, String label) throws IOException
    {
        String canonicalBase = baseDir.getCanonicalPath();
        String canonicalTarget = target.getCanonicalPath();
        String prefix = canonicalBase.endsWith(File.separator) ? canonicalBase : canonicalBase + File.separator;
        if (!canonicalTarget.equals(canonicalBase) && !canonicalTarget.startsWith(prefix))
        {
            throw new IOException("Refusing to use '" + label + "' outside '" + canonicalBase + "' (resolves to '" + canonicalTarget + "').");
        }
        // Reserved device names stay inside baseDir yet resolve to a device, not a file — a write silently vanishes.
        // Check the RAW relative segments (label), since canonicalising a device name may normalise it away.
        if (label != null)
        {
            for (String segment : label.split("[/\\\\]"))
            {
                if (isReservedDeviceName(segment))
                {
                    throw new IOException("Refusing to use '" + label + "': path segment '" + segment + "' is a reserved Windows device name (a write would go to the device, not a file on disk).");
                }
            }
        }
    }

    /**
     * EN: Whether a single path segment is a reserved Windows device name ({@code CON, PRN, AUX, NUL, COM1-9,
     *     LPT1-9}, case-insensitive), tested against the base name before its first {@code .} because
     *     {@code new File(baseDir, "patch/nul")} resolves to the NUL device rather than a real file — the write
     *     silently succeeds while nothing lands on disk. Hoisted here from the akumu path so every source that
     *     resolves an untrusted relative path (via {@link #ensureWithinDirectory(File, File, String)}) is guarded. <br>
     * RU: Является ли отдельный сегмент пути зарезервированным именем устройства Windows ({@code CON, PRN, AUX,
     *     NUL, COM1-9, LPT1-9}, без учёта регистра); проверяется базовая часть имени до первой {@code .}, поскольку
     *     {@code new File(baseDir, "patch/nul")} указывает на устройство NUL, а не на реальный файл — запись
     *     «успешна», но на диск ничего не попадает. Поднят сюда из пути akumu, чтобы защищённым был каждый источник,
     *     разрешающий недоверенный относительный путь (через {@link #ensureWithinDirectory(File, File, String)}). <br>
     * ==================================================================<br>
     * EN: @param segment a single path segment / RU: @param segment отдельный сегмент пути <br>
     * @return <br>
     *         {true}  - EN: reserved device name / RU: зарезервированное имя устройства <br>
     *         {false} - EN: ordinary name / RU: обычное имя <br>
     **/
    public static boolean isReservedDeviceName(String segment)
    {
        if (segment == null)
        {
            return false;
        }
        int dot = segment.indexOf('.');
        String baseName = (dot < 0 ? segment : segment.substring(0, dot)).toUpperCase();
        switch (baseName)
        {
            case "CON":
            case "PRN":
            case "AUX":
            case "NUL":
            case "COM1": case "COM2": case "COM3": case "COM4": case "COM5":
            case "COM6": case "COM7": case "COM8": case "COM9":
            case "LPT1": case "LPT2": case "LPT3": case "LPT4": case "LPT5":
            case "LPT6": case "LPT7": case "LPT8": case "LPT9":
                return true;
            default:
                return false;
        }
    }

    public static File[] getFileList(File path, int depth)
    {
        List<File> fileList = new ArrayList<>();

        File[] listOfFile = (path == null || !path.exists()) ? EMPTY_FILE_ARRAY : path.listFiles();

        for (File file : ((listOfFile == null) ? EMPTY_FILE_ARRAY : listOfFile))
        {
            if (file.isDirectory() && (depth != 0))
            {
                fileList.addAll(Arrays.asList(getFileList(file, (depth - 1))));
            }
            else if (file.isFile())
            {
                fileList.add(file);
            }
        }

        return fileList.toArray(new File[0]);
    }

    public static HashMap<String, File> getFileListForEasyCheck(File path, int depth, boolean lowerCase)
    {
        HashMap<String, File> fileMap = new HashMap<>();

        File[] listOfFile = (path == null || !path.exists()) ? EMPTY_FILE_ARRAY : path.listFiles();

        for (File file : ((listOfFile == null) ? EMPTY_FILE_ARRAY : listOfFile))
        {
            if (file.isDirectory() && (depth != 0))
            {
                for (File subFile : getFileList(file, (depth -1)))
                {
                    fileMap.put(getPathAndName(path, subFile, lowerCase), subFile);
                }
            }
            else if (file.isFile())
            {
                fileMap.put(getPathAndName(path, file, lowerCase), file);
            }
        }
        return fileMap;
    }

    private static String getPathAndName(File pathToFile, File file, boolean lowerCase)
    {
        String pathToDownload;
        if (pathToFile == null)
        {
            pathToDownload = MainConfig.DOWNLOAD_PATH.getAbsolutePath();
        }
        else
        {
            pathToDownload = pathToFile.getAbsolutePath();
        }
        String pathToCurrFile = file.getAbsolutePath();
        int offset = pathToDownload.endsWith(File.separator) ? pathToDownload.length() : pathToDownload.length() + 1;
        if (lowerCase)
        {
            return pathToCurrFile.substring(offset).replaceAll("\\\\", "/").toLowerCase();
        }
        return pathToCurrFile.substring(offset).replaceAll("\\\\", "/");
    }


    public static boolean createSubFolders(File originalFolder, FileInfoHolder fileInfoHolder)
    {
        if (!originalFolder.exists() && !originalFolder.mkdirs())
        {
            return false;
        }
        File targetDir = new File(originalFolder, fileInfoHolder.getFilePath());
        try
        {
            ensureWithinDirectory(originalFolder, targetDir, fileInfoHolder.getLinkPath());
        }
        catch (IOException e)
        {
            return false;
        }
        if (new File(originalFolder, (fileInfoHolder.getLinkPath())).exists())
        {
            return true;
        }
        return targetDir.exists() || targetDir.mkdirs();
    }

    public static boolean canGetAccessToFolder(File saveFolder)
    {
        // mkdirs (not mkdir): a multi-level output path whose parents do not yet exist is still creatable/writable.
        return saveFolder != null && (saveFolder.canWrite() || (!saveFolder.exists() && saveFolder.mkdirs() && saveFolder.canWrite()));
    }

    /**
     * EN: Prepares the temp-file pipeline's working directory at the start of a run and reclaims orphans left by
     *     a previous crash/kill: it creates {@code tempDir} if missing, then (only when {@code tempDir} is proven
     *     not to be, or contain, the output root) deletes every leftover pipeline artifact inside it — both the
     *     raw temp files ({@code dl-*.tmp}) and the decompressed staging files ({@code dl-*.part}) that were never
     *     renamed onto their targets. Both kinds now live in {@code tempDir}, so the sweep never enters
     *     {@code downloadRoot}: a real list entry literally named {@code *.part} is safe. All deletion is
     *     best-effort — a file that cannot be removed is skipped, never fatal — and the method returns how many
     *     artifacts were reclaimed for a single log line.<br>
     * RU: Готовит рабочий каталог конвейера временных файлов в начале запуска и освобождает сироты, оставшиеся от
     *     предыдущего сбоя/убийства процесса: создаёт {@code tempDir} при отсутствии, затем (только если доказано,
     *     что {@code tempDir} не является корнем вывода и не содержит его) удаляет каждый оставшийся артефакт
     *     конвейера внутри него — и сырые временные файлы ({@code dl-*.tmp}), и промежуточные распакованные файлы
     *     ({@code dl-*.part}), так и не переименованные в цель. Оба вида теперь лежат в {@code tempDir}, поэтому
     *     очистка никогда не заходит в {@code downloadRoot}: реальный элемент списка с именем {@code *.part} в
     *     безопасности. Всё удаление — по возможности: файл, который нельзя удалить, пропускается, но это не
     *     фатально; метод возвращает, сколько артефактов освобождено, ради одной строки лога.<br>
     * ==================================================================<br>
     * EN: @param tempDir the temp-file directory to create and sweep / RU: @param tempDir каталог временных файлов для создания и очистки <br>
     * EN: @param downloadRoot the output root, used only to refuse a temp dir that is or contains it / RU: @param downloadRoot корень вывода, используется лишь чтобы отклонить temp-каталог, который им является или его содержит <br>
     * @return <br>
     *         {int} - EN: number of orphan temp/.part files reclaimed / RU: число освобождённых сиротских временных/.part файлов <br>
     **/
    public static int prepareTempDirectory(File tempDir, File downloadRoot)
    {
        int reclaimed = 0;
        if (tempDir != null)
        {
            if (!tempDir.exists())
            {
                tempDir.mkdirs();
            }
            if (isSafeTempDir(tempDir, downloadRoot))
            {
                // Both the raw temp files AND the .part staging files live in temp_file_dir now, so the sweep never
                // enters the output tree (a real list entry named "*.part" is safe from deletion).
                reclaimed += sweepRawTempFiles(tempDir);
            }
        }
        return reclaimed;
    }

    /**
     * EN: Whether {@code tempDir} is safe to sweep: it must NOT be, nor be an ancestor of, the output root. A
     *     misconfigured {@code temp_file_dir} pointing at the download folder (or {@code ..} resolving to its
     *     parent) would otherwise let the sweep run over real data; refuse with an ERROR so the misconfiguration
     *     surfaces. (The sweep is already name-filtered to {@code dl-*.tmp}, so this is defence-in-depth.) <br>
     * RU: Безопасно ли очищать {@code tempDir}: он НЕ должен быть корнем вывода или его предком. Неверно заданный
     *     {@code temp_file_dir}, указывающий на папку загрузки (или {@code ..}, разрешающийся в её родителя), иначе
     *     дал бы очистке пройтись по реальным данным; отказываем с ERROR, чтобы ошибка конфигурации всплыла.
     *     (Очистка и так фильтруется по имени {@code dl-*.tmp}, поэтому это эшелонированная защита.) <br>
     * ==================================================================<br>
     * EN: @param tempDir the temp directory about to be swept / RU: @param tempDir каталог временных файлов перед очисткой <br>
     * EN: @param downloadRoot the output root that must not be inside tempDir / RU: @param downloadRoot корень вывода, который не должен быть внутри tempDir <br>
     * @return <br>
     *         {boolean} - EN: true when sweeping tempDir is safe / RU: true, когда очищать tempDir безопасно <br>
     **/
    private static boolean isSafeTempDir(File tempDir, File downloadRoot)
    {
        if (downloadRoot == null)
        {
            return true;
        }
        try
        {
            String temp = tempDir.getCanonicalPath();
            String root = downloadRoot.getCanonicalPath();
            String tempPrefix = temp.endsWith(File.separator) ? temp : temp + File.separator;
            if (root.equals(temp) || root.startsWith(tempPrefix))
            {
                IDummyLogger.log(IDummyLogger.ERROR, "Refusing to sweep temp_file_dir '" + temp + "' — it is or contains the output root '" + root + "'. Fix 'temp_file_dir' in Main.ini; skipping the sweep.");
                return false;
            }
            return true;
        }
        catch (IOException e)
        {
            // Cannot canonicalize -> do not risk sweeping.
            IDummyLogger.log(IDummyLogger.WARNING, "Cannot resolve temp_file_dir vs output root; skipping the temp sweep: " + e);
            return false;
        }
    }

    /**
     * EN: Deletes only the temp pipeline's own raw temp files ({@code dl-*.tmp}, non-recursively) inside
     *     {@code dir}, returning the count removed. Never touches any other file, so even a misdirected temp dir
     *     cannot lose user data. <br>
     * RU: Удаляет только собственные сырые временные файлы конвейера ({@code dl-*.tmp}, без рекурсии) внутри
     *     {@code dir}, возвращая число удалённых. Никогда не трогает иные файлы, поэтому даже неверно указанный
     *     каталог не потеряет пользовательские данные. <br>
     * ==================================================================<br>
     * EN: @param dir the temp directory to sweep / RU: @param dir каталог временных файлов для очистки <br>
     * @return <br>
     *         {int} - EN: number of raw temp files deleted / RU: число удалённых сырых временных файлов <br>
     **/
    private static int sweepRawTempFiles(File dir)
    {
        int removed = 0;
        File[] entries = dir.listFiles();
        if (entries == null)
        {
            return 0;
        }
        for (File entry : entries)
        {
            String name = entry.getName();
            if (entry.isFile() && name.startsWith(RAW_TEMP_PREFIX) && (name.endsWith(RAW_TEMP_SUFFIX) || name.endsWith(PART_FILE_SUFFIX)) && deleteQuietly(entry))
            {
                removed++;
            }
        }
        return removed;
    }

    /**
     * EN: Deletes a file/empty directory best-effort, swallowing any IO error; returns whether it is now gone. <br>
     * RU: Удаляет файл/пустой каталог по возможности, поглощая любую ошибку ввода-вывода; возвращает, исчез ли он. <br>
     * ==================================================================<br>
     * EN: @param file the file to delete / RU: @param file файл для удаления <br>
     * @return <br>
     *         {boolean} - EN: true if the file no longer exists / RU: true, если файла больше нет <br>
     **/
    private static boolean deleteQuietly(File file)
    {
        try
        {
            return Files.deleteIfExists(file.toPath());
        }
        catch (IOException ignored)
        {
            return false;
        }
    }
}
