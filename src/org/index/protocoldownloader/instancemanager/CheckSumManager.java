package org.index.protocoldownloader.instancemanager;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class CheckSumManager
{
    private final static MessageDigest SHA_MD_ALGORITHM;
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
    }

    private CheckSumManager()
    {

    }

    public static boolean check(byte[] inputArray, String checksum)
    {
        return getHashOfFile(inputArray).equals(checksum);
    }

    public static String getHashOfFile(byte[] inputArray)
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
