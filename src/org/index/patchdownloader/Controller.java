package org.index.patchdownloader;

import org.index.patchdownloader.cli.CliArguments;
import org.index.patchdownloader.config.MainConfigHolder;
import org.index.patchdownloader.config.configs.MainConfig;
import org.index.patchdownloader.enums.CDNLink;
import org.index.patchdownloader.impl.downloader.PipelineCoordinator;
import org.index.patchdownloader.interfaces.IDummyLogger;
import org.index.patchdownloader.model.linkgenerator.GeneralLinkGenerator;
import org.index.patchdownloader.util.FileUtils;

/**
 * EN: Program entry point. Loads the config, applies start-up arguments, builds the link generator for
 *     the configured Content-Delivery-Network (CDN)/version, then hands the resulting file map to the
 *     {@link PipelineCoordinator} which runs the download pipeline to completion and exits.<br>
 * RU: Точка входа программы. Загружает конфиг, применяет аргументы запуска, строит генератор ссылок для
 *     настроенной сети доставки контента (CDN)/версии, затем передаёт полученную карту файлов в {@link PipelineCoordinator},
 *     который прогоняет конвейер загрузки до завершения и выходит.<br>
 **/
public class Controller
{
    private final CDNLink _cdnType;
    private final int _patchVersion;

    private final GeneralLinkGenerator _fileLinkGenerator;

    /**
     * EN: Builds the link generator for the given CDN/version, loads its file list, and starts the
     *     download pipeline. Aborts (with a log) when the version/CDN is unavailable. <br>
     * RU: Строит генератор ссылок для заданного CDN/версии, загружает список файлов и запускает конвейер
     *     загрузки. Прерывается (с логом), если версия/CDN недоступны. <br>
     * ==================================================================<br>
     * EN: @param cdnType the CDN source / RU: @param cdnType источник CDN <br>
     * EN: @param patchVersion the patch version / RU: @param patchVersion версия патча <br>
     **/
    public Controller(CDNLink cdnType, int patchVersion)
    {
        _cdnType = cdnType;
        _patchVersion = patchVersion;
        _fileLinkGenerator = GeneralLinkGenerator.generateLinkToFiles(_cdnType, _patchVersion);
        if (_fileLinkGenerator == null)
        {
            IDummyLogger.log(IDummyLogger.ERROR, "Patch version is unavailable. Version: " + MainConfig.PATCH_VERSION_SOURCE + "; CDN Source: " + MainConfig.CDN_SOURCE + ";");
            System.exit(1);
            return;
        }
        _fileLinkGenerator.load();
        if (_fileLinkGenerator.getFileMapHolder().isEmpty())
        {
            IDummyLogger.log(IDummyLogger.ERROR, "Patch version is unavailable. Version: " + MainConfig.PATCH_VERSION_SOURCE + "; CDN Source: " + MainConfig.CDN_SOURCE + ";");
            System.exit(1);
            return;
        }
        IDummyLogger.log(IDummyLogger.INFO, "Version: " + MainConfig.PATCH_VERSION_SOURCE + "; CDN Source: " + MainConfig.CDN_SOURCE + "; Total available files: " + _fileLinkGenerator.getFileMapHolder().size() + ";");
        IDummyLogger.log(IDummyLogger.INFO, "Download list obtained. Program continue working...");

        new PipelineCoordinator(_fileLinkGenerator).run();
    }

    /**
     * EN: JVM entry point: loads the config, applies start-up arguments, validates the output folder and
     *     CDN source, then constructs the controller which runs the pipeline. <br>
     * RU: Точка входа JVM: загружает конфиг, применяет аргументы запуска, проверяет папку вывода и
     *     источник CDN, затем создаёт контроллер, запускающий конвейер. <br>
     * ==================================================================<br>
     * EN: @param args the start-up arguments / RU: @param args аргументы запуска <br>
     **/
    public static void main(String[] args)
    {
        try
        {
            MainConfigHolder.getInstance().load();
        }
        catch (Exception e)
        {
            IDummyLogger.log(IDummyLogger.ERROR, "Cannot load config 'work/config/Main.ini': " + e);
            System.exit(1);
            return;
        }
        CliArguments.parse(args);
        if (!FileUtils.canGetAccessToFolder(MainConfig.DOWNLOAD_PATH))
        {
            IDummyLogger.log(IDummyLogger.ERROR, "Folder '" + MainConfig.DOWNLOAD_PATH + "' is closed for writing.");
            System.exit(1);
            return;
        }
        if (MainConfig.CDN_SOURCE == null)
        {
            IDummyLogger.log(IDummyLogger.ERROR, "CDN Source is Undefined.");
            System.exit(1);
            return;
        }
        new Controller(MainConfig.CDN_SOURCE, MainConfig.PATCH_VERSION_SOURCE);
    }

}
