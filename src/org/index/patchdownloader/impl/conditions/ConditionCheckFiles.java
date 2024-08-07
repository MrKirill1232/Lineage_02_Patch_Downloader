package org.index.patchdownloader.impl.conditions;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.instancemanager.HashingManager;
import org.index.patchdownloader.interfaces.ICondition;
import org.index.patchdownloader.interfaces.IDummyLogger;
import org.index.patchdownloader.interfaces.IHashingAlgorithm;
import org.index.patchdownloader.interfaces.ILoadable;
import org.index.patchdownloader.interfaces.IThreadResponse;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.model.linkgenerator.GeneralLinkGenerator;
import org.index.patchdownloader.util.FileUtils;
import org.index.patchdownloader.util.concurrent.NamedExecutorServiceUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ConditionCheckFiles implements IDummyLogger, ILoadable, ICondition, IThreadResponse
{
    private final AtomicInteger         _simpleCounter;

    private final GeneralLinkGenerator  _linkGenerator;
    private final Set<String>           _excludeFileList;

    private Map<String, File>           _fileListMap;

    private final ScheduledExecutorService _executor;
    private final ScheduledFuture[]     _threads;

    private int _nextPercentNumber;

    public ConditionCheckFiles(GeneralLinkGenerator linkGenerator)
    {
        _simpleCounter  = new AtomicInteger(0);

        _linkGenerator  = linkGenerator;
        _excludeFileList= new CopyOnWriteArraySet<>();
        _fileListMap    = null;

        if (MainConfig.THREAD_COUNT_FOR_FILE_CHECK_IN_CONDITION > 1)
        {
            _executor = NamedExecutorServiceUtils.scheduledThreadPool((MainConfig.THREAD_COUNT_FOR_FILE_CHECK_IN_CONDITION), "Check files before downloading.");
            _threads  = new ScheduledFuture[MainConfig.THREAD_COUNT_FOR_FILE_CHECK_IN_CONDITION];
        }
        else
        {
            _executor = null;
            _threads  = null;
        }
    }

    @Override
    public void load()
    {
        IDummyLogger.log(IDummyLogger.INFO, getClass(),"load() method bump. Generating file list from existed files...", null);
        _fileListMap = FileUtils.getFileListForEasyCheck(MainConfig.DOWNLOAD_PATH, MainConfig.DEPTH_OF_FILE_CHECK, true);
        _nextPercentNumber = Math.max(0, ((_fileListMap.size() / 100) / 2));
        IDummyLogger.log(IDummyLogger.FINE, getClass(), "File list generated!" + " " + "Loaded " + _fileListMap.size() + " files.", null);
        if (_executor != null)
        {
            ArrayList<String> pathAndNameKeys = new ArrayList<>(_linkGenerator.getFileMapHolder().keySet());

            int totalSize = pathAndNameKeys.size();
            int partSize = totalSize / MainConfig.THREAD_COUNT_FOR_FILE_CHECK_IN_CONDITION;
            int remainder = totalSize % MainConfig.THREAD_COUNT_FOR_FILE_CHECK_IN_CONDITION;

            int start = 0;
            int end   = partSize;

            for (int index = 0; index < MainConfig.THREAD_COUNT_FOR_FILE_CHECK_IN_CONDITION; index++)
            {
                int finalIndex = index;
                int finalStart = start;
                int finalEnd   = end;
                _threads[index] = _executor.schedule(() -> checkFiles(finalIndex,  pathAndNameKeys.subList(finalStart, finalEnd)), 1, TimeUnit.MILLISECONDS);
                start = end;
                end   = end + partSize + (index + 1 < remainder ? 1 : 0);
            }
        }
        else
        {
            checkFiles(-1, _linkGenerator.getFileMapHolder().keySet());
        }
    }

    @Override
    public void waitCompletion()
    {
        if (_threads == null)
        {
            return;
        }
        for (ScheduledFuture<?> scheduledFuture : _threads)
        {
            try
            {
                scheduledFuture.get();
            }
            catch (Exception ignored)
            {
            }
        }
    }

    @Override
    public boolean check(FileInfoHolder fileInfoHolder)
    {
        return !_excludeFileList.contains(fileInfoHolder.getLinkPath().toLowerCase());
    }

    private void onThreadCompleteTask(int threadId)
    {
        boolean isAllThreadsComplete = true;
        if (threadId != -1 && _threads != null)
        {
            for (int index = 0; index < _threads.length; index++)
            {
                if (index == threadId || _threads[index] == null)
                {
                    continue;
                }
                isAllThreadsComplete = false;
            }
            _threads[threadId] = null;
        }
        if (!isAllThreadsComplete)
        {
            return;
        }
        _fileListMap.clear();
        _fileListMap = null;
        System.gc();
        IDummyLogger.log(IDummyLogger.FINE, getClass(), "File list generated!" + " " + "Fine files: " + _excludeFileList.size() + ". Required to update: " + (_linkGenerator.getFileMapHolder().size() - _excludeFileList.size()) + ".", null);
    }

    private void logProgress()
    {
        IDummyLogger.log(IDummyLogger.INFO, getClass(),"Progress: " + IDummyLogger.getPercentMessage(IDummyLogger.getPercentOfCompletion(_simpleCounter.get(), _linkGenerator.getFileMapHolder().size())), null);
    }

    private void checkFiles(int threadId, Collection<String> lookingFiles)
    {
        IHashingAlgorithm hashingAlgorithm = HashingManager.getAvailableHashingAlgorithm(_linkGenerator.getHashingAlgorithm(), true);
        for (String pathAndName : lookingFiles)
        {
            File existedFile = _fileListMap.getOrDefault(pathAndName, null);
            if (existedFile != null && existedFile.exists())
            {
                FileInfoHolder fileInfoHolder = _linkGenerator.getFileMapHolder().getOrDefault(pathAndName, null);

                boolean checkBySize = !MainConfig.CHECK_BY_SIZE;
                boolean checkByHash = !MainConfig.CHECK_BY_HASH_SUM;

                if (MainConfig.CHECK_BY_SIZE)
                {
                    checkBySize = fileInfoHolder.getFileLength() == ((int) existedFile.length());
                }
                if (MainConfig.CHECK_BY_HASH_SUM)
                {
                    checkByHash = fileInfoHolder.getFileHashSum().equals(hashingAlgorithm.calculateHash(existedFile));
                }
                if (checkBySize && checkByHash)
                {
                    _excludeFileList.add(pathAndName);
                }
            }
            if ((_simpleCounter.incrementAndGet() % _nextPercentNumber == 0) && MainConfig.LOGGING_FILE_CHECK_IN_CONDITION)
            {
                logProgress();
            }
        }
        onThreadCompleteTask(threadId);
    }
}
