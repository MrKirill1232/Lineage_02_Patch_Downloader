package org.index.patchdownloader.model.pipeline.verify;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

import org.index.patchdownloader.interfaces.IDummyLogger;
import org.index.patchdownloader.model.akumu.TorrentMetadata;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.model.sourcecompare.TorrentPieceTable;
import org.index.patchdownloader.model.sourcecompare.TorrentPieceTable.FilePieceRange;
import org.index.patchdownloader.model.sourcecompare.TorrentPieceTable.Segment;
import org.index.patchdownloader.util.FileUtils;

/**
 * EN: Post-store verifier for a torrent source (akumu), whose only cryptographic proof is the GLOBAL piece
 *     hashes — a piece can span a file boundary, so it is only verifiable once EVERY file it touches has reached
 *     its FINAL state for this run. "Final" is tracked EXPLICITLY, not inferred from mere on-disk presence: a
 *     scheduled file becomes final when the store stage reports it ({@link #onStored}); a file NOT scheduled this
 *     run (skipped by a restore/condition, so it is not re-downloaded) is pre-marked final at construction. This
 *     avoids the update-run trap where a boundary piece would be hashed against a STALE pre-existing neighbour (or
 *     a target mid-overwrite) and permanently mis-flagged corrupt before the neighbour's new bytes land. A piece
 *     is claimed exactly once via a CAS the moment its last file becomes final; a final sweep at run end verifies
 *     any piece no store event triggered and marks a piece whose file never finalised (a failed download) as
 *     UNVERIFIABLE — "cannot validate". Every torrent path is checked for containment under the root once, and an
 *     escaping path ({@code ../..}) is never read, so a crafted torrent cannot turn the verifier into a read
 *     oracle on files outside the output tree. An INVALID piece is corrupt and fails the run.<br>
 * RU: Пост-сохранный верификатор для торрент-источника (akumu), единственное криптодоказательство которого —
 *     ГЛОБАЛЬНЫЕ хеши кусков: кусок может пересекать границу файлов, поэтому он проверяем только когда КАЖДЫЙ
 *     файл, которого он касается, достиг СВОЕГО финального для этого запуска состояния. «Финальность» отслеживается
 *     ЯВНО, а не выводится из простого наличия на диске: запланированный файл становится финальным, когда о нём
 *     сообщает стадия сохранения ({@link #onStored}); файл, НЕ запланированный в этом запуске (пропущенный
 *     restore/условием, значит не перекачиваемый), помечается финальным при создании. Это исключает ловушку
 *     update-запуска, где граничный кусок хешировался бы против УСТАРЕВШЕГО соседа (или цели во время перезаписи) и
 *     навсегда ошибочно признавался битым до прихода новых байтов соседа. Кусок занимается ровно один раз через CAS
 *     в момент, когда его последний файл становится финальным; финальный проход в конце проверяет куски, которые не
 *     запустило ни одно событие, и помечает кусок, чей файл так и не стал финальным (проваленная загрузка), как
 *     UNVERIFIABLE — «нельзя проверить». Каждый путь торрента один раз проверяется на нахождение под корнем, и
 *     выходящий за пределы путь ({@code ../..}) никогда не читается, поэтому поддельный торрент не превратит
 *     верификатор в оракул чтения файлов вне дерева вывода. INVALID кусок — это повреждение, проваливающее запуск.<br>
 **/
public final class IncrementalPieceVerifier extends AbstractAsyncVerifier
{
    private static final int READ_BUFFER = 64 * 1024;

    private final TorrentMetadata _torrent;
    private final TorrentPieceTable _table;
    private final File _root;
    private final Map<String, Integer> _indexByPath;
    private final AtomicIntegerArray _pieceClaimed;
    private final AtomicIntegerArray _fileFinal;
    private final boolean[] _safePath;
    private final AtomicInteger _verified;
    private final AtomicInteger _corrupt;
    private final AtomicInteger _unverifiable;

