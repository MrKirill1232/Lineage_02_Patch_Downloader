package org.index.patchdownloader.config.parseutils;

import org.index.patchdownloader.config.ConfigParser;
import org.index.patchdownloader.config.annotations.ConfigParameter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Index
 */
public class ConfigInfoHolder
{
    private final   Map<Field, Object>              _originalFieldValueMap;
    private final   Map<String, Method>             _configMethodMap;

    private         Map<String, Field>              _fieldsWithAnnotation = null;
    private         Map<String, ConfigParameter>    _configKeyWithAnnotation = null;

    public ConfigInfoHolder(ConfigParser configParser)
    {
        _originalFieldValueMap  = new HashMap<>(configParser.getAttachedConfig().getDeclaredFields().length);
        _configMethodMap        = new HashMap<>(configParser.getAttachedConfig().getDeclaredMethods().length);
        parseFieldCollection    (configParser);
        parseMethodCollection   (configParser);
    }

    public void parseFieldCollection(ConfigParser configParser)
    {
        _fieldsWithAnnotation = new HashMap<>();
        _configKeyWithAnnotation = new HashMap<>();
        for (Field field : configParser.getAttachedConfig().getDeclaredFields())
        {
            if (
                    // cannot assign field without assignable information
                    (!field.isAnnotationPresent(ConfigParameter.class)) ||
                    // cannot change non-static values
                    (!Modifier.isStatic(field.getModifiers())) ||
                    // cannot change final field
                    (Modifier.isFinal(field.getModifiers())) ||
                    // cannot change non-visible fields
                    (Modifier.isPrivate(field.getModifiers()) || Modifier.isProtected(field.getModifiers()))
            )
            {
                continue;
            }
            final ConfigParameter annotation = field.getAnnotation(ConfigParameter.class);
            if (annotation == null || annotation.ignoredParameter())
            {
                continue;
            }
            String configKeyValue = (annotation.parameterName().isEmpty()) ? field.getName() : annotation.parameterName();
            _fieldsWithAnnotation.put(configKeyValue, field);
            _configKeyWithAnnotation.put(configKeyValue, annotation);
            if (!_originalFieldValueMap.containsKey(field))
            {   // why you need create a try handler if it already in map?
                try
                {
                    _originalFieldValueMap.put(field, field.get(Object.class));
                }
                catch (IllegalAccessException e)
                {   // in theory - do not happand
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public void parseMethodCollection(ConfigParser configParser)
    {
        for (Method method : configParser.getAttachedConfig().getDeclaredMethods())
        {

            if (
                    // cannot change non-static values
                    (!Modifier.isStatic(method.getModifiers())) ||
                    // cannot change final field
                    (Modifier.isFinal(method.getModifiers())) ||
                    // cannot change non-visible fields
                    (Modifier.isPrivate(method.getModifiers()) || Modifier.isProtected(method.getModifiers()))
            )
            {
                continue;
            }
            _configMethodMap.put(method.getName(), method);
        }
    }

    public Map<Field, Object> getOriginalFieldValueMap()
    {
        return _originalFieldValueMap;
    }

    public Map<String, Method> getConfigMethodMap()
    {
        return _configMethodMap;
    }

    public Map<String, Field> getFieldsWithAnnotation()
    {
        return _fieldsWithAnnotation == null ? Collections.emptyMap() : _fieldsWithAnnotation;
    }

    public Map<String, ConfigParameter> getConfigKeyWithAnnotation()
    {
        return _configKeyWithAnnotation == null ? Collections.emptyMap() : _configKeyWithAnnotation;
    }

    public void clear()
    {
        if (_fieldsWithAnnotation != null)
        {
            _fieldsWithAnnotation.clear();
        }
        _fieldsWithAnnotation = null;
        if (_configKeyWithAnnotation != null)
        {
            _configKeyWithAnnotation.clear();
        }
        _configKeyWithAnnotation = null;
    }
}
