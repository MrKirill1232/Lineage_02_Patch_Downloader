package org.index.patchdownloader.util.xml;

import git.index.fieldparser.FieldParserManager;
import git.index.fieldparser.model.FieldClassRef;
import org.dom4j.*;
import org.dom4j.io.SAXReader;
import org.index.patchdownloader.util.FileUtils;
import org.index.patchdownloader.util.exceptions.SimpleErrorHandler;
import org.xml.sax.SAXException;

import java.io.*;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public interface IXmlParser
{
    SimpleErrorHandler ERROR_HANDLER = new SimpleErrorHandler();

    default void parseFileList(File file, int depth)
    {
        File[] fileList = FileUtils.getFileList(file, depth);
        for (File parsedFile : fileList)
        {
            parseFile(parsedFile);
        }
    }

    default void parseFile(File file)
    {
        try
        {
            parseXmlString(file.toString(), Files.readString(file.toPath()));
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * EN: Parses an XML string. Strips a leading UTF-8 BOM (Byte Order Mark), reads the text into a
     *     {@link Document} via a hardened {@link SAXReader}, then hands the document to {@link #parseDocument}. <br>
     * RU: Разбирает XML-строку. Убирает ведущий BOM (Byte Order Mark, маркер порядка байтов) кодировки UTF-8,
     *     читает текст в {@link Document} через защищённый {@link SAXReader}, после чего передаёт документ
     *     в {@link #parseDocument}. <br>
     * ==================================================================<br>
     * EN: @param xmlInfo a label describing the XML source (path or origin), used for diagnostics /
     *     RU: @param xmlInfo метка источника XML (путь или происхождение), используется для диагностики <br>
     * EN: @param xmlContent the raw XML text to parse / RU: @param xmlContent исходный текст XML для разбора <br>
     **/
    default void parseXmlString(String xmlInfo, String xmlContent)
    {
        SAXReader reader = initReader();
        try
        {
            parseDocument(xmlInfo, reader.read(new StringReader(xmlContent.replace("\uFEFF", ""))));
        }
        catch (DocumentException e)
        {
            throw new RuntimeException(e);
        }
    }

    void parseDocument(String xmlInfo, Document document);

    default File getXsdScheme()
    {
        return null;
    }

    default File getDtdScheme()
    {
        return null;
    }

    private static SAXReader initReader()
    {
        SAXReader saxReader = new SAXReader();
        saxReader.setValidation(false);
        saxReader.setErrorHandler(ERROR_HANDLER);

        // EN: Harden against XXE (XML eXternal Entity) attacks explicitly rather than trusting the bundled
        //     dom4j default: the shipped SAXHelper wires the external-entity switches to the wrong feature URIs
        //     and swallows the failure, so external general entities stay enabled. XML here is downloaded from
        //     patch mirrors (attacker/MITM-controllable), so a forbidden DOCTYPE must fail closed, not silently pass.
        // RU: Защищаемся от XXE (XML eXternal Entity, внешние XML-сущности) явно, а не полагаясь на дефолт
        //     встроенного dom4j: поставляемый SAXHelper вешает выключатели внешних сущностей на неверные URI фич
        //     и глотает ошибку, из-за чего внешние общие сущности остаются включёнными. XML здесь скачивается
        //     с зеркал патчей (может быть подделан атакующим/MITM), поэтому запрещённый DOCTYPE должен падать
        //     закрыто, а не тихо проходить.
        setFeatureStrict(saxReader, "http://apache.org/xml/features/disallow-doctype-decl", true);
        setFeatureStrict(saxReader, "http://xml.org/sax/features/external-general-entities", false);
        setFeatureStrict(saxReader, "http://xml.org/sax/features/external-parameter-entities", false);
        setFeatureStrict(saxReader, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

        return saxReader;
    }

    private static void setFeatureStrict(SAXReader saxReader, String feature, boolean value)
    {
        try
        {
            saxReader.setFeature(feature, value);
        }
        catch (SAXException e)
        {
            // EN: Fail closed: if the parser cannot honour a security feature, refuse to parse rather than
            //     proceed with weaker protections. / RU: Падаем закрыто: если парсер не может применить фичу
            //     безопасности, отказываемся от разбора, а не продолжаем с ослабленной защитой.
            throw new RuntimeException("Failed to set XML security feature: " + feature, e);
        }
    }


    static Set<Element> getChildNodes(Node inputNode, String... searchingNodeNames)
    {
        if (searchingNodeNames == null || searchingNodeNames.length == 0)
        {
            return Collections.emptySet();
        }
        final Set<Element> returnNodeSet = new HashSet<>(0);
        if (inputNode instanceof Document doc)
        {
            for (Node element : doc.content())
            {
                if (element instanceof Element lookingElement)
                {
                    for (String nodeName : searchingNodeNames)
                    {
                        if (nodeName == null)
                        {
                            continue;
                        }
                        if (lookingElement.getName().equalsIgnoreCase(nodeName))
                        {
                            returnNodeSet.add(lookingElement);
                            break;
                        }
                    }
                }
            }
        }
        else if (inputNode instanceof Element lookingElement)
        {
            for (Element childElement : lookingElement.elements())
            {
                if (childElement == null)
                {
                    continue;
                }
                for (String nodeName : searchingNodeNames)
                {
                    if (nodeName == null)
                    {
                        continue;
                    }
                    if (childElement.getName().equalsIgnoreCase(nodeName))
                    {
                        returnNodeSet.add(childElement);
                        break;
                    }
                }
            }
        }
        return returnNodeSet;
    }

    /**
     * EN: Returns the text of the first direct child element named {@code childName}, or {@code null} when it
     *     is absent. Case-sensitive on the tag name (as the XML is written). <br>
     * RU: Возвращает текст первого прямого дочернего элемента с именем {@code childName} или {@code null},
     *     если его нет. Регистрозависимо по имени тега (как записан XML). <br>
     * ==================================================================<br>
     * EN: @param parent the parent element / RU: @param parent родительский элемент <br>
     * EN: @param childName the child tag name / RU: @param childName имя дочернего тега <br>
     * @return <br>
     *         {String} - EN: the child's text, or null / RU: текст ребёнка или null <br>
     **/
    static String childText(Element parent, String childName)
    {
        Element child = parent == null ? null : parent.element(childName);
        return child == null ? null : child.getText();
    }

    /**
     * EN: Reads a child element's text as a {@link String} through the shared {@link FieldParserManager}
     *     (the same field-parser backing the config), returning {@code defaultValue} when the child is absent. <br>
     * RU: Читает текст дочернего элемента как {@link String} через общий {@link FieldParserManager}
     *     (тот же field-parser, что и у конфига), возвращая {@code defaultValue}, если ребёнка нет. <br>
     * ==================================================================<br>
     * EN: @param parent the parent element / RU: @param parent родительский элемент <br>
     * EN: @param childName the child tag name / RU: @param childName имя дочернего тега <br>
     * EN: @param defaultValue value when the child is absent / RU: @param defaultValue значение при отсутствии <br>
     * @return <br>
     *         {String} - EN: the parsed string / RU: разобранная строка <br>
     **/
    static String parseString(Element parent, String childName, String defaultValue)
    {
        return FieldParserManager.getInstance().applyParserFromClass(String.class).parseValue(childText(parent, childName), new FieldClassRef<>(String.class), defaultValue);
    }

    /**
     * EN: Reads a child element's text as an {@code int} through the shared {@link FieldParserManager},
     *     returning {@code defaultValue} when the child is absent or not a number. <br>
     * RU: Читает текст дочернего элемента как {@code int} через общий {@link FieldParserManager},
     *     возвращая {@code defaultValue}, если ребёнка нет или это не число. <br>
     * ==================================================================<br>
     * EN: @param parent the parent element / RU: @param parent родительский элемент <br>
     * EN: @param childName the child tag name / RU: @param childName имя дочернего тега <br>
     * EN: @param defaultValue value when absent / not a number / RU: @param defaultValue значение при отсутствии/невалидности <br>
     * @return <br>
     *         {int} - EN: the parsed int / RU: разобранный int <br>
     **/
    static int parseInteger(Element parent, String childName, int defaultValue)
    {
        return FieldParserManager.getInstance().applyParserFromClass(Integer.class).parseValue(childText(parent, childName), new FieldClassRef<>(Integer.class), defaultValue);
    }
}
