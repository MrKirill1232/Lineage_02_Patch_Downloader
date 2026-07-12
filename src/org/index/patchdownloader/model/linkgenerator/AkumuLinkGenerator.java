package org.index.patchdownloader.model.linkgenerator;

import java.io.File;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.enums.ArchiveType;
import org.index.patchdownloader.enums.CDNLink;
import org.index.patchdownloader.enums.HashType;
import org.index.patchdownloader.interfaces.IDummyLogger;
import org.index.patchdownloader.model.akumu.AnubisClient;
import org.index.patchdownloader.model.akumu.TorrentMetadata;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.model.holders.LinkInfoHolder;
import org.index.patchdownloader.model.sourcecompare.ISourceVerifier;
import org.index.patchdownloader.model.sourcecompare.TorrentPieceVerifier;

/**
 * EN: Link generator for the akumu HTTP mirror. Given the configured folder URL it fetches (through the
 *     shared {@link AnubisClient} so the antibot is passed once) the first {@code .torrent} in that folder,
 *     parses it purely as a FILE LIST ({@link TorrentMetadata}) and maps every entry to an HTTP URL under the
 *     same folder ({@code folder + relativePath}). Files are raw (no Content Delivery Network (CDN) compression -&gt; {@link ArchiveType#NONE})
 *     and torrents carry no per-file hash, so entries have a size but no hash-sum (validation is size-only).
 *     If {@code akumu_folder_url} already points at a {@code .torrent}, that torrent is used directly and its
 *     containing directory is the file base.<br>
 * RU: Генератор ссылок для HTTP-зеркала akumu. По настроенному URL папки он получает (через общий
 *     {@link AnubisClient}, чтобы антибот пройти один раз) первый {@code .torrent} в этой папке, разбирает его
 *     чисто как СПИСОК ФАЙЛОВ ({@link TorrentMetadata}) и сопоставляет каждую запись HTTP-URL внутри той же
 *     папки ({@code folder + relativePath}). Файлы сырые (без сжатия сети доставки контента (Content Delivery Network, CDN) -&gt; {@link ArchiveType#NONE}), а
 *     торренты не несут хеша на файл, поэтому у записей есть размер, но нет хеш-суммы (проверка только по
 *     размеру). Если {@code akumu_folder_url} уже указывает на {@code .torrent}, он используется напрямую, а
 *     базой файлов служит его каталог.<br>
 **/
public class AkumuLinkGenerator extends GeneralLinkGenerator
{
    private static final Pattern TORRENT_HREF = Pattern.compile("href\\s*=\\s*\"([^\"]+?\\.torrent)\"", Pattern.CASE_INSENSITIVE);

    private TorrentMetadata _torrent;

    /**
     * EN: Validates that {@code akumu_folder_url} is configured, throwing if it is not; the actual torrent
     *     fetching and parsing is deferred to {@link #load()}. <br>
     * RU: Проверяет, что {@code akumu_folder_url} задан в настройках, и бросает исключение, если нет; само
     *     получение и разбор торрента откладываются до {@link #load()}. <br>
     **/
    public AkumuLinkGenerator()
    {
        super(CDNLink.AKUMU, -1);
        if (MainConfig.AKUMU_FOLDER_URL == null)
        {
            throw new NullPointerException("Requested Akumu Generator. Main.ini - 'akumu_folder_url' is not set.");
        }
    }

    /**
     * EN: The parsed torrent kept after {@link #load()} — its GLOBAL piece hashes let {@code -source_compare}
     *     verify a local client against the torrent (see {@code TorrentPieceVerifier}). {@code null} until load
     *     succeeds. <br>
     * RU: Разобранный торрент, сохранённый после {@link #load()} — его ГЛОБАЛЬНЫЕ хеши кусков позволяют
     *     {@code -source_compare} проверить локальный клиент по торренту (см. {@code TorrentPieceVerifier}).
     *     {@code null}, пока load не выполнится успешно. <br>
     * ==================================================================<br>
     * @return <br>
     *         {TorrentMetadata} - EN: the torrent, or null / RU: торрент или null <br>
     **/
    public TorrentMetadata getTorrent()
    {
        return _torrent;
    }

