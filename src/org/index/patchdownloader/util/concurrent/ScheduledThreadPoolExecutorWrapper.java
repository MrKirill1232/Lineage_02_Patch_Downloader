package org.index.patchdownloader.util.concurrent;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Stils
 */
public class ScheduledThreadPoolExecutorWrapper
{

    public static ScheduledThreadPoolExecutor createSingleScheduledExecutor(int corePoolSize, String name)
    {
        final ScheduledThreadPoolExecutor executor = NamedExecutorServiceUtils.scheduledThreadPoolExecutor(corePoolSize, name);

        executor.setRejectedExecutionHandler(new RejectedExecutionHandlerImpl());
        executor.setRemoveOnCancelPolicy(true);
        executor.prestartAllCoreThreads();
        executor.setMaximumPoolSize(20000);
        executor.scheduleWithFixedDelay(executor::purge, 30L, 30L, TimeUnit.SECONDS);

        return executor;
    }
}
