package org.index.patchdownloader.instancemanager;

import org.index.patchdownloader.enums.HashType;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.CRC32;

public class CheckSumManager
{
    private final static MessageDigest  SHA_MD_ALGORITHM;
    private final static CRC32          CRC_32_ALGORITHM;
    static
    {
        MessageDigest md = null;
        try
        {
            md = MessageDigest.getInstance("SHA-1");
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new RuntimeException(e);
        }
        SHA_MD_ALGORITHM = md;

        CRC_32_ALGORITHM = new CRC32();
    }

    private CheckSumManager()
    {

    }

    public static boolean check(HashType hashType, byte[] inputArray, String checksum)
    {
        return (hashType == HashType.SHA1 ? getHashSha01OfFile(inputArray) : getHashCrc32OfFile(inputArray)).equals(checksum);
    }

    public static String getHashCrc32OfFile(byte[] inputArray)
    {
        CRC_32_ALGORITHM.reset();
        CRC_32_ALGORITHM.update(inputArray);
        return String.format("%08x", CRC_32_ALGORITHM.getValue());
    }

    public static String getHashSha01OfFile(byte[] inputArray)
    {
        SHA_MD_ALGORITHM.reset();
        SHA_MD_ALGORITHM.update(inputArray);
        return binaryToHex(SHA_MD_ALGORITHM.digest());
    }

    public static String binaryToHex(byte[] data)
    {
        StringBuilder r = new StringBuilder(data.length * 2);
        for (byte b : data)
        {
            r.append(String.format("%02x", b));
        }
        return r.toString();
    }
}
