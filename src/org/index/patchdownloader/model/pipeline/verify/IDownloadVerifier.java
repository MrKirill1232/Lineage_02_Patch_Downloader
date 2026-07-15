package org.index.patchdownloader.model.pipeline.verify;

import org.index.patchdownloader.model.holders.FileInfoHolder;

/**
 * EN: Asynchronous post-store verification of a download run. As each file is finalised on disk the store stage
 *     reports it via {@link #onStored(FileInfoHolder)} (a non-blocking notification off the store worker), so the
 *     heavy read-and-hash overlaps the ongoing download instead of a blocking pass at the end. At the run's end
 *     {@link #awaitAndReport()} drains any outstanding work, logs the verdict and tells the coordinator whether
 *     the run is clean (its result feeds the process exit code). Concrete strategies encapsulate HOW a source is
 *     proven — a per-file hash ({@code PerFileVerifier}) or the torrent's global piece hashes
 *     ({@code IncrementalPieceVerifier}) — and the generator picks the right one polymorphically, so no caller
 *     branches on source type.<br>
 * RU: Асинхронная проверка запуска загрузки, выполняемая после сохранения. По мере финализации каждого файла на диске стадия
 *     сохранения сообщает о нём через {@link #onStored(FileInfoHolder)} (неблокирующее уведомление из воркера
 *     сохранения), поэтому тяжёлое чтение-и-хеширование перекрывается с идущей загрузкой, а не блокирующим
 *     проходом в конце. В конце запуска {@link #awaitAndReport()} доводит незавершённую работу, логирует вердикт
 *     и сообщает координатору, чист ли запуск (его результат идёт в код выхода процесса). Конкретные стратегии
 *     инкапсулируют, КАК доказывается источник — хешом на файл ({@code PerFileVerifier}) или глобальными хешами
 *     кусков торрента ({@code IncrementalPieceVerifier}), — а генератор выбирает нужную полиморфно, поэтому ни
 *     один вызывающий код не ветвится по типу источника.<br>
 **/
public interface IDownloadVerifier
{
    /**
     * EN: Notifies the verifier that a file has been finalised on disk. Must be fast and non-blocking (it runs on
     *     a store worker thread): it only schedules the actual verification, never performs it inline. <br>
     * RU: Уведомляет верификатор, что файл финализирован на диске. Должен быть быстрым и неблокирующим (выполняется
     *     в потоке воркера сохранения): лишь планирует саму проверку, но не выполняет её встрочно. <br>
     * ==================================================================<br>
     * EN: @param file the just-stored file's metadata / RU: @param file метаданные только что сохранённого файла <br>
     **/
    void onStored(FileInfoHolder file);

    /**
     * EN: Called once at the run's end (after every file reached a terminal state): drains all outstanding
     *     verification, logs the summary and returns whether the run is clean. <br>
     * RU: Вызывается один раз в конце запуска (после того, как каждый файл достиг терминального состояния): доводит всю
     *     незавершённую проверку, логирует сводку и возвращает, чист ли запуск. <br>
     * ==================================================================<br>
     * @return <br>
     *         {boolean} - EN: true when nothing was proven corrupt / RU: true, если ничего не признано битым <br>
     **/
    boolean awaitAndReport();

    /**
     * EN: The no-op verifier used when verification is disabled (both size and hash checks off): accepts every
     *     store notification silently and always reports the run clean. <br>
     * RU: Пустой верификатор для случая, когда проверка отключена (и размер, и хеш выключены): молча принимает
     *     каждое уведомление и всегда сообщает, что запуск чист. <br>
     **/
    IDownloadVerifier NONE = new IDownloadVerifier()
    {
        @Override
        public void onStored(FileInfoHolder file)
        {
            // verification disabled — nothing to schedule
        }

        @Override
        public boolean awaitAndReport()
        {
            return true;
        }
    };
}
