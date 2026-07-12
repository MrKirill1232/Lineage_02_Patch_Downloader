package org.index.patchdownloader.instancemanager;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.index.patchdownloader.interfaces.IDummyLogger;
import org.index.patchdownloader.interfaces.IPipelineSink;
import org.index.patchdownloader.model.pipeline.FileDownloadTask;
import org.index.patchdownloader.model.pipeline.enums.DownloadFailureType;
import org.index.patchdownloader.model.pipeline.enums.TaskStage;
import org.index.patchdownloader.model.pipeline.retry.IRetryHandler;
import org.index.patchdownloader.util.concurrent.PipelineExecutors;

/**
 * EN: Base for the three pipeline stages. Holds a non-blocking backlog queue and a dedicated
 *     {@link ForkJoinPool}; {@code pump()} drains the backlog into the pool only up to a per-stage
 *     in-flight cap (N). Each task runs as a {@link CompletableFuture} whose result handler forwards
 *     success to the next stage (or the terminal sink) and routes failure to the stage's retry
 *     handler. Concrete stages implement only {@code processTask}, {@code stage}, {@code classify}.<br>
 * RU: База для трёх стадий конвейера. Держит неблокирующую очередь-бэклог и выделенный
 *     {@link ForkJoinPool}; {@code pump()} сливает бэклог в пул только до предела одновременных задач
 *     стадии (N). Каждая задача выполняется как {@link CompletableFuture}, чей обработчик результата
 *     направляет успех на следующую стадию (или в терминальный приёмник), а сбой — в обработчик
 *     повторов стадии. Конкретные стадии реализуют только {@code processTask}, {@code stage},
 *     {@code classify}.<br>
 **/
public abstract class AbstractStageManager
{
    /**
     * EN: Lifecycle of a stage manager. <br>
     * RU: Жизненный цикл менеджера стадии. <br>
     **/
    public enum StageState
    {
        RUNNING,
        DRAINING,
        STOPPED
    }

    protected final Queue<FileDownloadTask> _queue;
    protected final AtomicInteger _inFlight;
    protected final IRetryHandler _retryHandler;

    protected ForkJoinPool _pool;
    protected int _capacity;
    protected volatile StageState _state;
    protected volatile AbstractStageManager _next;
    protected volatile IPipelineSink _sink;

    /**
     * EN: Per-thread marker set to {@code true} once the terminal sink call ({@code onDone}/{@code onFailed})
     *     for the task currently handled by {@link #onResult} has returned normally. {@link #forceFail}
     *     reads it to decide whether the sink's terminal accounting still has to run, instead of relying on
     *     the task's {@code TaskState} — the state is marked terminal BEFORE the sink call, so a state-based
     *     guard would wrongly suppress the last-resort boundary when the sink throws part-way. Reset at the
     *     top of every {@link #onResult}, so it never carries a stale value across tasks reusing a pool
     *     worker thread. <br>
     * RU: Пометка на уровне потока, ставится в {@code true}, как только терминальный вызов приёмника
     *     ({@code onDone}/{@code onFailed}) для задачи, обрабатываемой сейчас в {@link #onResult}, вернулся
     *     без исключения. {@link #forceFail} читает её, чтобы решить, должен ли ещё выполниться терминальный
     *     учёт приёмника, вместо опоры на {@code TaskState} задачи: состояние помечается терминальным ДО
     *     вызова приёмника, поэтому проверка по состоянию ошибочно погасила бы аварийную границу, если
     *     приёмник бросил исключение на полпути. Сбрасывается в начале каждого {@link #onResult}, поэтому
     *     не переносит устаревшее значение между задачами, повторно использующими поток-воркер пула. <br>
     **/
    private final ThreadLocal<Boolean> _sinkReported = ThreadLocal.withInitial(() -> Boolean.FALSE);

    protected AbstractStageManager(IRetryHandler retryHandler)
    {
        _queue = new ConcurrentLinkedQueue<>();
        _inFlight = new AtomicInteger(0);
        _retryHandler = retryHandler;
        _pool = null;
        _capacity = 1;
        _state = StageState.STOPPED;
        _next = null;
        _sink = null;
    }

    /**
     * EN: Starts the stage: creates the dedicated pool with the given parallelism (= in-flight cap)
     *     and moves to {@code RUNNING}. <br>
     * RU: Запускает стадию: создаёт выделенный пул с заданным параллелизмом (= предел одновременных
     *     задач) и переходит в {@code RUNNING}. <br>
     * ==================================================================<br>
     * EN: @param parallelism worker count / in-flight cap / RU: @param parallelism число воркеров / предел одновременности <br>
     **/
    public void start(int parallelism)
    {
        _capacity = Math.max(1, parallelism);
        _pool = PipelineExecutors.newStagePool(_capacity, getClass().getSimpleName());
        _state = StageState.RUNNING;
    }

