package org.index.patchdownloader.model.pipeline.request;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.enums.StorageStrategy;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.util.FileUtils;

/**
 * EN: Temp-file storage strategy: the raw (still-compressed) payload never lives in memory as a whole — it is
 *     streamed onto a raw temp file under {@code temp_file_dir}, decompressed straight into the final
 *     {@code .part} file, then the {@code .part} is fsynced and renamed onto the target. This lifts the memory
 *     path's roughly {@code 2 GB} ceiling: only small streaming buffers are held, so a payload larger than the
 *     heap flows through.
 *     <ul>
 *       <li><b>download</b> — the raw temp file is opened via a {@link RandomAccessFile} and PRE-SIZED to the
 *           known raw length ({@code setLength}); every {@link #acceptChunk} is a POSITIONED write at the
 *           chunk's absolute offset, so Content-Delivery-Network (CDN) parts / HTTP 206 ranges arriving out of
 *           order or concurrently land at their place with no concatenation. A part's absolute base offset is
 *           the running sum of the preceding parts' download lengths, matching the order the memory strategy
 *           concatenates in.</li>
 *       <li><b>decompress</b> — {@link #rawSource()} is a read channel over the raw temp file (a
 *           {@link FileChannel}, so its size is known) and {@link #decompressedSink()} is a write channel to
 *           {@code DOWNLOAD_PATH/linkPath.part}; the streaming codec copies raw → {@code .part} without ever
 *           buffering the whole file.</li>
 *       <li><b>store</b> — {@link #store()} fsyncs the {@code .part}, atomically renames it onto the target and
 *           deletes the raw temp file.</li>
 *       <li><b>cleanup</b> — a terminal failure (via {@link #freeAllResources()}) deletes the raw temp and the
 *           {@code .part}; a download retry (via {@link #resetForDownloadAttempt()}) discards the half-written
 *           raw temp so the fresh attempt starts clean.</li>
 *     </ul><br>
 * RU: Стратегия хранения через временный файл: сырые (ещё сжатые) данные никогда не лежат в памяти целиком — они
 *     потоково пишутся в сырой временный файл в каталоге {@code temp_file_dir}, распаковываются прямо в итоговый
 *     файл {@code .part}, после чего {@code .part} сбрасывается на диск (fsync) и переименовывается в цель. Это
 *     снимает примерно {@code 2 ГБ} потолок пути в памяти: удерживаются лишь небольшие потоковые буферы, поэтому
 *     сквозь стратегию проходит объём больше кучи.
 *     <ul>
 *       <li><b>загрузка</b> — сырой временный файл открывается через {@link RandomAccessFile} и заранее
 *           выделяется по известной сырой длине ({@code setLength}); каждый {@link #acceptChunk} — ПОЗИЦИОННАЯ
 *           запись по абсолютному смещению куска, поэтому части сети доставки контента (CDN) / диапазоны HTTP
 *           206, пришедшие не по порядку или параллельно, ложатся на своё место без склейки. Абсолютное базовое
 *           смещение части — накопленная сумма длин загрузки предыдущих частей, в том же порядке, в котором
 *           склеивает стратегия памяти.</li>
 *       <li><b>распаковка</b> — {@link #rawSource()} — канал чтения над сырым временным файлом (это
 *           {@link FileChannel}, поэтому его размер известен), а {@link #decompressedSink()} — канал записи в
 *           {@code DOWNLOAD_PATH/linkPath.part}; потоковый кодек копирует сырое → {@code .part}, ни разу не
 *           буферизуя весь файл.</li>
 *       <li><b>сохранение</b> — {@link #store()} сбрасывает {@code .part} на диск (fsync), атомарно
 *           переименовывает его в цель и удаляет сырой временный файл.</li>
 *       <li><b>очистка</b> — терминальный сбой (через {@link #freeAllResources()}) удаляет сырой временный файл
 *           и {@code .part}; повтор загрузки (через {@link #resetForDownloadAttempt()}) отбрасывает недописанный
 *           сырой временный файл, чтобы новая попытка началась с чистого листа.</li>
 *     </ul><br>
 **/
