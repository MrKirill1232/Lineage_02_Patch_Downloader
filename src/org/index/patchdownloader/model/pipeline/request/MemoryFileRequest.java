package org.index.patchdownloader.model.pipeline.request;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.enums.StorageStrategy;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.model.pipeline.enums.TaskStage;
import org.index.patchdownloader.util.FileUtils;

/**
 * EN: All-memory storage strategy: the raw payload is held as {@code byte[][]} per-part buffers and the
 *     decompressed payload as a single growable {@code byte[]}, exactly as the pre-refactor pipeline did —
 *     just behind the three stage-request interfaces. {@link #acceptChunk} appends into a part buffer;
 *     {@link #rawSource} is a channel over the concatenated parts (each freed as it is read);
 *     {@link #decompressedSink} is an in-memory buffer; {@link #store} writes the decompressed bytes to the
 *     target file. Every file is held wholly in RAM, so the payload is bounded by roughly {@code 2 GB} and
 *     the peak footprint is raw + decompressed — the {@code TempFileRequest} strategy lifts that.<br>
 * RU: Стратегия хранения «всё в памяти»: сырые данные держатся как {@code byte[][]} буферы по частям, а
 *     распакованные — как единый растущий {@code byte[]}, ровно как в конвейере до рефакторинга — только за
 *     тремя интерфейсами стадий-запросов. {@link #acceptChunk} дописывает в буфер части; {@link #rawSource} —
 *     канал над склеенными частями (каждая освобождается по мере чтения); {@link #decompressedSink} — буфер в
 *     памяти; {@link #store} пишет распакованные байты в целевой файл. Каждый файл держится в RAM целиком,
 *     поэтому объём ограничен примерно {@code 2 ГБ}, а пик потребления — сырое + распакованное; стратегия
 *     {@code TempFileRequest} снимает это ограничение.<br>
 **/
public class MemoryFileRequest extends AbstractFileRequest
{
    private static final byte[] EMPTY = new byte[0];
    private static final int MAX_ARRAY_LENGTH = Integer.MAX_VALUE - 8;

    private final Object _writeLock = new Object();
    private byte[][] _downloadedParts;
    private byte[] _decompressed;

    /**
     * EN: Allocates the per-part raw slots sized by the file's separated parts (or a single slot for a
     *     non-separated file), with no decompressed buffer yet.<br>
     * RU: Выделяет сырые слоты по частям файла (или один слот для не разделённого файла), пока без
     *     распакованного буфера.<br>
     * ==================================================================<br>
     * EN: @param fileInfo the static file metadata / RU: @param fileInfo статические метаданные файла <br>
     **/
    public MemoryFileRequest(FileInfoHolder fileInfo)
    {
        super(fileInfo);
        _downloadedParts = new byte[Math.max(fileInfo.getAllSeparatedParts().length, 1)][];
        _decompressed = null;
    }

    /**
     * EN: Always {@link StorageStrategy#MEMORY} — this request keeps the whole payload in RAM, so the decompress
     *     stage uses the fast {@code byte[]} path. <br>
     * RU: Всегда {@link StorageStrategy#MEMORY} — этот запрос держит весь объём в оперативной памяти, поэтому
     *     стадия распаковки использует быстрый путь по {@code byte[]}. <br>
     * ==================================================================<br>
     * @return <br>
     *         {StorageStrategy} - EN: always {@code MEMORY} / RU: всегда {@code MEMORY} <br>
     **/
    @Override
    public StorageStrategy storageStrategy()
    {
        return StorageStrategy.MEMORY;
    }