    @Override
    public HashType getHashingAlgorithm()
    {
        return HashType.SHA01;
    }

    /**
     * EN: Verifies akumu files against the torrent's GLOBAL piece hashes ({@code TorrentPieceVerifier}) once the
     *     torrent is parsed and carries piece hashes; otherwise (torrent not yet loaded, or one without pieces)
     *     falls back to the base per-file-hash verifier — which for akumu has no per-file hash to prove against,
     *     so those files are downloaded rather than reused. <br>
     * RU: Проверяет файлы akumu по ГЛОБАЛЬНЫМ хешам кусков торрента ({@code TorrentPieceVerifier}), как только
     *     торрент разобран и несёт хеши кусков; иначе (торрент ещё не загружен или без кусков) откатывается к
     *     базовому верификатору по хешу на файл — а у akumu хеша на файл нет, поэтому такие файлы скачиваются, а
     *     не переиспользуются. <br>
     * ==================================================================<br>
     * EN: @param root the folder to verify files against / RU: @param root папка, по которой проверяются файлы <br>
     * EN: @param checkSize whether to compare file size / RU: @param checkSize сравнивать ли размер файла <br>
     * EN: @param checkHash whether to compare the hash / RU: @param checkHash сравнивать ли хеш <br>
     * @return <br>
     *         {ISourceVerifier} - EN: torrent-piece verifier, or the base fallback / RU: piece-верификатор торрента или базовый запасной <br>
     **/
    @Override
    public ISourceVerifier createSourceVerifier(File root, boolean checkSize, boolean checkHash)
    {
        if (_torrent != null && _torrent.hasPieceHashes())
        {
            return new TorrentPieceVerifier(root, _torrent, checkSize, checkHash);
        }
        return super.createSourceVerifier(root, checkSize, checkHash);
    }

    /**
     * EN: Locates and downloads the folder's torrent, parses it, and builds one {@link FileInfoHolder} per
     *     file (size + HTTP link, no compression, no hash). Logs and leaves the map empty on any failure so
     *     the controller reports "version unavailable" uniformly. <br>
     * RU: Находит и скачивает торрент папки, разбирает его и строит по одному {@link FileInfoHolder} на файл
     *     (размер + HTTP-ссылка, без сжатия, без хеша). При любом сбое логирует и оставляет карту пустой, чтобы
     *     контроллер единообразно сообщил «версия недоступна». <br>
     **/
    @Override
    public void load()
    {
        try
        {
            AnubisClient client = AnubisClient.getInstance();
            String folderUrl = MainConfig.AKUMU_FOLDER_URL;
            String torrentUrl = resolveTorrentUrl(client, folderUrl);
            URI fileBase = directoryOf(URI.create(torrentUrl));

            IDummyLogger.log(IDummyLogger.INFO, "Akumu: downloading torrent '" + torrentUrl + "'...");
            byte[] torrentBytes = client.fetch(torrentUrl).body();
            TorrentMetadata torrent = TorrentMetadata.parse(torrentBytes);
            _torrent = torrent;
            IDummyLogger.log(IDummyLogger.INFO, "Akumu: torrent '" + torrent.getName() + "' parsed: " + torrent.getFiles().size() + " files, " + torrent.getTotalLength() + " bytes total.");

            for (TorrentMetadata.FileEntry entry : torrent.getFiles())
            {
                // Per-entry guard: one bad/unsafe path must not discard the whole file list.
                try
                {
                    addFile(fileBase, entry);
                }
                catch (Exception e)
                {
                    IDummyLogger.log(IDummyLogger.WARNING, "Akumu: skipping file entry '" + entry.getRelativePath() + "': " + e);
                }
            }
        }
        catch (Exception e)
        {
            IDummyLogger.log(IDummyLogger.ERROR, "Akumu: failed to build the file list from '" + MainConfig.AKUMU_FOLDER_URL + "': " + e);
            _fileMapHolder.clear();
            _torrent = null;
        }
    }

