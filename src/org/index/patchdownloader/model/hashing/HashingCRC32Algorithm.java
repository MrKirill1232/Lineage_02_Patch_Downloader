package org.index.patchdownloader.model.hashing;

import org.index.patchdownloader.enums.HashType;
import org.index.patchdownloader.interfaces.IHashingAlgorithm;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Future;
import java.util.zip.CRC32;

public class HashingCRC32Algorithm implements IHashingAlgorithm
{
    private final CRC32 _crc32Algorithm;

    public HashingCRC32Algorithm()
    {
        _crc32Algorithm = new CRC32();
    }

    @Override
    public String calculateHash(byte[] byteArray)
    {
        _crc32Algorithm.reset();
        _crc32Algorithm.update(byteArray);
        return IHashingAlgorithm.longToHex(_crc32Algorithm.getValue());
    }

    @Override
    public String calculateHash(File file)
    {
        _crc32Algorithm.reset();
        ByteBuffer buffer = ByteBuffer.allocateDirect(131070);
        try (AsynchronousFileChannel fc = AsynchronousFileChannel.open(file.toPath(), StandardOpenOption.READ))
        {
            long position = 0;
            Future<Integer> future;

            while ((future = fc.read(buffer, position)).get() != -1)
            {
                buffer.flip();
                _crc32Algorithm.update(buffer);
                buffer.clear();
                position += future.get();
            }
        }
        catch (Exception ignored)
        {
            // e.printStackTrace();
        }
        finally
        {
            buffer.clear();
        }
        return IHashingAlgorithm.longToHex(_crc32Algorithm.getValue());
    }

    @Override
    public HashType getHashingAlgorithm()
    {
        return HashType.CRC32;
    }

    @Override
    public IHashingAlgorithm getNewInstance()
    {
        return new HashingCRC32Algorithm();
    }
}
