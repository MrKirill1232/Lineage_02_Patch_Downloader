package org.index.patchdownloader.model.pipeline.verify;

import java.util.concurrent.atomic.AtomicInteger;

import org.index.patchdownloader.interfaces.IDummyLogger;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.model.sourcecompare.ISourceVerifier;
import org.index.patchdownloader.model.sourcecompare.SourceVerdict;

/**
 * EN: Post-store verifier for sources with a PER-FILE proof (a CDN/UpNova per-file hash, or a size-only check):
 *     each stored file is verified independently by re-reading it from disk and delegating to the shared
 *     {@link ISourceVerifier} (the same one restore/source-compare uses, rooted at the download folder). A
 *     {@code MISMATCH}/{@code SIZE_MISMATCH} is corrupt and fails the run; a file absent right after store is an
 *     anomaly counted separately. Since files are independent, each notification schedules one verification with
 *     no cross-file coordination.<br>
 * RU: Пост-сохранный верификатор для источников с ПОФАЙЛОВЫМ доказательством (хеш на файл CDN/UpNova либо
 *     проверка только по размеру): каждый сохранённый файл проверяется независимо повторным чтением с диска и
 *     делегированием общему {@link ISourceVerifier} (тому же, что использует restore/source-compare, с корнем в
 *     папке загрузки). {@code MISMATCH}/{@code SIZE_MISMATCH} — это повреждение, проваливающее запуск; файл,
 *     отсутствующий сразу после сохранения, — аномалия, считаемая отдельно. Поскольку файлы независимы, каждое
 *     уведомление планирует одну проверку без межфайловой координации.<br>
 **/
public final class PerFileVerifier extends AbstractAsyncVerifier
{
    private final ISourceVerifier _verifier;
    private final boolean _checkHash;
    private final AtomicInteger _verified;
    private final AtomicInteger _corrupt;
    private final AtomicInteger _unverifiable;
    private final AtomicInteger _missing;

    /**
     * EN: Builds the verifier over a shared, thread-safe {@link ISourceVerifier} rooted at the download folder.
     *     {@code checkHash} is remembered so a {@code MISMATCH} can be read correctly: the delegate returns
     *     {@code MISMATCH} both for a genuine hash difference AND for "cannot prove" (no hash to check against, or
     *     an unknown size with hashing off), so this verifier must know whether a hash was actually available. <br>
     * RU: Создаёт верификатор поверх общего потокобезопасного {@link ISourceVerifier} с корнем в папке загрузки.
     *     {@code checkHash} запоминается, чтобы {@code MISMATCH} читался правильно: делегат возвращает
     *     {@code MISMATCH} и при настоящем расхождении хеша, И при «нельзя доказать» (нет хеша для сверки либо
     *     неизвестный размер при выключенном хешировании), поэтому этот верификатор должен знать, был ли хеш
     *     доступен. <br>
     * ==================================================================<br>
     * EN: @param verifier the per-file source verifier (download folder as root) / RU: @param verifier пофайловый верификатор источника (корень — папка загрузки) <br>
     * EN: @param checkHash whether the hash check is on for this run / RU: @param checkHash включена ли проверка хеша для этого запуска <br>
     * EN: @param parallelism the number of verification threads / RU: @param parallelism число потоков проверки <br>
     **/
    public PerFileVerifier(ISourceVerifier verifier, boolean checkHash, int parallelism)
    {
        super(parallelism);
        _verifier = verifier;
        _checkHash = checkHash;
        _verified = new AtomicInteger(0);
        _corrupt = new AtomicInteger(0);
        _unverifiable = new AtomicInteger(0);
        _missing = new AtomicInteger(0);
    }

    @Override
    public void onStored(FileInfoHolder file)
    {
        submit(() -> tally(file, _verifier.verify(file)));
    }

