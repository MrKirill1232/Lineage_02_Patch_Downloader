package org.index.patchdownloader.model.linkgenerator;

import org.index.patchdownloader.enums.ArchiveType;
import org.index.patchdownloader.enums.CDNLink;
import org.index.patchdownloader.enums.FileTypeByLink;
import org.index.patchdownloader.enums.HashType;
import org.index.patchdownloader.interfaces.IDummyLogger;
import org.index.patchdownloader.model.holders.FileInfoHolder;
import org.index.patchdownloader.model.holders.LinkInfoHolder;
import org.index.patchdownloader.util.HttpDownloadUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

public class NcTaiwanLinkGenerator extends GeneralLinkGenerator
{
    /**
     * EN: The archive suffix the CDN puts on top of the real file name: {@code .zip} for a single compressed
     *     file, or {@code .z01} / {@code .z02} ... for one volume of a spanned archive. ONLY these two forms
     *     are stripped when the final name is restored — a name is never shortened blindly, otherwise a file
     *     whose own extension happens to be four characters long (for example {@code .torrent}) would come out
     *     truncated ({@code .tor}) and would then be treated as an archive it is not. <br>
     * RU: Архивный суффикс, который CDN добавляет поверх настоящего имени файла: {@code .zip} для одного сжатого
     *     файла либо {@code .z01} / {@code .z02} ... для тома многотомного архива. При восстановлении итогового
     *     имени отбрасываются ТОЛЬКО эти две формы — имя никогда не укорачивается вслепую, иначе файл, у которого
     *     собственное расширение состоит из четырёх символов (например {@code .torrent}), получился бы обрезанным
     *     ({@code .tor}) и дальше считался бы архивом, которым он не является. <br>
     **/
    private static final Pattern ARCHIVE_SUFFIX = Pattern.compile("\\.(?:zip|z\\d{2,})$", Pattern.CASE_INSENSITIVE);

    protected NcTaiwanLinkGenerator(int patchVersion)
    {
        super(CDNLink.NC_SOFT_TAIWAN, patchVersion);
    }

    protected NcTaiwanLinkGenerator(CDNLink cdnLink, int patchVersion)
    {
        super(cdnLink, patchVersion);
    }

    @Override
    public HashType getHashingAlgorithm()
    {
        return HashType.SHA01;
    }

    /**
     * EN: Downloads the file list and the hash list (synchronously) and parses them into the file map.
     *     A non-200 HTTP status aborts the run instead of silently leaving the map empty or unverified. <br>
     * RU: Скачивает (синхронно) список файлов и список хешей и разбирает их в карту файлов. HTTP-статус,
     *     отличный от 200, прерывает загрузку, а не оставляет карту пустой или без проверки хешей. <br>
     **/
    @Override
    public void load()
    {
        String fileListUrl = String.format(_cdnLinkType.getCdnFileListLink(), _patchVersion, _patchVersion);

        FileInfoHolder fileListInfo = new FileInfoHolder(getFileListFileName(), "", ArchiveType.NONE, false, 0);
        fileListInfo.setFileLength(-1);
        fileListInfo.setAccessLink(new LinkInfoHolder(fileListInfo));
        fileListInfo.getAccessLink().setAccessLink(fileListUrl);

        byte[] fileListData = HttpDownloadUtils.download(fileListInfo.getAccessLink());
        if (fileListInfo.getAccessLink().getHttpStatus() != 200)
        {
            throw new NoSuchElementException("File list is unavailable! HTTP status " + fileListInfo.getAccessLink().getHttpStatus() + ". Requested link " + fileListUrl + ";");
        }
        parseFileList(fileListData);

        String fileMapUrl = String.format(_cdnLinkType.getGeneralCdnLink(), _patchVersion, getFileListFileHash());

        FileInfoHolder fileMapInfo = new FileInfoHolder(getFileListFileHash(), "", ArchiveType.NONE, false, 0);
        fileMapInfo.setFileLength(-1);
        fileMapInfo.setAccessLink(new LinkInfoHolder(fileMapInfo));
        fileMapInfo.getAccessLink().setAccessLink(fileMapUrl);

        byte[] fileMapData = HttpDownloadUtils.download(fileMapInfo.getAccessLink());
        if (fileMapInfo.getAccessLink().getHttpStatus() != 200)
        {
            IDummyLogger.log(IDummyLogger.ERROR, "Hash list is unavailable! HTTP status " + fileMapInfo.getAccessLink().getHttpStatus() + ". Requested link " + fileMapUrl + ";");
            throw new NoSuchElementException("Hash list is unavailable! HTTP status " + fileMapInfo.getAccessLink().getHttpStatus() + ". Requested link " + fileMapUrl + ";");
        }
        parseHashList(fileMapData);
    }

