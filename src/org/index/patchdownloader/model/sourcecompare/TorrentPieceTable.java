package org.index.patchdownloader.model.sourcecompare;

import java.util.ArrayList;
import java.util.List;

import org.index.patchdownloader.model.akumu.TorrentMetadata;

/**
 * EN: Maps the torrent's GLOBAL pieces onto the individual files. Pieces cover the concatenation of all files
 *     in order, so a piece can span a file boundary. This table answers, for a piece, exactly which file
 *     byte-ranges compose it ({@link #segments(int)}), and, for a file, which pieces it overlaps split into
 *     INTERIOR (fully inside the file — verifiable from that file alone) and BOUNDARY (shared with a neighbour
 *     — needs the neighbour present) ({@link #fileRange(int)}). File lookup uses a binary search over the
 *     cumulative offsets, so a piece composed of many tiny files is still resolved efficiently.<br>
 * RU: Отображает ГЛОБАЛЬНЫЕ куски торрента на отдельные файлы. Куски покрывают конкатенацию всех файлов по
 *     порядку, поэтому кусок может пересекать границу файлов. Таблица отвечает: для куска — какие именно
 *     байтовые диапазоны файлов его составляют ({@link #segments(int)}); для файла — какие куски он пересекает,
 *     разделённые на ВНУТРЕННИЕ (целиком внутри файла — проверяемы только по нему) и ГРАНИЧНЫЕ (общие с соседом
 *     — нужен присутствующий сосед) ({@link #fileRange(int)}). Поиск файла — бинарным поиском по накопительным
 *     смещениям, поэтому кусок из множества крошечных файлов вычисляется эффективно.<br>
 **/
public final class TorrentPieceTable
{
    /**
     * EN: One file-slice of a piece: which file, at what in-file offset, and how many bytes. <br>
     * RU: Один файловый срез куска: какой файл, с какого смещения в файле и сколько байтов. <br>
     **/
    public record Segment(int fileIndex, long fileOffset, int length)
    {
    }

    /**
     * EN: The pieces a file touches: the full overlap range and the interior sub-range. Interior is empty when
     *     {@code interiorFirst &gt; interiorLast} (a file smaller than a piece or not piece-aligned). <br>
     * RU: Куски, которых касается файл: полный диапазон пересечения и внутренний под-диапазон. Внутренний пуст,
     *     когда {@code interiorFirst &gt; interiorLast} (файл меньше куска или не выровнен по куску). <br>
     **/
    public record FilePieceRange(int firstOverlap, int lastOverlap, int interiorFirst, int interiorLast)
    {
        /**
         * EN: Whether the file has interior pieces (pieces fully inside it, verifiable from the file alone). <br>
         * RU: Есть ли у файла внутренние куски (куски целиком внутри него, проверяемые только по этому файлу). <br>
         * @return <br>
         *         {true}  - EN: interior pieces exist / RU: внутренние куски есть <br>
         *         {false} - EN: no interior pieces / RU: внутренних кусков нет <br>
         **/
        public boolean hasInterior()
        {
            return interiorFirst <= interiorLast;
        }
    }

    private final long _pieceLength;
    private final int _pieceCount;
    private final long _totalLength;
    private final long[] _fileOffsets;
    private final long[] _fileLengths;

    private TorrentPieceTable(long pieceLength, int pieceCount, long totalLength, long[] fileOffsets, long[] fileLengths)
    {
        _pieceLength = pieceLength;
        _pieceCount = pieceCount;
        _totalLength = totalLength;
        _fileOffsets = fileOffsets;
        _fileLengths = fileLengths;
    }

    /**
     * EN: Builds the table from a torrent that has piece hashes (see {@link TorrentMetadata#hasPieceHashes()}).<br>
     * RU: Строит таблицу из торрента, у которого есть хеши кусков (см. {@link TorrentMetadata#hasPieceHashes()}).<br>
     * ==================================================================<br>
     * EN: @param torrent the parsed torrent (must have piece hashes) / RU: @param torrent разобранный торрент (должен иметь хеши кусков) <br>
     * @return <br>
     *         {TorrentPieceTable} - EN: the piece↔file table / RU: таблица кусок↔файл <br>
     **/
    public static TorrentPieceTable build(TorrentMetadata torrent)
    {
        long pieceLength = torrent.getPieceLength();
        int pieceCount = torrent.getPieceCount();
        long totalLength = torrent.getTotalLength();
        if (pieceLength <= 0 || pieceLength > Integer.MAX_VALUE)
        {
            throw new IllegalStateException("Torrent piece length out of supported range (1.." + Integer.MAX_VALUE + "): " + pieceLength);
        }
        if ((long) pieceCount * pieceLength < totalLength)
        {
            throw new IllegalStateException("Torrent piece hashes do not cover the total length: pieceCount=" + pieceCount + ", pieceLength=" + pieceLength + ", totalLength=" + totalLength);
        }
        List<TorrentMetadata.FileEntry> files = torrent.getFiles();
        long[] offsets = new long[files.size()];
        long[] lengths = new long[files.size()];
        for (int index = 0; index < files.size(); index++)
        {
            offsets[index] = files.get(index).getGlobalOffset();
            lengths[index] = files.get(index).getLength();
        }
        return new TorrentPieceTable(pieceLength, pieceCount, totalLength, offsets, lengths);
    }