    /**
     * EN: Builds the verifier. {@code scheduledLinkPaths} are the files being (re)downloaded this run; every other
     *     torrent file is pre-marked final (its on-disk state will not change this run). Each torrent path is
     *     containment-checked against {@code root} once. <br>
     * RU: Создаёт верификатор. {@code scheduledLinkPaths} — файлы, (пере)качиваемые в этом запуске; каждый другой
     *     файл торрента помечается финальным заранее (его состояние на диске в этом запуске не изменится). Каждый
     *     путь торрента один раз проверяется на нахождение под {@code root}. <br>
     * ==================================================================<br>
     * EN: @param torrent the parsed torrent (must carry piece hashes) / RU: @param torrent разобранный торрент (с хешами кусков) <br>
     * EN: @param root the download folder holding the files / RU: @param root папка загрузки с файлами <br>
     * EN: @param scheduledLinkPaths link paths scheduled for download this run / RU: @param scheduledLinkPaths пути, запланированные к загрузке в этом запуске <br>
     * EN: @param parallelism the number of verification threads / RU: @param parallelism число потоков проверки <br>
     **/
    public IncrementalPieceVerifier(TorrentMetadata torrent, File root, Collection<String> scheduledLinkPaths, int parallelism)
    {
        super(parallelism);
        _torrent = torrent;
        _root = root;
        _table = TorrentPieceTable.build(torrent);
        _pieceClaimed = new AtomicIntegerArray(_table.pieceCount());
        _verified = new AtomicInteger(0);
        _corrupt = new AtomicInteger(0);
        _unverifiable = new AtomicInteger(0);

        Set<String> scheduled = new HashSet<>();
        for (String path : scheduledLinkPaths)
        {
            scheduled.add(normalize(path));
        }
        List<TorrentMetadata.FileEntry> files = torrent.getFiles();
        _indexByPath = new HashMap<>();
        _fileFinal = new AtomicIntegerArray(files.size());
        _safePath = new boolean[files.size()];
        for (int index = 0; index < files.size(); index++)
        {
            String relative = files.get(index).getRelativePath();
            String key = normalize(relative);
            _indexByPath.put(key, index);
            _safePath[index] = isWithinRoot(relative);
            if (!scheduled.contains(key))
            {
                // Not (re)downloaded this run -> its on-disk bytes are final; a boundary piece it shares becomes
                // verifiable as soon as its scheduled neighbour lands.
                _fileFinal.set(index, 1);
            }
        }
    }

    @Override
    public void onStored(FileInfoHolder file)
    {
        // Mark final + decide which pieces are now verifiable INLINE: this is cheap (atomic marks + in-memory
        // segment/range lookups, NO filesystem I/O — the heavy read-and-hash is deferred to the pool via
        // hashAndTally). Doing the marks synchronously here (not on the pool) is what keeps the run-end final sweep
        // race-free: by the time awaitAndReport() runs, every stored file is already marked final.
        triggerForFile(normalize(file.getLinkPath()));
    }

    /**
     * EN: Marks the given file final and triggers every piece it overlaps. Marking BEFORE checking, combined with
     *     the atomic {@code _fileFinal} reads, means the last file of a boundary piece to run this always sees all
     *     its files final and claims the piece — no piece is missed even when two neighbours finalise concurrently. <br>
     * RU: Помечает файл финальным и запускает каждый кусок, который он пересекает. Пометка ДО проверки вместе с
     *     атомарными чтениями {@code _fileFinal} гарантирует, что последний файл граничного куска всегда видит все
     *     свои файлы финальными и занимает кусок — ни один кусок не теряется даже при одновременной финализации. <br>
     * ==================================================================<br>
     * EN: @param normalizedPath the just-finalised file's normalized path / RU: @param normalizedPath нормализованный путь только что финализированного файла <br>
     **/
    private void triggerForFile(String normalizedPath)
    {
        Integer fileIndex = _indexByPath.get(normalizedPath);
        if (fileIndex == null)
        {
            return;
        }
        _fileFinal.set(fileIndex, 1);
        FilePieceRange range = _table.fileRange(fileIndex);
        if (range.firstOverlap() < 0)
        {
            return;
        }
        for (int piece = range.firstOverlap(); piece <= range.lastOverlap(); piece++)
        {
            tryTrigger(piece);
        }
    }

    /**
     * EN: Verifies the piece once every file it touches is FINAL (not merely present), claiming it via a CAS so a
     *     boundary piece is hashed exactly once. A piece with a not-yet-final file is left for that file's trigger. <br>
     * RU: Проверяет кусок, когда каждый его файл ФИНАЛЕН (а не просто присутствует), занимая его через CAS, чтобы
     *     граничный кусок хешировался ровно один раз. Кусок с ещё не финальным файлом оставляется его триггеру. <br>
     * ==================================================================<br>
     * EN: @param piece the piece index / RU: @param piece индекс куска <br>
     **/
    private void tryTrigger(int piece)
    {
        if (_pieceClaimed.get(piece) != 0)
        {
            return;
        }
        if (!allSegmentFilesFinal(piece))
        {
            return;
        }
        if (_pieceClaimed.compareAndSet(piece, 0, 1))
        {
            submit(() -> hashAndTally(piece));
        }
    }