    /**
     * EN: Sets the next stage this stage forwards successful tasks to ({@code null} for the last
     *     stage, which reports DONE to the sink). <br>
     * RU: Задаёт следующую стадию, на которую переходят успешные задачи ({@code null} для последней
     *     стадии, сообщающей DONE приёмнику). <br>
     * ==================================================================<br>
     * EN: @param next the next stage or null / RU: @param next следующая стадия или null <br>
     **/
    public void setNext(AbstractStageManager next)
    {
        _next = next;
    }

    /**
     * EN: Sets the terminal sink (coordinator) that owns completion and budget release. <br>
     * RU: Задаёт терминальный приёмник (координатор), владеющий завершением и освобождением бюджета. <br>
     * ==================================================================<br>
     * EN: @param sink the pipeline sink / RU: @param sink приёмник конвейера <br>
     **/
    public void setSink(IPipelineSink sink)
    {
        _sink = sink;
    }

    /**
     * EN: Adds a task to this stage's backlog and pumps the pool (fills free slots). May be called
     *     from the main thread, a worker's result handler, or a retry. <br>
     * RU: Добавляет задачу в бэклог стадии и запускает pump (заполняет свободные слоты). Может
     *     вызываться из главного потока, обработчика результата воркера или повтора. <br>
     * ==================================================================<br>
     * EN: @param task the task to enqueue / RU: @param task задача для постановки в очередь <br>
     **/
    public void submit(FileDownloadTask task)
    {
        _queue.add(task);
        pump();
    }

    /**
     * EN: Drains the backlog into the pool while free capacity and RUNNING; each admitted task runs
     *     as a {@link CompletableFuture} whose completion routes to {@link #onResult}. <br>
     * RU: Сливает бэклог в пул, пока есть свободные слоты и состояние RUNNING; каждая допущенная
     *     задача выполняется как {@link CompletableFuture}, завершение которой идёт в {@link #onResult}. <br>
     **/
    private void pump()
    {
        while (_state == StageState.RUNNING)
        {
            if (!tryReserveSlot())
            {
                break;
            }
            FileDownloadTask task = _queue.poll();
            if (task == null)
            {
                // Release the phantom slot, then re-check: a concurrent submit() may have enqueued a
                // task in the window between our poll and this release (lost-wakeup guard).
                releaseSlot();
                if (_queue.isEmpty())
                {
                    break;
                }
                continue;
            }
            CompletableFuture
                    .runAsync(() -> runProcess(task), _pool)
                    .whenComplete((ignored, throwable) -> onResult(task, throwable));
        }
    }

    /**
     * EN: Runs the stage work and wraps any checked exception into a {@link CompletionException} so the
     *     future completes exceptionally and {@link #onResult} sees the real cause via {@link #unwrap}.
     *     Paired with {@link #unwrap}: keep both in sync so classify() gets the true cause. <br>
     * RU: Выполняет работу стадии и оборачивает проверяемое исключение в {@link CompletionException},
     *     чтобы future завершился с исключением, а {@link #onResult} увидел настоящую причину через
     *     {@link #unwrap}. В паре с {@link #unwrap}: держите их согласованными, чтобы classify() получал
     *     истинную причину. <br>
     * ==================================================================<br>
     * EN: @param task the task to process / RU: @param task обрабатываемая задача <br>
     **/
    private void runProcess(FileDownloadTask task)
    {
        try
        {
            processTask(task);
        }
        catch (Exception e)
        {
            throw new CompletionException(e);
        }
    }

    /**
     * EN: Atomically reserves one in-flight slot via a Compare-And-Swap (CAS) iff the current count is below
     *     capacity. Must stay CAS-based (not incrementAndGet-then-check) to keep the per-stage in-flight cap
     *     exact. <br>
     * RU: Атомарно резервирует один слот через Compare-And-Swap (CAS), только если текущее число меньше
     *     ёмкости. Должен оставаться на CAS (не incrementAndGet-затем-проверка), чтобы предел одновременности
     *     был точным. <br>
     * ==================================================================<br>
     * @return <br>
     *         {true}  - EN: a slot was reserved / RU: слот зарезервирован <br>
     *         {false} - EN: at capacity, not reserved / RU: достигнут предел, не зарезервировано <br>
     **/
    private boolean tryReserveSlot()
    {
        int current;
        do
        {
            current = _inFlight.get();
            if (current >= _capacity)
            {
                return false;
            }
        }
        while (!_inFlight.compareAndSet(current, current + 1));
        return true;
    }

