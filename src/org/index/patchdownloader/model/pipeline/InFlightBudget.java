package org.index.patchdownloader.model.pipeline;

import java.util.concurrent.Semaphore;

import org.index.patchdownloader.enums.StorageStrategy;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.model.pipeline.request.AbstractFileRequest;

/**
 * EN: In-flight budget guard for the in-memory download pipeline, and the source of its backpressure.
 *     Every file is held in RAM as byte arrays (compressed, then decompressed) all the way from download
 *     through store. The per-stage backlog queues are unbounded and the stages run at different speeds
 *     (store is the slow tail), so an unthrottled run could accumulate the bytes of hundreds of files at
 *     once and hit an {@code OutOfMemoryError}. This guard bounds the concurrent in-flight work in two
 *     dimensions:
 *     <ul>
 *       <li><b>memory budget</b> — a {@link Semaphore} whose permits are MEGABYTES. A task acquires its
 *           estimated footprint before download and releases it at its terminal state. Sized to
 *           {@code min(config max_inflight_memory_mb, Xmx - 100MB)} (where Xmx is the {@code -Xmx} JVM
 *           max-heap size), so the JVM always keeps headroom and the configured value can never exceed
 *           what the heap can hold.</li>
 *       <li><b>file slots</b> — a {@link Semaphore} whose permits are a FILE COUNT. This is the reliable
 *           bound when file sizes are unknown (the memory estimate then falls back to a default), so an
 *           unknown-size source cannot flood memory.</li>
 *     </ul>
 *     When either dimension is exhausted, {@link #acquire(AbstractFileRequest)} blocks the submitting (main)
 *     thread — the pipeline is never fed faster than it drains. An oversized file (larger than the whole
 *     budget) is CLAMPED to the full budget: it acquires every permit and runs alone, but never requests
 *     more permits than exist (which would deadlock forever). Caveat: the clamp under-accounts such a solo
 *     file — the heap must be able to hold one oversized file's peak (roughly 2x its size while it is being
 *     decompressed). Instances are stateless-per-run and shared across all stage threads (the two
 *     semaphores serialise acquire/release).<br>
 * RU: Ограничитель одновременной работы для in-memory конвейера загрузки и источник его backpressure.
 *     Каждый файл держится в RAM как массивы байтов (сжатый, затем распакованный) на всём пути от download
 *     до store. Очереди-бэклоги стадий не ограничены, а стадии работают с разной скоростью (store —
 *     медленный хвост), поэтому без троттлинга запуск мог бы накопить байты сотен файлов сразу и словить
 *     {@code OutOfMemoryError}. Этот ограничитель бьёт по двум измерениям:
 *     <ul>
 *       <li><b>бюджет памяти</b> — {@link Semaphore}, чьи квоты — МЕГАБАЙТЫ. Задача занимает свою оценку
 *           перед загрузкой и освобождает её при завершении. Размер = {@code min(config, Xmx - 100МБ)} (где
 *           Xmx — заданный флагом {@code -Xmx} максимальный размер кучи JVM), чтобы JVM всегда имела запас,
 *           а конфиг не мог превысить объём кучи.</li>
 *       <li><b>слоты файлов</b> — {@link Semaphore}, чьи квоты — ЧИСЛО файлов. Надёжная граница, когда
 *           размеры неизвестны (оценка памяти тогда откатывается к значению по умолчанию).</li>
 *     </ul>
 *     Когда любое измерение исчерпано, {@link #acquire(AbstractFileRequest)} блокирует отправляющий (главный)
 *     поток — конвейер не кормится быстрее, чем осушается. Слишком большой файл (больше всего бюджета)
 *     ОГРАНИЧИВАЕТСЯ полным бюджетом: занимает все квоты и качается в одиночку, но никогда не просит больше
 *     квот, чем существует (иначе — вечная взаимоблокировка). Оговорка: ограничение недоучитывает такой
 *     одиночный файл — куча должна вместить пик одного большого файла (примерно 2x его размера при
 *     распаковке). Экземпляр общий на запуск и разделяется всеми потоками стадий (семафоры сериализуют
 *     acquire/release).<br>
 *     <p>
 *     EN: The guard is STORAGE-STRATEGY-AWARE. A {@link StorageStrategy#MEMORY} file is charged its full raw +
 *     decompressed footprint as above. A {@link StorageStrategy#TEMPORARY} file streams through a temp file, so
 *     its payload never sits in the heap: it is charged only a small fixed streaming-buffer estimate
 *     ({@code TEMP_STREAMING_MB}), NOT its size, so a huge file no longer clamps the memory budget and runs
 *     alone. What bounds concurrent temp files instead is a THIRD dimension — a concurrent-temp-file
 *     {@link Semaphore} (disk-aware bound) acquired only by temp files on top of the shared file slot — so the
 *     disk cannot fill with unbounded raw temps + {@code .part} files even though they cost almost no memory.
 *     <br>
 *     RU: Ограничитель УЧИТЫВАЕТ СТРАТЕГИЮ ХРАНЕНИЯ. Файл {@link StorageStrategy#MEMORY} тарифицируется полным
 *     объёмом сырое + распакованное, как выше. Файл {@link StorageStrategy#TEMPORARY} идёт потоком через
 *     временный файл, поэтому его объём никогда не лежит в куче: ему начисляется лишь небольшая фиксированная
 *     оценка потоковых буферов ({@code TEMP_STREAMING_MB}), а НЕ его размер, поэтому огромный файл больше не
 *     зажимает бюджет памяти и не качается в одиночку. Одновременность временных файлов ограничивает вместо
 *     этого ТРЕТЬЕ измерение — {@link Semaphore} одновременных временных файлов (граница с учётом диска),
 *     занимаемый только временными файлами поверх общего слота файла, — чтобы диск не заполнялся неограниченным
 *     числом сырых временных + {@code .part} файлов, хотя они почти не стоят памяти. <br>
 **/
