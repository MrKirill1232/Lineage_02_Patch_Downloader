package org.index.patchdownloader.model.decompress;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.index.patchdownloader.interfaces.IDecompressor;
import org.index.patchdownloader.interfaces.IDummyLogger;

/**
 * EN: ZIP codec. Reads the first entry into a growable buffer (exact-length output, tolerant of an
 *     unknown declared size). Fails loudly (throws) instead of returning empty data. Stateless.<br>
 * RU: Кодек ZIP. Читает первую запись в растущий буфер (вывод точной длины, устойчив к неизвестному
 *     объявленному размеру). При ошибке сразу выбрасывает исключение, а не возвращает пустой массив.
 *     Без состояния.<br>
 **/
public class ZipDecompressor implements IDecompressor
{
    private static final int LOCAL_HEADER_MIN_LENGTH = 30;
    private static final int MAX_INITIAL_BUFFER = 32 * 1024 * 1024;

    /**
     * EN: Decompresses the first ZIP entry into an exact-length array; warns if the archive has more
     *     than one entry (only the first is used). <br>
     * RU: Распаковывает первую запись ZIP в массив точной длины; предупреждает, если в архиве более
     *     одной записи (используется только первая). <br>
     * ==================================================================<br>
     * EN: @param compressData the ZIP bytes / RU: @param compressData байты ZIP <br>
     * @return <br>
     *         {byte[]} - EN: exact-length decompressed bytes / RU: распакованные байты точной длины <br>
     **/
    @Override
    public byte[] decompress(byte[] compressData) throws IOException
    {
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(compressData)))
        {
            ZipEntry zipEntry = zipInputStream.getNextEntry();
            if (zipEntry == null)
            {
                throw new IOException("ZIP archive has no entries.");
            }
            long declaredSize = zipEntry.getSize();
            int sizeHint = declaredSize > 0 && declaredSize < Integer.MAX_VALUE ? (int) declaredSize : Math.max(compressData.length * 2, 1024);
            // Cap the initial buffer so an oversized declared entry size cannot trigger a huge upfront
            // allocation; the growable stream still expands as needed for genuinely large entries.
            sizeHint = Math.min(sizeHint, MAX_INITIAL_BUFFER);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(sizeHint);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = zipInputStream.read(buffer)) != -1)
            {
                outputStream.write(buffer, 0, read);
            }
            if (zipInputStream.getNextEntry() != null)
            {
                IDummyLogger.log(IDummyLogger.WARNING, "ZIP archive has multiple entries; only the first is used.");
            }
            return outputStream.toByteArray();
        }
    }

    /**
     * EN: Streams the first ZIP entry from {@code in} to {@code out} through the same {@link ZipInputStream}
     *     decoder the array path uses, so the decompressed bytes are identical; only the first entry is used and a
     *     multi-entry archive is warned about, exactly as the array overload does. The decoder wraps {@code in}
     *     via {@link Channels#newInputStream}; closing the decoder closes that source stream (hence {@code in}),
     *     while {@code out} is left open for the caller. Memory stays at one fixed transfer buffer. <br>
     * RU: Потоково передаёт первую запись ZIP из {@code in} в {@code out} тем же декодером {@link ZipInputStream},
     *     что использует путь через массив, поэтому распакованные байты идентичны; используется только первая
     *     запись, а о многозаписном архиве выдаётся предупреждение — ровно как в перегрузке через массив. Декодер
     *     оборачивает {@code in} через {@link Channels#newInputStream}; закрытие декодера закрывает этот источник
     *     (а значит и {@code in}), тогда как {@code out} остаётся открытым для вызывающего. Память удерживается в
     *     пределах одного фиксированного буфера передачи. <br>
     * ==================================================================<br>
     * EN: @param in the ZIP byte source / RU: @param in источник байтов ZIP <br>
     * EN: @param out the decompressed byte sink / RU: @param out приёмник распакованных байтов <br>
     * EN: @param expectedFinalLength expected uncompressed length or -1 (unused) / RU: @param expectedFinalLength ожидаемая распакованная длина или -1 (не используется) <br>
     **/
    @Override
    public void decompress(ReadableByteChannel in, WritableByteChannel out, long expectedFinalLength) throws IOException
    {
        try (ZipInputStream zipInputStream = new ZipInputStream(Channels.newInputStream(in)))
        {
            ZipEntry zipEntry = zipInputStream.getNextEntry();
            if (zipEntry == null)
            {
                throw new IOException("ZIP archive has no entries.");
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = zipInputStream.read(buffer)) != -1)
            {
                Decompressors.writeFully(out, ByteBuffer.wrap(buffer, 0, read));
            }
            if (zipInputStream.getNextEntry() != null)
            {
                IDummyLogger.log(IDummyLogger.WARNING, "ZIP archive has multiple entries; only the first is used.");
            }
        }
    }

    /**
     * EN: Recognises a ZIP archive by its local-file-header magic ({@code PK\003\004}) and minimum
     *     length. <br>
     * RU: Распознаёт ZIP-архив по сигнатуре локального заголовка ({@code PK\003\004}) и минимальной
     *     длине. <br>
     * ==================================================================<br>
     * EN: @param compressData the candidate bytes / RU: @param compressData байты-кандидат <br>
     * EN: @param expectedFinalLength expected uncompressed length or -1 / RU: @param expectedFinalLength ожидаемая распакованная длина или -1 <br>
     * @return <br>
     *         {true}  - EN: looks like a ZIP archive / RU: похоже на ZIP-архив <br>
     *         {false} - EN: not a ZIP archive / RU: не ZIP-архив <br>
     **/
    @Override
    public boolean check(byte[] compressData, long expectedFinalLength)
    {
        if (compressData.length < LOCAL_HEADER_MIN_LENGTH)
        {
            return false;
        }
        boolean zipMagic = compressData[0] == 0x50 && compressData[1] == 0x4B && compressData[2] == 0x03 && compressData[3] == 0x04;
        return zipMagic;
    }

    @Override
    public long getCompressSize(byte[] compressedDataArray)
    {
        long compressedSize = 0;
        compressedSize |= (compressedDataArray[18] & 0xff) | ((compressedDataArray[18 + 1] & 0xff) << 8);
        compressedSize |= ((long) (compressedDataArray[18 + 2] & 0xff) | ((compressedDataArray[18 + 3] & 0xff) << 8)) << 16;
        compressedSize &= 0xffffffffL;
        return compressedSize;
    }

    @Override
    public long getUnCompressSize(byte[] compressedDataArray)
    {
        long uncompSize = 0;
        uncompSize |= (compressedDataArray[22] & 0xff) | ((compressedDataArray[22 + 1] & 0xff) << 8);
        uncompSize |= ((long) (compressedDataArray[22 + 2] & 0xff) | ((compressedDataArray[22 + 3] & 0xff) << 8)) << 16;
        uncompSize &= 0xffffffffL;
        return uncompSize;
    }
}