    protected String getFileListFileName()
    {
        return "PatchFileInfo_TWLin2EP20_" + _patchVersion + ".dat";
    }

    protected String getFileListFileHash()
    {
        return "FileInfoMap_TWLin2EP20_" + _patchVersion + ".dat";
    }

    /**
     * EN: Parses the UTF-16LE file-list text into per-file holders (path/size/hash/type), grouping
     *     separated parts. <br>
     * RU: Разбирает UTF-16LE текст списка файлов в holder'ы по файлам (путь/размер/хеш/тип), группируя
     *     разделённые части. <br>
     * ==================================================================<br>
     * EN: @param data the downloaded file-list bytes / RU: @param data скачанные байты списка файлов <br>
     **/
    protected void parseFileList(byte[] data)
    {
        if (data == null || data.length == 0)
        {
            return;
        }
        String fileInfo = new String(data, StandardCharsets.UTF_16LE);
        String[] lines = fileInfo.split("\r\n");
        Map<String, String> stringMapOfValues = new HashMap<>();
        for (String line : lines)
        {
            line = line.replace("\uFEFF", "").replaceAll("\\\\", "/");
            String pathAndName = line.split(":", 2)[0];
            stringMapOfValues.put(pathAndName, line);
        }
        for (String line : stringMapOfValues.values())
        {
            String  pathAndName = line.split(":", 2)[0];
            int     typeOfFile  = Integer.parseInt(line.substring(line.length() - 1));
            boolean typeAllowsSeparation = (typeOfFile == FileTypeByLink.SEPARATED.ordinal() || typeOfFile == FileTypeByLink.UNK_04.ordinal());
            int     digitRunLength = trailingDigitRunLength(pathAndName);
            // A '.zNN' suffix names one volume of a spanned archive whatever the type field says. Trusting the type
            // alone lost every spanned set typed as something else (a DELTA_FILE '.dlt' split into '.z01'/'.z02'):
            // the volumes were taken for standalone files, collapsed onto one name, and a lone slice reached the
            // decoder. The volume suffix is checked as well, so such a set is assembled instead of being torn apart.
            // RU: Суффикс '.zNN' обозначает том многотомного архива независимо от того, что стоит в поле типа.
            // Доверие одному лишь типу теряло любой многотомный набор с другим типом (например DELTA_FILE '.dlt',
            // разбитый на '.z01'/'.z02'): тома принимались за самостоятельные файлы, сводились к одному имени, и до
            // распаковщика доходил одинокий кусок. Теперь проверяется и суффикс тома, поэтому набор склеивается.
            boolean isSeparated = digitRunLength >= 2 && (typeAllowsSeparation || isCompressedEntry(pathAndName));
            String  nameOfPart  = isSeparated ? pathAndName.substring(0, pathAndName.length() - digitRunLength) + "%02d" : pathAndName;
            int     countOfSeparatedFiles;
            if (isSeparated)
            {
                int part = Integer.parseInt(pathAndName.substring(pathAndName.length() - digitRunLength));
                if (part > 1)
                {
                    continue;
                }
                countOfSeparatedFiles = getCountOfSeparatedFiles(stringMapOfValues, nameOfPart);
            }
            else
            {
                countOfSeparatedFiles = 0;
            }

            FileInfoHolder fileInfoHolder = parseFileInfoFromLine(line, true, false, countOfSeparatedFiles);
            if (countOfSeparatedFiles > 0)
            {
                // Fill each split part by its 1-based index (%02d) by looking the part line up in the map
                for (int sIndex = 0; sIndex < countOfSeparatedFiles; sIndex++)
                {
                    String lookingInfo = stringMapOfValues.get(String.format(nameOfPart, (sIndex + 1)));
                    fileInfoHolder.setSeparatedPart(sIndex, parseFileInfoFromLine(lookingInfo, false, true, 0));
                }
            }
            putUnique((fileInfoHolder.getLinkPath()).toLowerCase(), fileInfoHolder);
        }
    }

