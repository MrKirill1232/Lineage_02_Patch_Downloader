package org.index.patchdownloader.model.hashing;

import org.index.patchdownloader.enums.HashType;
import org.index.patchdownloader.interfaces.IHashingAlgorithm;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Future;

public class HashingSHA01Algorithm implements IHashingAlgorithm
{
    private final MessageDigest _sha01Algorithm;

    public HashingSHA01Algorithm()
    {
        try
        {
            _sha01Algorithm = MessageDigest.getInstance("SHA-1");
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String calculateHash(byte[] byteArray)
    {
        _sha01Algorithm.reset();
        _sha01Algorithm.update(byteArray);
        return IHashingAlgorithm.binaryToHex(_sha01Algorithm.digest());
    }

    @Override
    public String calculateHash(File file)
    {
        _sha01Algorithm.reset();
        ByteBuffer buffer = ByteBuffer.allocateDirect(131070);
        try (AsynchronousFileChannel fc = AsynchronousFileChannel.open(file.toPath(), StandardOpenOption.READ))
        {
            long position = 0;
            Future<Integer> future;

            while ((future = fc.read(buffer, position)).get() != -1)
            {
                buffer.flip();
                _sha01Algorithm.update(buffer);
                buffer.clear();
                position += future.get();
            }
        }
        catch (Exception ignored)
        {

        }
        finally
        {
            buffer.clear();
        }
        return IHashingAlgorithm.binaryToHex(_sha01Algorithm.digest());
    }

    @Override
    public HashType getHashingAlgorithm()
    {
        return HashType.SHA01;
    }

    @Override
    public IHashingAlgorithm getNewInstance()
    {
        return new HashingSHA01Algorithm();
    }
}