    /**
     * EN: Appends the buffer's remaining bytes into the given part's slot (growing it on a subsequent chunk).
     *     The absolute {@code offset} is not needed for the in-memory append — chunks of one part arrive in
     *     order — it is honoured by the temp-file strategy's positioned writes. Out-of-range indices are
     *     ignored defensively (matching the old {@code addDownloadedPart}).<br>
     * RU: Дописывает оставшиеся байты буфера в слот указанной части (наращивая его на последующем куске).
     *     Абсолютное {@code offset} не нужно для дозаписи в памяти — куски одной части приходят по порядку — и
     *     учитывается позиционной записью стратегии временного файла. Выход за границы индекса игнорируется
     *     защитно (как в старом {@code addDownloadedPart}).<br>
     * ==================================================================<br>
     * EN: @param partIndex the part this chunk belongs to / RU: @param partIndex часть, которой принадлежит кусок <br>
     * EN: @param offset the absolute byte offset within the part (unused here) / RU: @param offset абсолютное смещение байта внутри части (здесь не используется) <br>
     * EN: @param src the source buffer (its remaining bytes are consumed) / RU: @param src исходный буфер (его оставшиеся байты потребляются) <br>
     **/
    @Override
    public void acceptChunk(long epoch, int partIndex, long offset, ByteBuffer src) throws IOException
    {
        synchronized (_writeLock)
        {
            // Fence + write ATOMICALLY under the same lock resetForDownloadAttempt uses: a straggler whose captured
            // epoch no longer matches the current attempt is dropped, and the check-then-write cannot be preempted
            // by a retry's reset — stale bytes never splice into the new attempt's slots.
            if (epoch != downloadEpoch() || _downloadedParts == null || partIndex < 0 || partIndex >= _downloadedParts.length)
            {
                return;
            }
            int length = src.remaining();
            if (length == 0)
            {
                return;
            }
            byte[] existing = _downloadedParts[partIndex];
            if (existing == null)
            {
                byte[] chunk = new byte[length];
                src.get(chunk);
                _downloadedParts[partIndex] = chunk;
            }
            else
            {
                // An in-memory part is bounded by a Java array (~2 GB); guard the int growth so a payload crossing
                // that ceiling fails with a clear, actionable message instead of a wrapped-negative
                // NegativeArraySizeException reported as a cryptic retryable failure.
                if ((long) existing.length + length > MAX_ARRAY_LENGTH)
                {
                    throw new IOException("File '" + getLinkPath() + "' exceeds the ~2 GB in-memory limit for a single part; use download_mode=hybrid or all_temp to stream it through a temp file.");
                }
                byte[] grown = new byte[existing.length + length];
                System.arraycopy(existing, 0, grown, 0, existing.length);
                src.get(grown, existing.length, length);
                _downloadedParts[partIndex] = grown;
            }
        }
    }

    /**
     * EN: No-op — the part's bytes are already materialised in its slot by {@link #acceptChunk}. <br>
     * RU: Пустая операция — байты части уже собраны в её слоте методом {@link #acceptChunk}. <br>
     * ==================================================================<br>
     * EN: @param partIndex the completed part / RU: @param partIndex завершённая часть <br>
     **/
    @Override
    public void partComplete(int partIndex)
    {
        // no-op: the in-memory slot already holds the whole part
    }

    /**
     * EN: No-op — all parts are already in memory; nothing to flush. <br>
     * RU: Пустая операция — все части уже в памяти; сбрасывать нечего. <br>
     **/
    @Override
    public void downloadComplete()
    {
        // no-op: the in-memory slots already hold every part
    }

    /**
     * EN: Opens a forward-only channel over the concatenated raw parts, in part order, freeing each part as it
     *     is fully read (so the transient compressed peak drops as decompression consumes it). Reports the
     *     total raw size via {@link SeekableByteChannel#size()} so the reader can allocate exactly once.<br>
     * RU: Открывает канал только для чтения над склеенными сырыми частями по порядку, освобождая каждую часть
     *     по мере полного прочтения (чтобы временный пик сжатых данных падал по мере их потребления
     *     распаковкой). Сообщает суммарный сырой размер через {@link SeekableByteChannel#size()}, чтобы
     *     читатель выделил память ровно один раз.<br>
     * ==================================================================<br>
     * @return <br>
     *         {ReadableByteChannel} - EN: a channel over the raw parts / RU: канал над сырыми частями <br>
     **/
    @Override
    public ReadableByteChannel rawSource()
    {
        return new PartsRawChannel();
    }

