package org.index.patchdownloader.util.parsers;

import org.index.patchdownloader.config.annotations.ConfigParameter;
import org.index.patchdownloader.util.Utils;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Index
 */
public class CollectionFieldParser implements IFieldParser
{
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
        Class<?> requiredCollectionType = (Class<?>) mainArgumentArray[0];
        Class<?> genericTypeOfCollection= (Class<?>) mainArgumentArray[1];
        String spliterator              = (String) mainArgumentArray[2];
        Object[] arrayWithValues        = ParseUtils.parseOneDimensionArray(genericTypeOfCollection, value, null, spliterator);
        if (arrayWithValues == null)
        {
            logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), requestClass, value, defaultValue,
                    "[One Dimension Array] is null"
            );
            return defaultValue;
        }
        try
        {
            Collection<? super Object> newCollection = createCollection(requiredCollectionType);
            Collections.addAll(newCollection, arrayWithValues);
            return newCollection;
        }
        catch (Exception e)
        {
            logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), requestClass, value, defaultValue,
                    "Cannot create new Instance of requested collection '" + requestClass.getSimpleName() + "'"
            );
            return defaultValue;
        }
    }

    @Override
    public void logParsingError(ConfigParameter configParameter, String configKey, Class<?> requestClass, Object value, Object defaultValue, String... message)
    {
        IFieldParser.innerLogger(ERROR, getClass(),
                "Cannot parse '" + ("Collection") + ((requestClass == null) ? ("") : (" with requested class '" + requestClass.getSimpleName() + "'")) +"'. " +
                        ((configKey == null)    ? ("") : (  "Config key: '"       + configKey                       + "'. "))   +
                        ((value == null)        ? ("") : (  "Requested value: '"  + String.valueOf(value)           + "'. "))   +
                        (                                   "Used default value '"+ String.valueOf(defaultValue)    + "'. ")    +
                        (Utils.joinStrings(".\n", message)) +
                        (""),
                null);
    }

    @Override
    @SuppressWarnings("unchecked")
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
            return defaultValue;
        }
        if (args == null || args.length == 0)
        {
            logParsingError(null, null, requestClass, value, defaultValue,
                    "[RequestClass] is null.", "[DefaultValue] is null"
            );
            return defaultValue;
        }
        if (value.getClass() == String.class)
        {
            Object[] mainArgumentArray = getMainArgumentsArray(null, requestClass, defaultValue, args);
            if (mainArgumentArray == null)
            {
                logParsingError(null, null, requestClass, value, defaultValue,
                        "[InputObject] is string, but cannot find [spliterator] in argument list"
                );
                return defaultValue;
            }
            return parseValue(null, requestClass, (String) value, defaultValue, args);
        }
        if (Collection.class.isAssignableFrom(value.getClass()))
        {
            Class<?> requestedClass = (requestClass == null) ? defaultValue.getClass() : requestClass;
            if (requestClass == null)
            {
                IFieldParser.innerLogger(WARNING, getClass(), "[requestClass] is null. Used class from [defaultValue]", null);
            }
            try
            {
                Collection<? super Object> valueAsCollection = ((Collection<? super Object>) value);
                Collection<? super Object> collection = createCollection(requestedClass);
                collection.addAll(valueAsCollection);
                return collection;
            }
            catch (Exception e)
            {
                logParsingError(null, null, requestedClass, value, defaultValue,
                        "Cannot create new Instance of requested collection '" + requestClass.getSimpleName() + "'"
                );
                return defaultValue;
            }
        }
        if (value.getClass().isArray())
        {
            Class<?> requestedClass = (requestClass == null) ? defaultValue.getClass() : requestClass;
            if (requestClass == null)
            {
                IFieldParser.innerLogger(WARNING, getClass(), "[requestClass] is null. Used class from [defaultValue]", null);
            }
            IFieldParser fieldParser = ParseUtils.getParserByFieldType(requestedClass);
            if (fieldParser == null)
            {
                logParsingError(null, null, requestClass, value, defaultValue,
                        "[IFieldParser] is null, because '" + requestClass + "' is not handled for parsing."
                );
                return defaultValue;
            }
            int lengthOfArray = Array.getLength(value);
            Object returnArray = Array.newInstance(requestClass, lengthOfArray);
            for (int index = 0; index < lengthOfArray; index++)
            {
                Object parsedObjectToArray = fieldParser.parseValueByObjectValue(requestedClass, Array.get(value, index), null);
                if (parsedObjectToArray == null && fieldParser.isDigitValue())
                {
                    logParsingError(null, null, requestClass, value, defaultValue,
                            "[IFieldParser] marked as 'digit' and [parsedObjectToArray] is null"
                    );
                    return defaultValue;
                }
                Array.set(returnArray, index, parsedObjectToArray);
            }
            return returnArray;
        }
        logParsingError(null, null, requestClass, value, defaultValue,
                "Cannot parse value"
        );
        return defaultValue;
    }

    /**
     * if config parameters null or config parameters is not contains spliterator - waits arg array as [generic class (<code>Class<?></code>)]<br>
     * in other case - waits args array as [generic class <code>Class<?></code>];[spliterator (<code>String</code>)]<br>
     * @return [request_collection];[generic_class];[spliterator]
     */
    @Override
    public Object[] getMainArgumentsArray(ConfigParameter configParameter, Class<?> requestClass, Object defaultValue, Object... arguments)
    {
        Class<?> requiredCollectionType = null;
        Class<?> genericTypeOfCollection= null;
        String spliterator              = null;

        int requiredCountOfArguments = (configParameter == null || configParameter.spliterator().isEmpty()) ? 2 : 1;
        if ((defaultValue == null && requestClass == null) || (arguments == null || arguments.length < requiredCountOfArguments))
        {
            logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), requestClass, null, defaultValue,
                    "Cannot parse values because request class null",
                    "Cannot parse values because not enough arguments"
            );
            return null;
        }
        if (requestClass == null)
        {
            IFieldParser.innerLogger(WARNING, getClass(), "[requestClass] is null. Used class from [defaultValue]", null);
            requiredCollectionType = defaultValue.getClass();
        }
        else
        {
            requiredCollectionType = requestClass;
        }
        Object genericValueInArgumentArray = Array.get(arguments, 0);
        if (genericValueInArgumentArray == null || genericValueInArgumentArray.getClass() != Class.class)
        {   // argument is not valid
            logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), requestClass, null, defaultValue,
                    "Cannot get [generic class] from argument list, " +
                            "because Looking [Class.class] for argument, but found " +
                            "" + (genericValueInArgumentArray == null ? null : genericValueInArgumentArray.getClass()) + "' with value '" + genericValueInArgumentArray + "'"
            );
            return null;
        }
        genericTypeOfCollection = (Class<?>) genericValueInArgumentArray;
        if (genericTypeOfCollection != null && genericTypeOfCollection.isInterface())
        {
            logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), requestClass, null, defaultValue,
                    "Cannot get [generic class] from argument list, " +
                            "because found class is Interface! Class: " +
                            "" + (genericValueInArgumentArray == null ? null : genericValueInArgumentArray.getClass()) + "' with value '" + genericValueInArgumentArray + "'"
            );
            return null;
        }
        if (configParameter == null || configParameter.spliterator().isEmpty())
        {
            if (arguments == null || arguments.length < 1)
            {
                logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), requestClass, null, defaultValue,
                        "[ConfigParameter] is null and count of arguments is not valid for parse spliterator from them"
                );
                return null;
            }
            Object spliteratorValueInArgumentArray = Array.get(arguments, requiredCountOfArguments - 1);
            if (spliteratorValueInArgumentArray == null || spliteratorValueInArgumentArray.getClass() != String.class)
            {   // argument is not valid
                logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), requestClass, null, defaultValue,
                        "Cannot get [spliterator] from argument list, " +
                                "because Looking [String.class] for argument, but found " +
                                "" + (spliteratorValueInArgumentArray == null ? null : spliteratorValueInArgumentArray.getClass()) + "' with value '" + spliteratorValueInArgumentArray + "'"
                );
                return null;
            }
            spliterator = (String) spliteratorValueInArgumentArray;
        }
        else
        {
            spliterator = configParameter.spliterator();
        }

        if (requiredCollectionType == null || genericTypeOfCollection == null || spliterator == null)
        {
            logParsingError(configParameter, (configParameter == null ? null : configParameter.parameterName()), requestClass, null, defaultValue,
                    "Cannot parse values from arguments array.",
                    ("Request Collection Type is null: " + (requiredCollectionType == null)),
                    ("Generic Type of Collection is null: " + (genericTypeOfCollection == null)),
                    ("Spliterator is null: " + (spliterator == null))
            );
            return null;
        }

        return new Object[] { requiredCollectionType, genericTypeOfCollection, spliterator };
    }

    @SuppressWarnings("unchecked")
    private static Collection<? super Object> createCollection(Class<?> requiredCollection) throws Exception
    {
        Constructor<?> constructor = null;
        constructor = requiredCollection.getConstructor();
        constructor.setAccessible(true);
        return (Collection<? super Object>) constructor.newInstance();
    }

    @SuppressWarnings("unchecked")
    public static <C, T extends Collection<C>> T parseCollection(String value, Class<T> requestCollectionClass, Class<C> genericClass, Collection<?> defaultValue, String delimiter)
    {
        IFieldParser iFieldParser = ParseUtils.getParserByFieldType(Collection.class);
        if (iFieldParser == null)
        {
            iFieldParser = new CollectionFieldParser();
        }
        return (T) iFieldParser.parseValue(null, requestCollectionClass, value, defaultValue, genericClass, delimiter);
    }
}
