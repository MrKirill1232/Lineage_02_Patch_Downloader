package org.index.patchdownloader.model.storage;

import org.index.patchdownloader.enums.DownloadMode;
import org.index.patchdownloader.enums.StorageStrategy;

/**
 * EN: Pure decision function that maps a run's {@link DownloadMode} and a single file's known size onto the
 *     per-file {@link StorageStrategy}. It has no state and touches no configuration or I/O, so it is trivially
 *     unit-testable and safe to call from any stage or thread.<br>
 * RU: Чистая функция решения, отображающая {@link DownloadMode} запуска и известный размер отдельного файла в
 *     стратегию хранения {@link StorageStrategy} для этого файла. Она без состояния и не обращается к
 *     конфигурации или вводу-выводу, поэтому легко покрывается модульными тестами и безопасна для вызова из
 *     любого этапа или потока.<br>
 *
 * @author Index
 **/
public final class StorageRouter
{
    /**
     * EN: Largest payload that can live in memory: a Java array's practical ceiling ({@code Integer.MAX_VALUE - 8},
     *     matching {@code MemoryFileRequest}'s own array guard). A file strictly larger than this can NEVER be held
     *     as a {@code byte[]}, so it is force-routed to a temp file regardless of mode. <br>
     * RU: Наибольший объём, который может жить в памяти: практический потолок Java-массива
     *     ({@code Integer.MAX_VALUE - 8}, совпадает с собственной защитой массива в {@code MemoryFileRequest}).
     *     Файл строго больше него НИКОГДА не поместится в {@code byte[]}, поэтому принудительно направляется во
     *     временный файл независимо от режима. <br>
     **/
    private static final long MAX_IN_MEMORY_BYTES = Integer.MAX_VALUE - 8;

    private StorageRouter()
    {
    }

    /**
     * EN: Picks the storage strategy for one file. An unknown size is expressed as a negative
     *     {@code knownSizeBytes}. A file physically too big for a Java array ({@code > MAX_IN_MEMORY_BYTES}, ~2 GB)
     *     is force-routed to {@link StorageStrategy#TEMPORARY} FIRST, in ANY mode — it can never be a {@code byte[]},
     *     so the temp path (which streams range-by-range into a pre-sized temp file) is the only one that can carry
     *     it; without this it would build a memory request and fail late mid-download at the array guard. Otherwise,
     *     by mode:
     *     <ul>
     *         <li>{@code ALL_MEMORY} → {@link StorageStrategy#MEMORY} (an over-{@code thresholdBytes} file is
     *             rejected earlier by the pipeline, not here).</li>
     *         <li>{@code HYBRID} → {@link StorageStrategy#TEMPORARY} when the size is unknown or strictly
     *             greater than {@code thresholdBytes}, otherwise {@link StorageStrategy#MEMORY}.</li>
     *         <li>{@code ALL_TEMP} → always {@link StorageStrategy#TEMPORARY}.</li>
     *     </ul><br>
     * RU: Выбирает стратегию хранения для одного файла. Неизвестный размер выражается отрицательным
     *     {@code knownSizeBytes}. Файл, физически не помещающийся в Java-массив ({@code > MAX_IN_MEMORY_BYTES},
     *     ~2 ГБ), СНАЧАЛА принудительно направляется в {@link StorageStrategy#TEMPORARY} в ЛЮБОМ режиме — он никогда
     *     не станет {@code byte[]}, поэтому путь временного файла (потоково пишущий диапазон за диапазоном в
     *     заранее выделенный файл) — единственный, кто его выдержит; без этого он построил бы запрос в памяти и упал
     *     бы поздно, посреди загрузки, на защите массива. Иначе — по режиму:
     *     <ul>
     *         <li>{@code ALL_MEMORY} → {@link StorageStrategy#MEMORY} (файл сверх {@code thresholdBytes}
     *             отклоняется раньше конвейером, а не здесь).</li>
     *         <li>{@code HYBRID} → {@link StorageStrategy#TEMPORARY}, если размер неизвестен или строго больше
     *             {@code thresholdBytes}, иначе {@link StorageStrategy#MEMORY}.</li>
     *         <li>{@code ALL_TEMP} → всегда {@link StorageStrategy#TEMPORARY}.</li>
     *     </ul><br>
     * ==================================================================<br>
     * EN: @param mode the run-wide download mode / RU: @param mode общий для запуска режим загрузки <br>
     * EN: @param knownSizeBytes the file's known size in bytes, or a negative value when unknown /
     *     RU: @param knownSizeBytes известный размер файла в байтах или отрицательное значение, если неизвестен <br>
     * EN: @param thresholdBytes the memory-vs-temp threshold in bytes (used only by {@code HYBRID}) /
     *     RU: @param thresholdBytes порог "память против временного файла" в байтах (используется только режимом {@code HYBRID}) <br>
     * @return <br>
     *         {StorageStrategy} - EN: the strategy backing this file / RU: стратегия, обслуживающая этот файл <br>
     **/
    public static StorageStrategy pick(DownloadMode mode, long knownSizeBytes, long thresholdBytes)
    {
        // Hard physical override, ahead of the mode rules: a payload that cannot fit a Java array can never be an
        // in-memory request, so it MUST stream through a temp file — even in ALL_MEMORY / an unset mode.
        if (knownSizeBytes > MAX_IN_MEMORY_BYTES)
        {
            return StorageStrategy.TEMPORARY;
        }
        if (mode == null)
        {
            // EN: No configured mode is treated as the historical all-memory behaviour.
            // RU: Отсутствие заданного режима трактуется как прежнее поведение "всё в памяти".
            return StorageStrategy.MEMORY;
        }
        switch (mode)
        {
            case ALL_TEMP:
                return StorageStrategy.TEMPORARY;
            case HYBRID:
                if (knownSizeBytes < 0 || knownSizeBytes > thresholdBytes)
                {
                    return StorageStrategy.TEMPORARY;
                }
                return StorageStrategy.MEMORY;
            case ALL_MEMORY:
            default:
                return StorageStrategy.MEMORY;
        }
    }
}
