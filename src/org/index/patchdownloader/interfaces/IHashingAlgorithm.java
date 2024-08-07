package org.index.patchdownloader.interfaces;

import org.index.patchdownloader.enums.HashType;

import java.io.File;

public interface IHashingAlgorithm
{
    String calculateHash(byte[] byteArray);

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
