package org.index.patchdownloader.util.parsers;

import org.index.patchdownloader.config.annotations.ConfigParameter;
import org.index.patchdownloader.util.Utils;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.regex.Pattern;

/**
 * @author Index
 */
public class ArrayOneDimensionFieldParser implements IFieldParser
{
    protected final static Pattern PATTERN_WHITE_SPACE = Pattern.compile("\\s+");

    @Override
    public Object parseValue(ConfigParameter configParameter, Class<?> requestClass, String value, Object defaultValue, Object... args)
    {
        if (value == null)
        {
            logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), requestClass, value, defaultValue,
                    "[Input value] is null"
                    );
            return defaultValue;
        }
        Object[] mainArgumentArray = getMainArgumentsArray(configParameter, requestClass, defaultValue, args);
        if (mainArgumentArray == null)
        {
            return defaultValue;
        }
        String delimiter                = (String)      mainArgumentArray[0];
        Class<?>        componentClass  = (Class<?>)    mainArgumentArray[1];
        IFieldParser    fieldParser     = ParseUtils.getParserByFieldType(componentClass);
        if (fieldParser == null)
        {
            logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), componentClass, value, defaultValue,
                    "Request class is not supported for parse"
            );
            return defaultValue;
        }
        String[]        splitValues     = value.split(delimiter);
        Object          array           = Array.newInstance(componentClass, splitValues.length);
        for (int index = 0; index < splitValues.length; index++)
        {
            String      valueAsString   = splitValues[index];
            Object      parsedValue     = fieldParser.parseValue(null, componentClass, valueAsString, null);
            if (parsedValue == null && fieldParser.isDigitValue())
            {
                logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), componentClass, value, defaultValue,
                        "[IFieldParser] marked as 'digit', but [parsedValue] is null"
                );
                return defaultValue;
            }
            Array.set(array, index, parsedValue);
        }
        return array;
    }

    @Override
    public Object parseValueByObjectValue(Class<?> requestClass, Object value, Object defaultValue, Object... args)
    {
        if (value == null)
        {
            logParsingError(null, null, requestClass, value, defaultValue,
                    "[InputObject] is null"
            );
            return defaultValue;
        }
        if (requestClass == null && defaultValue == null)
        {
            logParsingError(null, null, requestClass, value, defaultValue,
                    "[RequestClass] is null.", "[DefaultValue] is null"
            );
            return defaultValue;
        }
        if (requestClass == null)
        {
            requestClass = defaultValue.getClass();
            IFieldParser.innerLogger(WARNING, getClass(), "[requestClass] is null. Used class from [defaultValue]", null);
        }

        if (value.getClass() == String.class)
        {
            if (args == null || args.length == 0)
            {
                logParsingError(null, null, requestClass, value, defaultValue,
                        "[InputObject] is string, but cannot find [spliterator] in argument list"
                );
                return defaultValue;
            }
            return parseValue(null, requestClass, (String) value, defaultValue, args);
        }
        if (value.getClass().isArray())
        {
            IFieldParser fieldParser = ParseUtils.getParserByFieldType(requestClass);
            if (fieldParser == null)
            {
                logParsingError(null, null, requestClass, value, defaultValue,
                        "[IFieldParser] is null, because '" + requestClass + "' is not handled for parsing. [1]"
                );
                return defaultValue;
            }
            int lengthOfArray = Array.getLength(value);
            Object returnArray = Array.newInstance(requestClass, lengthOfArray);
            for (int index = 0; index < lengthOfArray; index++)
            {
                Object valueIntoArray = fieldParser.parseValueByObjectValue(requestClass, Array.get(value, index), null);
                if (valueIntoArray == null && fieldParser.isDigitValue())
                {
                    logParsingError(null, null, requestClass, value, defaultValue,
                            "[IFieldParser] marked as 'digit' and [valueIntoArray] is null"
                    );
                    return defaultValue;
                }
                Array.set(returnArray, index, valueIntoArray);
            }
            return returnArray;
        }
        if (Collection.class.isAssignableFrom(value.getClass()))
        {
            IFieldParser fieldParser = ParseUtils.getParserByFieldType(requestClass);
            if (fieldParser == null)
            {
                logParsingError(null, null, requestClass, value, defaultValue,
                        "[IFieldParser] is null, because '" + requestClass + "' is not handled for parsing. [2]"
                );
                return defaultValue;
            }
            Collection<Object> collection = (Collection<Object>) value;
            int lengthOfArray = collection.size();
            Object returnArray = Array.newInstance(requestClass, lengthOfArray);
            int index = 0;
            for (Object object : collection)
            {
                Object valueIntoArray = fieldParser.parseValueByObjectValue(requestClass, object, null);
                if (valueIntoArray == null && fieldParser.isDigitValue())
                {
                    logParsingError(null, null, requestClass, value, defaultValue,
                            "[IFieldParser] marked as 'digit' and [valueIntoArray] is null"
                    );
                    return defaultValue;
                }
                Array.set(returnArray, index++, valueIntoArray);
            }
            return returnArray;
        }
        logParsingError(null, null, requestClass, value, defaultValue,
                "Cannot parse value"
        );
        return defaultValue;
    }

    @Override
    public String replaceIllegalSymbols(String string)
    {
        return PATTERN_WHITE_SPACE.matcher(string).replaceAll("");
    }

    @Override
    public void logParsingError(ConfigParameter configParameter, String configKey, Class<?> requestClass, Object value, Object defaultValue, String... message)
    {
        IFieldParser.innerLogger(ERROR, getClass(),
                "Cannot parse '" + ("One Dimension Array") + ((requestClass == null) ? ("") : (" with requested class '" + requestClass.getSimpleName() + "'")) +"'. " +
                        ((configKey == null)    ? ("") : (  "Config key: '"       + configKey                       + "'. "))   +
                        ((value == null)        ? ("") : (  "Requested value: '"  + String.valueOf(value)           + "'. "))   +
                        (                                   "Used default value '"+ String.valueOf(defaultValue)    + "'. ")    +
                        (Utils.joinStrings(".\n", message)) +
                        (""),
                null);
    }

    @Override
    public Object[] getMainArgumentsArray(ConfigParameter configParameter, Class<?> requestClass, Object defaultValue, Object[] arguments)
    {
        if (configParameter == null)
        {   // not enought arguments for parse 1 dimensional array
            if (arguments == null || arguments.length == 0)
            {
                logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), requestClass, null, defaultValue,
                        "[ConfigParameter] is null and count of arguments is not valid for parse spliterator from them"
                );
                return null;
            }
        }
        else
        {
            if (configParameter.spliterator().isEmpty())
            {   // not enought arguments for parse 1 dimensional array
                logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), requestClass, null, defaultValue,
                        "[ConfigParameter] value of [spliterator()] is 'empty', method cannot parse spliterator from [spliterator()]"
                );
                return null;
            }
        }

        Class<?> requiredClass;
        if (requestClass == null)
        {
            if (defaultValue == null)
            {
                logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), requestClass, null, defaultValue,
                        "Cannot create a array with null class"
                );
                return null;
            }
            requestClass = defaultValue.getClass();
            IFieldParser.innerLogger(WARNING, getClass(), "[requestClass] is null. Used class from [defaultValue]", null);
        }
        int countOfDimensional = Utils.getDimensions(requestClass);
        if (countOfDimensional == 1)
        {
            requiredClass = requestClass.getComponentType();
        }
        else if (countOfDimensional == 0)
        {
            requiredClass = requestClass;
        }
        else
        {
            logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), requestClass, null, defaultValue,
                    "In [requestedClass] (" + requestClass + ") unhandled count of dimensions '" + countOfDimensional + "'"
            );
            return null;
        }
        if (configParameter != null /*&& !configParameter.spliterator().isEmpty()*/)
        {
            return new Object[] { configParameter.spliterator(), requiredClass };
        }

        Object valueOnFirstPlace = Array.get(arguments, 0);
        if (valueOnFirstPlace == null || valueOnFirstPlace.getClass() != String.class)
        {   // argument is not valid
            logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), requiredClass, null, defaultValue,
                    "Cannot get [spliterator] from argument list, " +
                            "because Looking [String.class] for argument, but found " +
                            "" + (valueOnFirstPlace == null ? null : valueOnFirstPlace.getClass()) + "' with value '" + valueOnFirstPlace + "'"
            );
            return null;
        }

        return new Object[] { valueOnFirstPlace, requiredClass };
    }

    @SuppressWarnings("unchecked")
    public static <C> C[] parseOneDimensionArray(Class<C> arrayClass, String value, C[] defaultValue, String spliterator)
    {
        IFieldParser iFieldParser = ParseUtils.getParserByFieldType(Array[].class);
        if (iFieldParser == null)
        {
            iFieldParser = new ArrayOneDimensionFieldParser();
        }
        return (C[]) iFieldParser.parseValue(null, arrayClass, value, defaultValue, spliterator);
    }
}
