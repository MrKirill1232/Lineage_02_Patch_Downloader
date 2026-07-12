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
import java.util.zip.CRC32;

/**
 * EN: {@link IHashingAlgorithm} backed by the Cyclic Redundancy Check 32-bit (CRC32) checksum.
 *     A single {@link CRC32} instance is reused, so one object is not safe for concurrent
 *     hashing; obtain a fresh copy via {@link #getNewInstance()} for each thread.
 * <br>
 * RU: Реализация {@link IHashingAlgorithm} на основе 32-битной циклической контрольной суммы (CRC32).
 *     Экземпляр {@link CRC32} переиспользуется, поэтому один объект нельзя применять для
 *     параллельного хеширования; для каждого потока берите новую копию через {@link #getNewInstance()}.
 */
public class HashingCRC32Algorithm implements IHashingAlgorithm
{
    private static final int READ_BUFFER = 1 << 16;

    private final CRC32 _crc32Algorithm;

    public HashingCRC32Algorithm()
    {
        _crc32Algorithm = new CRC32();
    }

    /**
     * EN: Computes the CRC32 checksum of the given byte array and returns it as an
     *     8-character hex string.
     * <br>
     * RU: Вычисляет контрольную сумму CRC32 переданного массива байтов и возвращает её
     *     как 8-символьную шестнадцатеричную строку.
     *
     * @param byteArray EN: data to checksum. <br> RU: данные для подсчёта контрольной суммы.
     * @return {String} EN: hex-encoded CRC32 value.
     *         <br> RU: значение CRC32 в шестнадцатеричном виде.
     */
    @Override
    public String calculateHash(byte[] byteArray)
    {
        _crc32Algorithm.reset();
        _crc32Algorithm.update(byteArray);
        return IHashingAlgorithm.longToHex(_crc32Algorithm.getValue());
    }

    /**
     * EN: Streams the file through CRC32 in 64 KB chunks with a plain synchronous {@link FileChannel}
     *     and returns the checksum as an 8-character hex string. On a read failure the error is
     *     logged and {@code null} is returned instead of a checksum built from partially read data,
     *     so callers can tell a failed read apart from a genuine hash. If the thread is interrupted,
     *     the interrupt flag is restored and {@code null} is returned.
     * <br>
     * RU: Читает файл через CRC32 блоками по 64 КБ обычным синхронным {@link FileChannel}
     *     и возвращает контрольную сумму как 8-символьную шестнадцатеричную строку. При ошибке чтения
     *     ошибка записывается в лог и возвращается {@code null}, а не сумма, посчитанная по неполно
     *     прочитанным данным, чтобы вызывающий код мог отличить сбой чтения от настоящего хеша.
     *     Если поток прерван, флаг прерывания восстанавливается и возвращается {@code null}.
     *
     * @param file EN: file to checksum. <br> RU: файл для подсчёта контрольной суммы.
     * @return {String} EN: hex-encoded CRC32, or {@code null} if the file could not be fully read.
     *         <br> RU: CRC32 в шестнадцатеричном виде или {@code null}, если файл не удалось прочитать целиком.
     */
    @Override
    public String calculateHash(File file)
    {
        _crc32Algorithm.reset();
        ByteBuffer buffer = ByteBuffer.allocateDirect(READ_BUFFER);
        try (FileChannel channel = FileChannel.open(file.toPath(), StandardOpenOption.READ))
        {
            while (channel.read(buffer) != -1)
            {
                buffer.flip();
                _crc32Algorithm.update(buffer);
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
        return IHashingAlgorithm.longToHex(_crc32Algorithm.getValue());
    }

    /**
     * EN: Returns the algorithm identifier this implementation provides.
     * <br>
     * RU: Возвращает идентификатор алгоритма, который предоставляет эта реализация.
     *
     * @return {HashType} EN: always {@link HashType#CRC32}. <br> RU: всегда {@link HashType#CRC32}.
     */
    @Override
    public HashType getHashingAlgorithm()
    {
        return HashType.CRC32;
    }

    /**
     * EN: Creates a fresh, independent instance for single-threaded use on another thread.
     * <br>
     * RU: Создаёт новый независимый экземпляр для однопоточного использования в другом потоке.
     *
     * @return {IHashingAlgorithm} EN: a new {@link HashingCRC32Algorithm}.
     *         <br> RU: новый экземпляр {@link HashingCRC32Algorithm}.
     */
    @Override
    public IHashingAlgorithm getNewInstance()
    {
        return new HashingCRC32Algorithm();
    }
}
