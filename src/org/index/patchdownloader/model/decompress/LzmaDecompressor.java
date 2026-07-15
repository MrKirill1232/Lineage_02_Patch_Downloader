package org.index.patchdownloader.model.decompress;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

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
        long sizeHint = getUnCompressSize(compressData);
        if (sizeHint <= 0)
        {
            sizeHint = Math.max((long) compressData.length * 2, 1024L);
        }
        // Cap the initial buffer so a corrupt/oversized header size cannot trigger a huge upfront
        // allocation; the growable stream still expands as needed for genuinely large files.
        int initialBuffer = (int) Math.min(sizeHint, MAX_INITIAL_BUFFER);
        try (LZMAInputStream archiveStream = new LZMAInputStream(new ByteArrayInputStream(compressData));
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream(initialBuffer))
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

    /**
     * EN: Streams an LZMA stream from {@code in} to {@code out} through the same {@link LZMAInputStream} decoder
     *     the array path uses, so the decompressed bytes are identical; only the source and destination are
     *     channels instead of arrays, keeping memory at one fixed transfer buffer. The decoder wraps {@code in}
     *     via {@link Channels#newInputStream}; closing the decoder closes that source stream (hence {@code in}),
     *     while {@code out} is left open for the caller. <br>
     * RU: Потоково передаёт LZMA-поток из {@code in} в {@code out} тем же декодером {@link LZMAInputStream}, что
     *     использует путь через массив, поэтому распакованные байты идентичны; отличаются лишь источник и
     *     приёмник — каналы вместо массивов, что удерживает память в пределах одного фиксированного буфера
     *     передачи. Декодер оборачивает {@code in} через {@link Channels#newInputStream}; закрытие декодера
     *     закрывает этот источник (а значит и {@code in}), тогда как {@code out} остаётся открытым для
     *     вызывающего. <br>
     * ==================================================================<br>
     * EN: @param in the LZMA byte source / RU: @param in источник байтов LZMA <br>
     * EN: @param out the decompressed byte sink / RU: @param out приёмник распакованных байтов <br>
     * EN: @param expectedFinalLength expected uncompressed length or -1 (unused) / RU: @param expectedFinalLength ожидаемая распакованная длина или -1 (не используется) <br>
     **/
    @Override
    public void decompress(ReadableByteChannel in, WritableByteChannel out, long expectedFinalLength) throws IOException
    {
        try (LZMAInputStream archiveStream = new LZMAInputStream(Channels.newInputStream(in)))
        {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = archiveStream.read(buffer)) != -1)
            {
                Decompressors.writeFully(out, ByteBuffer.wrap(buffer, 0, read));
            }
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
    public boolean check(byte[] compressData, long expectedFinalLength)
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
        long uncompressedSize = getUnCompressSize(compressData);
        if (uncompressedSize <= 0)
        {
            return false;
        }
        return true;
    }

    /**
     * EN: LZMA self-recognises an already-final payload by length: when the raw length equals the expected
     *     decompressed length, the bytes ARE the finished file, not an LZMA stream — mirrors the length half of
     *     {@link #check(byte[], long)} for the streaming path, which only peeks the header. <br>
     * RU: LZMA распознаёт уже-финальный объём по длине: когда сырая длина равна ожидаемой распакованной, байты и
     *     ЕСТЬ готовый файл, а не LZMA-поток — повторяет размерную половину {@link #check(byte[], long)} для
     *     потокового пути, который лишь подглядывает заголовок. <br>
     * ==================================================================<br>
     * EN: @param rawLength the raw payload length, or -1 / RU: @param rawLength сырая длина объёма или -1 <br>
     * EN: @param expectedFinalLength the expected decompressed length, or -1 / RU: @param expectedFinalLength ожидаемая распакованная длина или -1 <br>
     * @return <br>
     *         {boolean} - EN: true when raw length == expected final length / RU: true, когда сырая длина == ожидаемой финальной <br>
     **/
    @Override
    public boolean isAlreadyFinal(long rawLength, long expectedFinalLength)
    {
        return expectedFinalLength > 0 && rawLength == expectedFinalLength;
    }

    @Override
    public long getCompressSize(byte[] compressedDataArray)
    {
        return compressedDataArray.length;
    }

    /**
     * EN: Reads the 64-bit little-endian uncompressed-size field at offset 5 of the LZMA header as a
     *     full {@code long} (no truncation), so sizes at or above two gigabytes are represented exactly. <br>
     * RU: Читает 64-битное little-endian поле распакованного размера по смещению 5 заголовка LZMA как
     *     полноценный {@code long} (без усечения), поэтому размеры от двух гигабайт и выше
     *     представляются точно. <br>
     * ==================================================================<br>
     * EN: @param compressedDataArray the LZMA bytes / RU: @param compressedDataArray байты LZMA <br>
     * @return <br>
     *         {long} - EN: uncompressed size / RU: распакованный размер <br>
     **/
    @Override
    public long getUnCompressSize(byte[] compressedDataArray)
    {
        long uncompSize = 0;
        for (int index = 0; index < 8; ++index)
        {
            uncompSize |= ((long) (compressedDataArray[index + 5] & 255)) << (8 * index);
        }
        return uncompSize;
    }
}
