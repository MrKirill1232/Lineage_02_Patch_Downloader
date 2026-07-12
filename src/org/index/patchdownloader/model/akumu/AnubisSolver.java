package org.index.patchdownloader.model.akumu;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * EN: Pure solver for the Anubis antibot "fast" proof-of-work (a Hashcash variant, techaro.lol/Anubis). The
 *     server issues a random hex string and a difficulty D; the client must find the smallest non-negative
 *     nonce such that {@code SHA-256(randomData + nonce)} in lowercase hex begins with D zero characters
 *     (D zero nibbles). This is exactly the work a browser does — solving it is proof of doing that same work,
 *     not a bypass of it. Difficulty 4 costs on the order of 1e5 hashes (well under a second). No state, no
 *     I/O: given the challenge it returns the {@link Solution} to submit.<br>
 * RU: Чистый решатель "быстрого" proof-of-work антибота Anubis (вариант Hashcash, techaro.lol/Anubis). Сервер
 *     выдаёт случайную hex-строку и сложность D; клиент должен найти наименьший неотрицательный nonce такой,
 *     что {@code SHA-256(randomData + nonce)} в hex (нижний регистр) начинается с D нулей (D нулевых ниблов).
 *     Это ровно та работа, которую делает браузер — её решение есть доказательство выполнения той же работы, а
 *     не её обход. Сложность 4 стоит порядка 1e5 хешей (доли секунды). Без состояния и I/O: по челленджу
 *     возвращает {@link Solution} для отправки.<br>
 **/
public final class AnubisSolver
{
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final long MAX_ITERATIONS = 1L << 34;
    private static final int MAX_DIFFICULTY = 5;
    private static final long MAX_SOLVE_MILLIS = 30_000L;

    /**
     * EN: The result of solving a challenge: the winning nonce, its full hex hash (submitted as
     *     {@code response}), and how long solving took (submitted as {@code elapsedTime}). <br>
     * RU: Результат решения челленджа: победивший nonce, его полный hex-хеш (отправляется как
     *     {@code response}) и время решения (отправляется как {@code elapsedTime}). <br>
     **/
    public static final class Solution
    {
        private final long _nonce;
        private final String _hash;
        private final long _elapsedMs;

        private Solution(long nonce, String hash, long elapsedMs)
        {
            _nonce = nonce;
            _hash = hash;
            _elapsedMs = elapsedMs;
        }

        public long getNonce()
        {
            return _nonce;
        }

        public String getHash()
        {
            return _hash;
        }

        public long getElapsedMs()
        {
            return _elapsedMs;
        }
    }

    private AnubisSolver()
    {
    }