    /**
     * EN: Piece length in bytes (the size each global piece covers, except possibly the last). <br>
     * RU: Длина куска в байтах (размер, покрываемый каждым глобальным куском, кроме, возможно, последнего). <br>
     * @return <br>
     *         {long} - EN: piece length in bytes / RU: длина куска в байтах <br>
     **/
    public long pieceLength()
    {
        return _pieceLength;
    }

    /**
     * EN: Number of pieces in the torrent. <br>
     * RU: Число кусков в торренте. <br>
     * @return <br>
     *         {int} - EN: piece count / RU: число кусков <br>
     **/
    public int pieceCount()
    {
        return _pieceCount;
    }

    /**
     * EN: The file-slices composing piece {@code pieceIndex}, in file order (usually 1–2, more when the piece
     *     spans many tiny files). <br>
     * RU: Файловые срезы, составляющие кусок {@code pieceIndex}, в порядке файлов (обычно 1–2, больше если
     *     кусок пересекает много крошечных файлов). <br>
     * ==================================================================<br>
     * EN: @param pieceIndex the piece index / RU: @param pieceIndex индекс куска <br>
     * @return <br>
     *         {List} - EN: the segments of the piece / RU: сегменты куска <br>
     **/
    public List<Segment> segments(int pieceIndex)
    {
        long start = (long) pieceIndex * _pieceLength;
        long end = Math.min(start + _pieceLength, _totalLength);
        List<Segment> segments = new ArrayList<>(2);
        for (int index = firstFileOverlapping(start); index < _fileOffsets.length && _fileOffsets[index] < end; index++)
        {
            long fileStart = _fileOffsets[index];
            long fileEnd = fileStart + _fileLengths[index];
            long a = Math.max(start, fileStart);
            long b = Math.min(end, fileEnd);
            if (b > a)
            {
                segments.add(new Segment(index, a - fileStart, (int) (b - a)));
            }
        }
        return segments;
    }

    /**
     * EN: The pieces file {@code fileIndex} overlaps, split into the full overlap range and the interior
     *     sub-range (fully-inside pieces verifiable from this file alone). <br>
     * RU: Куски, которые пересекает файл {@code fileIndex}, разделённые на полный диапазон пересечения и
     *     внутренний под-диапазон (куски целиком внутри, проверяемые только по этому файлу). <br>
     * ==================================================================<br>
     * EN: @param fileIndex the file index / RU: @param fileIndex индекс файла <br>
     * @return <br>
     *         {FilePieceRange} - EN: overlap + interior ranges / RU: диапазоны пересечения + внутренний <br>
     **/
    public FilePieceRange fileRange(int fileIndex)
    {
        long offset = _fileOffsets[fileIndex];
        long length = _fileLengths[fileIndex];
        if (length <= 0)
        {
            return new FilePieceRange(-1, -1, 0, -1);
        }
        long piece = _pieceLength;
        int firstOverlap = (int) (offset / piece);
        int lastOverlap = (int) ((offset + length - 1) / piece);
        int interiorFirst = (int) ((offset + piece - 1) / piece);
        int interiorLast = (int) ((offset + length) / piece) - 1;
        return new FilePieceRange(firstOverlap, lastOverlap, interiorFirst, interiorLast);
    }

    /**
     * EN: Index of the first file whose byte range intersects {@code position} (binary search on the cumulative
     *     offsets, skipping zero-length files that end at or before it). <br>
     * RU: Индекс первого файла, чей байтовый диапазон пересекает {@code position} (бинарный поиск по
     *     накопительным офсетам, пропуская файлы нулевой длины, оканчивающиеся на нём или раньше). <br>
     * ==================================================================<br>
     * EN: @param position a global byte position / RU: @param position глобальная байтовая позиция <br>
     * @return <br>
     *         {int} - EN: index of the first overlapping file / RU: индекс первого пересекающего файла <br>
     **/
    private int firstFileOverlapping(long position)
    {
        int low = 0;
        int high = _fileOffsets.length - 1;
        int answer = 0;
        while (low <= high)
        {
            int mid = (low + high) >>> 1;
            if (_fileOffsets[mid] <= position)
            {
                answer = mid;
                low = mid + 1;
            }
            else
            {
                high = mid - 1;
            }
        }
        while (answer < _fileOffsets.length && _fileOffsets[answer] + _fileLengths[answer] <= position)
        {
            answer++;
        }
        return answer;
    }
}
