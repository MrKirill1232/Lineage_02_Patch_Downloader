package org.index.patchdownloader.model.akumu;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * EN: The parsed content of a {@code .torrent} used as an HTTP-mirror FILE LIST and, for source-compare, as a
 *     HASH source. akumu is fetched over HTTP (not P2P), so trackers / web-seeds are ignored — but the
 *     {@code piece length} + {@code pieces} (concatenated 20-byte SHA-1 (Secure Hash Algorithm 1) hash per
 *     piece) ARE kept so a local source
 *     can be verified against them. Handles single-file ({@code info.length}) and multi-file
 *     ({@code info.files[]}); a multi-file path is the {@code path} segments joined with '/', RELATIVE to the
 *     torrent directory. Each file also carries its GLOBAL byte offset (cumulative over the file order), which
 *     is how a file is located inside the global piece stream.<br>
 * RU: Разобранное содержимое {@code .torrent}, используемое как СПИСОК ФАЙЛОВ HTTP-зеркала и, для
 *     source-compare, как источник ХЕШЕЙ. akumu качается по HTTP (не P2P), поэтому трекеры / web-seed
 *     игнорируются — но {@code piece length} + {@code pieces} (конкатенация 20-байтных хешей SHA-1 (Secure
 *     Hash Algorithm 1) на кусок)
 *     СОХРАНЯЮТСЯ, чтобы можно было проверить локальный источник по ним. Поддерживает одно-файловые
 *     ({@code info.length}) и много-файловые ({@code info.files[]}); путь в много-файловом — сегменты
 *     {@code path} через '/', ОТНОСИТЕЛЬНО каталога торрента. Каждый файл несёт своё ГЛОБАЛЬНОЕ байтовое
 *     смещение (накопительно по порядку файлов) — так файл находится в глобальном потоке кусков.<br>
 **/
public final class TorrentMetadata
{
    private static final int SHA1_LENGTH = 20;

    /**
     * EN: One file entry: path relative to the mirror folder, exact byte length, and its GLOBAL offset in the
     *     concatenated piece stream. <br>
     * RU: Одна запись файла: путь относительно папки зеркала, точная длина в байтах и её ГЛОБАЛЬНОЕ смещение в
     *     конкатенированном потоке кусков. <br>
     **/
    public static final class FileEntry
    {
        private final String _relativePath;
        private final long _length;
        private final long _globalOffset;

        private FileEntry(String relativePath, long length, long globalOffset)
        {
            _relativePath = relativePath;
            _length = length;
            _globalOffset = globalOffset;
        }

        public String getRelativePath()
        {
            return _relativePath;
        }

        public long getLength()
        {
            return _length;
        }

        public long getGlobalOffset()
        {
            return _globalOffset;
        }
    }

    private final String _name;
    private final boolean _multiFile;
    private final List<FileEntry> _files;
    private final long _pieceLength;
    private final byte[] _pieces;

    private TorrentMetadata(String name, boolean multiFile, List<FileEntry> files, long pieceLength, byte[] pieces)
    {
        _name = name;
        _multiFile = multiFile;
        _files = Collections.unmodifiableList(files);
        _pieceLength = pieceLength;
        _pieces = pieces;
    }

