package org.index.patchdownloader.interfaces;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import org.index.patchdownloader.enums.ArchiveType;

/**
 * EN: Decompress-stage view of a file request. The stage handler streams the raw bytes from
 *     {@link #rawSource()} through the codec selected by {@link #compressType()} and writes the decompressed
 *     bytes into {@link #decompressedSink()}, then calls {@link #decompressComplete()}. The concrete request
 *     decides where each side lives (in-memory buffers, or the raw temp file and the final {@code .part}
 *     file), so the codec never sees a storage mode. {@link #expectedLength()} is the expected decompressed
 *     length used for bomb guarding and validation.<br>
 * RU: Представление запроса файла на стадии распаковки. Обработчик стадии потоково читает сырые байты из
 *     {@link #rawSource()} через кодек, выбранный по {@link #compressType()}, и пишет распакованные байты в
 *     {@link #decompressedSink()}, затем вызывает {@link #decompressComplete()}. Конкретный запрос сам решает,
 *     где находится каждая сторона (буферы в памяти либо сырой временный файл и итоговый файл {@code .part}),
 *     поэтому кодек не видит режим хранения. {@link #expectedLength()} — ожидаемая распакованная длина,
 *     используемая для защиты от «архивной бомбы» и валидации.<br>
 **/
public interface IDecompressRequest
{
    /**
     * EN: The archive type of the raw payload ({@code NONE} means the bytes are already the final file). <br>
     * RU: Тип архива сырых данных ({@code NONE} означает, что байты уже являются финальным файлом). <br>
     * ==================================================================<br>
     * @return <br>
     *         {ArchiveType} - EN: the compression type / RU: тип сжатия <br>
     **/
    ArchiveType compressType();

    /**
     * EN: The expected decompressed length in bytes, or {@code -1} when unknown. <br>
     * RU: Ожидаемая распакованная длина в байтах или {@code -1}, если неизвестна. <br>
     * ==================================================================<br>
     * @return <br>
     *         {long} - EN: the expected final length / RU: ожидаемая итоговая длина <br>
     **/
    long expectedLength();

    /**
     * EN: Opens a channel over the raw (still-compressed) bytes. For the in-memory mode it reads the
     *     concatenated download parts (freed as they are read); for the temp mode it reads the raw temp file.
     *     The returned channel is fully consumed then closed by the caller. <br>
     * RU: Открывает канал над сырыми (ещё сжатыми) байтами. В режиме памяти читает склеенные части загрузки
     *     (освобождаемые по мере чтения); во временном режиме читает сырой временный файл. Возвращённый канал
     *     вызывающий полностью вычитывает и затем закрывает. <br>
     * ==================================================================<br>
     * @return <br>
     *         {ReadableByteChannel} - EN: the raw byte source / RU: источник сырых байтов <br>
     **/
    ReadableByteChannel rawSource() throws IOException;

    /**
     * EN: Opens a channel that receives the decompressed bytes. For the in-memory mode it is a growable
     *     buffer; for the temp mode it is the final {@code .part} file. The caller closes it when done. <br>
     * RU: Открывает канал, принимающий распакованные байты. В режиме памяти это растущий буфер; во временном
     *     режиме — итоговый файл {@code .part}. Вызывающий закрывает его по завершении. <br>
     * ==================================================================<br>
     * @return <br>
     *         {WritableByteChannel} - EN: the decompressed byte sink / RU: приёмник распакованных байтов <br>
     **/
    WritableByteChannel decompressedSink() throws IOException;

    /**
     * EN: Signals that decompression finished and the decompressed sink is complete. <br>
     * RU: Сигнализирует, что распаковка завершена и приёмник распакованных данных заполнен. <br>
     **/
    void decompressComplete() throws IOException;
}
