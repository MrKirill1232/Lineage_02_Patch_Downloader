package org.index.patchdownloader.util;

import org.index.patchdownloader.model.holders.LinkHolder;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
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

    public static boolean createSubFolders(File originalFolder, LinkHolder linkHolder)
    {
        if (!originalFolder.exists() && !originalFolder.mkdirs())
        {
            return false;
        }
        if (new File(originalFolder, (linkHolder.getLinkPath())).exists())
        {
            return true;
        }
        String[] splitPath = linkHolder.getFilePath().split("/");
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