public class TempFileRequest extends AbstractFileRequest
{
    private final File _tempFile;
    private final File _partFile;
    private final long[] _partBaseOffsets;
    private final long _rawTotal;

    private final Object _rawLock;
    private volatile boolean _rawOpened;
    private volatile RandomAccessFile _rawAccess;
    private volatile FileChannel _rawChannel;
    private long _writtenBytes;

    /**
     * EN: Builds the request from the file metadata: derives each part's absolute base offset within the raw
     *     temp file (the running sum of the preceding parts' download lengths, or a single {@code 0} base for a
     *     non-separated file) and the total raw length used to pre-size the temp file. A unique raw temp path is
     *     reserved under {@code temp_file_dir}; the file itself is created lazily on the first write.<br>
     * RU: Строит запрос из метаданных файла: вычисляет абсолютное базовое смещение каждой части внутри сырого
     *     временного файла (накопленная сумма длин загрузки предыдущих частей либо единственная база {@code 0}
     *     для не разделённого файла) и суммарную сырую длину для предварительного выделения временного файла.
     *     Уникальный путь сырого временного файла резервируется в {@code temp_file_dir}; сам файл создаётся
     *     лениво при первой записи.<br>
     * ==================================================================<br>
     * EN: @param fileInfo the static file metadata / RU: @param fileInfo статические метаданные файла <br>
     **/
    public TempFileRequest(FileInfoHolder fileInfo)
    {
        super(fileInfo);
        _rawLock = new Object();
        _rawOpened = false;
        _rawAccess = null;
        _rawChannel = null;

        FileInfoHolder[] parts = fileInfo.getAllSeparatedParts();
        if (parts.length == 0)
        {
            // Non-separated file: one raw stream starting at offset 0. Its raw length is the compressed
            // download length when the source declared it, or -1 (unknown → the temp file is not pre-sized,
            // positioned writes simply extend it).
            _partBaseOffsets = new long[] { 0L };
            _rawTotal = fileInfo.getDownloadDataLength();
        }
        else
        {
            _partBaseOffsets = new long[parts.length];
            long running = 0L;
            boolean allKnown = true;
            for (int index = 0; index < parts.length; index++)
            {
                _partBaseOffsets[index] = running;
                FileInfoHolder part = parts[index];
                long length = part == null ? -1 : part.getDownloadDataLength();
                if (length > 0)
                {
                    running += length;
                }
                else
                {
                    allKnown = false;
                }
            }
            _rawTotal = allKnown ? running : -1L;
        }

        // Both the raw temp and the decompressed .part staging file live in temp_file_dir (same volume as the
        // output), NOT in the output tree: a target-folder ".part" cannot collide with a real list entry named
        // "<name>.part", and the startup sweep never touches the output tree. Store atomically renames .part ->
        // target (same-volume cross-dir move).
        String base = FileUtils.RAW_TEMP_PREFIX + UUID.randomUUID();
        _tempFile = new File(MainConfig.TEMP_FILE_DIR, base + FileUtils.RAW_TEMP_SUFFIX);
        _partFile = new File(MainConfig.TEMP_FILE_DIR, base + FileUtils.PART_FILE_SUFFIX);
        _writtenBytes = 0L;
    }

    /**
     * EN: Always {@link StorageStrategy#TEMPORARY} — this request streams through a temp file, so the decompress
     *     stage takes the streaming channel path and the budget charges only streaming buffers, not the file
     *     size. <br>
     * RU: Всегда {@link StorageStrategy#TEMPORARY} — этот запрос идёт потоком через временный файл, поэтому
     *     стадия распаковки берёт канальный потоковый путь, а бюджет учитывает лишь потоковые буферы, а не
     *     размер файла. <br>
     * ==================================================================<br>
     * @return <br>
     *         {StorageStrategy} - EN: always {@code TEMPORARY} / RU: всегда {@code TEMPORARY} <br>
     **/
    @Override
    public StorageStrategy storageStrategy()
    {
        return StorageStrategy.TEMPORARY;
    }