    /**
     * EN: Opens a channel that accumulates the decompressed bytes into the in-memory buffer. <br>
     * RU: Открывает канал, накапливающий распакованные байты во внутренний буфер в памяти. <br>
     * ==================================================================<br>
     * @return <br>
     *         {WritableByteChannel} - EN: the in-memory decompressed sink / RU: приёмник распакованных данных в памяти <br>
     **/
    @Override
    public WritableByteChannel decompressedSink()
    {
        return new DecompressedSinkChannel();
    }

    /**
     * EN: No-op — the decompressed bytes are already accumulated in the buffer by the sink channel. <br>
     * RU: Пустая операция — распакованные байты уже накоплены в буфере каналом-приёмником. <br>
     **/
    @Override
    public void decompressComplete()
    {
        // no-op: the decompressed buffer already holds the whole output
    }

    /**
     * EN: Writes the decompressed bytes to the target file via try-with-resources, after guarding the target
     *     path against a zip-slip escape of the download folder (no swallowed IO error). Byte-for-byte the old
     *     store-stage write.<br>
     * RU: Пишет распакованные байты в целевой файл через try-with-resources, предварительно защитив целевой
     *     путь от zip-slip выхода за пределы папки загрузки (без проглоченной ошибки IO). Побайтно та же запись
     *     старой стадии сохранения.<br>
     **/
    @Override
    public void store() throws IOException
    {
        File target = target();
        FileUtils.ensureWithinDirectory(MainConfig.DOWNLOAD_PATH, target, getLinkPath());
        byte[] data = _decompressed == null ? EMPTY : _decompressed;
        try (FileOutputStream fileOutputStream = new FileOutputStream(target))
        {
            fileOutputStream.write(data);
            fileOutputStream.flush();
        }
    }

    /**
     * EN: Discards any partial per-part slots at the start of a fresh download attempt, so a retry re-downloads
     *     onto clean slots instead of appending onto the bytes a previous, failed attempt already wrote. The
     *     streaming strategies (single-GET, self-split, CDN parts) fill a slot incrementally with
     *     {@link #acceptChunk}, so without this reset a mid-stream failure followed by a retry would grow the slot
     *     past the real size (a corrupt, over-long file). Re-allocated to the file's part count, mirroring the
     *     constructor (the temp strategy does the equivalent by deleting its half-written temp file). <br>
     * RU: Отбрасывает частично заполненные слоты частей в начале новой попытки загрузки, чтобы повтор качал на
     *     чистые слоты, а не дописывал к байтам, уже записанным предыдущей неудачной попыткой. Потоковые стратегии
     *     (одиночный GET, self-split, части CDN) заполняют слот инкрементально через {@link #acceptChunk}, поэтому
     *     без этого сброса сбой в середине потока и последующий повтор нарастили бы слот сверх реального размера
     *     (битый, слишком длинный файл). Переселяется по числу частей файла, как в конструкторе (стратегия
     *     временного файла делает эквивалент, удаляя свой недописанный временный файл). <br>
     **/
    @Override
    protected void resetForDownloadAttempt()
    {
        synchronized (_writeLock)
        {
            _downloadedParts = new byte[Math.max(_fileInfo.getAllSeparatedParts().length, 1)][];
            _decompressed = null;
        }
    }

    @Override
    protected void freeAfterStage(TaskStage completedStage)
    {
        if (completedStage == TaskStage.DECOMPRESS)
        {
            _downloadedParts = null;
        }
        else if (completedStage == TaskStage.STORE)
        {
            _decompressed = null;
        }
    }

    @Override
    protected void freeAllResources()
    {
        _downloadedParts = null;
        _decompressed = null;
    }

    /**
     * EN: Forward-only, read-only channel over the concatenated raw parts. Its {@link #size()} is the sum of
     *     the part lengths captured at construction, so a single exact allocation can drain it; each part is
     *     dropped as soon as it is fully consumed. Positioning / writing are unsupported (not needed by the
     *     decompress reader).<br>
     * RU: Канал только для последовательного чтения над склеенными сырыми частями. Его {@link #size()} — сумма
     *     длин частей, зафиксированная при создании, поэтому его можно вычитать одной точной аллокацией; каждая
     *     часть сбрасывается сразу после полного потребления. Позиционирование / запись не поддерживаются (не
     *     нужны читателю распаковки).<br>
     **/
    private final class PartsRawChannel implements SeekableByteChannel
    {
        private final long _size;
        private long _position;
        private int _index;
        private int _within;
        private boolean _open;

