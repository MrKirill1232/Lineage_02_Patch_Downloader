package org.index.patchdownloader.model.sourcecompare;

import org.index.patchdownloader.model.holders.FileInfoHolder;

/**
 * EN: Verifies one target file against a local source folder and returns a {@link SourceVerdict}. Two
 *     implementations: {@code CdnFileHashVerifier} (NC_SOFT / UpNova — a per-file hash is available) and
 *     {@code TorrentPieceVerifier} (akumu — global piece hashes). The condition picks the right one from the
 *     link generator. Implementations must be thread-safe: the condition calls {@link #verify(FileInfoHolder)}
 *     from many threads at once.<br>
 * RU: Проверяет один целевой файл по локальной папке-источнику и возвращает {@link SourceVerdict}. Две
 *     реализации: {@code CdnFileHashVerifier} (NC_SOFT / UpNova — есть хеш на файл) и {@code TorrentPieceVerifier}
 *     (akumu — глобальные хеши кусков). Условие выбирает нужную по генератору ссылок. Реализации должны быть
 *     потокобезопасны: условие вызывает {@link #verify(FileInfoHolder)} из множества потоков сразу.<br>
 **/
public interface ISourceVerifier
{
    /**
     * EN: Verifies the target file against the source folder. <br>
     * RU: Проверяет целевой файл по папке-источнику. <br>
     * ==================================================================<br>
     * EN: @param fileInfo the target file metadata (path, size, hash) / RU: @param fileInfo метаданные целевого файла (путь, размер, хеш) <br>
     * @return <br>
     *         {SourceVerdict} - EN: the verdict / RU: вердикт <br>
     **/
    SourceVerdict verify(FileInfoHolder fileInfo);
}
