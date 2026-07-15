package org.index.patchdownloader.model.pipeline.download;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.enums.StorageStrategy;
import org.index.patchdownloader.interfaces.IDownloadRequest;
import org.index.patchdownloader.interfaces.IDummyLogger;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.model.holders.LinkInfoHolder;
import org.index.patchdownloader.model.pipeline.HttpStatusException;

/**
 * EN: Self-split download for a non-separated large file: WE split it into byte ranges (using the size
 *     already known from the generator's file list — no per-file probe) and fetch the ranges CONCURRENTLY
 *     (HTTP Range), placing each by its absolute offset. A {@link StorageStrategy#TEMPORARY} request has each
 *     range streamed straight to its pre-sized temp file (no whole-file heap buffer, so a >2 GB file flows
 *     through); a {@link StorageStrategy#MEMORY} request assembles the ranges into one {@code byte[]}. The
 *     server's Range capability is probed ONCE per run (a tiny {@code Range: bytes=0-0} request) and cached; if
 *     the server does not honour Range, this strategy falls back to a single GET.<br>
 * RU: Самостоятельная разбивка не разделённого большого файла: МЫ делим его на байтовые диапазоны (по
 *     размеру, уже известному из списка файлов генератора — без проб на каждый файл) и качаем диапазоны
 *     ПАРАЛЛЕЛЬНО (HTTP Range), размещая каждый по абсолютному смещению. У запроса {@link StorageStrategy#TEMPORARY}
 *     каждый диапазон пишется прямо в его временный файл заранее выделенного размера (без буфера всего файла в
 *     куче, поэтому проходит и файл >2 ГБ); запрос {@link StorageStrategy#MEMORY} собирает диапазоны в один
 *     {@code byte[]}. Поддержка Range сервером проверяется ОДИН раз за запуск (крошечный запрос
 *     {@code Range: bytes=0-0}) и кэшируется; если сервер не поддерживает Range, стратегия откатывается к одному
 *     GET.<br>
 **/
public class SelfSplitDownloadStrategy extends AbstractDownloadStrategy
{
    private volatile Boolean _rangeSupported;

    public SelfSplitDownloadStrategy(HttpClient httpClient)
    {
        super(httpClient);
        _rangeSupported = null;
    }

    /**
     * EN: Applies to a non-separated file whose known compressed size is at least {@code self_split_min_mb}
     *     (when self-split is enabled), unless the one-time probe already found the server does not support
     *     Range (then the single-GET strategy takes over). <br>
     * RU: Применяется к не разделённому файлу с известным сжатым размером не меньше {@code self_split_min_mb}
     *     (когда self-split включён), если только разовая проба уже не показала, что сервер не поддерживает
     *     Range (тогда берёт верх стратегия одиночного GET). <br>
     * ==================================================================<br>
     * EN: @param request the request / RU: @param request запрос <br>
     * @return <br>
     *         {true}  - EN: eligible for self-split / RU: подходит для self-split <br>
     *         {false} - EN: not eligible / RU: не подходит <br>
     **/
    @Override
    public boolean supports(IDownloadRequest request)
    {
        FileInfoHolder fileInfo = request.fileInfo();
        if (fileInfo.getAllSeparatedParts().length > 0)
        {
            return false;
        }
        if (MainConfig.SELF_SPLIT_MIN_MB <= 0)
        {
            return false;
        }
        long total = fileInfo.getDownloadDataLength();
        if (total <= 0 || total < (long) MainConfig.SELF_SPLIT_MIN_MB * ONE_MB)
        {
            return false;
        }
        // A MEMORY request reassembles the whole file into one {@code byte[]}, which cannot exceed a Java array's
        // ~2 GB bound: for a >2 GB payload {@code new byte[(int) total]} would wrap the size to a negative int and
        // throw NegativeArraySizeException, so decline it and let single-GET take over. A TEMPORARY request instead
        // streams each range straight to its pre-sized temp file at its absolute offset (no whole-file buffer), so
        // it carries a >2 GB self-split fine — the very case temp/hybrid mode exists for.
        if (total > Integer.MAX_VALUE && request.storageStrategy() == StorageStrategy.MEMORY)
        {
            return false;
        }
        Boolean cached = _rangeSupported;
        return cached == null || cached;
    }

