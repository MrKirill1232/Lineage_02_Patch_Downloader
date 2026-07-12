package org.index.patchdownloader.util;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.model.holders.FileInfoHolder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class FileUtils
{
    public static final File[] EMPTY_FILE_ARRAY = new File[0];

    /**
     * EN: Defence-in-depth against path traversal (zip-slip): fails when the canonical {@code target} escapes
     *     {@code baseDir}. File lists come from untrusted sources (a crafted torrent from a mirror, a
     *     Content-Delivery-Network (CDN) file list) and their paths flow into {@code new File(baseDir, relativePath)};
     *     a {@code ..} or absolute path would otherwise write/read an arbitrary file on disk. Used by both the store
     *     stage and source-compare copy. <br>
     * RU: Эшелонированная защита от обхода пути (zip-slip): падает, если канонический {@code target} выходит за
     *     {@code baseDir}. Списки файлов приходят из недоверенных источников (поддельный торрент с зеркала,
     *     список файлов из сети доставки контента (CDN)), а их пути попадают в {@code new File(baseDir, relativePath)};
     *     без проверки {@code ..} или абсолютный путь записал бы или прочитал произвольный файл на диске. Применяется
     *     и стадией сохранения, и копированием source-compare. <br>
     * ==================================================================<br>
     * EN: @param baseDir the directory the target must stay within / RU: @param baseDir каталог, за который цель не должна выходить <br>
     * EN: @param target the resolved target file / RU: @param target вычисленный целевой файл <br>
     * EN: @param label the file's link path (for the message) / RU: @param label путь-ссылка файла (для сообщения) <br>
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
        return saveFolder != null && (saveFolder.canWrite() || (!saveFolder.exists() && saveFolder.mkdir() && saveFolder.canWrite()));
    }
}