    /**
     * EN: Folds one file's verdict into the counters, logging corruption as an error and a post-store absence as
     *     a warning. <br>
     * RU: Сворачивает вердикт одного файла в счётчики, логируя повреждение как ошибку, а отсутствие после
     *     сохранения — как предупреждение. <br>
     * ==================================================================<br>
     * EN: @param file the verified file / RU: @param file проверенный файл <br>
     * EN: @param verdict the verifier's verdict / RU: @param verdict вердикт верификатора <br>
     **/
    private void tally(FileInfoHolder file, SourceVerdict verdict)
    {
        switch (verdict)
        {
            case FULL_MATCH, PARTIAL_MATCH -> _verified.incrementAndGet();
            case SIZE_MISMATCH ->
            {
                // A size difference is objective, unambiguous corruption.
                _corrupt.incrementAndGet();
                IDummyLogger.log(IDummyLogger.ERROR, "Verify: '" + file.getLinkPath() + "' FAILED verification (SIZE_MISMATCH).");
            }
            case MISMATCH ->
            {
                if (unprovable(file))
                {
                    // "Cannot prove" — no hash to check against (or an unknown size with hashing off), NOT proven
                    // corrupt. Mirrors the old decompress-stage behaviour of logging "no original hash-sum;
                    // skipping hash check" and continuing, instead of failing the whole run.
                    _unverifiable.incrementAndGet();
                }
                else
                {
                    _corrupt.incrementAndGet();
                    IDummyLogger.log(IDummyLogger.ERROR, "Verify: '" + file.getLinkPath() + "' FAILED the hash check (content differs from the original).");
                }
            }
            case SOURCE_MISSING ->
            {
                // The store stage reported this file finalised, yet it is gone when the verifier reads it — a real
                // anomaly (external deletion / disk fault), not a benign skip. Fail-CLOSED: it cannot be certified.
                _missing.incrementAndGet();
                IDummyLogger.log(IDummyLogger.ERROR, "Verify: '" + file.getLinkPath() + "' is missing right after a successful store.");
            }
        }
    }

    /**
     * EN: Whether a {@code MISMATCH} verdict for this file means "cannot prove" rather than "content differs". The
     *     delegate returns {@code MISMATCH} for a file with no hash to check against (hash on, hash-sum null) or an
     *     unknown size when hashing is off — in both cases there was nothing to disprove the file against, so it is
     *     unverifiable, not corrupt. Only a file that DID carry a provable hash (or size) and failed it is real
     *     corruption. <br>
     * RU: Означает ли вердикт {@code MISMATCH} для этого файла «нельзя доказать», а не «содержимое отличается».
     *     Делегат возвращает {@code MISMATCH} для файла без хеша для сверки (хеш включён, хеш-сумма null) либо при
     *     неизвестном размере с выключенным хешированием — в обоих случаях файл не с чем сравнить, поэтому он
     *     непроверяем, а не битый. Настоящее повреждение — только файл, у которого БЫЛ доказуемый хеш (или размер)
     *     и он не сошёлся. <br>
     * ==================================================================<br>
     * EN: @param file the file whose MISMATCH is being classified / RU: @param file файл, чей MISMATCH классифицируется <br>
     * @return <br>
     *         {true}  - EN: unprovable (skip, not a failure) / RU: непроверяемо (пропуск, не сбой) <br>
     *         {false} - EN: a real hash mismatch (corrupt) / RU: настоящее расхождение хеша (битый) <br>
     **/
    private boolean unprovable(FileInfoHolder file)
    {
        if (_checkHash && file.getFileHashSum() == null)
        {
            return true;
        }
        return !_checkHash && file.getFileLength() < 0;
    }

    @Override
    protected boolean report()
    {
        int corrupt = _corrupt.get();
        int missing = _missing.get();
        boolean clean = corrupt == 0 && missing == 0;
        IDummyLogger.log(clean ? IDummyLogger.FINE : IDummyLogger.ERROR, "Verify: " + _verified.get() + " ok, " + corrupt + " corrupt, " + _unverifiable.get() + " unverifiable, " + missing + " missing.");
        // Fail-CLOSED: corruption OR a file missing right after store both leave the run uncertifiable.
        return clean;
    }
}