    /**
     * EN: Range-splits a non-separated large file by its known size, fetches the ranges concurrently, then
     *     validates the assembled length. Falls back to a single GET when the one-time probe shows the server
     *     does not honour Range. The bytes land differently per storage strategy but the fetch/validate is
     *     shared: a {@link StorageStrategy#TEMPORARY} request has each range streamed straight to its pre-sized
     *     temp file at its absolute offset (no whole-file heap buffer, so a >2 GB file flows through), while a
     *     {@link StorageStrategy#MEMORY} request assembles the ranges into one {@code byte[]} handed over in a
     *     single write — matching the pre-refactor behaviour. Orchestration only — the plan is built by
     *     {@link #planRangeSplit} and the concurrent fetch by {@link #fetchRanges}. <br>
     * RU: Делит не разделённый большой файл по известному размеру на диапазоны, качает их параллельно, затем
     *     проверяет собранную длину. Откатывается к одному GET, когда разовая проба показывает, что сервер не
     *     поддерживает Range. Байты ложатся по-разному в зависимости от стратегии хранения, но загрузка/проверка
     *     общие: у запроса {@link StorageStrategy#TEMPORARY} каждый диапазон пишется прямо в его временный файл
     *     заранее выделенного размера по абсолютному смещению (без буфера всего файла в куче, поэтому проходит и
     *     файл >2 ГБ), тогда как запрос {@link StorageStrategy#MEMORY} собирает диапазоны в один {@code byte[]},
     *     передаваемый одной записью, — как и до рефакторинга. Только оркестрация — план строит
     *     {@link #planRangeSplit}, а параллельную загрузку — {@link #fetchRanges}. <br>
     * ==================================================================<br>
     * EN: @param request the request to download / RU: @param request запрос для загрузки <br>
     **/
    @Override
    protected void doDownload(IDownloadRequest request) throws Exception
    {
        FileInfoHolder fileInfo = request.fileInfo();
        LinkInfoHolder link = fileInfo.getAccessLink();
        if (!isRangeSupported(link))
        {
            singleGet(request, link);
            return;
        }

        long assembled;
        try
        {
            RangePlan plan = planRangeSplit(request, link);
            assembled = request.storageStrategy() == StorageStrategy.TEMPORARY ? streamRanges(request, plan) : assembleRanges(request, plan);
        }
        catch (RangeIgnoredException e)
        {
            _rangeSupported = false;
            IDummyLogger.log(IDummyLogger.INFO, "Server returned 200 for a ranged chunk (Range ignored); disabling self-split for the run, single GET for '" + fileInfo.getLinkPath() + "'.");
            singleGet(request, link);
            return;
        }
        link.setHttpStatus(200);
        link.setHttpLength(assembled);
        request.partComplete(0);
        request.downloadComplete();
    }

    /**
     * EN: Single-GET fallback used when the server does not honour Range: streams the whole file straight into
     *     part slot 0 (recording the real status/length, throwing on a non-200) and signals the raw payload
     *     received. Feeds the request's slot only on success, so a failed status never pollutes it. <br>
     * RU: Запасной одиночный GET, когда сервер не поддерживает Range: потоково пишет весь файл прямо в слот
     *     части 0 (фиксируя реальный статус/длину, бросая на не-200) и сигнализирует о получении сырых данных.
     *     Кормит слот запроса только при успехе, поэтому проваленный статус его никогда не портит. <br>
     * ==================================================================<br>
     * EN: @param request the request to fill / RU: @param request заполняемый запрос <br>
     * EN: @param link the file link to fetch / RU: @param link ссылка файла для загрузки <br>
     **/
    private void singleGet(IDownloadRequest request, LinkInfoHolder link) throws Exception
    {
        long epoch = request.downloadEpoch();
        streamOne(link, (offset, chunk) -> request.acceptChunk(epoch, 0, offset, chunk));
        request.partComplete(0);
        request.downloadComplete();
    }

