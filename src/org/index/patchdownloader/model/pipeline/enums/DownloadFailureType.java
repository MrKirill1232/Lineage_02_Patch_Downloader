package org.index.patchdownloader.model.pipeline.enums;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.index.patchdownloader.model.pipeline.HttpStatusException;

/**
 * EN: Typed classification of why a stage failed, carrying a {@code retryable} flag. Only the
 *     download stage acts on the flag; decompress/store failures are always terminal — a corrupt
 *     archive or a disk/write error will not fix itself on a repeat.<br>
 * RU: Типизированная классификация причины сбоя стадии с флагом {@code retryable}. Флаг учитывает
 *     только стадия загрузки; сбои decompress/store всегда терминальны — повреждённый архив или
 *     ошибка записи на диск сами собой при повторе не исчезнут.<br>
 **/
public enum DownloadFailureType
{
    TRANSPORT(true),
    RATE_LIMIT(true),
    SERVER_ERROR(true),
    UNKNOWN(true),
    FORBIDDEN(false),
    NOT_FOUND(false),
    RANGE_NOT_SATISFIABLE(false),
    DECOMPRESS_ERROR(false),
    STORE_ERROR(false);

    public static final DownloadFailureType[] VALUES = values();

    private final boolean _retryable;

    DownloadFailureType(boolean retryable)
    {
        _retryable = retryable;
    }

    /**
     * EN: Whether a failure of this type is worth retrying (transient/server-side) rather than
     *     permanent (missing/forbidden/local error). <br>
     * RU: Стоит ли повторять сбой этого типа (временный/серверный) в отличие от постоянного
     *     (отсутствует/запрещено/локальная ошибка). <br>
     * @return <br>
     *         {true}  - EN: retryable failure / RU: повторяемый сбой <br>
     *         {false} - EN: permanent failure / RU: постоянный сбой <br>
     **/
    public boolean isRetryable()
    {
        return _retryable;
    }

    /**
     * EN: Maps an HTTP status code to a failure type: 408/429/5xx are retryable, 401/403 forbidden,
     *     404/410 not-found, 416 (HTTP Range Not Satisfiable) is a permanent range mismatch that would
     *     recur on every retry, any other non-2xx is treated conservatively as retryable UNKNOWN. <br>
     * RU: Сопоставляет HTTP-код с типом сбоя: 408/429/5xx — повторяемые, 401/403 — forbidden,
     *     404/410 — not-found, 416 (HTTP Range Not Satisfiable — запрошенный диапазон недоступен) —
     *     постоянное несоответствие диапазона, которое повторится при каждой попытке, любой другой
     *     не-2xx консервативно считается повторяемым UNKNOWN. <br>
     * ==================================================================<br>
     * EN: @param status the HTTP status code returned by the server <br>
     * RU: @param status HTTP-код, возвращённый сервером <br>
     * @return <br>
     *         {DownloadFailureType} - EN: the matching failure type / RU: соответствующий тип сбоя <br>
     **/
    public static DownloadFailureType fromHttpStatus(int status)
    {
        if (status == 408 || status == 429)
        {
            return RATE_LIMIT;
        }
        if (status >= 500 && status <= 599)
        {
            return SERVER_ERROR;
        }
        if (status == 401 || status == 403)
        {
            return FORBIDDEN;
        }
        if (status == 404 || status == 410)
        {
            return NOT_FOUND;
        }
        if (status == 416)
        {
            return RANGE_NOT_SATISFIABLE;
        }
        return UNKNOWN;
    }

    /**
     * EN: Classifies a throwable raised during the download stage. {@code HttpStatusException} maps
     *     via {@link #fromHttpStatus(int)}; timeouts/connect/IO map to {@code TRANSPORT}; anything
     *     else is conservatively {@code UNKNOWN} (retryable). <br>
     * RU: Классифицирует исключение стадии загрузки. {@code HttpStatusException} — через
     *     {@link #fromHttpStatus(int)}; таймауты/connect/IO — {@code TRANSPORT}; остальное
     *     консервативно {@code UNKNOWN} (повторяемое). <br>
     * ==================================================================<br>
     * EN: @param throwable the exception captured by the download future <br>
     * RU: @param throwable исключение, пойманное future загрузки <br>
     * @return <br>
     *         {DownloadFailureType} - EN: the matching failure type / RU: соответствующий тип сбоя <br>
     **/
    public static DownloadFailureType classifyDownload(Throwable throwable)
    {
        Throwable cause = throwable;
        while ((cause instanceof ExecutionException || cause instanceof CompletionException) && cause.getCause() != null)
        {
            cause = cause.getCause();
        }
        if (cause instanceof HttpStatusException httpStatusException)
        {
            return fromHttpStatus(httpStatusException.getStatus());
        }
        if (cause instanceof TimeoutException || cause instanceof HttpTimeoutException || cause instanceof HttpConnectTimeoutException || cause instanceof ConnectException)
        {
            return TRANSPORT;
        }
        if (cause instanceof IOException)
        {
            return TRANSPORT;
        }
        return UNKNOWN;
    }
}
