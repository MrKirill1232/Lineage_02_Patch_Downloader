package org.index.patchdownloader.model.sourcecompare;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.index.patchdownloader.model.akumu.TorrentMetadata;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.model.sourcecompare.TorrentPieceTable.FilePieceRange;
import org.index.patchdownloader.model.sourcecompare.TorrentPieceTable.Segment;

/**
 * EN: Source verifier for the akumu torrent: verifies a file against the source folder using the GLOBAL piece
 *     hashes. A file is proven by the pieces that overlap it. A piece fully inside the file (INTERIOR) is
 *     provable from that file alone; a piece on a file's edge (BOUNDARY) also needs the neighbour file present.
 *     Piece results are memoised (a boundary piece is shared by two files — verified once) in a thread-safe
 *     cache, since the condition verifies files in parallel. Verdict: any INVALID piece → MISMATCH; all pieces
 *     VALID → FULL_MATCH; only a boundary UNVERIFIABLE (missing neighbour) with a fully-VALID interior →
 *     PARTIAL_MATCH; otherwise MISMATCH. There is no size-only shortcut when hashing is on — some packed or
 *     Rivest-Shamir-Adleman (RSA) files keep their size while their content changes.<br>
 * RU: Верификатор источника для торрента akumu: проверяет файл по папке-источнику через ГЛОБАЛЬНЫЕ хеши кусков.
 *     Файл доказывается кусками, которые его пересекают. Кусок целиком внутри файла (ВНУТРЕННИЙ) доказуем по
 *     самому файлу; кусок на краю (ГРАНИЧНЫЙ) требует ещё и присутствующего соседа. Результаты кусков
 *     мемоизируются (граничный кусок общий для двух файлов — проверяется один раз) в потокобезопасном кэше, так
 *     как условие проверяет файлы параллельно. Вердикт: любой INVALID кусок → MISMATCH; все VALID →
 *     FULL_MATCH; только граничный UNVERIFIABLE (нет соседа) при полностью VALID внутренней части →
 *     PARTIAL_MATCH; иначе MISMATCH. При включённом хешировании нет упрощённой проверки только по размеру —
 *     некоторые упакованные или Rivest-Shamir-Adleman (RSA) файлы сохраняют размер, меняя содержимое.<br>
 **/
public class TorrentPieceVerifier implements ISourceVerifier
{
    private static final int READ_BUFFER = 64 * 1024;

    private enum PieceState
    {
        VALID,
        INVALID,
        UNVERIFIABLE
    }

    private final File _sourceRoot;
    private final TorrentMetadata _torrent;
    private final TorrentPieceTable _table;
    private final boolean _checkSize;
    private final boolean _checkHash;
    private final Map<Integer, CompletableFuture<PieceState>> _pieceCache;
    private final Map<String, Integer> _indexByPath;

    public TorrentPieceVerifier(File sourceRoot, TorrentMetadata torrent, boolean checkSize, boolean checkHash)
    {
        _sourceRoot = sourceRoot;
        _torrent = torrent;
        _table = TorrentPieceTable.build(torrent);
        _checkSize = checkSize;
        _checkHash = checkHash;
        _pieceCache = new ConcurrentHashMap<>();
        _indexByPath = new HashMap<>();
        List<TorrentMetadata.FileEntry> files = torrent.getFiles();
        for (int index = 0; index < files.size(); index++)
        {
            _indexByPath.put(normalize(files.get(index).getRelativePath()), index);
        }
    }

    /**
     * EN: Public entry point: resolves the torrent file by the holder's link path, then decides the verdict.
     *     Returns SOURCE_MISSING when the path has no torrent entry or the source file is absent; SIZE_MISMATCH
     *     when size checking is on and the source size differs from the torrent length; FULL_MATCH when hashing
     *     is off (size alone is enough); otherwise delegates to verifyByPieces for the piece-hash aggregation. <br>
     * RU: Публичная точка входа: находит файл торрента по пути ссылки из холдера и выносит вердикт. Возвращает
     *     SOURCE_MISSING, если для пути нет записи в торренте или исходный файл отсутствует; SIZE_MISMATCH, если
     *     проверка размера включена и размер источника не совпадает с длиной в торренте; FULL_MATCH, если
     *     хеширование выключено (достаточно одного размера); иначе делегирует verifyByPieces для сведения по
     *     хешам кусков. <br>
     * ==================================================================<br>
     * EN: @param fileInfo the file descriptor to verify / RU: @param fileInfo описатель проверяемого файла <br>
     * @return <br>
     *         {SourceVerdict} - EN: the aggregated verdict / RU: сводный вердикт <br>
     **/
    @Override
    public SourceVerdict verify(FileInfoHolder fileInfo)
    {
        Integer index = _indexByPath.get(normalize(fileInfo.getLinkPath()));
        if (index == null)
        {
            return SourceVerdict.SOURCE_MISSING;
        }
        File source = sourceFile(index);
        if (!source.isFile())
        {
            return SourceVerdict.SOURCE_MISSING;
        }
        long expectedLength = _torrent.getFiles().get(index).getLength();
        if (_checkSize && source.length() != expectedLength)
        {
            return SourceVerdict.SIZE_MISMATCH;
        }
        if (!_checkHash)
        {
            return SourceVerdict.FULL_MATCH;
        }
        return verifyByPieces(index);
    }