    /**
     * EN: Builds the range-split plan for the file's known size: the chunk size (clamped so a huge configured
     *     value cannot overflow int), the ceil-count of chunks, and one ranged GET per chunk (the last clamped to
     *     the final byte). Holds no assembly buffer — where the fetched bytes land is decided per storage strategy
     *     by the caller. Logs the plan. <br>
     * RU: Строит план разбивки по известному размеру файла: размер куска (ограничен, чтобы огромное значение из
     *     конфига не переполнило int), число кусков с округлением вверх и по одному GET с диапазоном на кусок
     *     (последний обрезан до последнего байта). Буфера сборки не держит — куда лягут скачанные байты, решает
     *     вызывающий в зависимости от стратегии хранения. Логирует план. <br>
     * ==================================================================<br>
     * EN: @param request the request being downloaded (for the log) / RU: @param request скачиваемый запрос (для лога) <br>
     * EN: @param link the file link to range-fetch / RU: @param link ссылка файла для загрузки по диапазонам <br>
     * @return <br>
     *         {RangePlan} - EN: the split plan / RU: план разбивки <br>
     **/
    private RangePlan planRangeSplit(IDownloadRequest request, LinkInfoHolder link)
    {
        long total = request.fileInfo().getDownloadDataLength();
        int chunk = (int) Math.min(Integer.MAX_VALUE, (long) MainConfig.SELF_SPLIT_CHUNK_MB * ONE_MB);
        int numChunks = (int) ((total + chunk - 1) / chunk);
        List<HttpRequest> requests = new ArrayList<>(numChunks);
        for (long start = 0; start < total; start += chunk)
        {
            long end = Math.min(start + chunk - 1L, total - 1L);
            requests.add(rangeRequest(link, start, end));
        }
        IDummyLogger.log(IDummyLogger.INFO, "Range split: " + numChunks + " chunks x " + MainConfig.SELF_SPLIT_CHUNK_MB + "MB (cap " + MainConfig.PARALLEL_PARTS_PER_FILE + ", total " + total + " bytes) for '" + request.fileInfo().getLinkPath() + "'.");
        return new RangePlan(total, chunk, requests);
    }

    /**
     * EN: Temp-file path: streams every range straight into the request's pre-sized raw temp file at its absolute
     *     offset ({@code index * chunk + within}) via a positioned write, so the whole file never sits in the heap
     *     and a payload larger than the heap flows through. Concurrent ranges write disjoint regions of the temp
     *     file, which the request's positioned writes handle safely. <br>
     * RU: Путь через временный файл: пишет каждый диапазон прямо в сырой временный файл запроса заранее выделенного
     *     размера по его абсолютному смещению ({@code index * chunk + within}) позиционной записью, поэтому весь
     *     файл никогда не лежит в куче и проходит объём больше кучи. Параллельные диапазоны пишут непересекающиеся
     *     области временного файла, что позиционные записи запроса обрабатывают безопасно. <br>
     * ==================================================================<br>
     * EN: @param request the request to stream into / RU: @param request запрос для потоковой записи <br>
     * EN: @param plan the range-split plan / RU: @param plan план разбивки <br>
     * @return <br>
     *         {long} - EN: the number of bytes streamed / RU: число потоково записанных байтов <br>
     **/
    private long streamRanges(IDownloadRequest request, RangePlan plan) throws Exception
    {
        // Pass the attempt token so acceptChunk fences a straggler range of a previous, cancelled attempt from
        // writing into the temp file a retry has already deleted and re-created (checked atomically vs the reset).
        long epoch = request.downloadEpoch();
        return fetchRanges(request, plan, (absoluteOffset, buffer) -> request.acceptChunk(epoch, 0, absoluteOffset, buffer));
    }

    /**
     * EN: In-memory path: assembles every range into one {@code byte[]} by absolute offset, then hands the whole
     *     file to the request in a single write — matching the pre-refactor behaviour for a file the memory mode
     *     keeps in RAM anyway. The buffer is sized to the known total (safe: the memory branch only runs for a
     *     file whose total fits a Java array, guarded in {@link #supports}). <br>
     * RU: Путь в памяти: собирает каждый диапазон в один {@code byte[]} по абсолютному смещению, затем передаёт
     *     весь файл запросу одной записью — как и до рефакторинга, для файла, который режим памяти всё равно держит
     *     в RAM. Буфер выделяется по известному общему размеру (безопасно: ветка памяти выполняется только для
     *     файла, чей размер помещается в массив Java, что гарантирует {@link #supports}). <br>
     * ==================================================================<br>
     * EN: @param request the request to fill / RU: @param request заполняемый запрос <br>
     * EN: @param plan the range-split plan / RU: @param plan план разбивки <br>
     * @return <br>
     *         {long} - EN: the number of bytes assembled / RU: число собранных байтов <br>
     **/
    private long assembleRanges(IDownloadRequest request, RangePlan plan) throws Exception
    {
        byte[] full = new byte[(int) plan.total()];
        long received = fetchRanges(request, plan, (absoluteOffset, buffer) -> buffer.get(full, (int) absoluteOffset, buffer.remaining()));
        request.acceptChunk(request.downloadEpoch(), 0, 0L, ByteBuffer.wrap(full));
        return received;
    }

