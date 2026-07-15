package org.index.patchdownloader.enums;

/**
 * EN: Run-wide storage policy selected by configuration (key {@code download_mode}). It decides, together with
 *     each file's known size, whether a file is processed entirely in memory or streamed through a temporary
 *     file on disk. It is picked once per run and never changes mid-run.<br>
 * RU: Общая для всего запуска политика хранения, выбираемая конфигурацией (ключ {@code download_mode}). Вместе
 *     с известным размером каждого файла она решает, обрабатывается ли файл целиком в оперативной памяти или
 *     потоково через временный файл на диске. Выбирается один раз за запуск и не меняется во время работы.<br>
 *
 * @author Index
 **/
public enum DownloadMode
{
    /**
     * EN: Every file is held entirely in memory — byte-identical to the historical behaviour. A file whose
     *     known size exceeds the threshold is rejected before download (handled by the pipeline, not here).<br>
     * RU: Каждый файл целиком удерживается в оперативной памяти — побайтово совпадает с прежним поведением.
     *     Файл, известный размер которого превышает порог, отклоняется до загрузки (это делает конвейер, а не
     *     данное перечисление).<br>
     **/
    ALL_MEMORY,

    /**
     * EN: A file is kept in memory when its known size is at or below the threshold, otherwise it is streamed
     *     through a temporary file. A file of unknown size is streamed through a temporary file (the safe
     *     choice).<br>
     * RU: Файл удерживается в оперативной памяти, если его известный размер не превышает порог, иначе он
     *     потоково проходит через временный файл. Файл неизвестного размера проходит через временный файл
     *     (безопасный выбор).<br>
     **/
    HYBRID,

    /**
     * EN: Every file is streamed through a temporary file on disk, regardless of size (even tiny files).<br>
     * RU: Каждый файл потоково проходит через временный файл на диске независимо от размера (даже крошечные
     *     файлы).<br>
     **/
    ALL_TEMP,
}
