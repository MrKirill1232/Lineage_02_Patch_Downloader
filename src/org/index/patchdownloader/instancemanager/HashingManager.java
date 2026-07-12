package org.index.patchdownloader.instancemanager;

import org.index.patchdownloader.enums.HashType;
import org.index.patchdownloader.interfaces.IHashingAlgorithm;
import org.index.patchdownloader.model.hashing.HashingCRC32Algorithm;
import org.index.patchdownloader.model.hashing.HashingSHA01Algorithm;

public class HashingManager
{
    private final static IHashingAlgorithm[] AVAILABLE_HASHING_CLASSES;
    static
    {
        AVAILABLE_HASHING_CLASSES = new IHashingAlgorithm[HashType.values().length];
        AVAILABLE_HASHING_CLASSES[HashType.SHA01.ordinal()] = new HashingSHA01Algorithm();
        AVAILABLE_HASHING_CLASSES[HashType.CRC32.ordinal()] = new HashingCRC32Algorithm();
    }

    private HashingManager()
    {

    }

    /**
     * EN: Checks the given bytes against an expected checksum using a FRESH algorithm instance, so the
     *     call is thread-safe on the concurrent decompress/condition paths (no shared stateful digest). <br>
     * RU: Проверяет данные по ожидаемой контрольной сумме, используя СВЕЖИЙ экземпляр алгоритма, поэтому
     *     вызов потокобезопасен на параллельных путях decompress/condition (без общего stateful-дайджеста). <br>
     * ==================================================================<br>
     * EN: @param hashType the hash algorithm / RU: @param hashType алгоритм хеша <br>
     * EN: @param inputArray the bytes to hash / RU: @param inputArray байты для хеширования <br>
     * EN: @param checksum the expected checksum / RU: @param checksum ожидаемая контрольная сумма <br>
     * @return <br>
     *         {true}  - EN: hash matches / RU: хеш совпадает <br>
     *         {false} - EN: mismatch or unknown algorithm / RU: несовпадение или неизвестный алгоритм <br>
     **/
    public static boolean check(HashType hashType, byte[] inputArray, String checksum)
    {
        IHashingAlgorithm hashingAlgorithm = getAvailableHashingAlgorithm(hashType, true);
        // Case-insensitive: computed hashes are lower-case hex, but the expected checksum comes from an external
        // file list (CDN / UpNova) that may use upper-case — a case-only difference is NOT a real mismatch.
        return hashingAlgorithm != null && hashingAlgorithm.calculateHash(inputArray).equalsIgnoreCase(checksum);
    }

    public static IHashingAlgorithm getAvailableHashingAlgorithm(HashType hashType, boolean newInstance)
    {
        return hashType == null ? null : (newInstance ? AVAILABLE_HASHING_CLASSES[hashType.ordinal()].getNewInstance() : AVAILABLE_HASHING_CLASSES[hashType.ordinal()]);
    }
}