    /**
     * EN: Writes one chunk of a part's raw bytes to the raw temp file at its absolute position
     *     ({@code partBase + offset}), consuming the buffer's remaining bytes. The temp file is opened and
     *     pre-sized on the first non-empty chunk. Because this is a positioned write, chunks of different parts
     *     may arrive concurrently (CDN parts) or out of order (206 ranges) and still land correctly.
     *     Out-of-range indices are ignored defensively (matching the memory strategy).<br>
     * RU: Пишет один кусок сырых байтов части в сырой временный файл по его абсолютной позиции
     *     ({@code partBase + offset}), потребляя оставшиеся байты буфера. Временный файл открывается и
     *     предварительно выделяется на первом непустом куске. Так как это позиционная запись, куски разных
     *     частей могут приходить параллельно (части CDN) или не по порядку (диапазоны 206) и всё равно ложатся
     *     корректно. Выход за границы индекса игнорируется защитно (как в стратегии памяти).<br>
     * ==================================================================<br>
     * EN: @param partIndex the part this chunk belongs to / RU: @param partIndex часть, которой принадлежит кусок <br>
     * EN: @param offset the absolute byte offset within the part / RU: @param offset абсолютное смещение байта внутри части <br>
     * EN: @param src the source buffer (its remaining bytes are consumed) / RU: @param src исходный буфер (его оставшиеся байты потребляются) <br>
     **/
    @Override
    public void acceptChunk(long epoch, int partIndex, long offset, ByteBuffer src) throws IOException
    {
        if (partIndex < 0 || partIndex >= _partBaseOffsets.length)
        {
            return;
        }
        if (src.remaining() == 0)
        {
            return;
        }
        synchronized (_rawLock)
        {
            // Fence + write ATOMICALLY under _rawLock (the lock resetForDownloadAttempt / downloadComplete use): a
            // straggler of a superseded attempt cannot reopen and write into the temp file a retry has deleted and
            // re-created. The positioned writes serialise here, but they are network-bound, so the cost is
            // negligible against the correctness the fence buys.
            if (epoch != downloadEpoch())
            {
                return;
            }
            int length = src.remaining();
            ensureRawWriteChannel();
            long position = _partBaseOffsets[partIndex] + offset;
            writeFullyAt(_rawChannel, src, position);
            _writtenBytes += length;
        }
    }

    /**
     * EN: No-op — a part's bytes are already durable in the raw temp file at their offset by
     *     {@link #acceptChunk}. <br>
     * RU: Пустая операция — байты части уже лежат в сыром временном файле по своему смещению благодаря
     *     {@link #acceptChunk}. <br>
     * ==================================================================<br>
     * EN: @param partIndex the completed part / RU: @param partIndex завершённая часть <br>
     **/
    @Override
    public void partComplete(int partIndex)
    {
        // no-op: positioned writes already placed the whole part in the raw temp file
    }

    /**
     * EN: Finalises the raw temp file once every part has been received: ensures the file exists (so a 0-byte
     *     payload still yields an empty raw temp for the reader), flushes it and closes the write channel, so the
     *     decompress stage can open a fresh read channel over a consistent file. <br>
     * RU: Финализирует сырой временный файл после получения всех частей: гарантирует существование файла (чтобы
     *     нулевой объём тоже дал пустой сырой временный файл для читателя), сбрасывает его на диск и закрывает
     *     канал записи, чтобы стадия распаковки открыла свежий канал чтения над согласованным файлом. <br>
     **/
    @Override
    public void downloadComplete() throws IOException
    {
        synchronized (_rawLock)
        {
            // Reconcile actually-received bytes against the metadata-declared raw total the temp file was pre-sized
            // to. A shortfall would leave a zero-padded tail (or, in a multi-part file, a gap/overwrite) — a shifted
            // or truncated file that temp mode would produce but memory mode (sequential append) would not. Fail
            // (retryable) instead of storing it, keeping temp mode byte-consistent with memory mode.
            if (_rawTotal > 0 && _writtenBytes != _rawTotal)
            {
                throw new IOException("Temp file for '" + getLinkPath() + "' received " + _writtenBytes + " of " + _rawTotal + " declared raw bytes (stale file list or the server clamped a range); failing to avoid a shifted/zero-padded file, will retry.");
            }
            ensureRawWriteChannel();
            FileChannel channel = _rawChannel;
            if (channel != null && channel.isOpen())
            {
                channel.force(false);
            }
            closeRawQuietly();
        }
    }