    /**
     * EN: Fetches every ranged request concurrently (bounded by {@code parallel_parts_per_file}) and hands each
     *     chunk to {@code sink} at its absolute file offset ({@code index * chunk + within}), rejecting a
     *     non-206/200 status or a chunk whose body would spill past its own slot ({@code min(chunk, remaining)}). A
     *     {@code 200} means the server ignored Range and sent the WHOLE file: it is accepted only for a single-chunk
     *     plan (where the one range already covers the whole file); for a multi-chunk plan a {@code 200} throws
     *     {@link RangeIgnoredException} so the caller disables self-split and falls back to a single GET instead of
     *     retrying a doomed ranged plan. Finally verifies the assembled length equals the declared total — a
     *     shortfall (a stale/oversized declared size the server clamped) throws so the download is retried instead
     *     of a truncated/zero-padded file being stored. The {@code sink} is called from the HTTP-client threads and
     *     must be safe for concurrent disjoint targets. <br>
     * RU: Качает каждый диапазонный запрос параллельно (не более {@code parallel_parts_per_file}) и передаёт каждый
     *     кусок в {@code sink} по его абсолютному смещению в файле ({@code index * chunk + within}), отвергая статус
     *     не-206/200 или кусок, тело которого вышло бы за пределы собственного слота ({@code min(chunk, остаток)}).
     *     Ответ {@code 200} значит, что сервер проигнорировал Range и прислал файл ЦЕЛИКОМ: он принимается только
     *     для плана из одного куска (где единственный диапазон и так покрывает весь файл); для плана из нескольких
     *     кусков {@code 200} бросает {@link RangeIgnoredException}, чтобы вызывающий отключил self-split и откатился
     *     к одному GET, а не повторял обречённый план с диапазонами. Наконец проверяет, что собранная длина равна
     *     заявленной — недобор (устаревший/завышенный размер, обрезанный сервером) бросает исключение, чтобы
     *     загрузка повторилась вместо сохранения обрезанного/дополненного нулями файла. {@code sink} вызывается из
     *     потоков HTTP-клиента и должен быть безопасен для параллельных непересекающихся целей. <br>
     * ==================================================================<br>
     * EN: @param request the request being downloaded (for messages) / RU: @param request скачиваемый запрос (для сообщений) <br>
     * EN: @param plan the range-split plan / RU: @param plan план разбивки <br>
     * EN: @param sink where each chunk is written, keyed by absolute offset / RU: @param sink куда пишется каждый кусок, по абсолютному смещению <br>
     * @return <br>
     *         {long} - EN: the number of bytes actually received / RU: число фактически полученных байтов <br>
     **/
    private long fetchRanges(IDownloadRequest request, RangePlan plan, RangeSink sink) throws Exception
    {
        int chunk = plan.chunk();
        long total = plan.total();
        boolean singleChunk = plan.requests().size() == 1;
        AtomicLong received = new AtomicLong(0);
        fetchConcurrently(plan.requests(), MainConfig.PARALLEL_PARTS_PER_FILE, new StreamHandler()
        {
            @Override
            public ChunkSink sink(int index)
            {
                long base = (long) index * chunk;
                long slot = Math.min((long) chunk, total - base);
                return (offset, buffer) ->
                {
                    int length = buffer.remaining();
                    if (offset + length > slot)
                    {
                        throw new IOException("Range chunk at offset " + (base + offset) + " returned bytes past its " + slot + "-byte slot (total " + total + ") for '" + request.fileInfo().getLinkPath() + "'.");
                    }
                    sink.write(base + offset, buffer);
                    received.addAndGet(length);
                };
            }

            @Override
            public boolean streamStatus(int index, int statusCode)
            {
                // A multi-chunk 200 means Range was ignored (whole file on one connection): do NOT stream it
                // into a single range slot; reject it (cancelled — see cancelOnReject) and let settled() abort the batch.
                return statusCode == 206 || (statusCode == 200 && singleChunk);
            }

            @Override
            public boolean cancelOnReject(int index, int statusCode)
            {
                // A rejected 200 in a multi-chunk plan is the WHOLE file (server ignored Range): CANCEL its body so
                // we never drag N x the whole file over the network just to discard it before falling back to a
                // single GET. (A single-chunk 200 is streamed, not rejected; a non-2xx error body is small — drained.)
                return statusCode == 200 && !singleChunk;
            }

            @Override
            public void settled(int index, int statusCode, long bytesStreamed) throws Exception
            {
                if (statusCode == 200 && !singleChunk)
                {
                    throw new RangeIgnoredException(request.fileInfo().getLinkPath());
                }
                if (statusCode != 206 && statusCode != 200)
                {
                    throw new HttpStatusException(statusCode);
                }
            }
        });
        if (received.get() != total)
        {
            throw new IOException("Self-split assembled " + received.get() + " of " + total + " expected bytes for '" + request.fileInfo().getLinkPath() + "' (declared size wrong or server clamped ranges); failed download, will retry.");
        }
        return received.get();
    }

