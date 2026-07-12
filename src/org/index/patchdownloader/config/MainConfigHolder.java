package org.index.patchdownloader.config;

import git.index.configparser.model.AbstractConfigHolder;
import org.index.patchdownloader.config.configs.MainConfig;

/**
 * EN: Loader for {@link MainConfig} backed by the {@code ConfigFieldParser} library. Reads the flat
 *     {@code key=value} file at {@code work/config/Main.ini} (relative to the working directory) and
 *     fills the annotated static fields. Strict singleton.<br>
 * RU: Загрузчик {@link MainConfig} на базе библиотеки {@code ConfigFieldParser}. Читает плоский файл
 *     {@code key=value} по пути {@code work/config/Main.ini} (относительно рабочего каталога) и
 *     заполняет аннотированные статические поля. Строгий синглтон.<br>
 **/
public class MainConfigHolder extends AbstractConfigHolder<MainConfig>
{
    private MainConfigHolder()
    {
        super();
    }

    /**
     * EN: Path to the config file, relative to the working directory. <br>
     * RU: Путь к файлу конфигурации относительно рабочего каталога. <br>
     * ==================================================================<br>
     * @return <br>
     *         {String} - EN: the relative config path / RU: относительный путь конфига <br>
     **/
    @Override
    public String getConfigPath()
    {
        return "work/config/Main.ini";
    }

    /**
     * EN: The config class whose static fields are filled. <br>
     * RU: Класс конфигурации, чьи статические поля заполняются. <br>
     * ==================================================================<br>
     * @return <br>
     *         {Class} - EN: MainConfig.class / RU: MainConfig.class <br>
     **/
    @Override
    public Class<MainConfig> getAttachedConfig()
    {
        return MainConfig.class;
    }

    private static final MainConfigHolder INSTANCE = new MainConfigHolder();

    public static MainConfigHolder getInstance()
    {
        return INSTANCE;
    }
}