    /**
     * EN: Returns the torrent URL: the configured URL itself when it already ends with {@code .torrent},
     *     otherwise the first {@code .torrent} link found in the folder's directory listing (resolved against
     *     the folder). <br>
     * RU: Возвращает URL торрента: сам настроенный URL, если он уже оканчивается на {@code .torrent}, иначе
     *     первая ссылка {@code .torrent}, найденная в листинге каталога папки (разрешённая относительно
     *     папки). <br>
     * ==================================================================<br>
     * EN: @param client the shared authenticated session / RU: @param client общая авторизованная сессия <br>
     * EN: @param folderUrl the configured folder (or torrent) URL / RU: @param folderUrl настроенный URL папки (или торрента) <br>
     * @return <br>
     *         {String} - EN: the absolute torrent URL / RU: абсолютный URL торрента <br>
     **/
    private String resolveTorrentUrl(AnubisClient client, String folderUrl) throws Exception
    {
        if (folderUrl.toLowerCase().endsWith(".torrent"))
        {
            return folderUrl;
        }
        String listingUrl = folderUrl.endsWith("/") ? folderUrl : folderUrl + "/";
        byte[] listing = client.fetch(listingUrl).body();
        Matcher matcher = TORRENT_HREF.matcher(new String(listing, java.nio.charset.StandardCharsets.UTF_8));
        if (!matcher.find())
        {
            throw new IllegalStateException("No '.torrent' link found in the akumu folder listing '" + listingUrl + "'.");
        }
        return URI.create(listingUrl).resolve(matcher.group(1)).toString();
    }

    /**
     * EN: Adds one torrent file entry to the map: builds its HTTP URL under the file base, splits the relative
     *     path into folder + name, and records the size (no hash — validated by size only). <br>
     * RU: Добавляет одну запись файла торрента в карту: строит её HTTP-URL под базой файлов, делит
     *     относительный путь на папку + имя и записывает размер (без хеша — проверка только по размеру). <br>
     * ==================================================================<br>
     * EN: @param fileBase the directory URL that holds the files / RU: @param fileBase URL каталога с файлами <br>
     * EN: @param entry the torrent file entry / RU: @param entry запись файла торрента <br>
     **/
    private void addFile(URI fileBase, TorrentMetadata.FileEntry entry) throws Exception
    {
        String relativePath = entry.getRelativePath();
        if (!isSafeRelativePath(relativePath))
        {
            throw new IllegalStateException("Unsafe torrent path (traversal / absolute / backslash / colon / reserved device name): '" + relativePath + "'");
        }
        int lastSlash = relativePath.lastIndexOf('/');
        String fileName = lastSlash < 0 ? relativePath : relativePath.substring(lastSlash + 1);
        String filePath = lastSlash < 0 ? "" : relativePath.substring(0, lastSlash + 1);

        URI fileUri = new URI(fileBase.getScheme(), fileBase.getAuthority(), fileBase.getPath() + relativePath, null, null);

        FileInfoHolder fileInfo = new FileInfoHolder(fileName, filePath, ArchiveType.NONE, false, 0);
        fileInfo.setDownloadDataLength((int) Math.min(Integer.MAX_VALUE, entry.getLength()));
        fileInfo.setFileLength((int) Math.min(Integer.MAX_VALUE, entry.getLength()));
        fileInfo.setAccessLink(new LinkInfoHolder(fileInfo));
        fileInfo.getAccessLink().setAccessLink(fileUri.toASCIIString());

        _fileMapHolder.put(fileInfo.getLinkPath().toLowerCase(), fileInfo);
    }

    /**
     * EN: The directory URI that contains the given file URI (its path up to and including the last '/'). <br>
     * RU: URI каталога, содержащего данный файловый URI (его путь до последнего '/' включительно). <br>
     * ==================================================================<br>
     * EN: @param fileUri a file URI (e.g. the torrent URL) / RU: @param fileUri URI файла (напр. URL торрента) <br>
     * @return <br>
     *         {URI} - EN: the containing directory URI / RU: URI содержащего каталога <br>
     **/
    private static URI directoryOf(URI fileUri) throws Exception
    {
        String path = fileUri.getPath();
        int lastSlash = path.lastIndexOf('/');
        String dirPath = lastSlash < 0 ? "/" : path.substring(0, lastSlash + 1);
        return new URI(fileUri.getScheme(), fileUri.getAuthority(), dirPath, null, null);
    }

