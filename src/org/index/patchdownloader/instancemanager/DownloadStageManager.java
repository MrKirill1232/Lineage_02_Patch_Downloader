package org.index.patchdownloader.instancemanager;

import java.net.http.HttpClient;
import java.util.List;

import org.index.patchdownloader.model.pipeline.FileDownloadTask;
import org.index.patchdownloader.model.pipeline.download.AbstractDownloadStrategy;
import org.index.patchdownloader.model.pipeline.download.AkumuDownloadStrategy;
import org.index.patchdownloader.model.pipeline.download.CdnPartsDownloadStrategy;
import org.index.patchdownloader.model.pipeline.download.SelfSplitDownloadStrategy;
import org.index.patchdownloader.model.pipeline.download.SingleDownloadStrategy;
import org.index.patchdownloader.model.pipeline.enums.DownloadFailureType;
import org.index.patchdownloader.model.pipeline.enums.TaskStage;
import org.index.patchdownloader.model.pipeline.retry.DownloadRetryHandler;

/**
 * EN: Download stage. Owns one shared, thread-safe {@link HttpClient} and a priority-ordered list of
 *     {@link AbstractDownloadStrategy}s; for each task it picks the first strategy whose
 *     {@code supports()} matches (akumu antibot session &gt; Content-Delivery-Network (CDN)-split parts &gt; self-split ranges &gt; single
 *     GET) and delegates. Failures are classified for the base retry handler ({@link DownloadRetryHandler}).<br>
 * RU: Стадия загрузки. Владеет одним общим потокобезопасным {@link HttpClient} и упорядоченным по приоритету
 *     списком {@link AbstractDownloadStrategy}; для каждой задачи выбирает первую стратегию, чей
 *     {@code supports()} подходит (antibot-сессия akumu &gt; части Content-Delivery-Network (CDN) &gt; диапазоны self-split &gt; один GET),
 *     и делегирует. Сбои классифицируются для базового обработчика повторов ({@link DownloadRetryHandler}).<br>
 **/
public class DownloadStageManager extends AbstractStageManager
{
    private final HttpClient _httpClient;
    private final List<AbstractDownloadStrategy> _strategies;
    private final AbstractDownloadStrategy _fallback;

    private DownloadStageManager()
    {
        super(new DownloadRetryHandler());
        _httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();
        _fallback = new SingleDownloadStrategy(_httpClient);
        _strategies = List.of(new AkumuDownloadStrategy(_httpClient), new CdnPartsDownloadStrategy(_httpClient), new SelfSplitDownloadStrategy(_httpClient), _fallback);
    }

    @Override
    protected TaskStage stage()
    {
        return TaskStage.DOWNLOAD;
    }

    /**
     * EN: Marks the task active and delegates its download to the first matching strategy. <br>
     * RU: Помечает задачу активной и делегирует её загрузку первой подходящей стратегии. <br>
     * ==================================================================<br>
     * EN: @param task the task to download / RU: @param task задача для загрузки <br>
     **/
    @Override
    protected void processTask(FileDownloadTask task) throws Exception
    {
        task.markActive();
        selectStrategy(task).download(task);
    }

    /**
     * EN: Returns the first strategy (in priority order) that supports the task; the single-GET fallback is
     *     last and always applies. <br>
     * RU: Возвращает первую стратегию (по приоритету), поддерживающую задачу; запасной одиночный GET — в
     *     конце и подходит всегда. <br>
     * ==================================================================<br>
     * EN: @param task the task to route / RU: @param task маршрутизируемая задача <br>
     * @return <br>
     *         {AbstractDownloadStrategy} - EN: the chosen strategy / RU: выбранная стратегия <br>
     **/
    private AbstractDownloadStrategy selectStrategy(FileDownloadTask task)
    {
        for (AbstractDownloadStrategy strategy : _strategies)
        {
            if (strategy.supports(task))
            {
                return strategy;
            }
        }
        return _fallback;
    }

    @Override
    protected DownloadFailureType classify(Throwable throwable)
    {
        return DownloadFailureType.classifyDownload(throwable);
    }

    private static final DownloadStageManager INSTANCE = new DownloadStageManager();

    public static DownloadStageManager getInstance()
    {
        return INSTANCE;
    }
}
