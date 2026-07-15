package org.index.patchdownloader.model.linkgenerator;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.enums.CDNLink;
import org.index.patchdownloader.enums.HashType;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.model.pipeline.verify.IDownloadVerifier;
import org.index.patchdownloader.model.pipeline.verify.PerFileVerifier;
import org.index.patchdownloader.model.sourcecompare.CdnFileHashVerifier;
import org.index.patchdownloader.model.sourcecompare.ISourceVerifier;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public abstract class GeneralLinkGenerator
{
    /**
     * EN: Returns the link generator that matches the given Content-Delivery-Network (CDN) type,
     * or {@code null} when the type is unknown. <br>
     * RU: Возвращает генератор ссылок, соответствующий указанному типу сети доставки контента
     * (Content-Delivery-Network, CDN), либо {@code null}, если тип неизвестен.
     *
     * @param cdnLinkType EN: CDN type to build a generator for. <br> RU: тип CDN, для которого создаётся генератор.
     * @param patchVersion EN: patch version passed to the generator. <br> RU: версия патча, передаваемая генератору.
     * @return EN: a matching generator, or {@code null} if the type is unknown. <br> RU: подходящий генератор либо {@code null}, если тип неизвестен.
     */
    public static GeneralLinkGenerator generateLinkToFiles(CDNLink cdnLinkType, int patchVersion)
    {
        switch (cdnLinkType)
        {
            case NC_SOFT_TAIWAN:
            {
                return new NcTaiwanLinkGenerator(patchVersion);
            }
            case NC_SOFT_JAPANESE:
            {
                return new NcJapaneseLinkGenerator(patchVersion);
            }
            case NC_SOFT_AMERICA:
            {
                return new NcAmericaLinkGenerator(patchVersion);
            }
            case NC_SOFT_KOREAN:
            {
                return new NcKoreanLinkGenerator(patchVersion);
            }
            case UP_NOVA_LAUNCHER:
            {
                return new NovaLauncherGenerator();
            }
            case AKUMU:
            {
                return new AkumuLinkGenerator();
            }
            default:
            {
                return null;
            }
        }
    }

    protected final CDNLink _cdnLinkType;
    protected final int _patchVersion;

    protected Map<String, FileInfoHolder> _fileMapHolder;

    protected GeneralLinkGenerator(CDNLink cdnLink, int patchVersion)
    {
        _cdnLinkType    = cdnLink;
        _patchVersion   = patchVersion;
        _fileMapHolder  = new HashMap<>();
    }

    public abstract HashType getHashingAlgorithm();

    public abstract void load();

    /**
     * EN: Creates the source verifier for checking this generator's files against a folder ({@code root}) —
     *     used for BOTH the restore check (folder = output) and source-compare (folder = local source). The base
     *     implementation uses the per-file-hash {@code CdnFileHashVerifier}; {@link AkumuLinkGenerator} overrides
     *     it to verify against the torrent's global piece hashes. This polymorphic hook replaces a runtime
     *     {@code instanceof} type-switch at the call site. <br>
     * RU: Создаёт верификатор источника для проверки файлов этого генератора по папке ({@code root}) — для
     *     проверки restore (папка = вывод) И для source-compare (папка = локальный источник). Базовая реализация
     *     использует верификатор по хешу на файл {@code CdnFileHashVerifier}; {@link AkumuLinkGenerator}
     *     переопределяет его для проверки по глобальным хешам кусков торрента. Этот полиморфный хук заменяет
     *     проверку типа через {@code instanceof} в месте вызова. <br>
     * ==================================================================<br>
     * EN: @param root the folder to verify files against / RU: @param root папка, по которой проверяются файлы <br>
     * EN: @param checkSize whether to compare file size / RU: @param checkSize сравнивать ли размер файла <br>
     * EN: @param checkHash whether to compare the hash / RU: @param checkHash сравнивать ли хеш <br>
     * @return <br>
     *         {ISourceVerifier} - EN: the verifier for this source type / RU: верификатор для этого типа источника <br>
     **/
    public ISourceVerifier createSourceVerifier(File root, boolean checkSize, boolean checkHash)
    {
        return new CdnFileHashVerifier(root, getHashingAlgorithm(), checkSize, checkHash);
    }

    /**
     * EN: Creates the asynchronous post-store verifier for a download run of this source, or
     *     {@link IDownloadVerifier#NONE} when both the size and hash checks are off. The base source has a
     *     per-file proof, so it wraps its {@link #createSourceVerifier(File, boolean, boolean)} (rooted at the
     *     download folder) in a {@link PerFileVerifier}; {@link AkumuLinkGenerator} overrides this to the
     *     torrent's incremental piece verifier when hashing is on. Another polymorphic hook, mirroring
     *     {@link #createSourceVerifier(File, boolean, boolean)}, so the coordinator never branches on source
     *     type. <br>
     * RU: Создаёт асинхронный верификатор, работающий после сохранения, для запуска загрузки этого источника либо
     *     {@link IDownloadVerifier#NONE}, когда и проверка размера, и хеша выключены. У базового источника есть
     *     пофайловое доказательство, поэтому он оборачивает свой
     *     {@link #createSourceVerifier(File, boolean, boolean)} (с корнем в папке загрузки) в
     *     {@link PerFileVerifier}; {@link AkumuLinkGenerator} переопределяет это на инкрементный
     *     piece-верификатор торрента, когда хеширование включено. Ещё один полиморфный хук, повторяющий
     *     {@link #createSourceVerifier(File, boolean, boolean)}, поэтому координатор не ветвится по типу
     *     источника. <br>
     * ==================================================================<br>
     * EN: @param parallelism the number of verification worker threads / RU: @param parallelism число потоков-воркеров проверки <br>
     * @return <br>
     *         {IDownloadVerifier} - EN: the post-store verifier for this run / RU: верификатор запуска, работающий после сохранения <br>
     **/
    public IDownloadVerifier createDownloadVerifier(Collection<String> scheduledLinkPaths, int parallelism)
    {
        boolean checkSize = MainConfig.CHECK_FILE_SIZE;
        boolean checkHash = MainConfig.CHECK_HASH_SUM;
        if (!checkSize && !checkHash)
        {
            return IDownloadVerifier.NONE;
        }
        // The base per-file verifier does not need the scheduled set (files are proven independently); it is used
        // only by the torrent-piece override to tell this-run-final files from stale pre-existing neighbours.
        return new PerFileVerifier(createSourceVerifier(MainConfig.DOWNLOAD_PATH, checkSize, checkHash), checkHash, parallelism);
    }

    public Map<String, FileInfoHolder> getFileMapHolder()
    {
        return _fileMapHolder;
    }
}
