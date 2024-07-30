package org.index.protocoldownloader.config.parsers;


import org.index.protocoldownloader.config.ConfigParser;
import org.index.protocoldownloader.config.configs.MainConfig;

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