        private PartsRawChannel()
        {
            long total = 0;
            if (_downloadedParts != null)
            {
                for (byte[] part : _downloadedParts)
                {
                    if (part != null)
                    {
                        total += part.length;
                    }
                }
            }
            _size = total;
            _position = 0L;
            _index = 0;
            _within = 0;
            _open = true;
        }

        @Override
        public int read(ByteBuffer dst) throws IOException
        {
            if (!_open)
            {
                throw new ClosedChannelException();
            }
            if (_downloadedParts == null)
            {
                return -1;
            }
            int written = 0;
            while (dst.hasRemaining() && _index < _downloadedParts.length)
            {
                byte[] part = _downloadedParts[_index];
                if (part == null || _within >= part.length)
                {
                    if (part != null)
                    {
                        // Drop the reference as it is fully consumed to reduce the transient compressed peak.
                        _downloadedParts[_index] = null;
                    }
                    _index++;
                    _within = 0;
                    continue;
                }
                int chunk = Math.min(dst.remaining(), part.length - _within);
                dst.put(part, _within, chunk);
                _within += chunk;
                _position += chunk;
                written += chunk;
            }
            if (written > 0)
            {
                return written;
            }
            return _index >= _downloadedParts.length ? -1 : 0;
        }

        @Override
        public long size()
        {
            return _size;
        }

        @Override
        public long position()
        {
            return _position;
        }

        @Override
        public SeekableByteChannel position(long newPosition)
        {
            throw new UnsupportedOperationException("forward-only raw source");
        }

        @Override
        public SeekableByteChannel truncate(long newSize)
        {
            throw new UnsupportedOperationException("read-only raw source");
        }

        @Override
        public int write(ByteBuffer src)
        {
            throw new NonWritableChannelException();
        }

        @Override
        public boolean isOpen()
        {
            return _open;
        }

        @Override
        public void close()
        {
            _open = false;
        }
    }

    /**
     * EN: Channel that accumulates decompressed bytes into {@code _decompressed}. When the very first write
     *     fully wraps a backing array (the single-shot in-memory decompress), the array is adopted directly —
     *     a zero-copy path that keeps the peak at one decompressed copy, matching the old
     *     {@code setDecompressed}; any other pattern appends by growing.<br>
     * RU: Канал, накапливающий распакованные байты в {@code _decompressed}. Когда самая первая запись целиком
     *     оборачивает массив-подложку (единовременная распаковка в памяти), массив принимается напрямую —
     *     путь без копирования, удерживающий пик в одной копии распакованных данных, как в старом
     *     {@code setDecompressed}; любой иной случай дописывает наращиванием.<br>
     **/
    private final class DecompressedSinkChannel implements WritableByteChannel
    {
        private boolean _open = true;

        @Override
        public int write(ByteBuffer src) throws IOException
        {
            if (!_open)
            {
                throw new ClosedChannelException();
            }
            int length = src.remaining();
            if (length == 0)
            {
                return 0;
            }
            if (_decompressed == null && src.hasArray() && src.arrayOffset() == 0 && src.position() == 0 && length == src.array().length)
            {
                _decompressed = src.array();
                src.position(src.limit());
                return length;
            }
            if (_decompressed == null)
            {
                byte[] chunk = new byte[length];
                src.get(chunk);
                _decompressed = chunk;
            }
            else
            {
                byte[] grown = new byte[_decompressed.length + length];
                System.arraycopy(_decompressed, 0, grown, 0, _decompressed.length);
                src.get(grown, _decompressed.length, length);
                _decompressed = grown;
            }
            return length;
        }

        @Override
        public boolean isOpen()
        {
            return _open;
        }

        @Override
        public void close()
        {
            _open = false;
        }
    }
}
