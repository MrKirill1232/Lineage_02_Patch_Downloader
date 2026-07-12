package org.index.patchdownloader.impl.downloader;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.instancemanager.DecompressStageManager;
import org.index.patchdownloader.instancemanager.DownloadStageManager;
import org.index.patchdownloader.instancemanager.StoreStageManager;
import org.index.patchdownloader.interfaces.ICondition;
import org.index.patchdownloader.interfaces.IDummyLogger;
import org.index.patchdownloader.interfaces.IPipelineSink;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.model.linkgenerator.GeneralLinkGenerator;
import org.index.patchdownloader.model.pipeline.FileDownloadTask;
import org.index.patchdownloader.model.pipeline.InFlightBudget;
import org.index.patchdownloader.model.pipeline.enums.DownloadFailureType;
import org.index.patchdownloader.util.FileUtils;

/**
 * EN: Single per-run owner of the pipeline. Builds one {@link FileDownloadTask} per downloadable file
 *     (skipping condition-filtered / un-creatable ones), wires the stage chain, throttles in-flight
 *     work via a memory (MB) + file-count budget, and detects completion deterministically with a
 *     counter + latch (no polling). Exits once, from the main thread, with a summary and an exit code
 *     that reflects failures.<br>
 * RU: Единственный на запуск владелец конвейера. Создаёт по одной {@link FileDownloadTask} на
 *     скачиваемый файл (пропуская отфильтрованные условиями / несоздаваемые), связывает цепочку
 *     стадий, ограничивает одновременную работу бюджетом памяти (МБ) + числа файлов и детерминированно
 *     определяет завершение счётчиком + защёлкой (без опроса). Выходит один раз из главного потока со
 *     сводкой и кодом выхода, отражающим сбои.<br>
 **/
public class PipelineCoordinator implements IPipelineSink
{
    private final GeneralLinkGenerator _generator;

    private final InFlightBudget _budget;

    private final AtomicInteger _remaining;
    private final AtomicInteger _done;
    private final AtomicInteger _failed;
    private final Queue<FileDownloadTask> _failedFiles;
    private final CountDownLatch _completionLatch;

    private int _total;

    /**
     * EN: Builds the coordinator for a run and its {@link InFlightBudget} (sized from config + heap). <br>
     * RU: Создаёт координатор для запуска и его {@link InFlightBudget} (рассчитан из конфига + кучи). <br>
     * ==================================================================<br>
     * EN: @param generator the loaded link generator providing the file map / RU: @param generator загруженный генератор ссылок с картой файлов <br>
     **/
    public PipelineCoordinator(GeneralLinkGenerator generator)
    {
        _generator = generator;

        _budget = new InFlightBudget(MainConfig.MAX_INFLIGHT_MEMORY_MB, MainConfig.MAX_INFLIGHT_FILES);

        _remaining = new AtomicInteger(0);
        _done = new AtomicInteger(0);
        _failed = new AtomicInteger(0);
        _failedFiles = new ConcurrentLinkedQueue<>();
        _completionLatch = new CountDownLatch(1);
        _total = 0;
    }

