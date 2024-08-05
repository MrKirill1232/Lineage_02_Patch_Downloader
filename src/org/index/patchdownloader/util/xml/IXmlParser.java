package org.index.patchdownloader.util.xml;

import org.dom4j.*;
import org.dom4j.io.SAXReader;
import org.index.patchdownloader.util.FileUtils;
import org.index.patchdownloader.util.StatSet;
import org.index.patchdownloader.util.exceptions.SimpleErrorHandler;

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

    default void parseXmlString(String xmlInfo, String xmlContent)
    {
        SAXReader reader = initReader();
        try
        {
            parseDocument(xmlInfo, reader.read(new InputStreamReader(new ByteArrayInputStream(xmlContent.getBytes()))));
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

        return saxReader;
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

    static Set<Node> getChildNodeByIgnoringHeaded(Node inputNode, String... searchingNodeNames)
    {
        if (searchingNodeNames == null || searchingNodeNames.length == 0)
        {
            return Collections.emptySet();
        }
        final Set<Node> returnNodeSet = new HashSet<>(0);
        if (inputNode instanceof Document doc)
        {
            for (Node element : doc.content())
            {
                if (element instanceof Element lookingElement)
                {
                    returnNodeSet.addAll(getChildNodes(lookingElement, searchingNodeNames));
                }
            }
        }
        else if (inputNode instanceof Element lookingElement)
        {
            for (Element childElement : lookingElement.elements())
            {
                returnNodeSet.addAll(getChildNodes(childElement, searchingNodeNames));
            }
        }
        return returnNodeSet;
    }

    static StatSet parseAttributeToStatSet(Element element)
    {
        if (element == null || element.attributeCount() == 0)
        {
            return StatSet.EMPTY_SET;
        }
        final StatSet returnSet = new StatSet();
        for (Attribute attribute : element.attributes())
        {
            returnSet.addValue(attribute.getName(), attribute.getValue());
        }
        return returnSet;
    }

    static StatSet parseTextToStatSet(Element element)
    {
        if (element == null || element.elements().isEmpty())
        {
            return StatSet.EMPTY_SET;
        }
        final StatSet returnSet = new StatSet();
        for (Element textElement : element.elements())
        {
            returnSet.addValue(textElement.getName(), textElement.getText());
        }
        return returnSet;
    }
}
