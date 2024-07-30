package org.index.protocoldownloader.impl;

import org.index.protocoldownloader.config.configs.MainConfig;
import org.index.protocoldownloader.instancemanager.CheckSumManager;
import org.index.protocoldownloader.util.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CompareBothSystems
{
    public static void startComparing(File pathToFirstSystem, File pathToSecondSystem)
    {
        File[] filesOfFSystem = FileUtils.getFileList(pathToFirstSystem , true);
        File[] filesOfSSystem = FileUtils.getFileList(pathToSecondSystem, true);

        Map<String, File> mapOfFirstSystem  = getPatchLikeMapOfFiles(pathToFirstSystem  , filesOfFSystem);
        Map<String, File> mapOfSecondSystem = getPatchLikeMapOfFiles(pathToSecondSystem , filesOfSSystem);

        Map<String, File> comparing01;
        Map<String, File> comparing02;
        if (mapOfFirstSystem.size() >= mapOfSecondSystem.size())
        {
            comparing01 = mapOfFirstSystem  ;
            comparing01 = mapOfSecondSystem ;
        }
        else
        {
            comparing01 = mapOfSecondSystem ;
            comparing01 = mapOfFirstSystem  ;
        }
        // reason , patchKey
        Map<String, Set<String>> notComparedFiles = new HashMap<>();
        for (String patchKey : comparing01.keySet())
        {
            File fileOfFsystem = mapOfFirstSystem   .getOrDefault(patchKey, null);
            File fileOfSsystem = mapOfSecondSystem  .getOrDefault(patchKey, null);
            if (fileOfFsystem == null || fileOfSsystem == null)
            {
                notComparedFiles.computeIfAbsent(("NOT_EXIST_IN_" + ((fileOfFsystem == null) ? 1 : 2) + "_SYSTEM"), v -> new HashSet<>()).add(patchKey);
            }
            else if (fileOfFsystem.length() != fileOfSsystem.length())
            {
                notComparedFiles.computeIfAbsent(("WRONG_FILE_SIZE"), v -> new HashSet<>()).add(patchKey);
            }
            else if (!getHashSum(fileOfFsystem).equalsIgnoreCase(getHashSum(fileOfSsystem)))
            {
                notComparedFiles.computeIfAbsent(("WRONG_HASHSUM"), v -> new HashSet<>()).add(patchKey);
            }
        }

        StringBuilder reasonBuilder = new StringBuilder();
        for (String reason : notComparedFiles.keySet())
        {
            reasonBuilder.append("Reason: ").append(reason).append("; File list:").append("\n");
            for (String patchKey : notComparedFiles.get(reason))
            {
                reasonBuilder.append("- ").append(patchKey).append("\n");
            }
        }
        System.out.println(reasonBuilder.toString());
    }

    private static Map<String, File> getPatchLikeMapOfFiles(File checkFolder, File[] files)
    {
        Map<String, File> patchLikeMap = new HashMap<>(files.length);
        for (File file : files)
        {
            patchLikeMap.put(getPathAndName(checkFolder, file), file);
        }
        return patchLikeMap;
    }

    private static String getPathAndName(File checkFolder, File file)
    {
        String pathToDownload = checkFolder.getAbsolutePath();
        String pathToCurrFile = file.getAbsolutePath();
        return pathToCurrFile.substring(pathToDownload.length() + 1);
    }

    private static String getHashSum(File file)
    {
        try (FileInputStream fis = new FileInputStream(file))
        {
            return CheckSumManager.getHashOfFile(fis.readAllBytes());
        }
        catch (IOException ignored)
        {

        }
        return "";
    }

    public static void main(String[] args)
    {
        startComparing(new File("Z:\\SourceProjects\\output\\system"), new File("D:\\LINEAGE_2_ARCHIVE\\ACTUAL\\NC_KOREAN\\Lineage2_KR\\system_original"));
    }
}