    @Override
    protected void beforeAwait()
    {
        // Final sweep: verify any piece whose files are all final but which no store event triggered (all files
        // pre-marked); a piece with a file that never finalised (a failed download) is UNVERIFIABLE.
        for (int piece = 0; piece < _pieceClaimed.length(); piece++)
        {
            if (!_pieceClaimed.compareAndSet(piece, 0, 1))
            {
                continue;
            }
            if (allSegmentFilesFinal(piece))
            {
                final int readyPiece = piece;
                submit(() -> hashAndTally(readyPiece));
            }
            else
            {
                _unverifiable.incrementAndGet();
            }
        }
    }

    private boolean allSegmentFilesFinal(int piece)
    {
        for (Segment segment : _table.segments(piece))
        {
            if (_fileFinal.get(segment.fileIndex()) == 0)
            {
                return false;
            }
        }
        return true;
    }

    private void hashAndTally(int piece)
    {
        Boolean valid = hashPiece(piece);
        if (valid == null)
        {
            _unverifiable.incrementAndGet();
        }
        else if (valid)
        {
            _verified.incrementAndGet();
        }
        else
        {
            _corrupt.incrementAndGet();
            IDummyLogger.log(IDummyLogger.ERROR, "Akumu integrity: piece " + piece + " FAILED the torrent hash (a file it covers is corrupt or truncated).");
        }
    }

    /**
     * EN: Reads the piece's file-segments from disk, hashes them with SHA-1 and compares to the torrent piece
     *     hash. Returns {@code TRUE} on a match, {@code FALSE} on a mismatch or a short file (corrupt), and
     *     {@code null} when a segment file is missing, unreadable, or its torrent path escapes the root
     *     (unverifiable — an escaping path is never opened). <br>
     * RU: Читает файловые сегменты куска с диска, хеширует их SHA-1 и сравнивает с хешем куска. Возвращает
     *     {@code TRUE} при совпадении, {@code FALSE} при несовпадении или коротком файле (повреждение) и
     *     {@code null}, когда файл сегмента отсутствует, нечитаем или его путь выходит за пределы корня
     *     (непроверяемо — выходящий путь не открывается). <br>
     * ==================================================================<br>
     * EN: @param piece the piece index / RU: @param piece индекс куска <br>
     * @return <br>
     *         {Boolean} - EN: TRUE valid / FALSE corrupt / null unverifiable / RU: TRUE валиден / FALSE битый / null непроверяемо <br>
     **/
    private Boolean hashPiece(int piece)
    {
        List<Segment> segments = _table.segments(piece);
        MessageDigest digest = newSha1();
        byte[] buffer = new byte[READ_BUFFER];
        for (Segment segment : segments)
        {
            if (!_safePath[segment.fileIndex()])
            {
                return null;
            }
            File file = sourceFile(segment.fileIndex());
            if (!file.isFile())
            {
                return null;
            }
            try (RandomAccessFile access = new RandomAccessFile(file, "r"))
            {
                access.seek(segment.fileOffset());
                int remaining = segment.length();
                while (remaining > 0)
                {
                    int read = access.read(buffer, 0, Math.min(buffer.length, remaining));
                    if (read < 0)
                    {
                        return Boolean.FALSE;
                    }
                    digest.update(buffer, 0, read);
                    remaining -= read;
                }
            }
            catch (IOException e)
            {
                return null;
            }
        }
        return Arrays.equals(digest.digest(), _torrent.pieceHash(piece)) ? Boolean.TRUE : Boolean.FALSE;
    }

    @Override
    protected boolean report()
    {
        int corrupt = _corrupt.get();
        IDummyLogger.log(corrupt == 0 ? IDummyLogger.FINE : IDummyLogger.ERROR, "Akumu integrity: " + _verified.get() + " pieces verified, " + corrupt + " corrupt, " + _unverifiable.get() + " unverifiable (cannot validate).");
        return corrupt == 0;
    }

    private File sourceFile(int fileIndex)
    {
        return new File(_root, _torrent.getFiles().get(fileIndex).getRelativePath());
    }

    private boolean isWithinRoot(String relativePath)
    {
        try
        {
            FileUtils.ensureWithinDirectory(_root, new File(_root, relativePath), relativePath);
            return true;
        }
        catch (IOException e)
        {
            return false;
        }
    }

    private static MessageDigest newSha1()
    {
        try
        {
            return MessageDigest.getInstance("SHA-1");
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-1 is required to verify torrent pieces.", e);
        }
    }

    private static String normalize(String path)
    {
        String normalized = path.replace('\\', '/').toLowerCase();
        return normalized.startsWith("/") ? normalized.substring(1) : normalized;
    }
}
