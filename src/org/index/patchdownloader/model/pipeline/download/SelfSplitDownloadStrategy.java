package org.index.patchdownloader.model.pipeline.download;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.interfaces.IDummyLogger;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.model.holders.LinkInfoHolder;
import org.index.patchdownloader.model.pipeline.FileDownloadTask;
import org.index.patchdownloader.model.pipeline.HttpStatusException;

/**
 * EN: Self-split download for a non-separated large file: WE split it into byte ranges (using the size
 *     already known from the generator's file list — no per-file probe) and fetch the ranges CONCURRENTLY
 *     (HTTP Range), assembling them by offset. The server's Range capability is probed ONCE per run (a tiny
 *     {@code Range: bytes=0-0} request) and cached; if the server does not honour Range, this strategy
 *     falls back to a single GET.<br>
 * RU: Самостоятельная разбивка не разделённого большого файла: МЫ делим его на байтовые диапазоны (по
 *     размеру, уже известному из списка файлов генератора — без проб на каждый файл) и качаем диапазоны
 *     ПАРАЛЛЕЛЬНО (HTTP Range), собирая по смещению. Поддержка Range сервером проверяется ОДИН раз за запуск
 *     (крошечный запрос {@code Range: bytes=0-0}) и кэшируется; если сервер не поддерживает Range, стратегия
 *     откатывается к одному GET.<br>
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
     * EN: @param task the task / RU: @param task задача <br>
     * @return <br>
     *         {true}  - EN: eligible for self-split / RU: подходит для self-split <br>
     *         {false} - EN: not eligible / RU: не подходит <br>
     **/
    @Override
    public boolean supports(FileDownloadTask task)
    {
        FileInfoHolder fileInfo = task.getFileInfo();
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
        Boolean cached = _rangeSupported;
        return cached == null || cached;
    }

    /**
     * EN: Range-splits a non-separated large file by its known size, fetches the ranges concurrently, then
     *     reassembles and validates the result. Falls back to a single GET when the one-time probe shows the
     *     server does not honour Range. Orchestration only — the plan is built by {@link #planRangeSplit} and
     *     the fetch/assemble/validate by {@link #fetchAndAssemble}. <br>
     * RU: Делит не разделённый большой файл по известному размеру на диапазоны, качает их параллельно, затем
     *     собирает и валидирует результат. Откатывается к одному GET, когда разовая проба показывает, что
     *     сервер не поддерживает Range. Только оркестрация — план строит {@link #planRangeSplit}, а
     *     загрузку/сборку/проверку — {@link #fetchAndAssemble}. <br>
     * ==================================================================<br>
     * EN: @param task the task to download / RU: @param task задача для загрузки <br>
     **/
    @Override
    protected void doDownload(FileDownloadTask task) throws Exception
    {
        FileInfoHolder fileInfo = task.getFileInfo();
        LinkInfoHolder link = fileInfo.getAccessLink();
        if (!isRangeSupported(link))
        {
            task.addDownloadedPart(0, fetchOne(link));
            return;
        }

        byte[] full;
        try
        {
            full = fetchAndAssemble(task, planRangeSplit(task, link));
        }
        catch (RangeIgnoredException e)
        {
            _rangeSupported = false;
            IDummyLogger.log(IDummyLogger.INFO, "Server returned 200 for a ranged chunk (Range ignored); disabling self-split for the run, single GET for '" + task.getLinkPath() + "'.");
            task.addDownloadedPart(0, fetchOne(link));
            return;
        }
        link.setHttpStatus(200);
        link.setHttpLength(full.length);
        task.addDownloadedPart(0, full);
    }

    /**
     * EN: Builds the range-split plan for the file's known size: the chunk size (clamped so a huge configured
     *     value cannot overflow int), the ceil-count of chunks, the preallocated assembly buffer, and one
     *     ranged GET per chunk (the last clamped to the final byte). Logs the plan. <br>
     * RU: Строит план разбивки по известному размеру файла: размер куска (ограничен, чтобы огромное значение из
     *     конфига не переполнило int), число кусков с округлением вверх, заранее выделенный буфер сборки и по
     *     одному GET с диапазоном на кусок (последний обрезан до последнего байта). Логирует план. <br>
     * ==================================================================<br>
     * EN: @param task the task being downloaded (for the log) / RU: @param task скачиваемая задача (для лога) <br>
     * EN: @param link the file link to range-fetch / RU: @param link ссылка файла для загрузки по диапазонам <br>
     * @return <br>
     *         {RangePlan} - EN: the split plan / RU: план разбивки <br>
     **/
    private RangePlan planRangeSplit(FileDownloadTask task, LinkInfoHolder link)
    {
        long total = task.getFileInfo().getDownloadDataLength();
        int chunk = (int) Math.min(Integer.MAX_VALUE, (long) MainConfig.SELF_SPLIT_CHUNK_MB * ONE_MB);
        int numChunks = (int) ((total + chunk - 1) / chunk);
        byte[] full = new byte[(int) total];
        List<HttpRequest> requests = new ArrayList<>(numChunks);
        for (long start = 0; start < total; start += chunk)
        {
            long end = Math.min(start + chunk - 1L, total - 1L);
            requests.add(rangeRequest(link, start, end));
        }
        IDummyLogger.log(IDummyLogger.INFO, "Range split: " + numChunks + " chunks x " + MainConfig.SELF_SPLIT_CHUNK_MB + "MB (cap " + MainConfig.PARALLEL_PARTS_PER_FILE + ", total " + total + " bytes) for '" + task.getLinkPath() + "'.");
        return new RangePlan(total, chunk, full, requests);
    }

    /**
     * EN: Fetches every ranged request concurrently (bounded by {@code parallel_parts_per_file}) and assembles
     *     the responses into the plan's buffer by offset ({@code index * chunk}), rejecting a non-206/200 status
     *     or a chunk whose body would spill past its own slot ({@code min(chunk, remaining)}). A {@code 200} means
     *     the server ignored Range and sent the WHOLE file: it is accepted only for a single-chunk plan (where the
     *     one range already covers the whole file); for a multi-chunk plan a {@code 200} throws
     *     {@link RangeIgnoredException} so the caller disables self-split and falls back to a single GET instead of
     *     retrying a doomed ranged plan. Finally verifies the reassembled length equals the declared total — a
     *     shortfall (a stale/oversized declared size the server clamped) throws so the download is retried instead
     *     of a zero-padded file being stored. <br>
     * RU: Качает каждый диапазонный запрос параллельно (не более {@code parallel_parts_per_file}) и собирает
     *     ответы в буфер плана по смещению ({@code index * chunk}), отвергая статус не-206/200 или кусок, тело
     *     которого вышло бы за пределы собственного слота ({@code min(chunk, остаток)}). Ответ {@code 200} значит,
     *     что сервер проигнорировал Range и прислал файл ЦЕЛИКОМ: он принимается только для плана из одного куска
     *     (где единственный диапазон и так покрывает весь файл); для плана из нескольких кусков {@code 200} бросает
     *     {@link RangeIgnoredException}, чтобы вызывающий отключил self-split и откатился к одному GET, а не повторял
     *     обречённый план с диапазонами. Наконец проверяет, что собранная длина равна заявленной — недобор
     *     (устаревший/завышенный размер, обрезанный сервером) бросает исключение, чтобы загрузка повторилась вместо
     *     сохранения дополненного нулями файла. <br>
     * ==================================================================<br>
     * EN: @param task the task being downloaded (for messages) / RU: @param task скачиваемая задача (для сообщений) <br>
     * EN: @param plan the range-split plan / RU: @param plan план разбивки <br>
     * @return <br>
     *         {byte[]} - EN: the fully reassembled file / RU: полностью собранный файл <br>
     **/
    private byte[] fetchAndAssemble(FileDownloadTask task, RangePlan plan) throws Exception
    {
        byte[] full = plan.full();
        int chunk = plan.chunk();
        boolean singleChunk = plan.requests().size() == 1;
        AtomicLong received = new AtomicLong(0);
        fetchConcurrently(plan.requests(), MainConfig.PARALLEL_PARTS_PER_FILE, (index, response) ->
        {
            int status = response.statusCode();
            if (status == 200 && !singleChunk)
            {
                throw new RangeIgnoredException(task.getLinkPath());
            }
            if (status != 206 && status != 200)
            {
                throw new HttpStatusException(status);
            }
            byte[] body = response.body() == null ? new byte[0] : response.body();
            int offset = (int) ((long) index * chunk);
            long slot = Math.min((long) chunk, (long) full.length - offset);
            if (body.length > slot)
            {
                throw new IOException("Range chunk at offset " + offset + " returned " + body.length + " bytes, past its " + slot + "-byte slot (buffer end " + full.length + ") for '" + task.getLinkPath() + "'.");
            }
            System.arraycopy(body, 0, full, offset, body.length);
            received.addAndGet(body.length);
        });
        if (received.get() != plan.total())
        {
            throw new IOException("Self-split assembled " + received.get() + " of " + plan.total() + " expected bytes for '" + task.getLinkPath() + "' (declared size wrong or server clamped ranges); failed download, will retry.");
        }
        return full;
    }

    /**
     * EN: The plan for a range-split download: total file size, chunk size, the preallocated assembly buffer
     *     (filled by {@link #fetchAndAssemble}), and one ranged request per chunk. <br>
     * RU: План для загрузки с разбивкой по диапазонам: полный размер файла, размер куска, заранее выделенный
     *     буфер сборки (заполняется {@link #fetchAndAssemble}) и по одному диапазонному запросу на кусок. <br>
     **/
    private record RangePlan(long total, int chunk, byte[] full, List<HttpRequest> requests)
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
            CompletableFuture<HttpResponse<Void>> probe = _httpClient.sendAsync(rangeRequest(sample, 0, 0), HttpResponse.BodyHandlers.discarding());
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
}
