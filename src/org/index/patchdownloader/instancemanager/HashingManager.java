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

    public static boolean check(HashType hashType, byte[] inputArray, String checksum)
    {
        IHashingAlgorithm hashingAlgorithm = getAvailableHashingAlgorithm(hashType, false);
        return hashingAlgorithm != null && hashingAlgorithm.calculateHash(inputArray).equals(checksum);
    }

    public static IHashingAlgorithm getAvailableHashingAlgorithm(HashType hashType, boolean newInstance)
    {
        return hashType == null ? null : (newInstance ? AVAILABLE_HASHING_CLASSES[hashType.ordinal()].getNewInstance() : AVAILABLE_HASHING_CLASSES[hashType.ordinal()]);
    }
}
