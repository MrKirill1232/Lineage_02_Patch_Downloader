package org.index.patchdownloader.enums;

/**
 * EN: The storage strategy derived per file from the run's {@link DownloadMode} and the file's known size (see
 *     {@code StorageRouter}). It tells the pipeline which request implementation backs a single file: an
 *     in-memory buffer or a temporary file on disk.<br>
 * RU: Стратегия хранения, выводимая для каждого файла из {@link DownloadMode} запуска и известного размера
 *     файла (см. {@code StorageRouter}). Она сообщает конвейеру, какая реализация запроса обслуживает
 *     отдельный файл: буфер в оперативной памяти или временный файл на диске.<br>
 *
 * @author Index
 **/
public enum StorageStrategy
{
    /**
     * EN: The file is held entirely in memory (raw parts plus decompressed bytes). Random-access memory usage
     *     scales with the file size.<br>
     * RU: Файл целиком удерживается в оперативной памяти (сырые части плюс распакованные байты). Расход
     *     оперативной памяти растёт вместе с размером файла.<br>
     **/
    MEMORY,

    /**
     * EN: The file is streamed through a temporary file on disk; only small streaming buffers live in memory,
     *     so a file larger than the heap can be processed.<br>
     * RU: Файл потоково проходит через временный файл на диске; в оперативной памяти живут лишь небольшие
     *     потоковые буферы, поэтому можно обработать файл, превышающий размер кучи.<br>
     **/
    TEMPORARY,
}
