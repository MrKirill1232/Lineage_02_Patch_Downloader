package org.index.patchdownloader.model.pipeline.verify;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.index.patchdownloader.interfaces.IDummyLogger;

/**
 * EN: Shared machinery for an asynchronous {@link IDownloadVerifier}: a bounded daemon thread pool the concrete
 *     strategy schedules verification work onto ({@link #submit(Runnable)}), plus the terminal template
 *     {@link #awaitAndReport()} that runs the strategy's pre-drain hook ({@link #beforeAwait()}, e.g. a final
 *     sweep), drains the pool, then reports ({@link #report()}). Subclasses only express WHAT to verify per file
 *     and how to aggregate the verdict — never the threading or lifecycle.<br>
 * RU: Общая механика для асинхронного {@link IDownloadVerifier}: ограниченный пул демон-потоков, на который
 *     конкретная стратегия ставит работу проверки ({@link #submit(Runnable)}), плюс терминальный шаблон
 *     {@link #awaitAndReport()}, который выполняет хук стратегии перед осушением ({@link #beforeAwait()},
 *     например финальный проход), осушает пул, затем отчитывается ({@link #report()}). Подклассы выражают лишь
 *     ЧТО проверять на файл и как свести вердикт — но не потоки и не жизненный цикл.<br>
 **/
public abstract class AbstractAsyncVerifier implements IDownloadVerifier
{
    private final ExecutorService _pool;
    private final AtomicInteger _taskErrors;

    /**
     * EN: Builds the verifier with a fixed daemon pool of the given parallelism (at least 1). <br>
     * RU: Создаёт верификатор с фиксированным демон-пулом заданного параллелизма (не меньше 1). <br>
     * ==================================================================<br>
     * EN: @param parallelism the number of verification worker threads / RU: @param parallelism число потоков-воркеров проверки <br>
     **/
    protected AbstractAsyncVerifier(int parallelism)
    {
        _pool = Executors.newFixedThreadPool(Math.max(1, parallelism), daemonFactory());
        _taskErrors = new AtomicInteger(0);
    }

    /**
     * EN: Schedules one verification task on the pool, guarded so that ANY throwable it raises is counted as a
     *     task error (which makes the run non-clean) and logged, never silently swallowed by the executor. A
     *     dropped tally could otherwise hide the very corruption this subsystem exists to catch, so the guard keeps
     *     it fail-CLOSED. Silently ignores a rejection (the pool is only shut down from {@link #awaitAndReport()}
     *     after the run is complete, so a reject cannot lose live work). <br>
     * RU: Ставит одну задачу проверки в пул, обёрнутую так, что ЛЮБОЙ её throwable считается ошибкой задачи (что
     *     делает запуск не-чистым) и логируется, а не молча проглатывается исполнителем. Иначе потерянный подсчёт
     *     мог бы скрыть именно то повреждение, ради поимки которого существует эта подсистема, поэтому обёртка
     *     держит её fail-CLOSED. Молча игнорирует отказ (пул закрывается только из {@link #awaitAndReport()} после
     *     завершения запуска, поэтому отказ не может потерять актуальную работу). <br>
     * ==================================================================<br>
     * EN: @param task the verification task / RU: @param task задача проверки <br>
     **/
    protected final void submit(Runnable task)
    {
        try
        {
            _pool.submit(() -> runGuarded(task));
        }
        catch (RejectedExecutionException ignored)
        {
            // the pool only closes at run end; a reject here means the run already finished — nothing to lose
        }
    }

    private void runGuarded(Runnable task)
    {
        try
        {
            task.run();
        }
        catch (Throwable throwable)
        {
            _taskErrors.incrementAndGet();
            IDummyLogger.log(IDummyLogger.ERROR, "Verify: a verification task failed and its result is unknown: " + throwable);
        }
    }

    @Override
    public final boolean awaitAndReport()
    {
        beforeAwait();
        _pool.shutdown();
        boolean terminated;
        try
        {
            terminated = _pool.awaitTermination(1, TimeUnit.HOURS);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            terminated = false;
        }
        boolean clean = report();
        // Fail-CLOSED: incomplete verification (a timeout leaving tasks unfinished) or a crashed task means some
        // unit was never proven — do NOT report the run clean, even if no positive corruption was tallied.
        if (!terminated)
        {
            IDummyLogger.log(IDummyLogger.ERROR, "Verify: verification did not finish in time; the run cannot be certified clean.");
        }
        int errors = _taskErrors.get();
        if (errors > 0)
        {
            IDummyLogger.log(IDummyLogger.ERROR, "Verify: " + errors + " verification task(s) crashed; the run cannot be certified clean.");
        }
        return terminated && errors == 0 && clean;
    }

    /**
     * EN: Hook run once before the pool is drained (default no-op). A strategy that verifies units spanning
     *     several files (torrent pieces) uses it for a final sweep of units no per-file event could trigger. <br>
     * RU: Хук, выполняемый один раз перед осушением пула (по умолчанию пусто). Стратегия, проверяющая единицы,
     *     охватывающие несколько файлов (куски торрента), использует его для финального прохода по единицам,
     *     которые не могло запустить ни одно событие по файлу. <br>
     **/
    protected void beforeAwait()
    {
        // default: nothing to do before draining
    }

    /**
     * EN: Logs the run's verification summary and returns whether it is clean (no proven corruption). <br>
     * RU: Логирует сводку проверки запуска и возвращает, чист ли он (нет доказанного повреждения). <br>
     * ==================================================================<br>
     * @return <br>
     *         {boolean} - EN: true when nothing was proven corrupt / RU: true, если ничего не признано битым <br>
     **/
    protected abstract boolean report();

    private static ThreadFactory daemonFactory()
    {
        AtomicInteger counter = new AtomicInteger(0);
        return runnable ->
        {
            Thread thread = new Thread(runnable, "download-verify-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
