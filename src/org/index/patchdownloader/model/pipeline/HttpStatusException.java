package org.index.patchdownloader.model.pipeline;

import java.io.IOException;

/**
 * EN: Thrown by the download stage on a non-200 HTTP response to carry the status code out of the
 *     worker. It is classified once in the stage failure handler (avoids double classification).<br>
 * RU: Бросается стадией загрузки при HTTP-ответе не-200, чтобы вынести код статуса из воркера.
 *     Классифицируется один раз в обработчике сбоя стадии (без двойной классификации).<br>
 **/
public class HttpStatusException extends IOException
{
    private final int _status;

    /**
     * EN: Builds the exception with the offending HTTP status code and a readable message. <br>
     * RU: Создаёт исключение с проблемным HTTP-кодом и читаемым сообщением. <br>
     * ==================================================================<br>
     * EN: @param status the non-200 HTTP status code returned by the server <br>
     * RU: @param status HTTP-код не-200, возвращённый сервером <br>
     **/
    public HttpStatusException(int status)
    {
        super("Unexpected HTTP status: " + status);
        _status = status;
    }

    /**
     * EN: Returns the HTTP status code carried by this exception. <br>
     * RU: Возвращает HTTP-код, который несёт это исключение. <br>
     * @return <br>
     *         {int} - EN: the HTTP status code / RU: HTTP-код статуса <br>
     **/
    public int getStatus()
    {
        return _status;
    }
}