    /**
     * EN: Finds the smallest nonce whose {@code SHA-256(randomData + nonce)} hex has {@code difficulty}
     *     leading zeros. Difficulty {@code <= 0} is solved by nonce 0. A difficulty above the supported maximum
     *     (real Anubis uses 4) is rejected immediately rather than burning CPU. Throws when the challenge is
     *     malformed, the difficulty is unreasonable, or no solution is found within the safety cap. <br>
     * RU: Находит наименьший nonce, чей {@code SHA-256(randomData + nonce)} в hex имеет {@code difficulty}
     *     ведущих нулей. Сложность {@code <= 0} решается nonce 0. Сложность выше поддерживаемого максимума
     *     (реальный Anubis использует 4) отклоняется сразу, а не жжёт CPU. Бросает при некорректном челлендже,
     *     неразумной сложности или если решение не найдено в пределах предохранителя. <br>
     * ==================================================================<br>
     * EN: @param randomData the server-issued challenge string / RU: @param randomData строка челленджа от сервера <br>
     * EN: @param difficulty the required number of leading zero hex chars / RU: @param difficulty требуемое число ведущих нулевых hex-символов <br>
     * @return <br>
     *         {Solution} - EN: the nonce + hash + elapsed time to submit / RU: nonce + хеш + время для отправки <br>
     **/
    public static Solution solve(String randomData, int difficulty)
    {
        if (randomData == null || randomData.isEmpty())
        {
            throw new IllegalArgumentException("Anubis challenge has no randomData.");
        }
        if (difficulty > MAX_DIFFICULTY)
        {
            // Real Anubis uses difficulty 4; anything much higher is misconfigured or hostile and would
            // burn minutes-to-hours of CPU. Refuse instead of hanging (also keeps zeros well below the digest size).
            throw new IllegalArgumentException("Anubis difficulty " + difficulty + " exceeds the supported maximum " + MAX_DIFFICULTY + " (likely a hostile or broken challenge).");
        }
        MessageDigest digest;
        try
        {
            digest = MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256 is required to solve the Anubis challenge.", e);
        }
        byte[] prefixBytes = randomData.getBytes(StandardCharsets.US_ASCII);
        int zeros = Math.max(0, difficulty);
        long start = System.currentTimeMillis();
        for (long nonce = 0; nonce < MAX_ITERATIONS; nonce++)
        {
            digest.reset();
            digest.update(prefixBytes);
            digest.update(Long.toString(nonce).getBytes(StandardCharsets.US_ASCII));
            byte[] hash = digest.digest();
            if (hasLeadingZeroNibbles(hash, zeros))
            {
                return new Solution(nonce, toHex(hash), System.currentTimeMillis() - start);
            }
            // Wall-clock safety valve: even a legitimate difficulty solves in well under a second, so if we
            // are still grinding after MAX_SOLVE_MILLIS the challenge is hostile or broken. Fail fast instead
            // of stalling the pipeline (solve() runs while the caller holds its auth lock). Checked once every
            // 65536 iterations to keep System.currentTimeMillis() off the hot path.
            if ((nonce & 0xFFFF) == 0 && (System.currentTimeMillis() - start) > MAX_SOLVE_MILLIS)
            {
                throw new IllegalStateException("Anubis solving for difficulty " + difficulty + " exceeded " + MAX_SOLVE_MILLIS + " ms (likely a hostile or broken challenge).");
            }
        }
        throw new IllegalStateException("No Anubis solution found for difficulty " + difficulty + " within " + MAX_ITERATIONS + " iterations.");
    }

    /**
     * EN: Whether the hash's hex form starts with {@code zeros} zero nibbles (each byte is two nibbles;
     *     an even count checks whole bytes, an odd count also checks the high nibble of the next byte). <br>
     * RU: Начинается ли hex-форма хеша с {@code zeros} нулевых ниблов (каждый байт — два нибла; чётное число
     *     проверяет целые байты, нечётное — ещё и старший нибл следующего байта). <br>
     * ==================================================================<br>
     * EN: @param hash the raw digest / RU: @param hash сырой дайджест <br>
     * EN: @param zeros the required leading zero nibbles / RU: @param zeros требуемые ведущие нулевые ниблы <br>
     * @return <br>
     *         {true}  - EN: enough leading zero nibbles / RU: достаточно ведущих нулевых ниблов <br>
     *         {false} - EN: not enough / RU: недостаточно <br>
     **/
    private static boolean hasLeadingZeroNibbles(byte[] hash, int zeros)
    {
        int fullBytes = zeros / 2;
        boolean checkHighNibble = (zeros & 1) == 1;
        if (fullBytes > hash.length || (checkHighNibble && fullBytes == hash.length))
        {
            // More leading zero nibbles required than the digest has -> unsatisfiable. Guards against
            // indexing past the 32-byte digest when difficulty is absurdly high.
            return false;
        }
        for (int index = 0; index < fullBytes; index++)
        {
            if (hash[index] != 0)
            {
                return false;
            }
        }
        if (checkHighNibble)
        {
            return (hash[fullBytes] & 0xF0) == 0;
        }
        return true;
    }

    private static String toHex(byte[] bytes)
    {
        char[] out = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++)
        {
            int value = bytes[index] & 0xFF;
            out[index * 2] = HEX[value >>> 4];
            out[index * 2 + 1] = HEX[value & 0x0F];
        }
        return new String(out);
    }
}