    /**
     * EN: Releases one previously reserved in-flight slot. <br>
     * RU: Освобождает один ранее зарезервированный слот одновременности. <br>
     **/
    private void releaseSlot()
    {
        _inFlight.decrementAndGet();
    }

    /**
     * EN: Result handler of a finished task: frees the slot, forwards success or routes failure,
     *     then pumps the pool again to refill the freed slot. <br>
     * RU: Обработчик результата завершённой задачи: освобождает слот, направляет успех или сбой,
     *     затем снова запускает pump для заполнения освободившегося слота. <br>
     * ==================================================================<br>
     * EN: @param task the finished task / RU: @param task завершённая задача <br>
     * EN: @param throwable the failure (or null on success) / RU: @param throwable сбой (или null при успехе) <br>
     **/
    private void onResult(FileDownloadTask task, Throwable throwable)
    {
        releaseSlot();
        _sinkReported.set(Boolean.FALSE);
        try
        {
            if (throwable == null)
            {
                onSuccess(task);
            }
            else
            {
                onFailure(task, unwrap(throwable));
            }
        }
        catch (Throwable t)
        {
            // Last-resort boundary: a throwable escaping the routing (Out-Of-Memory (OOM),
            // RejectedExecution, illegal state) must not strand the task and hang the run — force it to a
            // terminal FAILED.
            IDummyLogger.log(IDummyLogger.ERROR, "Unexpected error routing '" + task.getLinkPath() + "' at stage " + stage() + ": " + t);
            forceFail(task);
        }
        finally
        {
            pump();
        }
    }

    /**
     * EN: Last-resort terminal transition used when the normal routing threw: reports the task to the sink
     *     so completion accounting and budget release still happen. Guarded on {@link #_sinkReported}, not on
     *     the task's {@code TaskState}: the normal routes mark the task terminal BEFORE the sink call, so a
     *     terminal state does NOT imply the sink was told — if a sink callback threw part-way (e.g. an
     *     Out-Of-Memory (OOM) while logging progress), the accounting never ran and this boundary must still
     *     drive it, otherwise the completion latch never fires and the run hangs. Only forces the failure
     *     when no terminal sink call returned normally for this task. <br>
     * RU: Крайний (аварийный) терминальный переход, когда обычная маршрутизация бросила исключение: сообщает
     *     задачу приёмнику, чтобы учёт завершения и освобождение бюджета всё равно произошли. Защищён по
     *     {@link #_sinkReported}, а не по {@code TaskState} задачи: обычные маршруты помечают задачу
     *     терминальной ДО вызова приёмника, поэтому терминальное состояние НЕ означает, что приёмник был
     *     оповещён — если колбэк приёмника бросил исключение на полпути (например, Out-Of-Memory (OOM) при
     *     логировании прогресса), учёт не выполнился, и эта граница обязана его довести, иначе защёлка
     *     завершения не срабатывает и запуск зависает. Принудительно проваливает задачу только когда ни один
     *     терминальный вызов приёмника для неё не вернулся штатно. <br>
     * ==================================================================<br>
     * EN: @param task the task to force-fail / RU: @param task задача для принудительного FAILED <br>
     **/
    private void forceFail(FileDownloadTask task)
    {
        try
        {
            if (!_sinkReported.get())
            {
                task.markFailed(DownloadFailureType.UNKNOWN);
                _sink.onFailed(task, DownloadFailureType.UNKNOWN);
                _sinkReported.set(Boolean.TRUE);
            }
        }
        catch (Throwable t)
        {
            IDummyLogger.log(IDummyLogger.ERROR, "Failed to force-fail '" + task.getLinkPath() + "': " + t);
        }
    }

    /**
     * EN: Success routing: advance the task and forward it to the next stage; if this is the last
     *     stage, mark it DONE and report to the sink. <br>
     * RU: Маршрутизация успеха: продвинуть задачу и передать на следующую стадию; если это последняя
     *     стадия — пометить DONE и сообщить приёмнику. <br>
     * ==================================================================<br>
     * EN: @param task the successful task / RU: @param task успешная задача <br>
     **/
    protected void onSuccess(FileDownloadTask task)
    {
        task.advanceStage();
        AbstractStageManager next = _next;
        if (next != null)
        {
            next.submit(task);
        }
        else
        {
            task.markDone();
            _sink.onDone(task);
            _sinkReported.set(Boolean.TRUE);
        }
    }

