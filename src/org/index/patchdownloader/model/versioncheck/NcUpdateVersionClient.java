package org.index.patchdownloader.model.versioncheck;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * EN: Live version-check over the NC "Purple" launcher update protocol (a small binary frame over plain
 *     {@code TCP:27500}, not HTTP/TLS). A single {@code opcode 6} request for a service id (e.g. {@code L2_JP})
 *     returns that service's CURRENT patch version (protobuf field 4) and its content SHA-1 hash (field 10).
 *     Each host serves exactly ONE service, so the (host, service) pair is region-specific — see {@code CDNLink}.
 *     Stateless: one request, one short-lived socket, no retries. <br>
 * RU: Живой запрос версии по update-протоколу лаунчера NC «Purple» (небольшой бинарный кадр поверх обычного
 *     {@code TCP:27500}, не HTTP/TLS). Один запрос {@code opcode 6} для идентификатора сервиса (напр. {@code L2_JP})
 *     возвращает ТЕКУЩУЮ версию патча этого сервиса (поле protobuf 4) и SHA-1 хеш его контента (поле 10). Каждый
 *     хост обслуживает ровно ОДИН сервис, поэтому пара (хост, сервис) зависит от региона — см. {@code CDNLink}.
 *     Без состояния: один запрос, один короткоживущий сокет, без повторов. <br>
 **/
public final class NcUpdateVersionClient
{
    private static final int OPCODE_VERSION = 6;
    private static final int FIELD_VERSION = 4;
    private static final int FIELD_HASH = 10;

    /**
     * EN: The resolved version-check answer: the numeric patch version and the version's content SHA-1 hash
     *     (may be {@code null} if the response omitted it). <br>
     * RU: Разрешённый ответ проверки версии: числовая версия патча и SHA-1 хеш контента версии (может быть
     *     {@code null}, если ответ его не содержал). <br>
     **/
    public record VersionInfo(int version, String hash)
    {
    }

    private NcUpdateVersionClient()
    {
    }

    /**
     * EN: Opens a short-lived socket to {@code host:port}, sends one {@code opcode 6} request for {@code service}
     *     and parses the reply into a {@link VersionInfo}. The socket connect and read both honour
     *     {@code timeoutMs}. The caller owns nothing after the call — the socket is closed here. <br>
     * RU: Открывает короткоживущий сокет к {@code host:port}, посылает один запрос {@code opcode 6} для
     *     {@code service} и разбирает ответ в {@link VersionInfo}. Таймаут {@code timeoutMs} действует и на
     *     подключение, и на чтение. После вызова у вызывающего ничего нет — сокет закрывается здесь. <br>
     * ==================================================================<br>
     * EN: @param host the update server host (TCP:27500) / RU: @param host хост update-сервера (TCP:27500) <br>
     * EN: @param port the update server port (27500) / RU: @param port порт update-сервера (27500) <br>
     * EN: @param service the service id, e.g. {@code L2_JP} / RU: @param service идентификатор сервиса, напр. {@code L2_JP} <br>
     * EN: @param timeoutMs connect/read timeout in milliseconds / RU: @param timeoutMs таймаут подключения/чтения в миллисекундах <br>
     * @return <br>
     *         {VersionInfo} - EN: the current version and content hash / RU: текущая версия и хеш контента <br>
     **/
    public static VersionInfo query(String host, int port, String service, int timeoutMs) throws Exception
    {
        byte[] frame = buildRequest(service, OPCODE_VERSION);
        byte[] response;
        try (Socket socket = new Socket())
        {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            out.write(frame);
            out.flush();
            response = readAll(socket.getInputStream());
        }
        return parseVersion(response);
    }

    /**
     * EN: Builds the request frame: a 4-byte header ({@code frame_len} LE16, {@code opcode} LE16) followed by a
     *     protobuf payload of one length-delimited field 1 carrying the ASCII service id. {@code frame_len}
     *     counts itself. <br>
     * RU: Строит кадр запроса: 4-байтовый заголовок ({@code frame_len} LE16, {@code opcode} LE16), затем
     *     protobuf-нагрузка из одного length-delimited поля 1 с ASCII-идентификатором сервиса. {@code frame_len}
     *     учитывает сам себя. <br>
     * ==================================================================<br>
     * EN: @param service the service id / RU: @param service идентификатор сервиса <br>
     * EN: @param opcode the request opcode / RU: @param opcode опкод запроса <br>
     * @return <br>
     *         {byte[]} - EN: the wire request frame / RU: кадр запроса для отправки <br>
     **/
    private static byte[] buildRequest(String service, int opcode)
    {
        byte[] serviceBytes = service.getBytes(StandardCharsets.US_ASCII);
        int payloadLength = 2 + serviceBytes.length;           // tag(0x0A) + len byte + service bytes
        int frameLength = 4 + payloadLength;
        byte[] frame = new byte[frameLength];
        int index = 0;
        frame[index++] = (byte) (frameLength & 0xFF);
        frame[index++] = (byte) ((frameLength >> 8) & 0xFF);
        frame[index++] = (byte) (opcode & 0xFF);
        frame[index++] = (byte) ((opcode >> 8) & 0xFF);
        frame[index++] = 0x0A;                                 // field 1, wire type 2 (length-delimited)
        frame[index++] = (byte) serviceBytes.length;           // a service id is always short (< 128)
        System.arraycopy(serviceBytes, 0, frame, index, serviceBytes.length);
        return frame;
    }

