package org.index.patchdownloader.util.parsers;

import java.io.File;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class ParseUtils
{
    private ParseUtils()
    {
        // utility class
    }

    private final static Map<Class<?>, IFieldParser> MAP_OF_PARSERS = new HashMap<>();
    static
    {
        MAP_OF_PARSERS.put(byte.class       , new NumberByteFieldParser()       );
        MAP_OF_PARSERS.put(Byte.class       , new NumberByteFieldParser()       );
        MAP_OF_PARSERS.put(short.class      , new NumberShortFieldParser()      );
        MAP_OF_PARSERS.put(Short.class      , new NumberShortFieldParser()      );
        MAP_OF_PARSERS.put(int.class        , new NumberIntegerFieldParser()    );
        MAP_OF_PARSERS.put(Integer.class    , new NumberIntegerFieldParser()    );
        MAP_OF_PARSERS.put(long.class       , new NumberLongFieldParser()       );
        MAP_OF_PARSERS.put(Long.class       , new NumberLongFieldParser()       );
        MAP_OF_PARSERS.put(float.class      , new NumberFloatFieldParser()      );
        MAP_OF_PARSERS.put(Float.class      , new NumberFloatFieldParser()      );
        MAP_OF_PARSERS.put(double.class     , new NumberDoubleFieldParser()     );
        MAP_OF_PARSERS.put(Double.class     , new NumberDoubleFieldParser()     );
        MAP_OF_PARSERS.put(String.class     , new StringFiledParser()           );
        MAP_OF_PARSERS.put(Array[].class    , new ArrayOneDimensionFieldParser());
        MAP_OF_PARSERS.put(Array[][].class  , new ArrayTwoDimensionFieldParser());
        MAP_OF_PARSERS.put(Enum.class       , new EnumFieldParser()             );
        MAP_OF_PARSERS.put(Boolean.class    , new BooleanFieldParser()          );
        MAP_OF_PARSERS.put(boolean.class    , new BooleanFieldParser()          );
        MAP_OF_PARSERS.put(File.class       , new FileFieldParser()             );
        MAP_OF_PARSERS.put(Pattern.class    , new PatternFieldParser()          );
        MAP_OF_PARSERS.put(Collection.class , new CollectionFieldParser()       );
        MAP_OF_PARSERS.put(char.class       , new CharFieldParser()             );
        MAP_OF_PARSERS.put(Character.class  , new CharFieldParser()             );
    }

    public static IFieldParser getParserByFieldType(Class<?> requestClassParser)
    {
        if (requestClassParser.isArray())
        {
            if (requestClassParser.getComponentType().isArray())
            {
                if (requestClassParser.getComponentType().getComponentType().isArray())
                {   // 3 or more dimensions ;0
                    return null;
                }
                return MAP_OF_PARSERS.getOrDefault(Array[][].class, null);
            }
            return MAP_OF_PARSERS.getOrDefault(Array[].class, null);
        }
        if (requestClassParser.isEnum())
        {
            return MAP_OF_PARSERS.getOrDefault(Enum.class, null);
        }
        if (Collection.class.isAssignableFrom(requestClassParser))
        {
            return MAP_OF_PARSERS.getOrDefault(Collection.class, null);
        }
        return MAP_OF_PARSERS.getOrDefault(requestClassParser, null);
    }

    public static void addParserByFieldType(Class<?> classType, IFieldParser fieldParser, boolean replace)
    {
        if (!replace && MAP_OF_PARSERS.containsKey(classType))
        {
            return;
        }
        MAP_OF_PARSERS.put(classType, fieldParser);
    }

    public static <C> C parseValue(Object value, C defaultValue, Class<C> requestClass, Object... args)
    {
        if (value == null)
        {
            return defaultValue;
        }
        IFieldParser fieldParser = getParserByFieldType(requestClass);
        if (fieldParser == null)
        {
            return defaultValue;
        }
        Object parsedValue;
        if (value instanceof String)
        {
            parsedValue = fieldParser.parseValue(null, requestClass, String.valueOf(value), defaultValue, args);
        }
        else
        {
            parsedValue = fieldParser.parseValueByObjectValue(requestClass, value, defaultValue, args);
        }
        if (parsedValue == null && fieldParser.isDigitValue())
        {
            return defaultValue;
        }
        return requestClass.cast(parsedValue);
    }

    @SuppressWarnings("unchecked")
    public static <C> C[] parseOneDimensionArray(Class<C> arrayClass, String value, C[] defaultValue, String spliterator)
    {
        IFieldParser iFieldParser = getParserByFieldType(Array[].class);
        if (iFieldParser == null)
        {
            return defaultValue;
        }
        return (C[]) iFieldParser.parseValue(null, arrayClass, value, defaultValue, spliterator);
    }

    @SuppressWarnings("unchecked")
    public static <C> C[][] parseTwoDimensionArray(Class<C> arrayClass, String value, C[][] defaultValue, String firstDimSpliterator, String secondDimSpliterator)
    {
        IFieldParser iFieldParser = getParserByFieldType(Array[][].class);
        if (iFieldParser == null)
        {
            return defaultValue;
        }
        return (C[][]) iFieldParser.parseValue(null, arrayClass, value, defaultValue, firstDimSpliterator, secondDimSpliterator);
    }

    @SuppressWarnings("unchecked")
    public static <C, T extends Collection<C>> T parseCollection(String value, Class<T> requestCollectionClass, Class<C> genericClass, Collection<?> defaultValue, String delimiter)
    {
        IFieldParser iFieldParser = getParserByFieldType(Collection.class);
        if (iFieldParser == null)
        {
            return (T) defaultValue;
        }
        return (T) iFieldParser.parseValue(null, requestCollectionClass, value, defaultValue, genericClass, delimiter);
    }
}