    /**
     * EN: Failure routing: classify the throwable and either retry via this stage's retry handler or
     *     mark the task FAILED and report to the sink. <br>
     * RU: Маршрутизация сбоя: классифицировать исключение и либо повторить через обработчик повторов
     *     стадии, либо пометить задачу FAILED и сообщить приёмнику. <br>
     * ==================================================================<br>
     * EN: @param task the failed task / RU: @param task проваленная задача <br>
     * EN: @param throwable the (unwrapped) failure cause / RU: @param throwable (развёрнутая) причина сбоя <br>
     **/
    protected void onFailure(FileDownloadTask task, Throwable throwable)
    {
        DownloadFailureType failure = classify(throwable);
        if (_retryHandler.shouldRetry(task, failure))
        {
            IDummyLogger.log(IDummyLogger.WARNING, "Retrying '" + task.getLinkPath() + "' at stage " + stage() + " (attempt " + (task.getAttempts() + 1) + ", reason " + failure + ").");
            _retryHandler.onRetry(task, failure, this);
        }
        else
        {
            IDummyLogger.log(IDummyLogger.ERROR, "Failed '" + task.getLinkPath() + "' at stage " + stage() + " (reason " + failure + "): " + String.valueOf(throwable));
            task.markFailed(failure);
            _sink.onFailed(task, failure);
            _sinkReported.set(Boolean.TRUE);
        }
    }

    /**
     * EN: Unwraps a {@link CompletionException} to its underlying cause so per-stage classify() sees the
     *     real throwable (paired with {@link #runProcess}). <br>
     * RU: Разворачивает {@link CompletionException} до исходной причины, чтобы classify() стадии видел
     *     настоящее исключение (в паре с {@link #runProcess}). <br>
     * ==================================================================<br>
     * EN: @param throwable the (possibly wrapped) throwable / RU: @param throwable (возможно обёрнутое) исключение <br>
     * @return <br>
     *         {Throwable} - EN: the unwrapped cause / RU: развёрнутая причина <br>
     **/
    private static Throwable unwrap(Throwable throwable)
    {
        if (throwable instanceof CompletionException && throwable.getCause() != null)
        {
            return throwable.getCause();
        }
        return throwable;
    }

    /**
     * EN: Drains and stops the pool. Called by the coordinator only after all tasks are terminal, so
     *     no work is lost. Idempotent. <br>
     * RU: Дожидается завершения выполняемых задач и останавливает пул. Вызывается координатором только
     *     после того, как все задачи терминальны, поэтому работа не теряется. Идемпотентно. <br>
     **/
    public void shutdown()
    {
        _state = StageState.DRAINING;
        if (_pool != null)
        {
            _pool.shutdown();
            try
            {
                _pool.awaitTermination(1, TimeUnit.MINUTES);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        }
        _state = StageState.STOPPED;
    }

    /**
     * EN: Number of tasks currently executing on the pool (observability only; never drives exit). <br>
     * RU: Число задач, выполняемых сейчас в пуле (только для наблюдения; не влияет на выход). <br>
     * ==================================================================<br>
     * @return <br>
     *         {int} - EN: in-flight task count / RU: число задач в работе <br>
     **/
    public int getInFlightCount()
    {
        return _inFlight.get();
    }

    /**
     * EN: Number of tasks waiting in the backlog queue. <br>
     * RU: Число задач, ожидающих в очереди-бэклоге. <br>
     * ==================================================================<br>
     * @return <br>
     *         {int} - EN: queued task count / RU: число задач в очереди <br>
     **/
    public int getQueueSize()
    {
        return _queue.size();
    }

    /**
     * EN: Stage-specific work on the task (download / decompress / store); may throw. <br>
     * RU: Специфичная для стадии работа над задачей (download / decompress / store); может бросать. <br>
     * ==================================================================<br>
     * EN: @param task the task to process / RU: @param task обрабатываемая задача <br>
     **/
    protected abstract void processTask(FileDownloadTask task) throws Exception;

    /**
     * EN: The stage identity, used in logging and routing. <br>
     * RU: Идентификатор (обозначение) стадии, используется в логах и маршрутизации. <br>
     * ==================================================================<br>
     * @return <br>
     *         {TaskStage} - EN: this stage / RU: эта стадия <br>
     **/
    protected abstract TaskStage stage();

    /**
     * EN: Classifies a throwable from this stage into a {@link DownloadFailureType}. <br>
     * RU: Классифицирует исключение этой стадии в {@link DownloadFailureType}. <br>
     * ==================================================================<br>
     * EN: @param throwable the failure cause / RU: @param throwable причина сбоя <br>
     * @return <br>
     *         {DownloadFailureType} - EN: the classified failure / RU: классифицированный сбой <br>
     **/
    protected abstract DownloadFailureType classify(Throwable throwable);
}
