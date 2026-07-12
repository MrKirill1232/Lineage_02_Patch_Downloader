package org.index.patchdownloader.model.sourcecompare;

/**
 * EN: The result of checking one file against the source folder. Only {@link #FULL_MATCH} (proven identical)
 *     and, when the user opts in, {@link #PARTIAL_MATCH} (interior proven, only boundary bytes unverifiable
 *     because a neighbour is absent) may be reused by copy. Everything else is (re)downloaded.<br>
 * RU: Результат проверки одного файла по папке-источнику. Копированием переиспользуются только
 *     {@link #FULL_MATCH} (доказано идентичен) и, при явном согласии пользователя, {@link #PARTIAL_MATCH}
 *     (внутренняя часть доказана, недоказуемы лишь граничные байты из-за отсутствующего соседа). Остальное
 *     (пере)скачивается.<br>
 **/
public enum SourceVerdict
{
    FULL_MATCH,
    PARTIAL_MATCH,
    MISMATCH,
    SIZE_MISMATCH,
    SOURCE_MISSING;

    /**
     * EN: Whether this verdict lets the source file be reused (copied) instead of downloaded. FULL_MATCH always;
     *     PARTIAL_MATCH only when {@code trustPartial} is on. <br>
     * RU: Позволяет ли этот вердикт переиспользовать (скопировать) файл-источник вместо загрузки. FULL_MATCH
     *     всегда; PARTIAL_MATCH только при включённом {@code trustPartial}. <br>
     * ==================================================================<br>
     * EN: @param trustPartial whether interior-only proof is accepted / RU: @param trustPartial принимается ли доказательство только по внутренним кускам <br>
     * @return <br>
     *         {true}  - EN: reuse by copy / RU: переиспользовать копированием <br>
     *         {false} - EN: download instead / RU: скачивать вместо этого <br>
     **/
    public boolean isCopyable(boolean trustPartial)
    {
        return this == FULL_MATCH || (this == PARTIAL_MATCH && trustPartial);
    }
}
