package org.index.patchdownloader.util;

import org.index.patchdownloader.util.parsers.IFieldParser;
import org.index.patchdownloader.util.parsers.ParseUtils;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MultiKeyValueSet<T>
{
    private final Map<T, Object> _currentMultiKeyValueSet;

    public MultiKeyValueSet()
    {
        _currentMultiKeyValueSet = new HashMap<>();
    }

    public static MultiKeyValueSet<String> parseResultSet(ResultSet resultSet)
    {
        return new MultiKeyValueSet<>(String.class, resultSet);
    }

    private MultiKeyValueSet(Class<T> tClass, ResultSet resultSet)
    {
        Map<T, Object> parsedObjects = new HashMap<>();
        try
        {
            final ResultSetMetaData metaData = resultSet.getMetaData();
            for (int index = 1; index <= metaData.getColumnCount(); index++)
            {
                final String key = metaData.getColumnName(index);
                final Object value = resultSet.getObject(key);
                parsedObjects.put(tClass.cast(key), value);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        _currentMultiKeyValueSet = Collections.unmodifiableMap(parsedObjects);
    }

    public boolean isEmpty()
    {
        return _currentMultiKeyValueSet.isEmpty();
    }

    public int size()
    {
        return _currentMultiKeyValueSet.size();
    }

    public boolean containsKey(T key)
    {
        return _currentMultiKeyValueSet.containsKey(key);
    }

    public boolean containsValue(Object key)
    {
        return _currentMultiKeyValueSet.containsValue(key);
    }

    public boolean addValue(T key, Object value)
    {
        return _currentMultiKeyValueSet.put(key, value) != null;
    }

    public boolean removeValueByKey(T key)
    {
        return _currentMultiKeyValueSet.remove(key) != null;
    }

    // strongly not recommend to use!
    public boolean removeValueByValue(Object value)
    {
        if (value == null || !containsValue(value))
        {
            return true;
        }
        for (Map.Entry<T, Object> entry : _currentMultiKeyValueSet.entrySet())
        {
            if (entry.getValue().equals(value))
            {
                return removeValueByKey(entry.getKey());
            }
        }
        return false;
    }

    public Map<T, Object> getUnmodifiedSet()
    {
        return Collections.unmodifiableMap(_currentMultiKeyValueSet);
    }

    public Map<T, Object> getModifiableSet()
    {
        return _currentMultiKeyValueSet;
    }

    public Object getValueByKey(T key, Object defaultValue)
    {
        Object object = _currentMultiKeyValueSet.getOrDefault(key, null);
        return object == null ? defaultValue : object;
    }

    public <C> C parseValueByKey(T key, C defaultValue, Class<C> requestClass, Object... args)
    {
        Object object = _currentMultiKeyValueSet.getOrDefault(key, null);
        if (object == null)
        {
            return defaultValue;
        }
        return ParseUtils.parseValue(object, defaultValue, requestClass, args);
    }

    public boolean parseBooleanValue(T key, boolean defaultValue)
    {
        Object object = _currentMultiKeyValueSet.getOrDefault(key, null);
        if (object == null)
        {
            return defaultValue;
        }
        IFieldParser iFieldParser = ParseUtils.getParserByFieldType(boolean.class);
        return iFieldParser == null ? defaultValue : (boolean) iFieldParser.parseValueByObjectValue(boolean.class, object, defaultValue);
    }

    public char parseCharValue(T key, char defaultValue)
    {
        Object object = _currentMultiKeyValueSet.getOrDefault(key, null);
        if (object == null)
        {
            return defaultValue;
        }
        IFieldParser iFieldParser = ParseUtils.getParserByFieldType(char.class);
        return iFieldParser == null ? defaultValue : (char) iFieldParser.parseValueByObjectValue(char.class, object, defaultValue);
    }

    public byte parseByteValue(T key, byte defaultValue)
    {
        Object object = _currentMultiKeyValueSet.getOrDefault(key, null);
        if (object == null)
        {
            return defaultValue;
        }
        IFieldParser iFieldParser = ParseUtils.getParserByFieldType(byte.class);
        return iFieldParser == null ? defaultValue : (byte) iFieldParser.parseValueByObjectValue(byte.class, object, defaultValue);
    }

    public short parseShortValue(T key, short defaultValue)
    {
        Object object = _currentMultiKeyValueSet.getOrDefault(key, null);
        if (object == null)
        {
            return defaultValue;
        }
        IFieldParser iFieldParser = ParseUtils.getParserByFieldType(short.class);
        return iFieldParser == null ? defaultValue : (short) iFieldParser.parseValueByObjectValue(short.class, object, defaultValue);
    }

    public int parseIntValue(T key, int defaultValue)
    {
        Object object = _currentMultiKeyValueSet.getOrDefault(key, null);
        if (object == null)
        {
            return defaultValue;
        }
        IFieldParser iFieldParser = ParseUtils.getParserByFieldType(int.class);
        return iFieldParser == null ? defaultValue : (int) iFieldParser.parseValueByObjectValue(int.class, object, defaultValue);
    }

    public long parseLongValue(T key, long defaultValue)
    {
        Object object = _currentMultiKeyValueSet.getOrDefault(key, null);
        if (object == null)
        {
            return defaultValue;
        }
        IFieldParser iFieldParser = ParseUtils.getParserByFieldType(long.class);
        return iFieldParser == null ? defaultValue : (long) iFieldParser.parseValueByObjectValue(long.class, object, defaultValue);
    }

    public float parseFloatValue(T key, float defaultValue)
    {
        Object object = _currentMultiKeyValueSet.getOrDefault(key, null);
        if (object == null)
        {
            return defaultValue;
        }
        IFieldParser iFieldParser = ParseUtils.getParserByFieldType(float.class);
        return iFieldParser == null ? defaultValue : (float) iFieldParser.parseValueByObjectValue(float.class, object, defaultValue);
    }

    public double parseDoubleValue(T key, double defaultValue)
    {
        Object object = _currentMultiKeyValueSet.getOrDefault(key, null);
        if (object == null)
        {
            return defaultValue;
        }
        IFieldParser iFieldParser = ParseUtils.getParserByFieldType(double.class);
        return iFieldParser == null ? defaultValue : (double) iFieldParser.parseValueByObjectValue(double.class, object, defaultValue);
    }

    @SuppressWarnings("unchecked")
    public <C, G extends Collection<C>> G parseCollectionValue(T key, Class<G> requestCollectionClass, Class<C> genericClass, Collection<?> defaultValue, String delimiter)
    {
        Object object = _currentMultiKeyValueSet.getOrDefault(key, null);
        if (object == null)
        {
            return (G) defaultValue;
        }
        IFieldParser iFieldParser = ParseUtils.getParserByFieldType(Collection.class);
        if (iFieldParser == null)
        {
            return (G) defaultValue;
        }
        return (G) iFieldParser.parseValueByObjectValue(requestCollectionClass, object, defaultValue, genericClass, delimiter);
    }

}