    /**
     * EN: Stores a holder under its output path, resolving the case where two CDN entries produce the SAME final
     *     file. The list ships some files twice — once stored as-is and once compressed (a bare {@code .torrent}
     *     next to its {@code .torrent.zip}) — and both restore to one name. The compressed entry wins: it is the
     *     smaller transfer and decodes to exactly the same bytes. Previously the loser silently overwrote the
     *     winner, and because the list is iterated in hash order the survivor was not even predictable. <br>
     * RU: Кладёт holder по его итоговому пути, разбирая случай, когда две записи CDN дают ОДИН И ТОТ ЖЕ конечный
     *     файл. Часть файлов в списке идёт дважды — как есть и в сжатом виде (голый {@code .torrent} рядом со своим
     *     {@code .torrent.zip}), — и оба восстанавливаются в одно имя. Побеждает сжатая запись: её меньше качать, а
     *     распаковывается она в те же самые байты. Раньше проигравший молча затирал победителя, а так как список
     *     обходится в порядке хеш-таблицы, то и предсказать выжившего было нельзя. <br>
     * ==================================================================<br>
     * EN: @param mapKey the lower-cased output path / RU: @param mapKey итоговый путь в нижнем регистре <br>
     * EN: @param candidate the freshly parsed holder / RU: @param candidate только что разобранный holder <br>
     **/
    private void putUnique(String mapKey, FileInfoHolder candidate)
    {
        FileInfoHolder previous = _fileMapHolder.get(mapKey);
        if (previous != null)
        {
            boolean previousCompressed = previous.getCompressType() != ArchiveType.NONE;
            boolean candidateCompressed = candidate.getCompressType() != ArchiveType.NONE;
            FileInfoHolder keep = (!previousCompressed && candidateCompressed) ? candidate : previous;
            IDummyLogger.log(IDummyLogger.WARNING, "Two file-list entries resolve to the same file '" + mapKey + "'; keeping the " + (keep.getCompressType() != ArchiveType.NONE ? "compressed" : "stored-as-is") + " one.");
            if (keep == previous)
            {
                return;
            }
        }
        _fileMapHolder.put(mapKey, candidate);
    }

    private static int trailingDigitRunLength(String value)
    {
        int index = value.length();
        while (index > 0 && Character.isDigit(value.charAt(index - 1)))
        {
            index--;
        }
        return value.length() - index;
    }

    private static int getCountOfSeparatedFiles(Map<String, String> stringMapOfValues, String nameOfPart)
    {
        int separateCounter = 0;
        while (true)
        {
            String checkPart = String.format(nameOfPart, ((separateCounter) + 1));
            if (!stringMapOfValues.containsKey(checkPart))
            {
                return separateCounter;
            }
            separateCounter += 1;
        }
    }

