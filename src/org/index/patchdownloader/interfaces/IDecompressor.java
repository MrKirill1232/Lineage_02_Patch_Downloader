package org.index.patchdownloader.interfaces;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * EN: Contract for a single archive codec (LZMA (Lempel-Ziv-Markov chain Algorithm) / ZIP). Stateless: operates on raw byte arrays only,
 *     with no dependency on any pipeline request/task type, so both the decompress stage and the link
 *     generators (which unpack the file list itself) can use it.<br>
 * RU: Контракт одного кодека архива (LZMA (алгоритм Лемпеля-Зива-Маркова) / ZIP). Без состояния: работает только с сырыми массивами
 *     байтов, без зависимости от типов запросов/задач конвейера, поэтому его могут использовать и
 *     стадия распаковки, и генераторы ссылок (которые распаковывают сам список файлов).<br>
 **/
public interface IDecompressor
{
    /**
     * EN: Decompresses the given data and returns an exact-length byte array (no trailing padding).
     *     Throws on a malformed/failed stream instead of returning empty data. <br>
     * RU: Распаковывает данные и возвращает массив точной длины (без хвостовых нулей). Бросает
     *     исключение при повреждённом/неудачном потоке вместо возврата пустых данных. <br>
     * ==================================================================<br>
     * EN: @param compressData the compressed bytes / RU: @param compressData сжатые байты <br>
     * @return <br>
     *         {byte[]} - EN: the exact-length decompressed bytes / RU: распакованные байты точной длины <br>
     **/
    byte[] decompress(byte[] compressData) throws IOException;

    /**
     * EN: Streaming decompression: reads the still-compressed bytes from {@code in} and writes the exact
     *     decompressed bytes to {@code out}, holding only small fixed transfer buffers in memory rather than the
     *     whole raw or whole decompressed payload. The decompressed bytes are byte-for-byte identical to what the
     *     {@link #decompress(byte[])} overload would return for the same input, so a caller may freely pick the
     *     array path (small, in-memory files) or this streaming path (large, temp-file files). The caller owns
     *     both channels and closes them; this method must not close {@code out}, and closes {@code in} only if the
     *     underlying codec stream does so on its own close. {@code expectedFinalLength} is the expected
     *     decompressed length (or {@code -1} when unknown) and is available for sizing hints; it is never used to
     *     truncate or pad the output. <br>
     * RU: Потоковая распаковка: читает ещё сжатые байты из {@code in} и пишет распакованные байты точной длины в
     *     {@code out}, удерживая в памяти лишь небольшие фиксированные буферы передачи, а не весь сырой либо весь
     *     распакованный объём. Распакованные байты побайтно идентичны тому, что вернула бы перегрузка
     *     {@link #decompress(byte[])} для того же входа, поэтому вызывающий код волен выбирать путь через массив
     *     (небольшие файлы в памяти) или этот потоковый путь (большие файлы через временный файл). Каналами
     *     владеет вызывающий и он же их закрывает; этот метод не должен закрывать {@code out} и закрывает
     *     {@code in} лишь если это делает сам поток кодека при своём закрытии. {@code expectedFinalLength} —
     *     ожидаемая распакованная длина (или {@code -1}, если неизвестна) — доступна как подсказка размера; она
     *     никогда не используется для усечения или дополнения вывода. <br>
     * ==================================================================<br>
     * EN: @param in the raw (still-compressed) byte source / RU: @param in источник сырых (ещё сжатых) байтов <br>
     * EN: @param out the decompressed byte sink / RU: @param out приёмник распакованных байтов <br>
     * EN: @param expectedFinalLength expected uncompressed length or -1 / RU: @param expectedFinalLength ожидаемая распакованная длина или -1 <br>
     **/
    void decompress(ReadableByteChannel in, WritableByteChannel out, long expectedFinalLength) throws IOException;

    /**
     * EN: Heuristically decides whether the data looks like a decodable archive of this codec (and is
     *     not already the final uncompressed content of the given expected length). <br>
     * RU: Эвристически решает, похожи ли данные на декодируемый архив этого кодека (и не являются ли
     *     уже финальным распакованным содержимым указанной ожидаемой длины). <br>
     * ==================================================================<br>
     * EN: @param compressData the candidate bytes / RU: @param compressData байты-кандидат <br>
     * EN: @param expectedFinalLength expected uncompressed length, or -1 if unknown / RU: @param expectedFinalLength ожидаемая распакованная длина или -1 <br>
     * @return <br>
     *         {true}  - EN: looks compressed, decode it / RU: похоже сжато, распаковать <br>
     *         {false} - EN: not compressed / not this codec, use as-is / RU: не сжато / не этот кодек, использовать как есть <br>
     **/
    boolean check(byte[] compressData, long expectedFinalLength);

    /**
     * EN: Whether a payload of raw length {@code rawLength} is ALREADY the final uncompressed content (so it must
     *     be passed through, not decoded), from length alone — the recognition the streaming path uses because it
     *     only peeks the header and cannot run the full-payload {@link #check(byte[], long)}. Codecs that
     *     self-recognise by length (LZMA: raw length == expected final length) override this; codecs that recognise
     *     purely by a magic header (ZIP) keep the default {@code false}, so a genuine archive whose compressed size
     *     coincidentally equals its decompressed size is still decoded, not stored raw. <br>
     * RU: Является ли объём сырой длины {@code rawLength} УЖЕ финальным распакованным содержимым (значит, его надо
     *     пропустить, а не декодировать), исходя из одной длины — распознавание, которое использует потоковый путь,
     *     так как он лишь подглядывает заголовок и не может выполнить полную {@link #check(byte[], long)}. Кодеки,
     *     распознающие по длине (LZMA: сырая длина == ожидаемая финальная), переопределяют это; кодеки,
     *     распознающие только по магическому заголовку (ZIP), сохраняют дефолт {@code false}, поэтому настоящий
     *     архив, чей сжатый размер случайно равен распакованному, всё равно декодируется, а не хранится сырым. <br>
     * ==================================================================<br>
     * EN: @param rawLength the raw payload length, or -1 if unknown / RU: @param rawLength сырая длина объёма или -1, если неизвестна <br>
     * EN: @param expectedFinalLength the expected decompressed length, or -1 / RU: @param expectedFinalLength ожидаемая распакованная длина или -1 <br>
     * @return <br>
     *         {true}  - EN: already the final file (pass through) / RU: уже финальный файл (пропустить) <br>
     *         {false} - EN: decode it / RU: декодировать <br>
     **/
    default boolean isAlreadyFinal(long rawLength, long expectedFinalLength)
    {
        return false;
    }

    /**
     * EN: Reads the codec-specific compressed-size field from the header. <br>
     * RU: Читает специфичное для кодека поле размера сжатых данных из заголовка. <br>
     * ==================================================================<br>
     * EN: @param compressData the compressed bytes / RU: @param compressData сжатые байты <br>
     * @return <br>
     *         {long} - EN: compressed size / RU: размер сжатых данных <br>
     **/
    long getCompressSize(byte[] compressData);

    /**
     * EN: Reads the codec-specific uncompressed-size field from the header. <br>
     * RU: Читает специфичное для кодека поле размера распакованных данных из заголовка. <br>
     * ==================================================================<br>
     * EN: @param compressData the compressed bytes / RU: @param compressData сжатые байты <br>
     * @return <br>
     *         {long} - EN: uncompressed size / RU: размер распакованных данных <br>
     **/
    long getUnCompressSize(byte[] compressData);
}
