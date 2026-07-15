package org.index.patchdownloader.interfaces;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.index.patchdownloader.enums.StorageStrategy;
import org.index.patchdownloader.model.holders.FileInfoHolder;

/**
 * EN: Download-stage view of a file request. The download strategy (the stage handler) streams the raw
 *     bytes INTO the request through this interface instead of returning a whole {@code byte[]}: it pushes
 *     chunks per read ({@link #acceptChunk}), signals each part finished ({@link #partComplete}) and the
 *     whole raw payload received ({@link #downloadComplete}). The concrete request decides where the bytes
 *     land (an in-memory part buffer, or a pre-sized temp file written at absolute offsets), so the same
 *     strategy drives both storage modes.<br>
 * RU: Представление запроса файла на стадии загрузки. Стратегия загрузки (обработчик стадии) потоково пишет
 *     сырые байты В запрос через этот интерфейс, а не возвращает целый {@code byte[]}: она передаёт куски на
 *     каждое чтение ({@link #acceptChunk}), сигнализирует о завершении каждой части ({@link #partComplete}) и
 *     о полном получении сырых данных ({@link #downloadComplete}). Конкретный запрос сам решает, куда лягут
 *     байты (буфер части в памяти или заранее выделенный временный файл с записью по абсолютным смещениям),
 *     поэтому одна стратегия обслуживает оба режима хранения.<br>
 **/
public interface IDownloadRequest
{
    /**
     * EN: The static file metadata (path, size, hash, compression type, CDN parts) driving the download. <br>
     * RU: Статические метаданные файла (путь, размер, хеш, тип сжатия, части CDN), управляющие загрузкой. <br>
     * ==================================================================<br>
     * @return <br>
     *         {FileInfoHolder} - EN: the file metadata / RU: метаданные файла <br>
     **/
    FileInfoHolder fileInfo();

    /**
     * EN: The storage strategy backing this request, so a download strategy can size its own buffering: a
     *     {@link StorageStrategy#MEMORY} request assembles the payload in the heap (bounded to roughly {@code 2 GB}),
     *     while a {@link StorageStrategy#TEMPORARY} request streams every chunk to a pre-sized temp file at its
     *     absolute offset and so carries a payload larger than the heap. It lets the self-split strategy stream
     *     ranges straight to disk in temp mode instead of reassembling the whole file in a single {@code byte[]}. <br>
     * RU: Стратегия хранения, обслуживающая этот запрос, чтобы стратегия загрузки могла соразмерить собственную
     *     буферизацию: запрос {@link StorageStrategy#MEMORY} собирает данные в куче (ограничение примерно
     *     {@code 2 ГБ}), тогда как запрос {@link StorageStrategy#TEMPORARY} потоково пишет каждый кусок во временный
     *     файл заранее выделенного размера по его абсолютному смещению и потому несёт объём больше кучи. Это
     *     позволяет стратегии self-split в temp-режиме писать диапазоны прямо на диск, а не пересобирать весь файл в
     *     одном {@code byte[]}. <br>
     * ==================================================================<br>
     * @return <br>
     *         {StorageStrategy} - EN: the storage strategy of this request / RU: стратегия хранения этого запроса <br>
     **/
    StorageStrategy storageStrategy();

    /**
     * EN: A monotonically increasing token identifying the CURRENT download attempt, bumped at the start of every
     *     attempt (including a retry). A download strategy captures it when it begins and re-checks it before each
     *     {@link #acceptChunk} write, so a straggler thread from a PREVIOUS, abandoned attempt (e.g. an HTTP body
     *     still draining after a timeout cancel) is fenced out and cannot write into the storage a retry has since
     *     reset — the bytes of one attempt never bleed into another. <br>
     * RU: Монотонно растущий токен, идентифицирующий ТЕКУЩУЮ попытку загрузки, увеличиваемый в начале каждой
     *     попытки (включая повтор). Стратегия загрузки захватывает его в начале и перепроверяет перед каждой
     *     записью {@link #acceptChunk}, поэтому поток-«отставший» от ПРЕДЫДУЩЕЙ, брошенной попытки (например, тело
     *     HTTP, ещё дочитывающееся после отмены по таймауту) отсекается и не может писать в хранилище, которое
     *     повтор уже сбросил, — байты одной попытки никогда не перетекают в другую. <br>
     * ==================================================================<br>
     * @return <br>
     *         {long} - EN: the current download-attempt token / RU: токен текущей попытки загрузки <br>
     **/
    long downloadEpoch();