    /**
     * EN: Reads the whole single response the server sends before falling silent: reads until the stream ends, or
     *     until a brief pause confirms no more bytes are coming, or the read times out (whatever arrived is
     *     returned). <br>
     * RU: Читает единственный ответ, который сервер шлёт до того, как замолчать: читает, пока поток не кончится,
     *     либо пока короткая пауза не подтвердит, что байтов больше нет, либо пока чтение не истечёт по таймауту
     *     (возвращается всё, что успело прийти). <br>
     * ==================================================================<br>
     * EN: @param in the socket input stream / RU: @param in входной поток сокета <br>
     * @return <br>
     *         {byte[]} - EN: the raw response bytes / RU: сырые байты ответа <br>
     **/
    private static byte[] readAll(InputStream in) throws Exception
    {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        try
        {
            int read = in.read(chunk);
            while (read > 0)
            {
                buffer.write(chunk, 0, read);
                if (in.available() == 0)
                {
                    Thread.sleep(50);                          // the server sends one reply, then goes quiet
                    if (in.available() == 0)
                    {
                        break;
                    }
                }
                read = in.read(chunk);
            }
        }
        catch (SocketTimeoutException ignored)
        {
            // fine: whatever arrived before the timeout is already in the buffer
        }
        return buffer.toByteArray();
    }

    /**
     * EN: Parses the protobuf payload of an {@code opcode 6} reply. Skips the frame header by scanning from
     *     offset 4 for the first {@code 0x0A} (field 1's tag — robust to a 4- or 8-byte header), then walks the
     *     fields picking field {@code 4} (version varint) and field {@code 10} (hash string). Throws when the
     *     version field is absent. <br>
     * RU: Разбирает protobuf-нагрузку ответа {@code opcode 6}. Пропускает заголовок кадра, сканируя со смещения 4
     *     первый {@code 0x0A} (тег поля 1 — устойчиво к 4- или 8-байтовому заголовку), затем идёт по полям, беря
     *     поле {@code 4} (версия, varint) и поле {@code 10} (строка хеша). Бросает исключение, если поля версии
     *     нет. <br>
     * ==================================================================<br>
     * EN: @param response the raw response bytes / RU: @param response сырые байты ответа <br>
     * @return <br>
     *         {VersionInfo} - EN: the parsed version and hash / RU: разобранные версия и хеш <br>
     **/
    private static VersionInfo parseVersion(byte[] response)
    {
        int start = -1;
        for (int scan = 4; scan < response.length - 1; scan++)
        {
            if (response[scan] == 0x0A)
            {
                start = scan;
                break;
            }
        }
        if (start < 0)
        {
            throw new IllegalStateException("No protobuf payload in the version-check response.");
        }

        int version = -1;
        String hash = null;
        int index = start;
        while (index < response.length)
        {
            int tag = response[index++] & 0xFF;
            int field = tag >>> 3;
            int wire = tag & 7;
            if (wire == 0)                                     // varint
            {
                long[] value = readVarint(response, index);
                index = (int) value[1];
                if (field == FIELD_VERSION)
                {
                    version = (int) value[0];
                }
            }
            else if (wire == 2)                                // length-delimited
            {
                long[] lengthValue = readVarint(response, index);
                int length = (int) lengthValue[0];
                index = (int) lengthValue[1];
                if (index + length > response.length)
                {
                    break;
                }
                String text = new String(response, index, length, StandardCharsets.US_ASCII);
                index += length;
                if (field == FIELD_HASH)
                {
                    hash = text;
                }
            }
            else                                               // unknown wire type -> stop
            {
                break;
            }
            if (version >= 0 && hash != null)
            {
                break;
            }
        }
        if (version < 0)
        {
            throw new IllegalStateException("Version (field " + FIELD_VERSION + ") not found in the version-check response.");
        }
        return new VersionInfo(version, hash);
    }

    /**
     * EN: Reads a LEB128 varint from {@code data} at {@code offset}. <br>
     * RU: Читает LEB128 varint из {@code data} со смещения {@code offset}. <br>
     * ==================================================================<br>
     * EN: @param data the buffer / RU: @param data буфер <br>
     * EN: @param offset the start offset / RU: @param offset начальное смещение <br>
     * @return <br>
     *         {long[]} - EN: {value, nextOffset} / RU: {значение, следующее смещение} <br>
     **/
    private static long[] readVarint(byte[] data, int offset)
    {
        long value = 0;
        int shift = 0;
        int index = offset;
        while (true)
        {
            int part = data[index++] & 0xFF;
            value |= (long) (part & 0x7F) << shift;
            if ((part & 0x80) == 0)
            {
                break;
            }
            shift += 7;
        }
        return new long[]{value, index};
    }
}