    /**
     * EN: Destination for one assembled range chunk, keyed by its absolute offset in the file. The temp path
     *     writes it into the raw temp file at that offset; the memory path copies it into the assembly array. <br>
     * RU: Назначение одного собранного куска диапазона по его абсолютному смещению в файле. Путь через временный
     *     файл пишет его в сырой временный файл по этому смещению; путь в памяти копирует его в массив сборки. <br>
     **/
    @FunctionalInterface
    private interface RangeSink
    {
        void write(long absoluteOffset, ByteBuffer buffer) throws IOException;
    }

    /**
     * EN: The plan for a range-split download: total file size, chunk size, and one ranged request per chunk.
     *     Holds no assembly buffer — where the fetched bytes land is decided per storage strategy by the
     *     caller. <br>
     * RU: План для загрузки с разбивкой по диапазонам: полный размер файла, размер куска и по одному диапазонному
     *     запросу на кусок. Буфера сборки не держит — куда лягут скачанные байты, решает вызывающий в зависимости
     *     от стратегии хранения. <br>
     **/
    private record RangePlan(long total, int chunk, List<HttpRequest> requests)
    {
    }

    /**
     * EN: Thrown when a ranged chunk of a multi-chunk plan comes back {@code 200}: the server ignored the HTTP
     *     Range header and streamed the whole file, so the ranged plan can never succeed. The caller catches this,
     *     disables self-split for the run and falls back to a single GET. <br>
     * RU: Бросается, когда диапазонный кусок плана из нескольких кусков возвращается со статусом {@code 200}:
     *     сервер проигнорировал заголовок HTTP Range и отдал файл целиком, поэтому план с диапазонами уже не может
     *     завершиться успешно. Вызывающий ловит это, отключает self-split на весь запуск и откатывается к одному
     *     GET. <br>
     **/
    private static final class RangeIgnoredException extends IOException
    {
        private RangeIgnoredException(String linkPath)
        {
            super("Server ignored Range (returned 200 for a ranged chunk) for '" + linkPath + "'; falling back to single GET.");
        }
    }