    private FileInfoHolder parseFileInfoFromLine(String line, boolean original, boolean isSeparated, int countOfSeparatedParts)
    {
        String[] splitLineInfo = line.split(":", 5);
        if (splitLineInfo.length != 4)
        {
            throw new IllegalArgumentException("[path]:[size]:[sha-1 hash]:[file_type]. Structure is not full!");
        }
        String  pathUndName = splitLineInfo[0];
        String  fileLength  = splitLineInfo[1];
        String  hashSum     = splitLineInfo[2];

        String filePath = getPathOfFile(pathUndName);
        String fileName = getNameOfFile(pathUndName, !original);

        // A split part is a raw slice of the archive: it is concatenated first and only the assembled whole is
        // decoded, so a part itself is never an archive. An original is decoded only when the CDN actually stores
        // it compressed - entries such as a bare '.torrent' are already final and must be kept byte-for-byte.
        // RU: Часть разделённого файла - это сырой кусок архива: части сначала склеиваются, и распаковывается уже
        // собранное целое, поэтому сама часть архивом не является. Оригинал распаковывается лишь тогда, когда CDN
        // и правда хранит его сжатым: записи вроде голого '.torrent' уже готовы и должны сохраняться байт в байт.
        ArchiveType compressType = (original && isCompressedEntry(pathUndName)) ? ArchiveType.LZMA_ARCHIVE : ArchiveType.NONE;

        FileInfoHolder fileInfoHolder = new FileInfoHolder(fileName, filePath, compressType, isSeparated, countOfSeparatedParts);
        if ((original && countOfSeparatedParts == 0) || isSeparated)
        {
            fileInfoHolder.setAccessLink(new LinkInfoHolder(fileInfoHolder));
            fileInfoHolder.getAccessLink().setAccessLink(formatGetUrl(pathUndName));
        }
        fileInfoHolder.setDownloadDataLength(Long.parseLong(fileLength));
        fileInfoHolder.setDownloadDataHashSum(hashSum);

        return fileInfoHolder;
    }

    /**
     * EN: Parses the UTF-16LE hash-list text and fills the final (decompressed) length + hash-sum on the
     *     already-parsed file holders. <br>
     * RU: Разбирает UTF-16LE текст списка хешей и заполняет финальную (распакованную) длину + хеш-сумму на
     *     уже разобранных holder'ах файлов. <br>
     * ==================================================================<br>
     * EN: @param data the downloaded hash-list bytes / RU: @param data скачанные байты списка хешей <br>
     **/
    protected void parseHashList(byte[] data)
    {
        if (data == null || data.length == 0)
        {
            return;
        }

        String hashInfo = new String(data, StandardCharsets.UTF_16LE);
        String[] lines = hashInfo.split("\r\n");

        for (String line : lines)
        {
            String[] splitLineInfo = line.replace("\uFEFF", "").replaceAll("\\\\", "/").split(":");

            String pathAndName = splitLineInfo[0].toLowerCase();

            FileInfoHolder fileInfo = _fileMapHolder.getOrDefault(pathAndName, null);
            if (fileInfo == null)
            {
                continue;
            }

            String  fileLength  = splitLineInfo[1];
            String  hashSum     = splitLineInfo[2];

            fileInfo.setFileLength(Long.parseLong(fileLength));
            fileInfo.setFileHashSum(hashSum);
        }
    }

    /**
     * EN: Returns the last path segment. With {@code ignoreExtension} the raw CDN name is returned as-is (that is
     *     what the download URL must use); otherwise the archive suffix is stripped to get the final on-disk name
     *     ({@code L2.bin.dlt.z01} -> {@code L2.bin.dlt}, {@code map.unr.zip} -> {@code map.unr}). A name that
     *     carries no archive suffix is returned untouched. <br>
     * RU: Возвращает последний сегмент пути. С {@code ignoreExtension} отдаётся исходное имя с CDN как есть (именно
     *     оно нужно для ссылки на скачивание); иначе отбрасывается архивный суффикс, чтобы получить итоговое имя на
     *     диске ({@code L2.bin.dlt.z01} -> {@code L2.bin.dlt}, {@code map.unr.zip} -> {@code map.unr}). Имя без
     *     архивного суффикса возвращается без изменений. <br>
     * ==================================================================<br>
     * EN: @param pathAndName the raw file-list path / RU: @param pathAndName исходный путь из списка файлов <br>
     * EN: @param ignoreExtension keep the raw name / RU: @param ignoreExtension оставить исходное имя <br>
     * @return <br>
     *         {String} - EN: the file name / RU: имя файла <br>
     **/
    private String getNameOfFile(String pathAndName, boolean ignoreExtension)
    {
        String[] splitPathByFolders
                = pathAndName.split("/");
        String nameOfFile
                = splitPathByFolders[splitPathByFolders.length - 1];
        if (!ignoreExtension)
        {
            nameOfFile = ARCHIVE_SUFFIX.matcher(nameOfFile).replaceFirst("");
        }
        return nameOfFile;
    }

