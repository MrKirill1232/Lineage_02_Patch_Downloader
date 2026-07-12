package org.index.patchdownloader.util.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * EN: Factory + helpers for the pipeline's dedicated {@link ForkJoinPool}s. Each stage gets its own
 *     pool (never the common pool) with named worker threads. {@link #managedBlock(Callable)} wraps a
 *     blocking call in a {@link ForkJoinPool.ManagedBlocker} so a parked worker is accounted for.<br>
 * RU: Фабрика и хелперы для выделенных {@link ForkJoinPool} конвейера. У каждой стадии свой пул (не
 *     общий) с именованными потоками. {@link #managedBlock(Callable)} оборачивает блокирующий вызов в
 *     {@link ForkJoinPool.ManagedBlocker}, чтобы запаркованный воркер учитывался пулом.<br>
 **/
public final class PipelineExecutors
{
    private PipelineExecutors()
    {
    }

    /**
     * EN: Creates a dedicated {@link ForkJoinPool} with the requested parallelism (min 1) and named
     *     worker threads. Not the common pool. <br>
     * RU: Создаёт выделенный {@link ForkJoinPool} с заданным параллелизмом (мин. 1) и именованными
     *     потоками. Не общий пул. <br>
     * ==================================================================<br>
     * EN: @param parallelism target concurrent workers / RU: @param parallelism целевое число воркеров <br>
     * EN: @param name thread-name prefix / RU: @param name префикс имени потока <br>
     * @return <br>
     *         {ForkJoinPool} - EN: the dedicated pool / RU: выделенный пул <br>
     **/
    public static ForkJoinPool newStagePool(int parallelism, String name)
    {
        int poolSize = Math.max(1, parallelism);
        return new ForkJoinPool(poolSize, new NamedForkJoinWorkerThreadFactory(name), null, false);
    }

    /**
     * EN: Runs a blocking callable inside a {@link ForkJoinPool.ManagedBlocker} so the surrounding dedicated
     *     stage pool keeps a correct parallelism account while the worker is parked on I/O.<br>
     *     <b>Why this is needed.</b> A {@link ForkJoinPool} targets a fixed parallelism (the stage's
     *     {@code PARALLEL_*} cap) and assumes its workers run short, CPU-bound, NON-blocking tasks. Our
     *     download/store stages instead block a worker on I/O (an HTTP exchange, a file write). To the pool a
     *     parked worker still counts as "active", so the effective compute parallelism silently drops below the
     *     target; in the worst case every worker parks on I/O at once and the pool makes no progress
     *     (starvation / deadlock), because it never learns it should run more threads.<br>
     *     <b>What a {@code ManagedBlocker} does.</b> It tells the pool "this worker is about to block", giving
     *     the pool the chance to <i>compensate</i> — activate or spin up a spare worker so the number of
     *     genuinely-running threads stays near the target while this one waits. It is therefore the sanctioned
     *     way to block inside a ForkJoinPool (FJP), and its effect is to ADD a compensating thread, not remove one.<br>
     *     <b>What it is NOT.</b> It does not pin the task to a thread, does not prevent work-stealing, and does
     *     not "hold back" other tasks — it concerns exactly ONE blocking operation on ONE worker thread. The
     *     number of concurrent downloads is bounded elsewhere (the stage's in-flight cap + the in-flight memory
     *     budget), never by this call.<br>
     *     <b>Mechanics.</b> {@link ForkJoinPool#managedBlock} loops {@code while (!isReleasable()) block()}; the
     *     {@link ResultBlocker} here runs the callable exactly once inside {@code block()}, captures its result
     *     or exception, and marks itself releasable. Any exception thrown by the callable is rethrown to the
     *     caller.<br>
     * RU: Выполняет блокирующий callable внутри {@link ForkJoinPool.ManagedBlocker}, чтобы окружающий
     *     выделенный пул стадии корректно учитывал параллелизм, пока воркер запаркован на I/O.<br>
     *     <b>Зачем это нужно.</b> {@link ForkJoinPool} держит фиксированный параллелизм (лимит стадии
     *     {@code PARALLEL_*}) и предполагает, что воркеры выполняют короткие CPU-bound, НЕ блокирующие задачи.
     *     Наши стадии download/store вместо этого блокируют воркер на I/O (обмен HTTP, запись файла). Для пула
     *     запаркованный воркер по-прежнему считается «активным», поэтому фактический параллелизм тихо падает
     *     ниже целевого; в худшем случае все воркеры разом паркуются на I/O и пул не двигается (голодание /
     *     дедлок), так как он не узнаёт, что нужно запустить ещё потоки.<br>
     *     <b>Что делает {@code ManagedBlocker}.</b> Он сообщает пулу «этот воркер сейчас заблокируется», давая
     *     пулу шанс <i>компенсировать</i> — активировать или создать запасной воркер, чтобы число реально
     *     работающих потоков осталось у цели, пока этот ждёт. Это санкционированный способ блокироваться внутри
     *     ForkJoinPool (FJP), и его эффект — ДОБАВить компенсирующий поток, а не убрать.<br>
     *     <b>Чем НЕ является.</b> Не привязывает задачу к потоку, не мешает краже работы и не «придерживает» другие
     *     задачи — касается ровно ОДНОЙ блокирующей операции на ОДНОМ воркере. Число одновременных загрузок
     *     ограничено в другом месте (in-flight лимит стадии + бюджет памяти), а не этим вызовом.<br>
     *     <b>Механика.</b> {@link ForkJoinPool#managedBlock} крутит {@code while (!isReleasable()) block()};
     *     {@link ResultBlocker} здесь выполняет callable ровно один раз внутри {@code block()}, сохраняет
     *     результат или исключение и помечает себя releasable. Любое исключение callable пробрасывается
     *     вызывающему.<br>
     * ==================================================================<br>
     * EN: @param blockingCall the blocking operation to run / RU: @param blockingCall блокирующая операция <br>
     * @return <br>
     *         {T} - EN: the callable's result / RU: результат callable <br>
     **/
    public static <T> T managedBlock(Callable<T> blockingCall) throws Exception
    {
        ResultBlocker<T> blocker = new ResultBlocker<>(blockingCall);
        ForkJoinPool.managedBlock(blocker);
        if (blocker._exception != null)
        {
            throw blocker._exception;
        }
        return blocker._result;
    }

    /**
     * EN: A one-shot {@link ForkJoinPool.ManagedBlocker} adapter. The contract driven by
     *     {@link ForkJoinPool#managedBlock} is {@code while (!isReleasable()) block()}: here {@link #block()}
     *     performs the whole blocking call exactly once, stores the result or exception, sets {@code _done} and
     *     returns {@code true}; {@link #isReleasable()} then reports {@code _done}, so the loop runs a single
     *     iteration. Single-use per call — a fresh instance is created for every {@code managedBlock}. <br>
     * RU: Одноразовый адаптер {@link ForkJoinPool.ManagedBlocker}. Контракт, которым управляет
     *     {@link ForkJoinPool#managedBlock}, — {@code while (!isReleasable()) block()}: здесь {@link #block()}
     *     один раз выполняет весь блокирующий вызов, сохраняет результат или исключение, ставит {@code _done} и
     *     возвращает {@code true}; {@link #isReleasable()} затем отдаёт {@code _done}, поэтому цикл делает одну
     *     итерацию. Одноразовый на вызов — на каждый {@code managedBlock} создаётся новый экземпляр. <br>
     **/
    private static final class ResultBlocker<T> implements ForkJoinPool.ManagedBlocker
    {
        private final Callable<T> _callable;
        private T _result;
        private Exception _exception;
        private boolean _done;

        private ResultBlocker(Callable<T> callable)
        {
            _callable = callable;
            _result = null;
            _exception = null;
            _done = false;
        }

        @Override
        public boolean block()
        {
            try
            {
                _result = _callable.call();
            }
            catch (Exception e)
            {
                _exception = e;
            }
            _done = true;
            return true;
        }

        @Override
        public boolean isReleasable()
        {
            return _done;
        }
    }

    /**
     * EN: Names {@link ForkJoinWorkerThread}s of a stage pool with a stable prefix + counter. <br>
     * RU: Именует потоки {@link ForkJoinWorkerThread} пула стадии стабильным префиксом + счётчиком. <br>
     **/
    private static final class NamedForkJoinWorkerThreadFactory implements ForkJoinPool.ForkJoinWorkerThreadFactory
    {
        private final String _namePrefix;
        private final AtomicInteger _counter;

        private NamedForkJoinWorkerThreadFactory(String namePrefix)
        {
            _namePrefix = namePrefix;
            _counter = new AtomicInteger();
        }

        @Override
        public ForkJoinWorkerThread newThread(ForkJoinPool pool)
        {
            ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            thread.setName(_namePrefix + "-" + _counter.getAndIncrement());
            return thread;
        }
    }
}
