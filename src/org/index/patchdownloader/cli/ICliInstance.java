package org.index.patchdownloader.cli;

/**
 * EN: One command-line argument handler. Each implementation answers to a set of flags
 *     ({@link #getParsableAttributes()}), carries a help line ({@link #getDescription()}) and parses its own
 *     value token(s) ({@link #parseAttribute(int, String[])}). There is exactly one implementation per
 *     argument; the implementations are registered in the {@link CliArg} enum, which the dispatcher
 *     ({@code CliArguments}) and the {@code -help} handler both iterate.<br>
 * RU: Обработчик одного аргумента командной строки. Каждая реализация отвечает на набор флагов
 *     ({@link #getParsableAttributes()}), содержит строку справки ({@link #getDescription()}) и разбирает свои
 *     токены-значения ({@link #parseAttribute(int, String[])}). Ровно одна реализация на аргумент;
 *     реализации регистрируются в enum {@link CliArg}, который обходят и диспетчер ({@code CliArguments}), и
 *     обработчик {@code -help}.<br>
 **/
public interface ICliInstance
{
    /**
     * EN: The flags this argument answers to (e.g. {@code {"-help", "-h"}}); matched case-insensitively by the
     *     dispatcher. <br>
     * RU: Флаги, на которые отвечает этот аргумент (напр. {@code {"-help", "-h"}}); диспетчер сопоставляет без
     *     учёта регистра. <br>
     * @return <br>
     *         {String[]} - EN: the recognised flags / RU: распознаваемые флаги <br>
     **/
    String[] getParsableAttributes();

    /**
     * EN: A short, self-explanatory help line printed by the {@code -help} argument. <br>
     * RU: Короткая понятная строка справки, печатаемая аргументом {@code -help}. <br>
     * @return <br>
     *         {String} - EN: the help text / RU: текст справки <br>
     **/
    String getDescription();

    /**
     * EN: Parses this argument starting at its flag (found at {@code currIndex} in {@code arguments}),
     *     consuming any following value tokens, and returns the index of the LAST token it consumed — the
     *     dispatcher then advances one past it. A flag that consumes no value returns {@code currIndex}
     *     unchanged; a flag that consumes one value returns {@code currIndex + 1}. <br>
     * RU: Разбирает аргумент, начиная с его флага (по {@code currIndex} в {@code arguments}), поглощая
     *     последующие токены-значения, и возвращает индекс ПОСЛЕДНЕГО поглощённого токена — диспетчер затем
     *     проходит на один дальше. Флаг без значения возвращает {@code currIndex} без изменений; флаг с одним
     *     значением возвращает {@code currIndex + 1}. <br>
     * ==================================================================<br>
     * EN: @param currIndex index of this argument's flag in the array / RU: @param currIndex индекс флага этого аргумента в массиве <br>
     * EN: @param arguments the full start-up argument array / RU: @param arguments полный массив аргументов запуска <br>
     * @return <br>
     *         {int} - EN: index of the last token this argument consumed / RU: индекс последнего поглощённого этим аргументом токена <br>
     **/
    int parseAttribute(int currIndex, String[] arguments);
}
