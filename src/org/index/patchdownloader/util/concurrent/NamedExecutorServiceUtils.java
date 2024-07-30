package org.index.patchdownloader.util.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

/**
 * @author Stils
 */
public class NamedExecutorServiceUtils
{

    public static ExecutorService fixedThreadPool(int poolSize, String namePrefix)
    {
        ThreadFactory threadFactory = new ThreadFactory()
        {
            private int threadCount = 0;

            @Override
            public Thread newThread(Runnable r)
            {
                Thread thread = new Thread(r);
                thread.setName(namePrefix + "-" + threadCount++);
                return thread;
            }
        };

        return Executors.newFixedThreadPool(poolSize, threadFactory);
    }

    public static ScheduledExecutorService scheduledThreadPool(int poolSize, String namePrefix)
    {
        ThreadFactory threadFactory = new ThreadFactory()
        {
            private int threadCount = 0;

            @Override
            public Thread newThread(Runnable r)
            {
                Thread thread = new Thread(r);
                thread.setName(namePrefix + "-" + threadCount++);
                return thread;
            }
        };

        return Executors.newScheduledThreadPool(poolSize, threadFactory);
    }

    public static ScheduledThreadPoolExecutor scheduledThreadPoolExecutor(int poolSize, String namePrefix)
    {
        ThreadFactory threadFactory = new ThreadFactory()
        {
            private int threadCount = 0;

            @Override
            public Thread newThread(Runnable r)
            {
                Thread thread = new Thread(r);
                thread.setName(namePrefix + "-" + threadCount++);
                return thread;
            }
        };

        return new ScheduledThreadPoolExecutor(poolSize, threadFactory);
    }
}
