package org.index.patchdownloader.interfaces;

import org.index.patchdownloader.enums.HashType;

import java.io.File;

public interface IHashingAlgorithm
{
    /**
     * EN: Hashes an in-memory byte array and returns the hash as a lower-case hex string. This overload does no
     *     I/O, so it never returns {@code null}. Implementations are stateful — use a fresh instance per thread. <br>
     * RU: Хеширует массив байтов в памяти и возвращает хеш строкой в нижнем регистре в шестнадцатеричном виде.
     *     Эта перегрузка не делает ввод-вывод, поэтому никогда не возвращает {@code null}. Реализации stateful —
     *     берите свежий экземпляр на поток. <br>
     * ==================================================================<br>
     * EN: @param byteArray the bytes to hash / RU: @param byteArray байты для хеширования <br>
     * @return <br>
     *         {String} - EN: lower-case hex hash (never null) / RU: хеш в нижнем регистре (hex), никогда не null <br>
     **/
    String calculateHash(byte[] byteArray);

    /**
     * EN: Hashes the WHOLE file and returns the hash as a lower-case hex string, or {@code null} when the file
     *     cannot be read to the end (I/O error or interruption). Callers MUST treat {@code null} as "no hash
     *     available" — i.e. a mismatch / re-download — never as a value, and never accept the hash of a partial
     *     read. Implementations are stateful — use a fresh instance per thread. <br>
     * RU: Хеширует ФАЙЛ ЦЕЛИКОМ и возвращает хеш строкой в нижнем регистре в шестнадцатеричном виде, либо
     *     {@code null}, если файл не удалось дочитать до конца (ошибка ввода-вывода или прерывание). Вызывающий
     *     код ОБЯЗАН трактовать {@code null} как «хеша нет» — то есть несовпадение / перекачать — а не как
     *     значение, и никогда не принимать хеш неполного чтения. Реализации stateful — берите свежий экземпляр на
     *     поток. <br>
     * ==================================================================<br>
     * EN: @param file the file to hash / RU: @param file файл для хеширования <br>
     * @return <br>
     *         {String} - EN: lower-case hex hash, or null on a read failure / RU: хеш в нижнем регистре (hex) или null при сбое чтения <br>
     **/
    String calculateHash(File file);

    HashType getHashingAlgorithm();

    IHashingAlgorithm getNewInstance();

    static String longToHex(long data)
    {
        return String.format("%08x", data);
    }

    static String binaryToHex(byte[] data)
    {
        StringBuilder r = new StringBuilder(data.length * 2);
        for (byte b : data)
        {
            r.append(String.format("%02x", b));
        }
        return r.toString();
    }
}