    /**
     * EN: Opens a read-only {@link FileChannel} over the raw temp file. Being a channel with a known size, the
     *     streaming decompressor can detect the raw length up front. The caller fully consumes and closes it. <br>
     * RU: Открывает {@link FileChannel} только для чтения над сырым временным файлом. Поскольку у канала известен
     *     размер, потоковый распаковщик заранее определяет сырую длину. Вызывающий полностью вычитывает и
     *     закрывает его. <br>
     * ==================================================================<br>
     * @return <br>
     *         {ReadableByteChannel} - EN: a read channel over the raw temp file / RU: канал чтения над сырым временным файлом <br>
     **/
    @Override
    public ReadableByteChannel rawSource() throws IOException
    {
        return FileChannel.open(_tempFile.toPath(), StandardOpenOption.READ);
    }

    /**
     * EN: Opens a write {@link FileChannel} to the final {@code DOWNLOAD_PATH/linkPath.part} file (created,
     *     truncated), after guarding the target path against a zip-slip escape of the download folder and
     *     ensuring the destination folder exists. The decompress stage streams the decompressed bytes into it and
     *     closes it. <br>
     * RU: Открывает {@link FileChannel} записи в итоговый файл {@code DOWNLOAD_PATH/linkPath.part} (создаётся,
     *     усекается), предварительно защитив целевой путь от zip-slip выхода за пределы папки загрузки и
     *     гарантировав существование целевой папки. Стадия распаковки потоково пишет в него распакованные байты и
     *     закрывает его. <br>
     * ==================================================================<br>
     * @return <br>
     *         {WritableByteChannel} - EN: a write channel to the .part file / RU: канал записи в файл .part <br>
     **/
    @Override
    public WritableByteChannel decompressedSink() throws IOException
    {
        File target = target();
        FileUtils.ensureWithinDirectory(MainConfig.DOWNLOAD_PATH, target, getLinkPath());
        File part = partFile();
        File parent = part.getParentFile();
        if (parent != null && !parent.exists())
        {
            parent.mkdirs();
        }
        return FileChannel.open(part.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * EN: No-op — the decompressed bytes are already on the {@code .part} file, written by the decompress stage
     *     through {@link #decompressedSink()}. <br>
     * RU: Пустая операция — распакованные байты уже на файле {@code .part}, записанные стадией распаковки через
     *     {@link #decompressedSink()}. <br>
     **/
    @Override
    public void decompressComplete()
    {
        // no-op: the .part file already holds the whole decompressed output
    }

    /**
     * EN: Finalises the file to disk: guards the target against a zip-slip escape, fsyncs the {@code .part} file
     *     so the decompressed bytes are durable, atomically renames {@code .part → target} (falling back to a
     *     plain replace when the filesystem cannot do an atomic move — the temp dir lives on the same volume as
     *     the output, so this is a rename, never a cross-volume copy), then deletes the raw temp file. <br>
     * RU: Финализирует файл на диск: защищает цель от zip-slip выхода, сбрасывает файл {@code .part} на диск
     *     (fsync), чтобы распакованные байты стали устойчивыми, атомарно переименовывает {@code .part → цель}
     *     (откатываясь к обычной замене, если файловая система не умеет атомарный перенос — временный каталог
     *     лежит на том же томе, что и вывод, поэтому это переименование, а не копирование между томами), затем
     *     удаляет сырой временный файл. <br>
     **/
    @Override
    public void store() throws IOException
    {
        File target = target();
        FileUtils.ensureWithinDirectory(MainConfig.DOWNLOAD_PATH, target, getLinkPath());
        // Ensure the TARGET's folder exists before the rename: the .part now stages in temp_file_dir (not the
        // target's folder), so opening the .part no longer creates the output subfolder as a side effect.
        File targetParent = target.getParentFile();
        if (targetParent != null && !targetParent.exists())
        {
            targetParent.mkdirs();
        }
        File part = partFile();
        fsync(part);
        Path partPath = part.toPath();
        Path targetPath = target.toPath();
        try
        {
            Files.move(partPath, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (AtomicMoveNotSupportedException e)
        {
            Files.move(partPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
        deleteQuietly(_tempFile);
    }

    /**
     * EN: Discards any half-written raw temp file at the start of a fresh download attempt (Q8: delete +
     *     re-create), so a retry never appends onto or reuses a partially-filled temp file. <br>
     * RU: Отбрасывает любой недописанный сырой временный файл в начале новой попытки загрузки (Q8: удалить +
     *     создать заново), чтобы повтор никогда не дописывал в частично заполненный временный файл и не
     *     переиспользовал его. <br>
     **/
    @Override
    protected void resetForDownloadAttempt()
    {
        synchronized (_rawLock)
        {
            closeRawQuietly();
            deleteQuietly(_tempFile);
            deleteQuietly(_partFile);
            _writtenBytes = 0L;
        }
    }

    /**
     * EN: Terminal-failure cleanup: closes the raw write channel (if still open) and deletes both the raw temp
     *     file and the {@code .part} file so a failed file leaves no orphan artifacts behind. <br>
     * RU: Очистка при терминальном сбое: закрывает канал записи сырого файла (если ещё открыт) и удаляет и сырой
     *     временный файл, и файл {@code .part}, чтобы проваленный файл не оставлял сиротских артефактов. <br>
     **/
    @Override
    protected void freeAllResources()
    {
        synchronized (_rawLock)
        {
            closeRawQuietly();
        }
        deleteQuietly(_tempFile);
        deleteQuietly(partFile());
    }

    /**
     * EN: The staging {@code .part} file the decompressed bytes are written to before the atomic rename onto the
     *     target — a {@code dl-<uuid>.part} sibling of the raw temp file in {@code temp_file_dir} (same volume as
     *     the output, so the rename is not a cross-volume copy), NOT in the output tree. <br>
     * RU: Промежуточный файл {@code .part}, в который пишутся распакованные байты до атомарного переименования в
     *     цель, — сосед сырого временного файла {@code dl-<uuid>.part} в {@code temp_file_dir} (тот же том, что и
     *     вывод, поэтому переименование не является копированием между томами), НЕ в дереве вывода. <br>
     * ==================================================================<br>
     * @return <br>
     *         {File} - EN: the .part staging file / RU: промежуточный файл .part <br>
     **/
    private File partFile()
    {
        return _partFile;
    }

    /**
     * EN: Lazily opens (once) the raw temp file for writing: creates {@code temp_file_dir} if missing, opens a
     *     {@link RandomAccessFile} and pre-sizes it to the known raw length ({@code setLength}) so the positioned
     *     part writes never have to extend it mid-download. Double-checked so concurrent CDN part writers open it
     *     exactly once. <br>
     * RU: Лениво (однократно) открывает сырой временный файл на запись: создаёт {@code temp_file_dir} при
     *     отсутствии, открывает {@link RandomAccessFile} и предварительно выделяет его до известной сырой длины
     *     ({@code setLength}), чтобы позиционные записи частей не расширяли файл во время загрузки. Двойная
     *     проверка гарантирует, что параллельные писатели частей CDN открывают его ровно один раз. <br>
     **/
    private void ensureRawWriteChannel() throws IOException
    {
        if (_rawOpened)
        {
            return;
        }
        synchronized (_rawLock)
        {
            if (_rawOpened)
            {
                return;
            }
            File dir = MainConfig.TEMP_FILE_DIR;
            if (dir != null && !dir.exists())
            {
                dir.mkdirs();
            }
            RandomAccessFile access = new RandomAccessFile(_tempFile, "rw");
            try
            {
                if (_rawTotal > 0)
                {
                    access.setLength(_rawTotal);
                }
            }
            catch (IOException e)
            {
                access.close();
                throw e;
            }
            _rawAccess = access;
            _rawChannel = access.getChannel();
            _rawOpened = true;
        }
    }

    /**
     * EN: Closes the raw write channel and its backing {@link RandomAccessFile} (both best-effort) and clears the
     *     opened flag so a subsequent attempt re-creates them. Held under {@code _rawLock} by callers. <br>
     * RU: Закрывает канал записи сырого файла и подложку {@link RandomAccessFile} (по возможности) и снимает флаг
     *     открытия, чтобы последующая попытка создала их заново. Вызывается под {@code _rawLock}. <br>
     **/
    private void closeRawQuietly()
    {
        RandomAccessFile access = _rawAccess;
        FileChannel channel = _rawChannel;
        _rawAccess = null;
        _rawChannel = null;
        _rawOpened = false;
        if (channel != null)
        {
            try
            {
                channel.close();
            }
            catch (IOException ignored)
            {
                // best-effort: closing the raw write channel must never mask the real outcome
            }
        }
        if (access != null)
        {
            try
            {
                access.close();
            }
            catch (IOException ignored)
            {
                // best-effort: the channel close above already released the file descriptor in practice
            }
        }
    }

    /**
     * EN: Writes a buffer's remaining bytes to the channel at the given absolute file position, looping until
     *     nothing remains (a positioned write may accept fewer bytes than requested per call). Positioned writes
     *     do not touch the channel's own position, so disjoint parts may be written concurrently. <br>
     * RU: Пишет оставшиеся байты буфера в канал по указанной абсолютной позиции файла, повторяя, пока не
     *     останется ничего (позиционная запись за вызов может принять меньше байтов, чем запрошено). Позиционная
     *     запись не трогает собственную позицию канала, поэтому непересекающиеся части можно писать параллельно. <br>
     * ==================================================================<br>
     * EN: @param channel the raw temp file channel / RU: @param channel канал сырого временного файла <br>
     * EN: @param src the buffer whose remaining bytes are written / RU: @param src буфер, чьи оставшиеся байты пишутся <br>
     * EN: @param position the absolute file offset to write at / RU: @param position абсолютное смещение в файле для записи <br>
     **/
    private static void writeFullyAt(FileChannel channel, ByteBuffer src, long position) throws IOException
    {
        long cursor = position;
        while (src.hasRemaining())
        {
            int written = channel.write(src, cursor);
            if (written <= 0)
            {
                break;
            }
            cursor += written;
        }
    }

    /**
     * EN: Forces the file's content to the storage device (fsync) by opening it and calling
     *     {@link FileChannel#force(boolean)}; a missing file is a no-op. <br>
     * RU: Принудительно сбрасывает содержимое файла на устройство хранения (fsync), открывая его и вызывая
     *     {@link FileChannel#force(boolean)}; отсутствующий файл — пустая операция. <br>
     * ==================================================================<br>
     * EN: @param file the file to fsync / RU: @param file файл для fsync <br>
     **/
    private static void fsync(File file) throws IOException
    {
        if (!file.exists())
        {
            return;
        }
        try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.WRITE))
        {
            channel.force(true);
        }
    }

    /**
     * EN: Deletes a file if it exists, swallowing any IO error (cleanup must never throw over the real
     *     outcome). <br>
     * RU: Удаляет файл, если он существует, поглощая любую ошибку ввода-вывода (очистка не должна бросать поверх
     *     реального результата). <br>
     * ==================================================================<br>
     * EN: @param file the file to delete / RU: @param file файл для удаления <br>
     **/
    private static void deleteQuietly(File file)
    {
        if (file == null)
        {
            return;
        }
        try
        {
            Files.deleteIfExists(file.toPath());
        }
        catch (IOException ignored)
        {
            // best-effort cleanup: a leftover artifact is swept on the next start, never fatal here
        }
    }
}