public final class InFlightBudget
{
    private static final int HEADROOM_MB = 100;
    private static final int DEFAULT_FILE_MB = 8;
    private static final long ONE_MB = 1024L * 1024L;

    /**
     * EN: Fixed memory charge (MB) for a temp-file request: it holds only small streaming buffers (a few HTTP
     *     body buffers + the codec transfer buffer), never the payload, so a constant estimate is both safe and
     *     independent of the file size. <br>
     * RU: Фиксированная плата памяти (МБ) за запрос с временным файлом: он держит лишь небольшие потоковые буферы
     *     (несколько буферов тела HTTP + буфер передачи кодека), но не сам объём, поэтому постоянная оценка и
     *     безопасна, и не зависит от размера файла. <br>
     **/
    private static final int TEMP_STREAMING_MB = 16;

    private final Semaphore _memoryBudget;
    private final Semaphore _fileSlots;
    private final Semaphore _tempFileSlots;
    private final int _budgetMB;

    /**
     * EN: Sizes the budgets for a run: memory permits (MB) = {@code min(configMaxMemoryMb, Xmx - 100MB)},
     *     clamped to at least 1; file-slot permits = {@code max(1, maxInflightFiles)}. <br>
     * RU: Рассчитывает бюджеты запуска: квоты памяти (МБ) = {@code min(configMaxMemoryMb, Xmx - 100МБ)},
     *     не меньше 1; квоты слотов файлов = {@code max(1, maxInflightFiles)}. <br>
     * ==================================================================<br>
     * EN: @param configMaxMemoryMb the configured MB cap ({@code max_inflight_memory_mb}) / RU: @param configMaxMemoryMb настроенный лимит МБ <br>
     * EN: @param maxInflightFiles the configured file-count cap ({@code max_inflight_files}) / RU: @param maxInflightFiles настроенный лимит числа файлов <br>
     **/
    public InFlightBudget(int configMaxMemoryMb, int maxInflightFiles)
    {
        long maxMemMb = Runtime.getRuntime().maxMemory() / ONE_MB;
        int safeMb = (int) Math.max(1, maxMemMb - HEADROOM_MB);
        _budgetMB = Math.max(1, Math.min(configMaxMemoryMb, safeMb));
        _memoryBudget = new Semaphore(_budgetMB);
        _fileSlots = new Semaphore(Math.max(1, maxInflightFiles));
        // Concurrent-temp-file bound (disk-aware): derived from the file-slot cap. Sized to match the file
        // slots so a temp file that already holds a file slot can always take a temp slot (no deadlock); a
        // future config key could tighten it below the file-slot cap for tighter disk control.
        _tempFileSlots = new Semaphore(Math.max(1, maxInflightFiles));
    }

    /**
     * EN: Reserves the in-flight budget for a task before it is submitted: one file slot plus its
     *     estimated MB. Blocking here throttles submission (backpressure). Uninterruptible on purpose —
     *     interruption is not a supported cancel path, and a swallowed interrupt would submit work without
     *     permits and later release permits that were never acquired. <br>
     * RU: Резервирует бюджет одновременности для задачи перед отправкой: один слот файла плюс её оценку в
     *     МБ. Блокировка здесь троттлит отправку (backpressure). Непрерываемо намеренно — прерывание не
     *     является поддерживаемым способом отмены, а проглоченное прерывание отправило бы работу без квот и
     *     позже освободило бы квоты, которые не занимались. <br>
     * ==================================================================<br>
     * EN: @param task the task about to be submitted / RU: @param task задача перед отправкой <br>
     **/
    public void acquire(AbstractFileRequest task)
    {
        int mb = estimateMB(task);
        task.setAcquiredMB(mb);
        // Fixed global acquisition order (file slot -> temp slot -> memory) shared by every task, so a temp
        // file that holds a file slot can never be blocked waiting for a temp slot behind a task that holds a
        // temp slot but waits for a file slot: there is no cyclic wait.
        _fileSlots.acquireUninterruptibly();
        if (task.storageStrategy() == StorageStrategy.TEMPORARY)
        {
            _tempFileSlots.acquireUninterruptibly();
        }
        _memoryBudget.acquireUninterruptibly(mb);
    }