    /**
     * EN: Whether a torrent file path is safe to write under the download directory: not empty, not absolute,
     *     with no {@code ..} segment, no backslash, no colon (drive letter or NTFS alternate data stream) and no
     *     reserved Windows device name. Zip-slip / path-traversal guard, since the torrent comes from an untrusted
     *     HTTP mirror and its paths flow into {@code new File(DOWNLOAD_PATH, ...)}. <br>
     * RU: Безопасен ли путь файла торрента для записи под каталогом загрузки: не пустой, не абсолютный, без
     *     сегмента {@code ..}, без обратного слэша, без двоеточия (буква диска или альтернативный поток данных
     *     NTFS) и без зарезервированного имени устройства Windows. Защита от zip-slip / обхода пути, так как
     *     торрент приходит с недоверенного HTTP-зеркала, а его пути попадают в {@code new File(DOWNLOAD_PATH, ...)}. <br>
     * ==================================================================<br>
     * EN: @param relativePath the torrent-relative file path / RU: @param relativePath относительный путь файла из торрента <br>
     * @return <br>
     *         {true}  - EN: safe to use / RU: безопасно использовать <br>
     *         {false} - EN: unsafe — reject / RU: небезопасно — отклонить <br>
     **/
    private static boolean isSafeRelativePath(String relativePath)
    {
        if (relativePath == null || relativePath.isBlank())
        {
            return false;
        }
        if (relativePath.indexOf('\\') >= 0 || relativePath.startsWith("/"))
        {
            return false;
        }
        // Reject a colon anywhere: a drive letter ("C:...") is only one case; on NTFS "name:stream"
        // targets an alternate data stream (a hidden write that the size check never sees).
        if (relativePath.indexOf(':') >= 0)
        {
            return false;
        }
        for (String segment : relativePath.split("/"))
        {
            if (segment.equals("..") || isReservedDeviceName(segment))
            {
                return false;
            }
        }
        return true;
    }

    /**
     * EN: Whether a single path segment is a reserved Windows device name (CON, PRN, AUX, NUL,
     *     COM1-9, LPT1-9, case-insensitive), tested against the base name before its first {@code .}
     *     because {@code new File(DOWNLOAD_PATH, "patch/nul")} resolves to the NUL device rather than a
     *     real file — the store silently succeeds while nothing lands on disk. <br>
     * RU: Является ли отдельный сегмент пути зарезервированным именем устройства Windows (CON, PRN, AUX,
     *     NUL, COM1-9, LPT1-9, без учёта регистра); проверяется базовая часть имени до первой {@code .},
     *     поскольку {@code new File(DOWNLOAD_PATH, "patch/nul")} указывает на устройство NUL, а не на
     *     реальный файл — запись «успешна», но на диск ничего не попадает. <br>
     * ==================================================================<br>
     * EN: @param segment a single path segment / RU: @param segment отдельный сегмент пути <br>
     * @return <br>
     *         {true}  - EN: reserved device name / RU: зарезервированное имя устройства <br>
     *         {false} - EN: ordinary name / RU: обычное имя <br>
     **/
    private static boolean isReservedDeviceName(String segment)
    {
        int dot = segment.indexOf('.');
        String baseName = (dot < 0 ? segment : segment.substring(0, dot)).toUpperCase();
        switch (baseName)
        {
            case "CON":
            case "PRN":
            case "AUX":
            case "NUL":
            case "COM1": case "COM2": case "COM3": case "COM4": case "COM5":
            case "COM6": case "COM7": case "COM8": case "COM9":
            case "LPT1": case "LPT2": case "LPT3": case "LPT4": case "LPT5":
            case "LPT6": case "LPT7": case "LPT8": case "LPT9":
                return true;
            default:
                return false;
        }
    }
}
