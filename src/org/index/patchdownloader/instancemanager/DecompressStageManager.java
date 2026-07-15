package org.index.patchdownloader.instancemanager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;

import org.index.patchdownloader.enums.StorageStrategy;
import org.index.patchdownloader.model.decompress.Decompressors;
import org.index.patchdownloader.model.pipeline.enums.DownloadFailureType;
import org.index.patchdownloader.model.pipeline.enums.TaskStage;
import org.index.patchdownloader.model.pipeline.request.AbstractFileRequest;
import org.index.patchdownloader.model.pipeline.retry.NoRetryHandler;

/**
 * EN: Decompress stage (CPU-bound; does not use ForkJoinPool (FJP).ManagedBlocker because the work never
 *     blocks). Reads the request's raw source, decodes via the stateless {@link Decompressors}, and writes the
 *     result to the request's decompressed sink. It does NOT validate the output — size and hash (or torrent
 *     piece) verification runs asynchronously AFTER the file is stored, against the finished file on disk (see
 *     {@code IDownloadVerifier}), so a single verification model covers every storage mode. Failures are
 *     terminal — no retries (uses {@link NoRetryHandler}).<br>
 * RU: Стадия распаковки (нагружает CPU; не использует ForkJoinPool (FJP).ManagedBlocker, поскольку операция
 *     никогда не блокируется). Читает сырой источник запроса, декодирует через stateless {@link Decompressors}
 *     и пишет результат в приёмник распакованных данных запроса. Она НЕ проверяет вывод — проверка размера и
 *     хеша (или куска торрента) выполняется асинхронно ПОСЛЕ сохранения файла, по готовому файлу на диске (см.
 *     {@code IDownloadVerifier}), поэтому единая модель проверки покрывает любой режим хранения. Сбои
 *     терминальны — повторов нет (используется {@link NoRetryHandler}).<br>
 **/
public class DecompressStageManager extends AbstractStageManager
{
    private DecompressStageManager()
    {
        super(NoRetryHandler.INSTANCE);
    }

    @Override
    protected TaskStage stage()
    {
        return TaskStage.DECOMPRESS;
    }

    /**
     * EN: Decompresses one request, picking the path from its {@link StorageStrategy}: an in-memory request keeps
     *     the fast {@code byte[]} path (whole raw decoded to a whole array, then written once), while a temp-file
     *     request streams raw source → codec → decompressed sink so a payload larger than the heap never has to
     *     materialise. Both reach the byte planes only through
     *     {@link org.index.patchdownloader.interfaces.IDecompressRequest}, so the storage mode differs only in
     *     what the channels are backed by, never in the decompressed output. <br>
     * RU: Распаковывает один запрос, выбирая путь по его {@link StorageStrategy}: запрос в памяти сохраняет
     *     быстрый путь по {@code byte[]} (весь сырой объём декодируется в целый массив и пишется одной записью),
     *     тогда как запрос с временным файлом потоково гонит сырой источник → кодек → приёмник распакованных
     *     данных, поэтому объём больше кучи никогда не приходится материализовать. Оба достигают слоёв байтов
     *     только через {@link org.index.patchdownloader.interfaces.IDecompressRequest}, поэтому режим хранения
     *     различается лишь тем, чем подкреплены каналы, но не распакованным выводом. <br>
     * ==================================================================<br>
     * EN: @param task the request to decompress / RU: @param task запрос для распаковки <br>
     **/
    @Override
    protected void processTask(AbstractFileRequest task) throws Exception
    {
        if (task.storageStrategy() == StorageStrategy.MEMORY)
        {
            decompressInMemory(task);
        }
        else
        {
            decompressStreaming(task);
        }
        task.decompressComplete();
    }

    /**
     * EN: The all-memory fast path: drain the raw source into one array, decode it with the {@code byte[]} codec,
     *     then write it to the decompressed sink in a single call (which lets the in-memory sink adopt the array
     *     without copying). <br>
     * RU: Быстрый путь «всё в памяти»: вычитать сырой источник в один массив, декодировать его кодеком по
     *     {@code byte[]} и записать в приёмник распакованных данных одной записью (что позволяет приёмнику в
     *     памяти принять массив без копирования). <br>
     * ==================================================================<br>
     * EN: @param task the in-memory request to decompress / RU: @param task запрос в памяти для распаковки <br>
     **/
    private void decompressInMemory(AbstractFileRequest task) throws IOException
    {
        byte[] combined;
        try (ReadableByteChannel raw = task.rawSource())
        {
            combined = readAll(raw);
        }
        byte[] out = Decompressors.decompress(task.compressType(), combined, task.expectedLength());
        try (WritableByteChannel sink = task.decompressedSink())
        {
            sink.write(ByteBuffer.wrap(out));
        }
    }