    /**
     * EN: Aggregates the states of every piece overlapping the file into a verdict (see class doc). <br>
     * RU: Сводит состояния всех кусков, пересекающих файл, в вердикт (см. док класса). <br>
     * ==================================================================<br>
     * EN: @param fileIndex the torrent file index / RU: @param fileIndex индекс файла в торренте <br>
     * @return <br>
     *         {SourceVerdict} - EN: the aggregated verdict / RU: сводный вердикт <br>
     **/
    private SourceVerdict verifyByPieces(int fileIndex)
    {
        FilePieceRange range = _table.fileRange(fileIndex);
        if (range.firstOverlap() < 0)
        {
            return SourceVerdict.FULL_MATCH;
        }
        boolean anyUnverifiable = false;
        boolean interiorAllValid = true;
        for (int piece = range.firstOverlap(); piece <= range.lastOverlap(); piece++)
        {
            PieceState state = pieceState(piece);
            boolean interior = piece >= range.interiorFirst() && piece <= range.interiorLast();
            if (state == PieceState.INVALID)
            {
                return SourceVerdict.MISMATCH;
            }
            if (state == PieceState.UNVERIFIABLE)
            {
                anyUnverifiable = true;
            }
            if (interior && state != PieceState.VALID)
            {
                interiorAllValid = false;
            }
        }
        if (!anyUnverifiable)
        {
            return SourceVerdict.FULL_MATCH;
        }
        return (range.hasInterior() && interiorAllValid) ? SourceVerdict.PARTIAL_MATCH : SourceVerdict.MISMATCH;
    }

    /**
     * EN: Memoised piece result. Stores a CompletableFuture per piece so the heavy read-and-hash of verifyPiece
     *     runs OUTSIDE the map: the thread that inserts the placeholder future computes the state and completes
     *     it, while others just join. This keeps the ConcurrentHashMap bin lock held only for the cheap insert,
     *     so unrelated pieces (and table resizes) are never blocked behind another thread's multi-MB hash. <br>
     * RU: Мемоизированный результат куска. Хранит CompletableFuture на каждый кусок, чтобы тяжёлое
     *     чтение-и-хеширование verifyPiece выполнялось ВНЕ карты: поток, вставивший заглушку-future, вычисляет
     *     состояние и завершает её, остальные лишь ждут через join. Так блокировка бакета ConcurrentHashMap
     *     удерживается только на дешёвую вставку — соседние куски (и перестроение таблицы) не ждут за чужим
     *     многомегабайтным хешем. <br>
     * ==================================================================<br>
     * EN: @param pieceIndex the piece index / RU: @param pieceIndex индекс куска <br>
     * @return <br>
     *         {PieceState} - EN: VALID / INVALID / UNVERIFIABLE / RU: VALID / INVALID / UNVERIFIABLE <br>
     **/
    private PieceState pieceState(int pieceIndex)
    {
        CompletableFuture<PieceState> future = _pieceCache.get(pieceIndex);
        if (future == null)
        {
            CompletableFuture<PieceState> created = new CompletableFuture<>();
            future = _pieceCache.putIfAbsent(pieceIndex, created);
            if (future == null)
            {
                future = created;
                try
                {
                    created.complete(verifyPiece(pieceIndex));
                }
                catch (Throwable throwable)
                {
                    created.completeExceptionally(throwable);
                    throw throwable;
                }
            }
        }
        return future.join();
    }

    /**
     * EN: Verifies one piece: every file it touches must exist and match its torrent size (else UNVERIFIABLE /
     *     INVALID); then the exact segment bytes are hashed with Secure Hash Algorithm 1 (SHA-1) and compared to
     *     the piece hash. <br>
     * RU: Проверяет один кусок: каждый файл, которого он касается, должен существовать и совпадать по размеру с
     *     торрентом (иначе UNVERIFIABLE / INVALID); затем точные байты сегментов хешируются алгоритмом Secure
     *     Hash Algorithm 1 (SHA-1) и сравниваются с хешем куска. <br>
     * ==================================================================<br>
     * EN: @param pieceIndex the piece index / RU: @param pieceIndex индекс куска <br>
     * @return <br>
     *         {PieceState} - EN: VALID / INVALID / UNVERIFIABLE / RU: VALID / INVALID / UNVERIFIABLE <br>
     **/
    private PieceState verifyPiece(int pieceIndex)
    {
        List<Segment> segments = _table.segments(pieceIndex);
        for (Segment segment : segments)
        {
            File file = sourceFile(segment.fileIndex());
            if (!file.isFile())
            {
                return PieceState.UNVERIFIABLE;
            }
            if (file.length() != _torrent.getFiles().get(segment.fileIndex()).getLength())
            {
                return PieceState.INVALID;
            }
        }
        MessageDigest digest = newSha1();
        byte[] buffer = new byte[READ_BUFFER];
        for (Segment segment : segments)
        {
            try (RandomAccessFile access = new RandomAccessFile(sourceFile(segment.fileIndex()), "r"))
            {
                access.seek(segment.fileOffset());
                int remaining = segment.length();
                while (remaining > 0)
                {
                    int read = access.read(buffer, 0, Math.min(buffer.length, remaining));
                    if (read < 0)
                    {
                        return PieceState.INVALID;
                    }
                    digest.update(buffer, 0, read);
                    remaining -= read;
                }
            }
            catch (IOException e)
            {
                return PieceState.UNVERIFIABLE;
            }
        }
        return Arrays.equals(digest.digest(), _torrent.pieceHash(pieceIndex)) ? PieceState.VALID : PieceState.INVALID;
    }

    private File sourceFile(int fileIndex)
    {
        return new File(_sourceRoot, _torrent.getFiles().get(fileIndex).getRelativePath());
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
