package org.index.patchdownloader.instancemanager;

import org.index.patchdownloader.interfaces.IRequest;
import org.index.patchdownloader.util.concurrent.ScheduledThreadPoolExecutorWrapper;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractQueueManager
{
    protected final Queue<IRequest>     _requestQueue;

    private ScheduledThreadPoolExecutor _executor;
    private ScheduledFuture[]           _threads;
    private boolean[]                   _threadsStatus;
    private AtomicBoolean               _globalBlocker;

    public AbstractQueueManager()
    {
        _requestQueue = new ConcurrentLinkedQueue<>();
    }

    public void addRequestToQueue(IRequest request)
    {
        _requestQueue.add(request);
    }

    public void initThreadPool(int corePoolSize, int threadCount)
    {
        _globalBlocker = new AtomicBoolean(false);

        _executor = ScheduledThreadPoolExecutorWrapper.createSingleScheduledExecutor(corePoolSize, getClass().getSimpleName());
        _threads = new ScheduledFuture[threadCount];
        _threadsStatus = new boolean[threadCount];

        for (int threadId = 0; threadId < threadCount; threadId++)
        {
            _threadsStatus[threadId] = false;
        }
        for (int threadId = 0; threadId < threadCount; threadId++)
        {
            int finalThreadId = threadId;
            _threads[threadId] = _executor.scheduleWithFixedDelay(() -> startTask(finalThreadId), 0, 10, TimeUnit.MILLISECONDS);
        }
    }

    private void startTask(int threadId)
    {
        if (_globalBlocker.get())
        {
            return;
        }
        if (_threadsStatus[threadId])
        {
            return;
        }
        _threadsStatus[threadId] = true;
        runQueueEntry();
        _threadsStatus[threadId] = false;
    }

    public abstract void runQueueEntry();

    public int getCountOfTaskInQueue()
    {
        return _requestQueue.size();
    }
}