    /**
     * EN: Runs the whole pipeline on the main thread: builds tasks, wires + starts stages, submits all
     *     tasks (throttled by budget), blocks on the completion latch, then shuts down and exits. <br>
     * RU: Запускает весь конвейер в главном потоке: строит задачи, связывает + запускает стадии,
     *     отправляет все задачи (с учётом бюджета), блокируется на защёлке завершения, затем
     *     останавливает и выходит. <br>
     **/
    public void run()
    {
        List<FileDownloadTask> tasks = buildTasks();
        _total = tasks.size();
        _remaining.set(_total);
        if (_total == 0)
        {
            IDummyLogger.log(IDummyLogger.WARNING, "Nothing to download (all files skipped or file list empty).");
            finish();
            return;
        }

        wireChain();
        startStages();

        for (FileDownloadTask task : tasks)
        {
            _budget.acquire(task);
            DownloadStageManager.getInstance().submit(task);
        }

        try
        {
            _completionLatch.await();
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
        finish();
    }

    /**
     * EN: Builds the task list from the generator's file map, skipping files rejected by conditions or
     *     whose destination folders cannot be created (these are NOT counted towards completion). <br>
     * RU: Строит список задач из карты файлов генератора, пропуская файлы, отклонённые условиями или у
     *     которых нельзя создать целевые папки (они НЕ учитываются в завершении). <br>
     * ==================================================================<br>
     * @return <br>
     *         {List} - EN: the tasks to process / RU: задачи для обработки <br>
     **/
    private List<FileDownloadTask> buildTasks()
    {
        List<ICondition> conditions = ICondition.loadConditions(_generator);
        List<FileDownloadTask> tasks = new ArrayList<>();
        for (FileInfoHolder fileInfo : _generator.getFileMapHolder().values())
        {
            if (!ICondition.checkCondition(conditions, fileInfo))
            {
                continue;
            }
            if (!FileUtils.createSubFolders(MainConfig.DOWNLOAD_PATH, fileInfo))
            {
                IDummyLogger.log(IDummyLogger.ERROR, "Cannot create destination folder for '" + fileInfo.getLinkPath() + "'. Ignoring.");
                continue;
            }
            tasks.add(new FileDownloadTask(fileInfo));
        }
        return tasks;
    }

    /**
     * EN: Links the three stages (download -> decompress -> store -> sink), sets this coordinator as
     *     their sink, and injects the run's hash algorithm into the decompress stage. <br>
     * RU: Связывает три стадии (download -> decompress -> store -> sink), назначает этот координатор
     *     их приёмником и передаёт алгоритм хеша запуска в стадию распаковки. <br>
     **/
    private void wireChain()
    {
        DownloadStageManager download = DownloadStageManager.getInstance();
        DecompressStageManager decompress = DecompressStageManager.getInstance();
        StoreStageManager store = StoreStageManager.getInstance();

        download.setNext(decompress);
        decompress.setNext(store);
        store.setNext(null);

        download.setSink(this);
        decompress.setSink(this);
        store.setSink(this);

        decompress.setHashType(_generator.getHashingAlgorithm());
    }

    /**
     * EN: Starts the three stage pools. When threaded usage is disabled, every stage runs with
     *     parallelism 1. <br>
     * RU: Запускает три пула стадий. Если многопоточность отключена, каждая стадия работает с
     *     параллелизмом 1. <br>
     **/
    private void startStages()
    {
        boolean threaded = MainConfig.THREAD_USAGE;
        DownloadStageManager.getInstance().start(threaded ? MainConfig.PARALLEL_DOWNLOADING : 1);
        DecompressStageManager.getInstance().start(threaded ? MainConfig.PARALLEL_DECODING : 1);
        StoreStageManager.getInstance().start(threaded ? MainConfig.PARALLEL_STORING : 1);
    }

    /**
     * EN: Drains and stops the three stage pools. Called only after the completion latch fired (all tasks
     *     terminal), so no in-flight work is lost. <br>
     * RU: Дожидается завершения задач и останавливает три пула стадий. Вызывается только после срабатывания
     *     защёлки завершения (все задачи терминальны), поэтому незавершённая (выполняемая) работа не теряется. <br>
     **/
    private void shutdownStages()
    {
        DownloadStageManager.getInstance().shutdown();
        DecompressStageManager.getInstance().shutdown();
        StoreStageManager.getInstance().shutdown();
    }

    /**
     * EN: Terminal-success callback (last stage): counts the file and runs shared terminal bookkeeping. <br>
     * RU: Колбэк терминального успеха (последняя стадия): считает файл и выполняет общий терминальный
     *     учёт. <br>
     * ==================================================================<br>
     * EN: @param task the completed task / RU: @param task завершённая задача <br>
     **/
    @Override
    public void onDone(FileDownloadTask task)
    {
        _done.incrementAndGet();
        logProgress(task, true);
        onTerminal(task);
    }

    /**
     * EN: Terminal-failure callback: records the failed file and runs shared terminal bookkeeping. <br>
     * RU: Колбэк терминального сбоя: фиксирует проваленный файл и выполняет общий терминальный учёт. <br>
     * ==================================================================<br>
     * EN: @param task the failed task / RU: @param task проваленная задача <br>
     * EN: @param failure the failure reason / RU: @param failure причина сбоя <br>
     **/
    @Override
    public void onFailed(FileDownloadTask task, DownloadFailureType failure)
    {
        _failed.incrementAndGet();
        _failedFiles.add(task);
        logProgress(task, false);
        onTerminal(task);
    }

    /**
     * EN: Shared terminal bookkeeping — MUST run exactly once per counted task (via onDone/onFailed):
     *     releases the task's budget and counts the completion latch down when the last task finishes.
     *     Never call this directly for a task already reported through onDone/onFailed (double-count). <br>
     * RU: Общий терминальный учёт — ДОЛЖЕН выполняться ровно один раз на учтённую задачу (через
     *     onDone/onFailed): освобождает бюджет задачи и опускает защёлку завершения, когда завершается
     *     последняя задача. Не вызывайте напрямую для задачи, уже отчитанной через onDone/onFailed. <br>
     * ==================================================================<br>
     * EN: @param task the task reaching a terminal state / RU: @param task задача, достигшая терминала <br>
     **/
    private void onTerminal(FileDownloadTask task)
    {
        releaseBudget(task);
        if (_remaining.decrementAndGet() == 0)
        {
            _completionLatch.countDown();
        }
    }

    @Override
    public void releaseBudget(FileDownloadTask task)
    {
        _budget.release(task);
    }

    /**
     * EN: Logs a per-file progress line (overall percent + file path + OK/FAIL). <br>
     * RU: Логирует строку прогресса по файлу (общий процент + путь файла + OK/FAIL). <br>
     * ==================================================================<br>
     * EN: @param task the finished task / RU: @param task завершённая задача <br>
     * EN: @param success whether the file was stored successfully / RU: @param success успешно ли сохранён файл <br>
     **/
    private void logProgress(FileDownloadTask task, boolean success)
    {
        int processed = _done.get() + _failed.get();
        int percent = _total == 0 ? 100 : IDummyLogger.getPercentOfCompletion(processed, _total);
        String status = success ? "OK" : "FAIL";
        IDummyLogger.log(IDummyLogger.INFO, "Progress " + IDummyLogger.getPercentMessage(percent) + " | " + task.getLinkPath() + ": " + status);
    }

    /**
     * EN: Shuts the stages down, prints the run summary (ok / failed / total + failed paths) and exits
     *     the JVM once from the main thread, with a non-zero code when any file failed. <br>
     * RU: Останавливает стадии, печатает сводку запуска (ok / failed / total + пути сбоев) и один раз
     *     выходит из JVM из главного потока с ненулевым кодом, если хоть один файл упал. <br>
     **/
    private void finish()
    {
        shutdownStages();
        int failed = _failed.get();
        IDummyLogger.log(failed == 0 ? IDummyLogger.FINE : IDummyLogger.WARNING, "Done: " + _done.get() + " ok, " + failed + " failed, " + _total + " total.");
        for (FileDownloadTask task : _failedFiles)
        {
            IDummyLogger.log(IDummyLogger.ERROR, "FAILED: " + task.getLinkPath() + " (" + task.getFailure() + ")");
        }
        System.exit(failed == 0 ? 0 : 1);
    }
}
