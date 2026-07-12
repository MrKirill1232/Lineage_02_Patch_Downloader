package org.index.patchdownloader.model.akumu;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * EN: Minimal, self-contained bencode decoder (the encoding used by {@code .torrent} files). Decodes the
 *     four bencode types into plain Java values: integer -&gt; {@link Long}, byte string -&gt; {@code byte[]},
 *     list -&gt; {@link List}, dictionary -&gt; {@link Map} (insertion-ordered, keys decoded as UTF-8). Only
 *     the decoding side is implemented — the tool never writes torrents. Byte strings are kept raw so binary
 *     fields (e.g. {@code pieces}) survive; callers decode text fields themselves.<br>
 * RU: Небольшой самодостаточный декодер bencode (кодировка {@code .torrent}-файлов). Декодирует четыре типа
 *     bencode в обычные Java-значения: целое -&gt; {@link Long}, байтовая строка -&gt; {@code byte[]},
 *     список -&gt; {@link List}, словарь -&gt; {@link Map} (в порядке вставки, ключи как UTF-8). Реализовано
 *     только декодирование — инструмент никогда не пишет торренты. Байтовые строки хранятся сырыми, чтобы
 *     бинарные поля (напр. {@code pieces}) не портились; текстовые поля вызывающий декодирует сам.<br>
 **/
public final class BencodeReader
{
    private static final int MAX_NESTING_DEPTH = 64;

    private final byte[] _data;
    private int _pos;
    private int _depth;

    private BencodeReader(byte[] data)
    {
        _data = data;
        _pos = 0;
        _depth = 0;
    }

    /**
     * EN: Decodes a single top-level bencode value from the given bytes. <br>
     * RU: Декодирует одно значение bencode верхнего уровня из переданных байтов. <br>
     * ==================================================================<br>
     * EN: @param data the bencoded bytes / RU: @param data байты в кодировке bencode <br>
     * @return <br>
     *         {Object} - EN: Long / byte[] / List / Map / RU: Long / byte[] / List / Map <br>
     **/
    public static Object decode(byte[] data)
    {
        if (data == null || data.length == 0)
        {
            throw new IllegalArgumentException("Empty bencode data.");
        }
        return new BencodeReader(data).readValue();
    }

    private Object readValue()
    {
        byte token = peek();
        if (token == 'i')
        {
            return readInteger();
        }
        if (token == 'l')
        {
            return readList();
        }
        if (token == 'd')
        {
            return readDictionary();
        }
        if (token >= '0' && token <= '9')
        {
            return readByteString();
        }
        throw new IllegalStateException("Unexpected bencode token '" + (char) token + "' at offset " + _pos + ".");
    }

    private Long readInteger()
    {
        expect('i');
        int end = indexOf((byte) 'e');
        long value = Long.parseLong(new String(_data, _pos, end - _pos, StandardCharsets.US_ASCII));
        _pos = end + 1;
        return value;
    }

    private byte[] readByteString()
    {
        int colon = indexOf((byte) ':');
        int length = Integer.parseInt(new String(_data, _pos, colon - _pos, StandardCharsets.US_ASCII));
        // (long) so a huge declared length cannot overflow the sum into a negative that slips past the guard
        // (which would then reach new byte[length] and run out of memory (OOM) on a corrupt torrent).
        if (length < 0 || (long) colon + 1 + length > _data.length)
        {
            throw new IllegalStateException("Byte-string length " + length + " out of bounds at offset " + _pos + ".");
        }
        byte[] out = new byte[length];
        System.arraycopy(_data, colon + 1, out, 0, length);
        _pos = colon + 1 + length;
        return out;
    }

    private List<Object> readList()
    {
        enterContainer();
        expect('l');
        List<Object> list = new ArrayList<>();
        while (peek() != 'e')
        {
            list.add(readValue());
        }
        _pos += 1;
        _depth -= 1;
        return list;
    }

    private Map<String, Object> readDictionary()
    {
        enterContainer();
        expect('d');
        Map<String, Object> map = new LinkedHashMap<>();
        while (peek() != 'e')
        {
            String key = new String(readByteString(), StandardCharsets.UTF_8);
            map.put(key, readValue());
        }
        _pos += 1;
        _depth -= 1;
        return map;
    }

    /**
     * EN: Guards against unbounded recursion: a hostile torrent made of tens of thousands of nested
     *     containers ({@code l}/{@code d}) would otherwise recurse until a {@link StackOverflowError},
     *     an {@link Error} that slips past the caller's {@code catch (Exception)} malformed-torrent handling.
     *     Real torrents nest only a few levels deep. <br>
     * RU: Защита от неограниченной рекурсии: враждебный торрент из десятков тысяч вложенных контейнеров
     *     ({@code l}/{@code d}) иначе рекурсировал бы до {@link StackOverflowError} — это {@link Error},
     *     который проскочил бы мимо обработки испорченного торрента через {@code catch (Exception)} у
     *     вызывающего. Настоящие торренты вкладываются всего на несколько уровней. <br>
     **/
    private void enterContainer()
    {
        _depth += 1;
        if (_depth > MAX_NESTING_DEPTH)
        {
            throw new IllegalStateException("Bencode nesting depth exceeded " + MAX_NESTING_DEPTH + " at offset " + _pos + ".");
        }
    }

    private byte peek()
    {
        if (_pos >= _data.length)
        {
            throw new IllegalStateException("Unexpected end of bencode data at offset " + _pos + ".");
        }
        return _data[_pos];
    }

    private void expect(char token)
    {
        if (peek() != token)
        {
            throw new IllegalStateException("Expected '" + token + "' but found '" + (char) _data[_pos] + "' at offset " + _pos + ".");
        }
        _pos += 1;
    }

    private int indexOf(byte target)
    {
        for (int index = _pos; index < _data.length; index++)
        {
            if (_data[index] == target)
            {
                return index;
            }
        }
        throw new IllegalStateException("Expected '" + (char) target + "' after offset " + _pos + " but reached end of data.");
    }
}