    /**
     * EN: Releases the budget a task holds (its acquired MB and one file slot). Called exactly once, at the
     *     task's terminal state, so the freed capacity admits new work. <br>
     * RU: Освобождает бюджет, удерживаемый задачей (её занятые МБ и один слот файла). Вызывается ровно один
     *     раз, при завершении задачи, чтобы освободившаяся ёмкость впустила новую работу. <br>
     * ==================================================================<br>
     * EN: @param task the task reaching a terminal state / RU: @param task задача, достигшая терминального состояния <br>
     **/
    public void release(AbstractFileRequest task)
    {
        _memoryBudget.release(task.getAcquiredMB());
        if (task.storageStrategy() == StorageStrategy.TEMPORARY)
        {
            _tempFileSlots.release();
        }
        _fileSlots.release();
    }

    /**
     * EN: The total memory budget in MB ({@code min(config, Xmx - 100MB)}). Exposed for logging/diagnostics. <br>
     * RU: Общий бюджет памяти в МБ ({@code min(config, Xmx - 100МБ)}). Открыт для логов/диагностики. <br>
     * @return <br>
     *         {int} - EN: the memory budget in MB / RU: бюджет памяти в МБ <br>
     **/
    public int getBudgetMB()
    {
        return _budgetMB;
    }

    /**
     * EN: Estimates a task's in-flight memory footprint in MB. A {@link StorageStrategy#TEMPORARY} task streams
     *     through a temp file and never holds the payload in the heap (akumu included, now that its temp path
     *     streams the body straight to the temp file rather than buffering a whole {@code byte[]}), so it is charged
     *     a small fixed {@code TEMP_STREAMING_MB} (clamped to the budget) regardless of file size. A
     *     {@link StorageStrategy#MEMORY} task is charged its whole footprint (compressed + decompressed; for a
     *     multi-part file the sum of all parts, since they are held together), using {@code DEFAULT_FILE_MB} when
     *     the lengths are unknown, and clamped to the total budget so an oversized file runs alone without ever
     *     requesting more permits than exist. <br>
     * RU: Оценивает потребление памяти задачи в МБ. Задача {@link StorageStrategy#TEMPORARY} идёт потоком через
     *     временный файл и никогда не держит объём в куче (akumu в том числе — теперь его temp-путь стримит тело
     *     прямо во временный файл, а не буферизует целый {@code byte[]}), поэтому ей начисляется небольшая
     *     фиксированная {@code TEMP_STREAMING_MB} (ограниченная бюджетом) независимо от размера файла. Задача
     *     {@link StorageStrategy#MEMORY} тарифицируется полным объёмом (сжатое + распакованное; для многочастного
     *     файла — сумма всех частей, так как они держатся вместе), используя {@code DEFAULT_FILE_MB} при
     *     неизвестных длинах и ограничивая общим бюджетом, чтобы большой файл качался в одиночку и не просил
     *     больше квот, чем существует. <br>
     * ==================================================================<br>
     * EN: @param task the task to size / RU: @param task оцениваемая задача <br>
     * @return <br>
     *         {int} - EN: estimated MB (clamped to the budget) / RU: оценка в МБ (ограничена бюджетом) <br>
     **/
    private int estimateMB(AbstractFileRequest task)
    {
        // A TEMPORARY file streams through a temp file and never holds its payload in the heap (akumu included now
        // that its temp path streams the body straight to the temp file instead of buffering a whole byte[]), so it
        // is charged a small fixed estimate regardless of file size.
        if (task.storageStrategy() == StorageStrategy.TEMPORARY)
        {
            return Math.min(TEMP_STREAMING_MB, _budgetMB);
        }
        FileInfoHolder fileInfo = task.getFileInfo();
        long bytes = 0;
        FileInfoHolder[] parts = fileInfo.getAllSeparatedParts();
        if (parts.length > 0)
        {
            // Multi-part files download ALL parts concurrently — charge the sum, not just the parent chunk.
            for (FileInfoHolder part : parts)
            {
                if (part != null && part.getDownloadDataLength() > 0)
                {
                    bytes += part.getDownloadDataLength();
                }
                else
                {
                    bytes += (long) DEFAULT_FILE_MB * ONE_MB;
                }
            }
        }
        else if (fileInfo.getDownloadDataLength() > 0)
        {
            bytes += fileInfo.getDownloadDataLength();
        }
        if (fileInfo.getFileLength() > 0)
        {
            bytes += fileInfo.getFileLength();
        }
        int mb = (int) Math.min(Integer.MAX_VALUE, (bytes + ONE_MB - 1) / ONE_MB);
        if (mb <= 0)
        {
            mb = DEFAULT_FILE_MB;
        }
        return Math.min(mb, _budgetMB);
    }
}
