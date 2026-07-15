package org.index.patchdownloader.interfaces;

import java.io.File;
import java.io.IOException;

/**
 * EN: Store-stage view of a file request (the last stage). {@link #store()} finalises the decompressed
 *     content to disk at {@link #target()}. For the in-memory mode it writes the decompressed bytes; for the
 *     temp mode it fsyncs the {@code .part} file, renames it to the target and deletes the raw temp file.
 *     The target is {@code DOWNLOAD_PATH/linkPath}, guarded against zip-slip inside {@link #store()}.<br>
 * RU: Представление запроса файла на стадии сохранения (последняя стадия). {@link #store()} финализирует
 *     распакованное содержимое на диск по пути {@link #target()}. В режиме памяти пишет распакованные байты;
 *     во временном режиме сбрасывает на диск файл {@code .part}, переименовывает его в цель и удаляет сырой
 *     временный файл. Цель — {@code DOWNLOAD_PATH/linkPath}, защищённая от zip-slip внутри {@link #store()}.<br>
 **/
public interface IStoreRequest
{
    /**
     * EN: The destination file {@code DOWNLOAD_PATH/linkPath} the content is finalised to. <br>
     * RU: Целевой файл {@code DOWNLOAD_PATH/linkPath}, в который финализируется содержимое. <br>
     * ==================================================================<br>
     * @return <br>
     *         {File} - EN: the destination file / RU: целевой файл <br>
     **/
    File target();

    /**
     * EN: Finalises the file to disk (in-memory: write bytes; temp: fsync + rename {@code .part} -&gt; target,
     *     delete raw temp), after guarding the target path against a zip-slip escape of the download folder. <br>
     * RU: Финализирует файл на диск (память: записать байты; temp: fsync + переименовать {@code .part} -&gt;
     *     цель, удалить сырой временный файл), предварительно защитив целевой путь от zip-slip выхода за
     *     пределы папки загрузки. <br>
     **/
    void store() throws IOException;
}
