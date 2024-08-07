package org.index.patchdownloader.util;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.model.holders.FileInfoHolder;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class FileUtils
{
    public static final File[] EMPTY_FILE_ARRAY = new File[0];

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
        if (lowerCase)
        {
            return pathToCurrFile.substring(pathToDownload.length() + 1).replaceAll("\\\\", "/").toLowerCase();
        }
        return pathToCurrFile.substring(pathToDownload.length() + 1).replaceAll("\\\\", "/");
    }


    public static boolean createSubFolders(File originalFolder, FileInfoHolder fileInfoHolder)
    {
        if (!originalFolder.exists() && !originalFolder.mkdirs())
        {
            return false;
        }
        if (new File(originalFolder, (fileInfoHolder.getLinkPath())).exists())
        {
            return true;
        }
        String[] splitPath = fileInfoHolder.getFilePath().split("/");
        File checkCreation = originalFolder;
        for (String path : splitPath)
        {
            checkCreation = new File(checkCreation, ("/" + path));
            if (checkCreation.exists())
            {
                continue;
            }
            if (!checkCreation.mkdir())
            {
                return false;
            }
        }
        return true;
    }

    public static boolean canGetAccessToFolder(File saveFolder)
    {
        return saveFolder != null && (saveFolder.canWrite() || (!saveFolder.exists() && saveFolder.mkdir() && saveFolder.canWrite()));
    }
}
