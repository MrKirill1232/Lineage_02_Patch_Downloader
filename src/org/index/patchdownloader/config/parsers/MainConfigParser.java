package org.index.patchdownloader.config.parsers;


import org.index.patchdownloader.config.ConfigParser;
import org.index.patchdownloader.config.configs.MainConfig;

/**
 * @author Index
 */
public class MainConfigParser extends ConfigParser
{
    private final static MainConfigParser ACTIVE_INSTANCE = new MainConfigParser();

    public static MainConfigParser getInstance()
    {
        return ACTIVE_INSTANCE;
    }

    @Override
    public String getConfigPath()
    {
        return "work/config/Main.ini";
    }

    @Override
    public Class<?> getAttachedConfig()
    {
        return MainConfig.class;
    }
}