    /**
     * EN: The streaming path for temp-file requests: open the raw source and decompressed sink and run the payload
     *     through the streaming codec, which never holds the whole raw or whole decompressed payload in memory; the
     *     output is verified downstream by the async after-store verifier, not here. The raw source is the ALREADY
     *     ASSEMBLED raw temp file, never the live download stream: although both codecs (LZMA / ZIP) are
     *     forward-streaming and could in principle decode as bytes arrive, that holds ONLY for a strictly in-order
     *     single stream. The general case fetches the payload as parallel, out-of-order byte ranges (HTTP 206 / CDN
     *     parts) that land at their absolute offsets, and a sequential decoder cannot consume byte {@code K+1}
     *     before byte {@code K} — so the download stage must finish and assemble the whole raw file before this
     *     stage decodes it. Fusing decode into the download is therefore NOT possible in the general case (only for
     *     an in-order single stream), which is why decompress is a separate stage over the assembled raw source. <br>
     * RU: Потоковый путь для запросов с временным файлом: открыть сырой источник и приёмник распакованных данных и
     *     прогнать объём через потоковый кодек, который никогда не держит в памяти весь сырой либо весь
     *     распакованный объём; вывод проверяется далее асинхронным верификатором, работающим после сохранения, а не
     *     здесь. Сырой источник — это УЖЕ СОБРАННЫЙ сырой временный файл, а не живой поток загрузки: хотя оба кодека
     *     (LZMA / ZIP) потоковые и в принципе могли бы декодировать по мере поступления байтов, это верно ТОЛЬКО для
     *     строго последовательного одиночного потока. В общем случае объём качается параллельными байтовыми
     *     диапазонами вне порядка (HTTP 206 / части CDN), которые ложатся по своим абсолютным смещениям, а
     *     последовательный декодер не может взять байт {@code K+1} раньше байта {@code K} — поэтому стадия загрузки
     *     обязана завершиться и собрать весь сырой файл, прежде чем эта стадия его декодирует. Поэтому слить
     *     декодирование с загрузкой в общем случае НЕЛЬЗЯ (только для последовательного одиночного потока); из-за
     *     этого распаковка — отдельная стадия над собранным сырым источником. <br>
     * ==================================================================<br>
     * EN: @param task the temp-file request to decompress / RU: @param task запрос с временным файлом для распаковки <br>
     **/
    private void decompressStreaming(AbstractFileRequest task) throws IOException
    {
        try (ReadableByteChannel raw = task.rawSource();
             WritableByteChannel sink = task.decompressedSink())
        {
            Decompressors.decompress(task.compressType(), raw, sink, task.expectedLength());
        }
    }

    /**
     * EN: Reads a raw-source channel fully into a byte array. When the channel reports its size (a
     *     {@link SeekableByteChannel}: the in-memory concatenated parts or a temp file), the array is allocated
     *     exactly once — matching the old {@code concatParts} footprint; otherwise it grows. A short read
     *     (fewer bytes than the declared size) is trimmed. <br>
     * RU: Полностью вычитывает канал сырого источника в массив байтов. Когда канал сообщает свой размер
     *     ({@link SeekableByteChannel}: склеенные части в памяти или временный файл), массив выделяется ровно
     *     один раз — как прежний {@code concatParts}; иначе растёт. Недобор (меньше байтов, чем заявленный
     *     размер) обрезается. <br>
     * ==================================================================<br>
     * EN: @param channel the raw byte source / RU: @param channel источник сырых байтов <br>
     * @return <br>
     *         {byte[]} - EN: all bytes read from the channel / RU: все прочитанные из канала байты <br>
     **/
    private static byte[] readAll(ReadableByteChannel channel) throws IOException
    {
        long declaredSize = channel instanceof SeekableByteChannel seekable ? seekable.size() : -1L;
        if (declaredSize >= 0 && declaredSize <= Integer.MAX_VALUE)
        {
            byte[] buffer = new byte[(int) declaredSize];
            ByteBuffer view = ByteBuffer.wrap(buffer);
            while (view.hasRemaining() && channel.read(view) >= 0)
            {
                // keep reading until the declared size is filled or the channel ends
            }
            int read = view.position();
            return read == buffer.length ? buffer : Arrays.copyOf(buffer, read);
        }
        byte[] buffer = new byte[0];
        ByteBuffer view = ByteBuffer.allocate(64 * 1024);
        int read;
        while ((read = channel.read(view)) >= 0)
        {
            if (read == 0)
            {
                continue;
            }
            view.flip();
            int grown = buffer.length;
            buffer = Arrays.copyOf(buffer, grown + view.remaining());
            view.get(buffer, grown, view.remaining());
            view.clear();
        }
        return buffer;
    }

    @Override
    protected DownloadFailureType classify(Throwable throwable)
    {
        return DownloadFailureType.DECOMPRESS_ERROR;
    }

    private static final DecompressStageManager INSTANCE = new DecompressStageManager();

    public static DecompressStageManager getInstance()
    {
        return INSTANCE;
    }
}
