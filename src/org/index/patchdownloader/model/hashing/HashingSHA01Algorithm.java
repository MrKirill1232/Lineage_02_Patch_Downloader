package org.index.patchdownloader.model.hashing;

import org.index.patchdownloader.enums.HashType;
import org.index.patchdownloader.interfaces.IDummyLogger;
import org.index.patchdownloader.interfaces.IHashingAlgorithm;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashingSHA01Algorithm implements IHashingAlgorithm
{
    private static final int READ_BUFFER = 1 << 16;

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

    /**
     * EN: Streams the whole file through the SHA-1 (Secure-Hash-Algorithm 1) digest in 64&nbsp;KB chunks with a
     *     plain synchronous {@link FileChannel} and returns the digest as a lower-case hex string. On a read
     *     failure the error is logged and {@code null} is returned (per the {@link IHashingAlgorithm} contract)
     *     so the caller never receives the hash of a partial or empty read as if it were valid. If the reading
     *     thread is interrupted the channel closes with {@link ClosedByInterruptException}; the interrupt flag is
     *     restored and {@code null} is returned. <br>
     * RU: Пропускает файл целиком через дайджест SHA-1 (Secure-Hash-Algorithm 1) блоками по 64&nbsp;КБ обычным
     *     синхронным {@link FileChannel} и возвращает дайджест строкой в нижнем регистре в шестнадцатеричном
     *     виде. При ошибке чтения ошибка пишется в лог и возвращается {@code null} (по контракту
     *     {@link IHashingAlgorithm}), чтобы вызывающий код не принял хеш неполного или пустого чтения за
     *     корректный. Если поток чтения прерван, канал закрывается с {@link ClosedByInterruptException}; флаг
     *     прерывания восстанавливается и возвращается {@code null}. <br>
     * ==================================================================<br>
     * @param file EN: the file to hash / RU: файл для хеширования <br>
     * @return <br>
     *         {String} - EN: lower-case hex SHA-1 of the file, or null on a read failure / RU: SHA-1 файла (hex, нижний регистр) или null при сбое чтения <br>
     */
    @Override
    public String calculateHash(File file)
    {
        _sha01Algorithm.reset();
        ByteBuffer buffer = ByteBuffer.allocateDirect(READ_BUFFER);
        try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ))
        {
            while (channel.read(buffer) != -1)
            {
                buffer.flip();
                _sha01Algorithm.update(buffer);
                buffer.clear();
            }
        }
        catch (ClosedByInterruptException e)
        {
            Thread.currentThread().interrupt();
            IDummyLogger.log(IDummyLogger.ERROR, "Interrupted while hashing file '" + file + "': " + e);
            return null;
        }
        catch (IOException e)
        {
            IDummyLogger.log(IDummyLogger.ERROR, "Failed to hash file '" + file + "': " + e);
            return null;
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