    /**
     * EN: Parses raw {@code .torrent} bytes into a file list (with global offsets) plus the piece-hash data.
     *     Throws {@link IllegalStateException} when the bencode has no {@code info} dictionary or neither
     *     {@code files} nor {@code length}. Absent {@code piece length}/{@code pieces} is tolerated (hash
     *     verification simply unavailable). <br>
     * RU: Разбирает сырые байты {@code .torrent} в список файлов (с глобальными смещениями) и данные хешей кусков.
     *     Бросает {@link IllegalStateException}, если в bencode нет словаря {@code info} либо нет ни
     *     {@code files}, ни {@code length}. Отсутствие {@code piece length}/{@code pieces} допустимо (проверка
     *     хешей просто недоступна). <br>
     * ==================================================================<br>
     * EN: @param torrentBytes the downloaded torrent file bytes / RU: @param torrentBytes скачанные байты торрент-файла <br>
     * @return <br>
     *         {TorrentMetadata} - EN: the parsed file list + piece data / RU: разобранный список файлов + данные кусков <br>
     **/
    @SuppressWarnings("unchecked")
    public static TorrentMetadata parse(byte[] torrentBytes)
    {
        Object root = BencodeReader.decode(torrentBytes);
        if (!(root instanceof Map))
        {
            throw new IllegalStateException("Torrent root is not a bencode dictionary.");
        }
        Object infoObject = ((Map<String, Object>) root).get("info");
        if (!(infoObject instanceof Map))
        {
            throw new IllegalStateException("Torrent has no 'info' dictionary.");
        }
        Map<String, Object> info = (Map<String, Object>) infoObject;
        String name = asText(info.get("name"));
        long pieceLength = info.get("piece length") instanceof Long length ? length : 0L;
        byte[] pieces = info.get("pieces") instanceof byte[] raw ? raw : new byte[0];

        List<FileEntry> files = new ArrayList<>();
        Object filesObject = info.get("files");
        boolean multiFile = filesObject instanceof List;
        long running = 0;
        if (multiFile)
        {
            for (Object fileObject : (List<Object>) filesObject)
            {
                Map<String, Object> fileMap = (Map<String, Object>) fileObject;
                long length = asLong(fileMap.get("length"));
                Object pathObject = fileMap.get("path");
                if (!(pathObject instanceof List) || ((List<?>) pathObject).isEmpty())
                {
                    throw new IllegalStateException("Torrent file entry has no 'path'.");
                }
                List<Object> pathSegments = (List<Object>) pathObject;
                StringBuilder relativePath = new StringBuilder();
                for (Object segment : pathSegments)
                {
                    if (relativePath.length() > 0)
                    {
                        relativePath.append('/');
                    }
                    relativePath.append(asText(segment));
                }
                files.add(new FileEntry(relativePath.toString(), length, running));
                running += length;
            }
        }
        else
        {
            Object lengthObject = info.get("length");
            if (lengthObject == null)
            {
                throw new IllegalStateException("Torrent 'info' has neither 'files' nor 'length'.");
            }
            long length = asLong(lengthObject);
            files.add(new FileEntry(name, length, 0L));
            running = length;
        }
        return new TorrentMetadata(name, multiFile, files, pieceLength, pieces);
    }

    public String getName()
    {
        return _name;
    }

    public boolean isMultiFile()
    {
        return _multiFile;
    }

    public List<FileEntry> getFiles()
    {
        return _files;
    }

    /**
     * EN: Sums the byte length of every file in the torrent (the extent of the global piece stream). <br>
     * RU: Суммирует длину всех файлов торрента (протяжённость глобального потока кусков). <br>
     * @return <br>
     *         {long} - EN: total size in bytes / RU: общий размер в байтах <br>
     **/
    public long getTotalLength()
    {
        long total = 0;
        for (FileEntry file : _files)
        {
            total += file.getLength();
        }
        return total;
    }

    public long getPieceLength()
    {
        return _pieceLength;
    }

    /**
     * EN: The number of pieces (SHA-1 hashes) in this torrent — {@code pieces.length / 20}. <br>
     * RU: Число кусков (SHA-1 хешей) в этом торренте — {@code pieces.length / 20}. <br>
     * @return <br>
     *         {int} - EN: piece count / RU: число кусков <br>
     **/
    public int getPieceCount()
    {
        return _pieces.length / SHA1_LENGTH;
    }

    /**
     * EN: Whether piece-hash verification is available (piece length &gt; 0 and a matching {@code pieces}
     *     blob is present). <br>
     * RU: Доступна ли проверка по хешам кусков (длина куска &gt; 0 и присутствует согласованный блок
     *     {@code pieces}). <br>
     * @return <br>
     *         {true}  - EN: piece hashing usable / RU: хеширование кусков доступно <br>
     *         {false} - EN: no piece data / RU: нет данных кусков <br>
     **/
    public boolean hasPieceHashes()
    {
        return _pieceLength > 0 && _pieces.length >= SHA1_LENGTH && _pieces.length % SHA1_LENGTH == 0;
    }

    /**
     * EN: The 20-byte SHA-1 hash of piece {@code index}. <br>
     * RU: 20-байтный SHA-1 хеш куска {@code index}. <br>
     * ==================================================================<br>
     * EN: @param index the piece index (0-based) / RU: @param index индекс куска (с нуля) <br>
     * @return <br>
     *         {byte[]} - EN: the 20-byte piece hash / RU: 20-байтный хеш куска <br>
     **/
    public byte[] pieceHash(int index)
    {
        int from = index * SHA1_LENGTH;
        return Arrays.copyOfRange(_pieces, from, from + SHA1_LENGTH);
    }

    private static String asText(Object value)
    {
        if (value instanceof byte[] bytes)
        {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(value);
    }

    private static long asLong(Object value)
    {
        if (value instanceof Long longValue)
        {
            return longValue;
        }
        throw new IllegalStateException("Expected an integer torrent field but found " + (value == null ? "null" : value.getClass().getSimpleName()) + ".");
    }
}
