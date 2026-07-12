package org.index.patchdownloader.util.exceptions;

import org.index.patchdownloader.interfaces.IDummyLogger;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * EN: SAX (Simple API for XML) error handler that logs XML warnings/errors through {@link IDummyLogger}
 *     (instead of a bare {@code System.out}) and rethrows fatal errors so a corrupt document does not
 *     pass silently.<br>
 * RU: Обработчик ошибок SAX (Simple API for XML), который логирует XML-предупреждения/ошибки через {@link IDummyLogger}
 *     (вместо голого {@code System.out}) и пробрасывает фатальные ошибки, чтобы повреждённый документ
 *     не проходил молча.<br>
 **/
public class SimpleErrorHandler implements ErrorHandler
{
    /**
     * EN: Logs a recoverable XML warning. <br>
     * RU: Логирует восстановимое XML-предупреждение. <br>
     * ==================================================================<br>
     * EN: @param e the SAX warning / RU: @param e SAX-предупреждение <br>
     **/
    @Override
    public void warning(SAXParseException e) throws SAXException
    {
        IDummyLogger.log(IDummyLogger.WARNING, "XML warning: " + e.getMessage());
    }

    /**
     * EN: Logs a recoverable XML error. <br>
     * RU: Логирует восстановимую XML-ошибку. <br>
     * ==================================================================<br>
     * EN: @param e the SAX error / RU: @param e SAX-ошибка <br>
     **/
    @Override
    public void error(SAXParseException e) throws SAXException
    {
        IDummyLogger.log(IDummyLogger.ERROR, "XML error: " + e.getMessage());
    }

    /**
     * EN: Logs and rethrows a fatal XML error (a corrupt document must not be parsed silently). <br>
     * RU: Логирует и пробрасывает фатальную XML-ошибку (повреждённый документ не должен разбираться молча). <br>
     * ==================================================================<br>
     * EN: @param e the fatal SAX error / RU: @param e фатальная SAX-ошибка <br>
     **/
    @Override
    public void fatalError(SAXParseException e) throws SAXException
    {
        IDummyLogger.log(IDummyLogger.ERROR, "XML fatal error: " + e.getMessage());
        throw e;
    }
}
