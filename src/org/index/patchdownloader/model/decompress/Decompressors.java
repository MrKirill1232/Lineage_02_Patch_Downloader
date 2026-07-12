package org.index.patchdownloader.model.decompress;

import java.io.IOException;

import org.index.patchdownloader.enums.ArchiveType;
import org.index.patchdownloader.interfaces.IDecompressor;
import org.index.patchdownloader.interfaces.IDummyLogger;

/**
 * EN: Stateless entry point for decompression, keyed by {@link ArchiveType} ordinal. Used by both
 *     the decompress stage and the link generators (which unpack the file list itself), so no
 *     request/task type leaks into the codec layer. Returns the input unchanged when it is not a
 *     recognised archive.<br>
 * RU: Точка входа распаковки без состояния, индексируемая по порядковому номеру (ordinal) элемента {@link ArchiveType}.
 *     Используется и стадией распаковки, и генераторами ссылок (которые распаковывают сам список
 *     файлов), поэтому типы запросов/задач не протекают в слой кодеков. Возвращает вход без
 *     изменений, если это не распознанный архив.<br>
 **/
public final class Decompressors
{
    private static final IDecompressor[] DECOMPRESSORS;

    /**
     * EN: Absolute output ceiling used only when the expected uncompressed length is unknown (-1).
     *     Bounds a decompression bomb (an archive whose header declares gigabytes) that has no
     *     expected-length reference to check against. <br>
     * RU: Абсолютный потолок вывода, применяемый только когда ожидаемая распакованная длина неизвестна
     *     (-1). Ограничивает «архивную бомбу» (архив, заголовок которого объявляет гигабайты), для
     *     которой нет эталонной ожидаемой длины для сверки. <br>
     **/
    private static final long ABSOLUTE_MAX_OUTPUT = 256L * 1024 * 1024;

    static
    {
        DECOMPRESSORS = new IDecompressor[ArchiveType.values().length];
        DECOMPRESSORS[ArchiveType.LZMA_ARCHIVE.ordinal()] = new LzmaDecompressor();
        DECOMPRESSORS[ArchiveType.ZIP_ARCHIVE.ordinal()] = new ZipDecompressor();
    }

    private Decompressors()
    {
    }

    /**
     * EN: Decompresses the data according to the archive type. If the type is {@code NONE}/unknown or
     *     the data is not a recognised archive of that type, the data is returned unchanged.
     *     Otherwise the codec returns an exact-length decompressed array (or throws on a bad stream). <br>
     * RU: Распаковывает данные согласно типу архива. Если тип {@code NONE}/неизвестен или данные не
     *     являются распознанным архивом этого типа — данные возвращаются без изменений. Иначе кодек
     *     возвращает распакованный массив точной длины (или выбрасывает исключение при повреждённом потоке). <br>
     * ==================================================================<br>
     * EN: @param type the archive type / RU: @param type тип архива <br>
     * EN: @param data the (possibly compressed) bytes / RU: @param data (возможно сжатые) байты <br>
     * EN: @param expectedFinalLength expected uncompressed length or -1 / RU: @param expectedFinalLength ожидаемая распакованная длина или -1 <br>
     * @return <br>
     *         {byte[]} - EN: decompressed bytes, or the input unchanged / RU: распакованные байты или вход без изменений <br>
     **/
    public static byte[] decompress(ArchiveType type, byte[] data, int expectedFinalLength) throws IOException
    {
        if (data == null)
        {
            return new byte[0];
        }
        if (type == null || type == ArchiveType.NONE)
        {
            return data;
        }
        IDecompressor decompressor = DECOMPRESSORS[type.ordinal()];
        if (decompressor == null || !decompressor.check(data, expectedFinalLength))
        {
            // Not a recognised archive of this type -> pass through unchanged. If we DO know the expected
            // final length and it does not match, the data is suspicious (declared archive but neither
            // decodable nor the finished file) — surface it instead of silently storing corrupt bytes.
            if (decompressor != null && expectedFinalLength > 0 && data.length != expectedFinalLength)
            {
                IDummyLogger.log(IDummyLogger.WARNING, "Data declared as " + type + " is not decodable and does not match expected length (" + data.length + " vs " + expectedFinalLength + "); storing as-is.");
            }
            return data;
        }
        // EN: Guard against a decompression bomb. When the expected uncompressed length is known, allow
        //     it plus a small slack; otherwise fall back to a fixed absolute ceiling. First reject a
        //     header that already declares an over-cap size (stops the huge decode before any
        //     allocation), then re-check the actual output length in case the header lied.
        // RU: Защита от «архивной бомбы». Если ожидаемая распакованная длина известна, разрешаем её плюс
        //     небольшой запас; иначе используем фиксированный абсолютный потолок. Сначала отклоняем
        //     заголовок, который уже объявляет размер сверх лимита (это останавливает громоздкую
        //     распаковку до какой-либо аллокации), затем перепроверяем фактическую длину вывода на
        //     случай, если заголовок соврал.
        long outputCap = expectedFinalLength > 0 ? (long) expectedFinalLength + (expectedFinalLength / 4) + 65536L : ABSOLUTE_MAX_OUTPUT;
        int declaredSize = decompressor.getUnCompressSize(data);
        if (declaredSize > outputCap)
        {
            throw new IOException("Refusing to decompress " + type + ": declared size exceeds cap (" + declaredSize + " > " + outputCap + ").");
        }
        byte[] result = decompressor.decompress(data);
        if (result.length > outputCap)
        {
            throw new IOException("Decompressed " + type + " output exceeds cap (" + result.length + " > " + outputCap + ").");
        }
        return result;
    }
}
