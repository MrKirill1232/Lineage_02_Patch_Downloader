package org.index.patchdownloader.interfaces;

import java.io.IOException;

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
    boolean check(byte[] compressData, int expectedFinalLength);

    /**
     * EN: Reads the codec-specific compressed-size field from the header. <br>
     * RU: Читает специфичное для кодека поле размера сжатых данных из заголовка. <br>
     * ==================================================================<br>
     * EN: @param compressData the compressed bytes / RU: @param compressData сжатые байты <br>
     * @return <br>
     *         {int} - EN: compressed size / RU: размер сжатых данных <br>
     **/
    int getCompressSize(byte[] compressData);

    /**
     * EN: Reads the codec-specific uncompressed-size field from the header. <br>
     * RU: Читает специфичное для кодека поле размера распакованных данных из заголовка. <br>
     * ==================================================================<br>
     * EN: @param compressData the compressed bytes / RU: @param compressData сжатые байты <br>
     * @return <br>
     *         {int} - EN: uncompressed size / RU: размер распакованных данных <br>
     **/
    int getUnCompressSize(byte[] compressData);
}