    /**
     * EN: One-time, cached probe of whether the server honours Range: sends a tiny {@code bytes=0-0} request with
     *     a discarding body handler (so a server that ignores Range and answers 200 with the WHOLE file costs no
     *     memory) and treats a {@code 206} as support. Only a definitive answer (206 or a non-206 status) is
     *     cached; a transient transport error or timeout is NOT cached — it falls back to a single GET for this
     *     call and re-probes on the next large file. An interrupt cancels the in-flight probe, restores the
     *     interrupt flag and is rethrown (never cached). Thread-safe (double-checked). <br>
     * RU: Разовая кэшированная проба поддержки Range сервером: посылает крошечный запрос {@code bytes=0-0} с
     *     отбрасывающим обработчиком тела (чтобы сервер, который игнорирует Range и отвечает 200 с файлом ЦЕЛИКОМ,
     *     не стоил памяти) и считает {@code 206} поддержкой. Кэшируется только определённый ответ (206 или статус
     *     не-206); временный сбой транспорта или таймаут НЕ кэшируется — для этого вызова берётся один GET, а на
     *     следующем большом файле проба повторяется. Прерывание отменяет выполняющуюся пробу, восстанавливает флаг
     *     прерывания и перебрасывается (никогда не кэшируется). Потокобезопасно (двойная проверка). <br>
     * ==================================================================<br>
     * EN: @param sample a real file link to probe / RU: @param sample реальная ссылка файла для пробы <br>
     * @return <br>
     *         {true}  - EN: server supports Range / RU: сервер поддерживает Range <br>
     *         {false} - EN: no Range support / RU: нет поддержки Range <br>
     **/
    private boolean isRangeSupported(LinkInfoHolder sample) throws Exception
    {
        Boolean cached = _rangeSupported;
        if (cached != null)
        {
            return cached;
        }
        synchronized (this)
        {
            if (_rangeSupported != null)
            {
                return _rangeSupported;
            }
            boolean supported;
            // Headers-only probe: a server that ignores Range and answers 200 with the WHOLE file is NOT drained
            // (the old discarding() handler drained the entire body, up to the whole-exchange deadline, under this
            // monitor). The status alone decides Range support.
            CompletableFuture<HttpResponse<Void>> probe = _httpClient.sendAsync(rangeRequest(sample, 0, 0), headersOnly());
            try
            {
                supported = probe.get(EXCHANGE_TIMEOUT_SECONDS, TimeUnit.SECONDS).statusCode() == 206;
            }
            catch (InterruptedException e)
            {
                probe.cancel(true);
                Thread.currentThread().interrupt();
                throw e;
            }
            catch (Exception e)
            {
                probe.cancel(true);
                IDummyLogger.log(IDummyLogger.INFO, "Server Range probe failed transiently (" + e.getClass().getSimpleName() + "); single GET this time, will re-probe on the next large file.");
                return false;
            }
            _rangeSupported = supported;
            IDummyLogger.log(IDummyLogger.INFO, "Server Range support probed: " + (supported ? "YES (self-split enabled)" : "NO (single GET for non-split files)") + ".");
            return supported;
        }
    }

    /**
     * EN: A body handler that reads NOTHING — it cancels the body subscription on subscribe and completes, so the
     *     response future settles as soon as the HEADERS arrive. Used by the Range probe so a server that ignores
     *     Range and answers 200 with the WHOLE file is not drained (which previously blocked up to the whole-exchange
     *     deadline while holding the probe monitor). <br>
     * RU: Обработчик тела, который НЕ читает ничего — отменяет подписку на тело при подписке и завершается, поэтому
     *     future ответа завершается сразу по приходу ЗАГОЛОВКОВ. Используется пробой Range, чтобы сервер, который
     *     игнорирует Range и отвечает 200 с файлом ЦЕЛИКОМ, не вычитывался (это раньше блокировало до дедлайна всего
     *     обмена, удерживая монитор пробы). <br>
     * ==================================================================<br>
     * @return <br>
     *         {HttpResponse.BodyHandler} - EN: a headers-only body handler / RU: обработчик тела только по заголовкам <br>
     **/
    private static HttpResponse.BodyHandler<Void> headersOnly()
    {
        return responseInfo -> new HttpResponse.BodySubscriber<Void>()
        {
            private final CompletableFuture<Void> _result = new CompletableFuture<>();

            @Override
            public CompletionStage<Void> getBody()
            {
                return _result;
            }

            @Override
            public void onSubscribe(Flow.Subscription subscription)
            {
                subscription.cancel();
                _result.complete(null);
            }

            @Override
            public void onNext(java.util.List<ByteBuffer> items)
            {
            }

            @Override
            public void onError(Throwable throwable)
            {
                _result.complete(null);
            }

            @Override
            public void onComplete()
            {
                _result.complete(null);
            }
        };
    }
}