    /**
     * EN: Tells whether the CDN entry is a compressed payload (a {@code .zip}, or a {@code .zNN} volume that the
     *     parts are concatenated into) rather than an already-final file stored as-is. The file-list type field
     *     cannot answer this: a raw {@code .torrent} and its {@code .torrent.zip} counterpart both carry type 0,
     *     so the suffix is the only reliable signal. <br>
     * RU: Сообщает, является ли запись на CDN сжатым содержимым ({@code .zip} либо том {@code .zNN}, из которых
     *     склеивается архив), а не уже готовым файлом, который лежит как есть. Поле типа из списка файлов ответа не
     *     даёт: у сырого {@code .torrent} и у его пары {@code .torrent.zip} тип одинаковый (0), поэтому суффикс —
     *     единственный надёжный признак. <br>
     * ==================================================================<br>
     * EN: @param pathAndName the raw file-list path / RU: @param pathAndName исходный путь из списка файлов <br>
     * @return <br>
     *         {boolean} - EN: true when the entry needs decompressing / RU: true, когда запись нужно распаковывать <br>
     **/
    private static boolean isCompressedEntry(String pathAndName)
    {
        return ARCHIVE_SUFFIX.matcher(pathAndName).find();
    }

    private String getPathOfFile(String pathAndName)
    {
        String[] splitPathByFolders
                = pathAndName.split("/");

        if (splitPathByFolders.length <= 1)
        {
            return "";
        }

        StringBuilder returnName = new StringBuilder();

        boolean isZipHeader = splitPathByFolders[0].length() == 3 && splitPathByFolders[0].equalsIgnoreCase("zip");
        boolean isPatchVersionHeader = splitPathByFolders[0].equalsIgnoreCase(String.valueOf(getPatchVersion(pathAndName)));

        if (!(isZipHeader || isPatchVersionHeader))
        {
            returnName.append(splitPathByFolders[0]).append("/");
        }

        if (splitPathByFolders.length - 1 <= 0)
        {
            return returnName.toString();
        }

        for (int index = 1; index < splitPathByFolders.length - 1; index++)
        {
            returnName.append(splitPathByFolders[index]).append("/");
        }
        return returnName.toString();
    }

    // input 528\system\LineageMonster12.u.dlt.zip
    private int getPatchVersion(String pathUndName)
    {
        String[] splitThePath = pathUndName.split("/", 2);
        for (char digitCheck : splitThePath[0].toCharArray())
        {
            if (!Character.isDigit(digitCheck))
            {
                return _patchVersion;
            }
        }
        return Integer.parseInt(splitThePath[0]);
    }

    private String formatGetUrl(String pathAndName)
    {
        String path = getPathOfFile(pathAndName);
        String name = getNameOfFile(pathAndName, true);
        int patchVer= getPatchVersion(pathAndName);
        if (path.isEmpty())
        {
            return String.format(_cdnLinkType.getGeneralCdnLink(), _patchVersion, pathAndName);
        }
        if (patchVer != _patchVersion)
        {
            return String.format(_cdnLinkType.getGeneralCdnLink(), _patchVersion, (patchVer + "/" + path + name));
        }
        return String.format(_cdnLinkType.getGeneralCdnLink(), patchVer, (pathAndName));
    }
}
