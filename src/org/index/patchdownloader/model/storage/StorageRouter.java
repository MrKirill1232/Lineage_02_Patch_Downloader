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
    private StorageRouter()
    {
    }

    /**
     * EN: Picks the storage strategy for one file. An unknown size is expressed as a negative
     *     {@code knownSizeBytes}. Rules by mode:
     *     <ul>
     *         <li>{@code ALL_MEMORY} → always {@link StorageStrategy#MEMORY} (an oversized file is rejected
     *             later by the pipeline, not here).</li>
     *         <li>{@code HYBRID} → {@link StorageStrategy#TEMPORARY} when the size is unknown or strictly
     *             greater than {@code thresholdBytes}, otherwise {@link StorageStrategy#MEMORY}.</li>
     *         <li>{@code ALL_TEMP} → always {@link StorageStrategy#TEMPORARY}.</li>
     *     </ul><br>
     * RU: Выбирает стратегию хранения для одного файла. Неизвестный размер выражается отрицательным
     *     {@code knownSizeBytes}. Правила по режиму:
     *     <ul>
     *         <li>{@code ALL_MEMORY} → всегда {@link StorageStrategy#MEMORY} (слишком большой файл отклоняется
     *             позже конвейером, а не здесь).</li>
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
