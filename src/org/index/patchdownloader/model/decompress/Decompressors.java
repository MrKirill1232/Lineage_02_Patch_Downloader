package org.index.patchdownloader.model.decompress;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;

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

    /**
     * EN: How many leading bytes the streaming path peeks so the codec can inspect its header — detect the format
     *     and read the uncompressed size — without buffering the whole file. 64 comfortably covers the largest
     *     header (ZIP = 30 bytes, LZMA = 13); the peeked bytes are put back ahead of the rest of the stream, so
     *     none are lost. <br>
     * RU: Сколько ведущих байтов подглядывает потоковый путь, чтобы кодек рассмотрел свой заголовок — определил
     *     формат и прочитал распакованный размер — не буферизуя весь файл. 64 с запасом покрывают самый большой
     *     заголовок (ZIP = 30 байт, LZMA = 13); подглянутые байты возвращаются в начало потока, поэтому ни один не
     *     теряется. <br>
     **/
    private static final int HEADER_PEEK = 64;

    /**
     * EN: Fixed transfer-buffer size for the streaming copy / pass-through loops. <br>
     * RU: Фиксированный размер буфера передачи для потоковых циклов копирования / сквозного прохода. <br>
     **/
    private static final int TRANSFER_BUFFER = 8192;

    static
    {
        DECOMPRESSORS = new IDecompressor[ArchiveType.values().length];
        DECOMPRESSORS[ArchiveType.LZMA_ARCHIVE.ordinal()] = new LzmaDecompressor();
        DECOMPRESSORS[ArchiveType.ZIP_ARCHIVE.ordinal()] = new ZipDecompressor();
    }

    private Decompressors()
    {
    }

    // =====================================================================================
    // =======================  IN-MEMORY (byte[]) DECODE PATH  ============================
    // ===  For callers that already hold the whole payload in RAM: MEMORY storage mode  ===
    // ===  and the link generators that unpack a downloaded file list. Peer of the        =
    // ===  streaming path below; both produce byte-for-byte identical output.             =
    // =====================================================================================

    /**
     * EN: In-memory decode — the path for callers that already hold the whole payload in RAM: MEMORY storage mode
     *     ({@code DecompressStageManager.decompressInMemory}) and the link generators that unpack a downloaded
     *     file list (e.g. {@code NcKoreanLinkGenerator}). Temp-file mode uses the streaming overload below instead.
     *     When the type is {@code NONE}/unknown, or the bytes are not a recognised archive of that type, they are
     *     returned unchanged; otherwise the codec decodes them — bounded by the zip-bomb guard — into an
     *     exact-length array (or throws on a corrupt stream). <br>
     * RU: Декод в памяти — путь для вызывающих, у которых весь объём уже в оперативной памяти: режим хранения
     *     MEMORY ({@code DecompressStageManager.decompressInMemory}) и генераторы ссылок, распаковывающие скачанный
     *     список файлов (напр. {@code NcKoreanLinkGenerator}). Режим временного файла использует потоковую
     *     перегрузку ниже. Если тип {@code NONE}/неизвестен или байты не являются распознанным архивом этого типа —
     *     они возвращаются без изменений; иначе кодек декодирует их — ограничивая защитой от «архивной бомбы» — в
     *     массив точной длины (или бросает исключение при повреждённом потоке). <br>
     * ==================================================================<br>
     * EN: @param type the archive type / RU: @param type тип архива <br>
     * EN: @param data the (possibly compressed) bytes / RU: @param data (возможно сжатые) байты <br>
     * EN: @param expectedFinalLength expected uncompressed length or -1 / RU: @param expectedFinalLength ожидаемая распакованная длина или -1 <br>
     * @return <br>
     *         {byte[]} - EN: decompressed bytes, or the input unchanged / RU: распакованные байты или вход без изменений <br>
     **/
    public static byte[] decompress(ArchiveType type, byte[] data, long expectedFinalLength) throws IOException
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
        // Zip-bomb guard. cap = expected length + slack, or a fixed ceiling when the length is unknown. Reject a
        // header that already declares over the cap right here (before any allocation); the actual output is also
        // capped as it inflates (see decompressBounded), in case the header lied.
        // RU: Защита от «архивной бомбы». Лимит = ожидаемая длина + запас, либо фиксированный потолок, если длина
        // неизвестна. Заголовок, уже заявляющий размер сверх лимита, отклоняем прямо здесь (до аллокации);
        // фактический вывод тоже ограничивается по мере распаковки (см. decompressBounded) — вдруг заголовок соврал.
        long outputCap = expectedFinalLength > 0 ? expectedFinalLength + (expectedFinalLength / 4) + 65536L : ABSOLUTE_MAX_OUTPUT;
        long declaredSize = decompressor.getUnCompressSize(data);
        if (declaredSize > outputCap)
        {
            throw new IOException("Refusing to decompress " + type + ": declared size exceeds cap (" + declaredSize + " > " + outputCap + ").");
        }
        // Enforce the cap DURING inflation, not after: a data-descriptor ZIP declares 0 in its header, so the
        // declared-size pre-check passes and a bomb would otherwise inflate gigabytes into memory before any
        // post-hoc length check. Route the decode through the streaming codec + CappedChannel so it throws the
        // moment the running output exceeds the cap, before the heap fills.
        return decompressBounded(decompressor, data, outputCap, type, expectedFinalLength);
    }

    /**
     * EN: Decodes {@code data} through the codec's STREAMING path into an in-memory buffer wrapped by a
     *     {@link CappedChannel}, so a bomb is stopped mid-inflate the instant the running output crosses
     *     {@code cap}. Byte-for-byte identical to the array decode for a well-behaved archive (same codec), but
     *     bounded. <br>
     * RU: Декодирует {@code data} ПОТОКОВЫМ путём кодека в буфер в памяти, обёрнутый {@link CappedChannel}, поэтому
     *     «бомба» останавливается посреди распаковки в момент, когда суммарный вывод переходит {@code cap}.
     *     Побайтно идентично декоду по массиву для корректного архива (тот же кодек), но ограничено. <br>
     * ==================================================================<br>
     * EN: @param decompressor the codec / RU: @param decompressor кодек <br>
     * EN: @param data the compressed bytes / RU: @param data сжатые байты <br>
     * EN: @param cap the output cap in bytes / RU: @param cap потолок вывода в байтах <br>
     * EN: @param type the archive type (for messages) / RU: @param type тип архива (для сообщений) <br>
     * EN: @param expectedFinalLength expected uncompressed length or -1 / RU: @param expectedFinalLength ожидаемая распакованная длина или -1 <br>
     * @return <br>
     *         {byte[]} - EN: the bounded decompressed bytes / RU: ограниченные распакованные байты <br>
     **/
    private static byte[] decompressBounded(IDecompressor decompressor, byte[] data, long cap, ArchiveType type, long expectedFinalLength) throws IOException
    {
        int hint = (int) Math.min(Math.max(cap, 1024L), 1L << 20);
        ByteArrayOutputStream sink = new ByteArrayOutputStream(hint);
        try (ReadableByteChannel raw = Channels.newChannel(new ByteArrayInputStream(data));
             WritableByteChannel capped = new CappedChannel(Channels.newChannel(sink), cap, type))
        {
            decompressor.decompress(raw, capped, expectedFinalLength);
        }
        return sink.toByteArray();
    }

    // =====================================================================================
    // =======================  STREAMING (channel) DECODE PATH  ===========================
    // ===  For temp-file mode: raw source -> codec -> sink, never holding the whole      ===
    // ===  payload in memory, so a file larger than the heap can flow through. Peer of  ===
    // ===  the in-memory path above; same codec, byte-for-byte identical output.         ==
    // =====================================================================================

    /**
     * EN: Streaming counterpart of {@link #decompress(ArchiveType, byte[], long)}: reads the raw payload from
     *     {@code in} and writes the decompressed bytes to {@code out} without ever holding the whole raw or whole
     *     decompressed payload in memory, so a file larger than the heap can flow through. The output is
     *     byte-for-byte identical to what the array overload produces for the same input. Only the first
     *     {@link #HEADER_PEEK} bytes are buffered — to run the same {@code check} / bomb-guard header inspection —
     *     then re-served to the codec ahead of the rest of the stream, so nothing is dropped. When the type is
     *     {@code NONE}/unknown, or the bytes are not a recognised archive of that type, the raw bytes are copied
     *     through unchanged, mirroring the array overload's pass-through. The bomb guard is enforced two ways: the
     *     declared header size is rejected before decoding, and the actually written output is capped as it flows.
     *     The caller owns and closes both channels. <br>
     * RU: Потоковый аналог {@link #decompress(ArchiveType, byte[], long)}: читает сырой объём из {@code in} и
     *     пишет распакованные байты в {@code out}, ни разу не удерживая в памяти весь сырой либо весь
     *     распакованный объём, поэтому сквозь него может пройти файл больше кучи. Вывод побайтно идентичен тому,
     *     что даёт перегрузка по массиву для того же входа. Буферизуются только первые {@link #HEADER_PEEK}
     *     байтов — чтобы выполнить ту же проверку {@code check} / защиту от «архивной бомбы» по заголовку — и
     *     затем повторно подаются кодеку перед остальным потоком, поэтому ничего не теряется. Если тип
     *     {@code NONE}/неизвестен или байты не являются распознанным архивом этого типа, сырые байты копируются
     *     без изменений, как и в перегрузке по массиву. Защита от «бомбы» действует двумя путями: объявленный в
     *     заголовке размер отклоняется до декодирования, а фактически записанный вывод ограничивается по мере
     *     поступления. Каналами владеет и закрывает их вызывающий. <br>
     * ==================================================================<br>
     * EN: @param type the archive type / RU: @param type тип архива <br>
     * EN: @param in the raw (possibly compressed) byte source / RU: @param in источник сырых (возможно сжатых) байтов <br>
     * EN: @param out the decompressed byte sink / RU: @param out приёмник распакованных байтов <br>
     * EN: @param expectedFinalLength expected uncompressed length or -1 / RU: @param expectedFinalLength ожидаемая распакованная длина или -1 <br>
     **/
    public static void decompress(ArchiveType type, ReadableByteChannel in, WritableByteChannel out, long expectedFinalLength) throws IOException
    {
        if (in == null)
        {
            return;
        }
        if (type == null || type == ArchiveType.NONE)
        {
            copyAll(in, out);
            return;
        }
        IDecompressor decompressor = DECOMPRESSORS[type.ordinal()];
        long rawLength = in instanceof SeekableByteChannel seekable ? seekable.size() : -1L;
        byte[] header = peekHeader(in, HEADER_PEEK);
        ReadableByteChannel raw = new PrefixedChannel(header, in);
        // The array overload's check() sees the WHOLE payload and so rejects data whose length already equals the
        // expected final length (it is the finished file, not an archive — e.g. LZMA's self-recognition guard
        // {@code expectedFinalLength == compressData.length}). The streaming path only peeks the header, so
        // check(header) cannot see the true raw length; apply that same guard here from the channel's known size so
        // an already-decompressed payload is passed through, not re-decoded — keeping streaming byte-for-byte with
        // the array path.
        boolean alreadyFinal = decompressor != null && decompressor.isAlreadyFinal(rawLength, expectedFinalLength);
        if (decompressor == null || alreadyFinal || !decompressor.check(header, expectedFinalLength))
        {
            // Not a recognised archive of this type -> pass through unchanged. Mirror the array overload's
            // warning when the raw length is known, does not match the expected final length, and the bytes were
            // declared as this archive (suspicious: neither decodable nor the finished file).
            if (decompressor != null && expectedFinalLength > 0 && rawLength >= 0 && rawLength != expectedFinalLength)
            {
                IDummyLogger.log(IDummyLogger.WARNING, "Data declared as " + type + " is not decodable and does not match expected length (" + rawLength + " vs " + expectedFinalLength + "); storing as-is.");
            }
            copyAll(raw, out);
            return;
        }
        long outputCap = expectedFinalLength > 0 ? expectedFinalLength + (expectedFinalLength / 4) + 65536L : ABSOLUTE_MAX_OUTPUT;
        long declaredSize = decompressor.getUnCompressSize(header);
        if (declaredSize > outputCap)
        {
            throw new IOException("Refusing to decompress " + type + ": declared size exceeds cap (" + declaredSize + " > " + outputCap + ").");
        }
        decompressor.decompress(raw, new CappedChannel(out, outputCap, type), expectedFinalLength);
    }

    /**
     * EN: Reads up to {@code max} leading bytes of a channel into a fresh array (fewer only if the channel ends
     *     first). Used to sniff the archive header without consuming the whole stream. <br>
     * RU: Считывает до {@code max} ведущих байтов канала в новый массив (меньше — только если канал закончился
     *     раньше). Используется для инспекции заголовка архива без вычитывания всего потока. <br>
     * ==================================================================<br>
     * EN: @param in the channel to peek / RU: @param in канал для инспекции <br>
     * EN: @param max the maximum number of bytes to read / RU: @param max максимальное число читаемых байтов <br>
     * @return <br>
     *         {byte[]} - EN: the leading bytes actually read / RU: фактически прочитанные ведущие байты <br>
     **/
    private static byte[] peekHeader(ReadableByteChannel in, int max) throws IOException
    {
        ByteBuffer buffer = ByteBuffer.allocate(max);
        while (buffer.hasRemaining())
        {
            if (in.read(buffer) < 0)
            {
                break;
            }
        }
        byte[] header = new byte[buffer.position()];
        buffer.flip();
        buffer.get(header);
        return header;
    }

    /**
     * EN: Copies a channel to a channel with a fixed transfer buffer, writing every byte fully (never partially).
     *     Used for the {@code NONE}/pass-through paths where the raw bytes are stored unchanged. <br>
     * RU: Копирует канал в канал фиксированным буфером передачи, записывая каждый байт полностью (никогда
     *     частично). Используется для путей {@code NONE}/сквозного прохода, где сырые байты хранятся без изменений. <br>
     * ==================================================================<br>
     * EN: @param in the source channel / RU: @param in исходный канал <br>
     * EN: @param out the destination channel / RU: @param out канал назначения <br>
     **/
    private static void copyAll(ReadableByteChannel in, WritableByteChannel out) throws IOException
    {
        ByteBuffer buffer = ByteBuffer.allocate(TRANSFER_BUFFER);
        while (in.read(buffer) >= 0)
        {
            buffer.flip();
            writeFully(out, buffer);
            buffer.clear();
        }
    }

    /**
     * EN: Writes a buffer's remaining bytes to a channel completely, looping until nothing remains, so a channel
     *     that accepts fewer bytes per call than requested still receives every byte. Package-private (Java has no
     *     keyword for that visibility) so the sibling {@link LzmaDecompressor} / {@link ZipDecompressor} codecs can
     *     share it. <br>
     * RU: Полностью записывает оставшиеся байты буфера в канал, повторяя, пока не останется ничего, — так что
     *     канал, принимающий за вызов меньше байтов, чем запрошено, всё равно получает каждый байт. Пакетно-приватный
     *     (в Java нет ключевого слова для этой видимости), чтобы его могли использовать соседние кодеки
     *     {@link LzmaDecompressor} / {@link ZipDecompressor}. <br>
     * ==================================================================<br>
     * EN: @param out the destination channel / RU: @param out канал назначения <br>
     * EN: @param buffer the buffer whose remaining bytes are drained / RU: @param buffer буфер, чьи оставшиеся байты сбрасываются <br>
     **/
    static void writeFully(WritableByteChannel out, ByteBuffer buffer) throws IOException
    {
        while (buffer.hasRemaining())
        {
            out.write(buffer);
        }
    }

    /**
     * EN: A read-only channel that first serves a small buffered prefix (the peeked header) and then delegates to
     *     the wrapped channel, so the codec sees the original byte stream in full order even though the header was
     *     already consumed for inspection. Closing it closes the wrapped channel. <br>
     * RU: Канал только для чтения, который сначала отдаёт небольшой буферизованный префикс (подсмотренный
     *     заголовок), а затем делегирует обёрнутому каналу, поэтому кодек видит исходный поток байтов в полном
     *     порядке, хотя заголовок уже был прочитан для инспекции. Его закрытие закрывает обёрнутый канал. <br>
     **/
    private static final class PrefixedChannel implements ReadableByteChannel
    {
        private final byte[] _prefix;
        private final ReadableByteChannel _rest;
        private int _prefixPosition;
        private boolean _open;

        private PrefixedChannel(byte[] prefix, ReadableByteChannel rest)
        {
            _prefix = prefix;
            _rest = rest;
            _prefixPosition = 0;
            _open = true;
        }

        @Override
        public int read(ByteBuffer dst) throws IOException
        {
            if (!_open)
            {
                throw new ClosedChannelException();
            }
            if (_prefixPosition < _prefix.length)
            {
                int chunk = Math.min(dst.remaining(), _prefix.length - _prefixPosition);
                if (chunk == 0)
                {
                    return 0;
                }
                dst.put(_prefix, _prefixPosition, chunk);
                _prefixPosition += chunk;
                return chunk;
            }
            return _rest.read(dst);
        }

        @Override
        public boolean isOpen()
        {
            return _open;
        }

        @Override
        public void close() throws IOException
        {
            _open = false;
            _rest.close();
        }
    }

    /**
     * EN: A write-through channel that counts the decompressed bytes flowing to the real sink and throws once the
     *     running total exceeds the bomb-guard cap, stopping a lying header mid-stream before the disk (or heap)
     *     fills. It never closes the wrapped sink (the caller owns it). <br>
     * RU: Сквозной канал записи, который считает распакованные байты, идущие в реальный приёмник, и бросает
     *     исключение, как только суммарный объём превысит потолок защиты от «бомбы», останавливая соврамший
     *     заголовок посреди потока до заполнения диска (или кучи). Он никогда не закрывает обёрнутый приёмник
     *     (им владеет вызывающий). <br>
     **/
    private static final class CappedChannel implements WritableByteChannel
    {
        private final WritableByteChannel _out;
        private final long _cap;
        private final ArchiveType _type;
        private long _written;

        private CappedChannel(WritableByteChannel out, long cap, ArchiveType type)
        {
            _out = out;
            _cap = cap;
            _type = type;
            _written = 0L;
        }

        @Override
        public int write(ByteBuffer src) throws IOException
        {
            int written = _out.write(src);
            _written += written;
            if (_written > _cap)
            {
                throw new IOException("Decompressed " + _type + " output exceeds cap (" + _written + " > " + _cap + ").");
            }
            return written;
        }

        @Override
        public boolean isOpen()
        {
            return _out.isOpen();
        }

        @Override
        public void close()
        {
            // The wrapped sink is owned and closed by the caller; this wrapper holds no resources of its own.
        }
    }
}