    /**
     * EN: Number of independently fetched parts: 1 for a single/self-split file, or N for a
     *     Content-Delivery-Network (CDN) multi-part / 206-range split. <br>
     * RU: Число независимо скачиваемых частей: 1 для одиночного/self-split файла или N для многочастного
     *     файла сети доставки контента (CDN) / разбивки по диапазонам 206. <br>
     * ==================================================================<br>
     * @return <br>
     *         {int} - EN: the part count / RU: число частей <br>
     **/
    int partCount();

    /**
     * EN: Accepts one chunk of a part's raw bytes at the given absolute offset within that part, on behalf of the
     *     download attempt identified by {@code epoch} ({@link #downloadEpoch()} captured when the strategy began).
     *     The write is dropped ATOMICALLY (under the request's own lock, mutually exclusive with the retry reset)
     *     when {@code epoch} no longer matches the current attempt — so a straggler thread of a previous, cancelled
     *     attempt can never splice stale bytes into the storage a retry has reset. Consumes the buffer's remaining
     *     bytes. In-memory requests append them sequentially; a temp-file request writes them at their offset via a
     *     positioned write, so out-of-order or concurrent parts land correctly. <br>
     * RU: Принимает один кусок сырых байтов части по указанному абсолютному смещению внутри этой части от имени
     *     попытки загрузки, определяемой {@code epoch} ({@link #downloadEpoch()}, захваченным, когда стратегия
     *     началась). Запись АТОМАРНО (под собственным локом запроса, взаимоисключающим со сбросом при повторе)
     *     отбрасывается, когда {@code epoch} больше не совпадает с текущей попыткой — поэтому поток-«отставший» от
     *     предыдущей, отменённой попытки не может вставить устаревшие байты в хранилище, которое повтор сбросил.
     *     Потребляет оставшиеся байты буфера. Запросы в памяти дописывают их последовательно; запрос с временным
     *     файлом пишет их по смещению позиционной записью, поэтому части в произвольном порядке или параллельные
     *     части ложатся корректно. <br>
     * ==================================================================<br>
     * EN: @param epoch the download-attempt token the write belongs to / RU: @param epoch токен попытки загрузки, которой принадлежит запись <br>
     * EN: @param partIndex the part this chunk belongs to / RU: @param partIndex часть, которой принадлежит кусок <br>
     * EN: @param offset the absolute byte offset within the part / RU: @param offset абсолютное смещение байта внутри части <br>
     * EN: @param src the source buffer (its remaining bytes are consumed) / RU: @param src исходный буфер (его оставшиеся байты потребляются) <br>
     **/
    void acceptChunk(long epoch, int partIndex, long offset, ByteBuffer src) throws IOException;

    /**
     * EN: Signals that all bytes of the given part have been received. <br>
     * RU: Сигнализирует, что все байты указанной части получены. <br>
     * ==================================================================<br>
     * EN: @param partIndex the completed part / RU: @param partIndex завершённая часть <br>
     **/
    void partComplete(int partIndex) throws IOException;

    /**
     * EN: Signals that the whole raw payload (every part) has been received; the request may finalise its raw
     *     store (for a temp file, flush pending save writes). <br>
     * RU: Сигнализирует, что все сырые данные (все части) получены; запрос может финализировать своё сырое
     *     хранилище (для временного файла — дождаться отложенных операций записи). <br>
     **/
    void downloadComplete() throws IOException;
}
