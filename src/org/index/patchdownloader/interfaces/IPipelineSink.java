package org.index.patchdownloader.interfaces;

import org.index.patchdownloader.model.pipeline.FileDownloadTask;
import org.index.patchdownloader.model.pipeline.enums.DownloadFailureType;

/**
 * EN: The terminal sink of the pipeline, implemented by the coordinator. Stages route successes to
 *     the next stage themselves and report only terminal results (DONE/FAILED) plus budget release
 *     to this sink, which owns completion accounting.<br>
 * RU: Терминальный приёмник конвейера, реализуемый координатором. Стадии сами направляют успех на
 *     следующую стадию и сообщают сюда только терминальные результаты (DONE/FAILED) и освобождение
 *     бюджета; приёмник владеет учётом завершения.<br>
 **/
public interface IPipelineSink
{
    /**
     * EN: Called once when a task has been fully stored (terminal success). <br>
     * RU: Вызывается один раз, когда задача полностью сохранена (терминальный успех). <br>
     * ==================================================================<br>
     * EN: @param task the task that reached DONE / RU: @param task задача, достигшая DONE <br>
     **/
    void onDone(FileDownloadTask task);

    /**
     * EN: Called once when a task failed terminally (retries exhausted, permanent, or a
     *     non-retryable stage such as decompress/store). <br>
     * RU: Вызывается один раз при терминальном сбое задачи (исчерпаны повторы, постоянный сбой или
     *     не повторяемая стадия — decompress/store). <br>
     * ==================================================================<br>
     * EN: @param task the task that reached FAILED / RU: @param task задача, достигшая FAILED <br>
     * EN: @param failure the classified failure reason / RU: @param failure классифицированная причина сбоя <br>
     **/
    void onFailed(FileDownloadTask task, DownloadFailureType failure);

    /**
     * EN: Releases the in-flight memory-budget permits held by the task. Called once at terminal. <br>
     * RU: Освобождает удерживаемые задачей квоты бюджета памяти. Вызывается один раз в терминале. <br>
     * ==================================================================<br>
     * EN: @param task the task whose permits are released / RU: @param task задача, чьи квоты освобождаются <br>
     **/
    void releaseBudget(FileDownloadTask task);
}
