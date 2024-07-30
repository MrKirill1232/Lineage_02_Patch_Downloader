package org.index.patchdownloader.util;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class StatSet
{
    public static final StatSet EMPTY_SET = new StatSet((String) null);

    private final Map<String, Object> _availableStatSet;

    private StatSet(String dummy)
    {
        _availableStatSet = Collections.unmodifiableMap(new HashMap<>());
    }

    public StatSet()
    {
        _availableStatSet = new LinkedHashMap<>();
    }

    public StatSet(ResultSet resultSet)
    {
        final Map<String, Object> stringObjectMap = new HashMap<>();
        try
        {
            final ResultSetMetaData metaData = resultSet.getMetaData();
            for (int index = 1; index <= metaData.getColumnCount(); index++)
            {
                final String key = metaData.getColumnName(index);
                final Object value = resultSet.getObject(key);
                stringObjectMap.put(key, value);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        _availableStatSet = Collections.unmodifiableMap(stringObjectMap);
    }

    public <K, V> StatSet(Map<K, V> initialMap)
    {
        _availableStatSet = new HashMap<>();
        for (Map.Entry<K, V> entry : initialMap.entrySet())
        {
            final String key = String.valueOf(entry.getKey());
            final Object value = entry.getValue();
            _availableStatSet.put(key, value);
        }
    }

    public boolean contains(String key)
    {
        return _availableStatSet.containsKey(key);
    }

    @Deprecated
    public boolean set(String key, Object value)
    {
        return addValue(key, value);
    }

    public boolean isEmpty()
    {
        return _availableStatSet.isEmpty();
    }

    public boolean addValue(String key, Object value)
    {
        return _availableStatSet.put(key, value) != null;
    }

    public boolean removeValueByKey(String key)
    {
        return _availableStatSet.remove(key) != null;
    }

    // strongly not recommend to use!
    public boolean removeValueByValue(Object value)
    {
        for (Map.Entry<String, Object> entry : _availableStatSet.entrySet())
        {
            if (entry.getValue() == value)
            {
                return removeValueByKey(entry.getKey());
            }
        }
        return false;
    }

    public String getString(String key, String defaultValue)
    {
        return ParseUtils.parseString(getOrNull(key), defaultValue);
    }

    public boolean getBoolean(String key, boolean defaultValue)
    {
        return ParseUtils.parseBoolean(getOrNull(key), defaultValue);
    }

    public int getInt(String key, int defaultValue)
    {
        return ParseUtils.parseInteger(getOrNull(key), defaultValue);
    }

    public long getLong(String key, long defaultValue)
    {
        return ParseUtils.parseLong(getOrNull(key), defaultValue);
    }

    public float getFloat(String key, float defaultValue)
    {
        return ParseUtils.parseFloat(getOrNull(key), defaultValue);
    }

    public double getDouble(String key, double defaultValue)
    {
        return ParseUtils.parseDouble(getOrNull(key), defaultValue);
    }

    private Object getOrNull(String key)
    {
        return _availableStatSet.getOrDefault(key ,null);
    }

    public Map<String, Object> getStatSet()
    {
        return _availableStatSet;
    }

}
