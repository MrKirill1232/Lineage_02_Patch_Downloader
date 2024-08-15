package org.index.patchdownloader.util.parsers;

import org.index.patchdownloader.config.annotations.ConfigParameter;
import org.index.patchdownloader.util.Utils;

import java.lang.reflect.Array;
import java.util.regex.Pattern;

/**
 * @author Index
 */
public class ArrayTwoDimensionFieldParser implements IFieldParser
{
    protected final static Pattern PATTERN_WHITE_SPACE = ArrayOneDimensionFieldParser.PATTERN_WHITE_SPACE;

    @Override
    public Object parseValue(ConfigParameter configParameter, Class<?> requestClass, String value, Object defaultValue, Object... args)
    {
        if (value == null)
        {   // cannot parse null value
            logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), requestClass, value, defaultValue,
                    "[Input value] is null"
            );
            return defaultValue;
        }

        Object[] mainArgumentsArray = getMainArgumentsArray(configParameter, requestClass, defaultValue, args);
        if (mainArgumentsArray == null)
        {
            return defaultValue;
        }

        String oneDimDelimiter = (String) mainArgumentsArray[0];
        String twoDimDelimiter = (String) mainArgumentsArray[1];
        Class<?>        componentClass  = (Class<?>) mainArgumentsArray[2];
        IFieldParser    fieldParser     = ParseUtils.getParserByFieldType(componentClass);
        if (fieldParser == null)
        {
            logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), requestClass, value, defaultValue,
                    "[IFieldParser] is null, because '" + requestClass + "' is not handled for parsing. [1]"
            );
            return defaultValue;
        }

        String[] oneDimSplitValues = value.split(oneDimDelimiter);
        Object firstDimArray = Array.newInstance(componentClass, oneDimSplitValues.length, 0);

        for (int oneDimIndex = 0; oneDimIndex < oneDimSplitValues.length; oneDimIndex++)
        {
            String[] twoDimSplitValues = oneDimSplitValues[oneDimIndex].split(twoDimDelimiter);

            Object secondDimArray = Array.newInstance(componentClass, twoDimSplitValues.length);

            Array.set(firstDimArray, oneDimIndex, secondDimArray);

            for (int twoDimIndex = 0; twoDimIndex < twoDimSplitValues.length; twoDimIndex++)
            {
                String      valueAsString   = twoDimSplitValues[twoDimIndex];
                Object      parsedValue     = fieldParser.parseValue(null, componentClass, valueAsString, null);
                if (parsedValue == null && fieldParser.isDigitValue())
                {
                    logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), requestClass, value, defaultValue,
                            "[IFieldParser] marked as 'digit' and [valueIntoArray] is null"
                    );
                    return defaultValue;
                }
                Array.set(secondDimArray, twoDimIndex, parsedValue);
            }
        }
        return firstDimArray;
    }

    @Override
    public void logParsingError(ConfigParameter configParameter, String configKey, Class<?> requestClass, Object value, Object defaultValue, String... message)
    {
        IFieldParser.innerLogger(ERROR, getClass(),
                "Cannot parse '" + ("Two Dimension Array") + ((requestClass == null) ? ("") : (" with requested class '" + requestClass.getSimpleName() + "'")) +"'. " +
                        ((configKey == null)    ? ("") : (  "Config key: '"       + configKey                       + "'. "))   +
                        ((value == null)        ? ("") : (  "Requested value: '"  + String.valueOf(value)           + "'. "))   +
                        (                                   "Used default value '"+ String.valueOf(defaultValue)    + "'. ")    +
                        (Utils.joinStrings(".\n", message)) +
                        (""),
                null);
    }

    @Override
    public String replaceIllegalSymbols(String string)
    {
        return PATTERN_WHITE_SPACE.matcher(string).replaceAll("");
    }

    @Override
    public Object[] getMainArgumentsArray(ConfigParameter configParameter, Class<?> requestClass, Object defaultValue, Object... arguments)
    {
        if (configParameter == null)
        {   // not enought arguments for parse 2 dimensional array
            if (arguments == null || arguments.length < 2)
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
            {   // not enought arguments for parse 2 dimensional array
                if (arguments == null || arguments.length < 1)
                {
                    logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), requestClass, null, defaultValue,
                            "[ConfigParameter] value of [spliterator()] is 'empty', method cannot parse spliterator from [spliterator()]"
                    );
                    return null;
                }
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
        if (countOfDimensional == 2)
        {
            requiredClass = requestClass.getComponentType().getComponentType();
        }
        else if (countOfDimensional == 1)
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
        if (configParameter != null && !configParameter.spliterator().isEmpty())
        {
            Object valueOnFirstPlace = Array.get(arguments, 0);
            if (valueOnFirstPlace == null || valueOnFirstPlace.getClass() != String.class)
            {   // argument is not valid
                logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), requiredClass, null, defaultValue,
                        "[01] Cannot get [spliterator 02] from argument list, " +
                                "because Looking [String.class] for argument, but found " +
                                "" + (valueOnFirstPlace == null ? null : valueOnFirstPlace.getClass()) + "' with value '" + valueOnFirstPlace + "'"
                );
                return null;
            }
            return new Object[] { configParameter.spliterator(), valueOnFirstPlace, requiredClass };
        }
        else
        {
            Object valueOnFirstPlace = Array.get(arguments, 0);
            if (valueOnFirstPlace == null || valueOnFirstPlace.getClass() != String.class)
            {   // argument is not valid
                logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), requiredClass, null, defaultValue,
                        "Cannot get [spliterator 01] from argument list, " +
                                "because Looking [String.class] for argument, but found " +
                                "" + (valueOnFirstPlace == null ? null : valueOnFirstPlace.getClass()) + "' with value '" + valueOnFirstPlace + "'"
                );
                return null;
            }
            Object valueOnSecondPlace = Array.get(arguments, 1);
            if (valueOnSecondPlace == null || valueOnSecondPlace.getClass() != String.class)
            {   // argument is not valid
                logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), requiredClass, null, defaultValue,
                        "[02] Cannot get [spliterator 02] from argument list, " +
                                "because Looking [String.class] for argument, but found " +
                                "" + (valueOnFirstPlace == null ? null : valueOnFirstPlace.getClass()) + "' with value '" + valueOnFirstPlace + "'"
                );
                return null;
            }
            return new Object[] { valueOnFirstPlace, valueOnSecondPlace, requiredClass };
        }
    }

    @SuppressWarnings("unchecked")
    public static <C> C[][] parseTwoDimensionArray(Class<C> arrayClass, String value, C[][] defaultValue, String firstDimSpliterator, String secondDimSpliterator)
    {
        IFieldParser iFieldParser = ParseUtils.getParserByFieldType(Array[][].class);
        if (iFieldParser == null)
        {
            iFieldParser = new ArrayTwoDimensionFieldParser();
        }
        return (C[][]) iFieldParser.parseValue(null, arrayClass, value, defaultValue, firstDimSpliterator, secondDimSpliterator);
    }

//    public static void main(String[] args)
//    {
//        ArrayTwoDimensionFieldParser arrayTwoDimensionFieldParser = new ArrayTwoDimensionFieldParser();
//        arrayTwoDimensionFieldParser.parseValue(null, int[][].class, "1:2;1:3;5:3", new int[0][], ";", ":");
//    }
}
