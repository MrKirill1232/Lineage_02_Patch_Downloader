package org.index.patchdownloader.model.sourcecompare;

import java.io.File;
import java.io.IOException;

import org.index.patchdownloader.enums.HashType;
import org.index.patchdownloader.instancemanager.HashingManager;
import org.index.patchdownloader.model.holders.FileInfoHolder;

/**
 * EN: Source verifier for Content-Delivery-Network (CDN) sources (NC_SOFT / UpNova) whose file list already
 *     carries a PER-FILE hash. It reads the source copy, hashes it with the run's algorithm (a FRESH instance
 *     per call — the shared ones are not thread-safe) and compares to the expected {@code fileHashSum}. Size is
 *     a fast pre-filter; if the target has no hash to prove against, the file is reported as a MISMATCH
 *     (downloaded) rather than trusted on size alone.<br>
 * RU: Верификатор источника для сетей доставки контента (Content-Delivery-Network, CDN) (NC_SOFT / UpNova), чей
 *     список файлов уже содержит хеш для каждого файла. Читает копию из источника, хеширует алгоритмом запуска
 *     (СВЕЖИЙ экземпляр на вызов — общие не потокобезопасны) и сравнивает с ожидаемым {@code fileHashSum}.
 *     Размер — быстрый префильтр; если у цели нет хеша для доказательства, файл считается MISMATCH
 *     (скачивается), а не доверяется по одному размеру.<br>
 **/
public class CdnFileHashVerifier implements ISourceVerifier
{
    private final File _sourceRoot;
    private final HashType _hashType;
    private final boolean _checkSize;
    private final boolean _checkHash;

    /**
     * EN: Builds a verifier bound to one source root and hash algorithm. <br>
     * RU: Создаёт верификатор, привязанный к одному корню-источнику и алгоритму хеширования. <br>
     * ==================================================================<br>
     * EN: @param sourceRoot the local folder that holds the source copies / RU: @param sourceRoot локальная папка с копиями-источниками <br>
     * EN: @param hashType   the hash algorithm used to prove a file / RU: @param hashType   алгоритм хеширования для доказательства файла <br>
     * EN: @param checkSize  whether to fast-reject on size mismatch / RU: @param checkSize  отбраковывать ли сразу при несовпадении размера <br>
     * EN: @param checkHash  whether to hash the source and compare / RU: @param checkHash  хешировать ли источник и сравнивать <br>
     **/
    public CdnFileHashVerifier(File sourceRoot, HashType hashType, boolean checkSize, boolean checkHash)
    {
        _sourceRoot = sourceRoot;
        _hashType = hashType;
        _checkSize = checkSize;
        _checkHash = checkHash;
    }

    @Override
    public SourceVerdict verify(FileInfoHolder fileInfo)
    {
        File source = new File(_sourceRoot, fileInfo.getLinkPath());
        // EN: Canonicalize and enforce that the resolved source stays under the source root; a CDN list entry
        //     with '..' segments must never let us read/hash a file outside the root.
        // RU: Приводим путь к каноническому виду и проверяем, что источник остаётся внутри корня; запись из
        //     списка CDN с сегментами '..' не должна давать читать/хешировать файл вне корня.
        try
        {
            File canonicalSource = source.getCanonicalFile();
            String rootPrefix = _sourceRoot.getCanonicalPath() + File.separator;
            if (!canonicalSource.getPath().startsWith(rootPrefix))
            {
                return SourceVerdict.SOURCE_MISSING;
            }
            source = canonicalSource;
        }
        catch (IOException e)
        {
            return SourceVerdict.SOURCE_MISSING;
        }
        if (!source.isFile())
        {
            return SourceVerdict.SOURCE_MISSING;
        }
        int expectedLength = fileInfo.getFileLength();
        if (_checkSize && expectedLength >= 0 && source.length() != expectedLength)
        {
            return SourceVerdict.SIZE_MISMATCH;
        }
        if (!_checkHash)
        {
            // EN: With hashing off, an unknown/unrepresentable length (-1, e.g. a >2GB size that overflowed int
            //     parsing) means nothing was actually verified. Mirror the null-hash rule and report MISMATCH so
            //     the file is re-downloaded rather than trusted blind.
            // RU: При выключенном хешировании неизвестная/непредставимая длина (-1, например размер >2ГБ, не
            //     влезший в int) означает, что ничего не проверено. Как и при отсутствии хеша, возвращаем
            //     MISMATCH, чтобы файл скачался заново, а не был принят вслепую.
            if (expectedLength < 0)
            {
                return SourceVerdict.MISMATCH;
            }
            return SourceVerdict.FULL_MATCH;
        }
        String expectedHash = fileInfo.getFileHashSum();
        if (expectedHash == null)
        {
            return SourceVerdict.MISMATCH;
        }
        String actualHash = HashingManager.getAvailableHashingAlgorithm(_hashType, true).calculateHash(source);
        if (actualHash == null)
        {
            // Source could not be read to the end (per the IHashingAlgorithm contract) — it cannot be proven, so
            // report MISMATCH and let the file be downloaded rather than trusting an unverifiable copy.
            return SourceVerdict.MISMATCH;
        }
        return expectedHash.equalsIgnoreCase(actualHash) ? SourceVerdict.FULL_MATCH : SourceVerdict.MISMATCH;
    }
}
