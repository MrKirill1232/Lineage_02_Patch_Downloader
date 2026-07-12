package org.index.patchdownloader.model.decompress;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.index.patchdownloader.interfaces.IDecompressor;
import org.index.patchdownloader.interfaces.IDummyLogger;
import org.tukaani.xz.LZMAInputStream;

/**
 * EN: LZMA (Lempel-Ziv-Markov chain Algorithm) codec. Reads the whole stream into a growable
 *     buffer, so the returned array is exact length (fixes the old trailing-zero backing-array
 *     bug). Fails loudly (throws) instead of returning empty data. Stateless.<br>
 * RU: Кодек LZMA (Lempel-Ziv-Markov chain Algorithm). Читает весь поток в растущий буфер, поэтому
 *     возвращаемый массив имеет точную длину (исправляет старую ошибку с лишними нулями в конце
 *     базового массива (backing array)). При ошибке сразу выбрасывает исключение, а не возвращает
 *     пустой массив. Без состояния.<br>
 **/
public class LzmaDecompressor implements IDecompressor
{
    private static final int MIN_HEADER_LENGTH = 13;
    private static final int MAX_INITIAL_BUFFER = 32 * 1024 * 1024;

    /**
     * EN: Decompresses an LZMA stream into an exact-length array using a growable output buffer. <br>
     * RU: Распаковывает LZMA-поток в массив точной длины с помощью растущего буфера. <br>
     * ==================================================================<br>
     * EN: @param compressData the LZMA bytes / RU: @param compressData байты LZMA <br>
     * @return <br>
     *         {byte[]} - EN: exact-length decompressed bytes / RU: распакованные байты точной длины <br>
     **/
    @Override
    public byte[] decompress(byte[] compressData) throws IOException
    {
        int sizeHint = getUnCompressSize(compressData);
        if (sizeHint <= 0 || sizeHint == Integer.MAX_VALUE)
        {
            sizeHint = Math.max(compressData.length * 2, 1024);
        }
        // Cap the initial buffer so a corrupt/oversized header size cannot trigger a huge upfront
        // allocation; the growable stream still expands as needed for genuinely large files.
        sizeHint = Math.min(sizeHint, MAX_INITIAL_BUFFER);
        try (LZMAInputStream archiveStream = new LZMAInputStream(new ByteArrayInputStream(compressData));
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream(sizeHint))
        {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = archiveStream.read(buffer)) != -1)
            {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }

    private static int getPropertiesByte(byte[] input)
    {
        return input[0] & 0xFF;
    }

    private static int getDictionarySize(byte[] input)
    {
        int dictSize = 0;
        for (int index = 0; index < 4; ++index)
        {
            dictSize |= (input[index + 1] & 255) << (8 * index);
        }
        return dictSize;
    }

    /**
     * EN: Validates the LZMA header (properties byte, dictionary size, uncompressed size) and that
     *     the data is not already the final uncompressed content. <br>
     * RU: Проверяет заголовок LZMA (байт свойств, размер словаря, распакованный размер) и что данные
     *     не являются уже финальным распакованным содержимым. <br>
     * ==================================================================<br>
     * EN: @param compressData the candidate bytes / RU: @param compressData байты-кандидат <br>
     * EN: @param expectedFinalLength expected uncompressed length or -1 / RU: @param expectedFinalLength ожидаемая распакованная длина или -1 <br>
     * @return <br>
     *         {true}  - EN: valid LZMA, decode it / RU: корректный LZMA, распаковать <br>
     *         {false} - EN: not decodable LZMA / RU: не декодируемый LZMA <br>
     **/
    @Override
    public boolean check(byte[] compressData, int expectedFinalLength)
    {
        if (compressData.length < MIN_HEADER_LENGTH)
        {
            return false;
        }
        if (expectedFinalLength > 0 && expectedFinalLength == compressData.length)
        {
            return false;
        }
        if (getPropertiesByte(compressData) > (4 * 5 + 4) * 9 + 8)
        {
            IDummyLogger.log(IDummyLogger.ERROR, "Cannot decode LZMA: invalid properties byte.");
            return false;
        }
        int dictionarySize = getDictionarySize(compressData);
        if (dictionarySize < 0 || dictionarySize > LZMAInputStream.DICT_SIZE_MAX)
        {
            IDummyLogger.log(IDummyLogger.ERROR, "Cannot decode LZMA: dictionary is too big for this implementation.");
            return false;
        }
        int uncompressedSize = getUnCompressSize(compressData);
        if (uncompressedSize <= 0 || uncompressedSize == Integer.MAX_VALUE)
        {
            return false;
        }
        return true;
    }

    @Override
    public int getCompressSize(byte[] compressedDataArray)
    {
        return compressedDataArray.length;
    }

    /**
     * EN: Reads the 64-bit little-endian uncompressed-size field at offset 5 of the LZMA header,
     *     clamped to {@code int}. <br>
     * RU: Читает 64-битное little-endian поле распакованного размера по смещению 5 заголовка LZMA,
     *     ограниченное {@code int}. <br>
     * ==================================================================<br>
     * EN: @param compressedDataArray the LZMA bytes / RU: @param compressedDataArray байты LZMA <br>
     * @return <br>
     *         {int} - EN: uncompressed size (clamped) / RU: распакованный размер (ограниченный) <br>
     **/
    @Override
    public int getUnCompressSize(byte[] compressedDataArray)
    {
        long uncompSize = 0;
        for (int index = 0; index < 8; ++index)
        {
            uncompSize |= ((long) (compressedDataArray[index + 5] & 255)) << (8 * index);
        }
        return (int) Math.min(Integer.MAX_VALUE, uncompSize);
    }
}
