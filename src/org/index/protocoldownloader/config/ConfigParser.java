package org.index.protocoldownloader.config;

import org.index.protocoldownloader.config.annotations.ConfigParameter;
import org.index.protocoldownloader.util.StatSet;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * @author Index
 */
public abstract class ConfigParser
{
    private boolean _loaded;
    private StatSet _parsedParameters;
    private Map<String, Method> _declaredMethods;

    public ConfigParser()
    {
        _parsedParameters = StatSet.EMPTY_SET;
    }

    public void load()
    {
        if (_loaded)
        {
            return;
        }
        _loaded = true;
        Properties properties = new Properties();
        final File file = new File(getConfigPath());
        if (file.exists())
        {
            try (FileInputStream fileInputStream = new FileInputStream(file);
                 InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream, Charset.defaultCharset()))
            {
                properties.load(inputStreamReader);
            }
            catch (Exception e)
            {
                properties = null;
                System.err.println("[" + getAttachedConfig().getSimpleName() + "] There was an error loading config reason: " + e.getMessage());
                // LOGGER.warning("[" + _file.getName() + "] There was an error loading config reason: " + e.getMessage());
            }
        }
        if (properties != null)
        {
            final Object newInstanceOfRequestedConfig = tryToCreateInstance();
            if (newInstanceOfRequestedConfig == null)
            {
                System.err.println(getClass().getSimpleName() + ": " + "error while loading config. Cannot create new instance of attached config!");
                return;
            }
            _declaredMethods = new HashMap<>();
            final StatSet temporary = new StatSet();
            fillMethodMap(newInstanceOfRequestedConfig, _declaredMethods);
            fillStatSet(temporary, properties);
            fillParametersWithAnnotation(newInstanceOfRequestedConfig, temporary);
            _parsedParameters = temporary;
            invokeOnLoadMethod(newInstanceOfRequestedConfig);
        }
    }

    public void reload()
    {
        _loaded = false;
        load();
    }

    private Object tryToCreateInstance()
    {
        try
        {
            return getAttachedConfig().getConstructor().newInstance();
        }
        catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException | IllegalArgumentException | InstantiationException | ExceptionInInitializerError e)
        {
            e.printStackTrace();
            return null;
        }
    }

    private void fillMethodMap(Object activeInstanceOfConfig, Map<String, Method> mapOfMethods)
    {
        for (Method method : activeInstanceOfConfig.getClass().getDeclaredMethods())
        {
            if (mapOfMethods.put(method.getName(), method) != null)
            {
                System.err.println(getAttachedConfig().getSimpleName() + ": " + "Duplication method " + method.getName() + " in class!");
            }
        }
    }

    private void fillStatSet(StatSet statSet, Properties properties)
    {
        for (String key : properties.stringPropertyNames())
        {
            if (statSet.contains(key))
            {
                System.err.println(getClass().getSimpleName() + ": " + getConfigPath() + "Rewriting value " + key + ";");
            }
            statSet.set(key, properties.getOrDefault(key, null));
        }
    }

    private void fillParametersWithAnnotation(Object instanceOfRequestedConfig, StatSet statSet)
    {
        if (getAttachedConfig() == null || statSet.isEmpty())
        {
            return;
        }
        for (Field field : getAttachedConfig().getDeclaredFields())
        {
            if (!Modifier.isStatic(field.getModifiers()) ||
                    Modifier.isFinal(field.getModifiers()) || Modifier.isPrivate(field.getModifiers()) || Modifier.isProtected(field.getModifiers()))
            {
                continue;
            }
            Boolean originalAccessable = false;
            try
            {
                final ConfigParameter annotation = getAnnotationOrNull(field);
                if (annotation == null || annotation.ignoredParameter())
                {
                    originalAccessable = null;
                    continue;
                }
                originalAccessable = field.canAccess(null);
                field.setAccessible(true);

                if (annotation.setParameterMethod().isEmpty())
                {
                    tryToAssignVariable(instanceOfRequestedConfig, annotation, field, statSet);
                }
                else
                {
                    callMethodForAssigningValue(instanceOfRequestedConfig, annotation, field, statSet);
                }
            }
            catch (Exception e)
            {
                originalAccessable = null;
            }
            finally
            {
                if (originalAccessable != null)
                {
                    field.setAccessible(originalAccessable);
                }
            }
        }
    }

    private void invokeOnLoadMethod(Object invokeInstance)
    {
        for (final Class<?> _interface : getAttachedConfig().getInterfaces())
        {
            if (_interface.isAssignableFrom(IConfig.class))
            {
                try
                {
                    final Method method = _interface.getDeclaredMethod("onLoad");
                    method.invoke(invokeInstance);
                    break;
                }
                catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException | IllegalArgumentException e)
                {
                    e.printStackTrace();
                }
            }
        }
    }

    private ConfigParameter getAnnotationOrNull(Field field)
    {
        try
        {
            return field.getAnnotation(ConfigParameter.class);
        }
        catch (NullPointerException npe)
        {
            return null;
        }
    }

    private void callMethodForAssigningValue(Object instanceOfRequestedConfig, ConfigParameter annotationOfVariable, Field variable, StatSet parameters)
    {
        final Method requestedMethod = _declaredMethods.getOrDefault(annotationOfVariable.setParameterMethod(), null);
        if (requestedMethod == null)
        {
            System.err.println(getAttachedConfig().getSimpleName() + ": " + "Requested method " + annotationOfVariable.setParameterMethod() + " not exist in config file!");
            return;
        }
        Boolean originalAccessable = Modifier.isStatic(variable.getModifiers()) ? requestedMethod.canAccess(null) : requestedMethod.canAccess(instanceOfRequestedConfig);
        try
        {
            if (!Modifier.isStatic(variable.getModifiers()) && Modifier.isPrivate(variable.getModifiers()))
            {
                originalAccessable = null;
                throw new IllegalAccessException("Privates method should be static or public for chance accessible!");
            }
            requestedMethod.setAccessible(true);
            if (requestedMethod.getParameterCount() > 0)
            {
                final Class<?> requestedType = requestedMethod.getParameterTypes()[0].isArray() ? requestedMethod.getParameterTypes()[0].getComponentType() : requestedMethod.getParameterTypes()[0];
                final Double mod = annotationOfVariable.multiplyMod() == 1 ? null : annotationOfVariable.multiplyMod();
                final Double minValue = annotationOfVariable.minValue() == -1 ? null : annotationOfVariable.minValue();
                final Double maxValue = annotationOfVariable.maxValue() == -1 ? null : annotationOfVariable.maxValue();
                final boolean canBeNull = annotationOfVariable.canBeNull();
                final String spliterator = requestedMethod.getParameterTypes()[0].isArray() ? annotationOfVariable.spliterator() : null;
                final Object array = getArrayObject(parameters.getString(annotationOfVariable.parameterName(), null), spliterator, requestedMethod.getName(), requestedType, getDefaultValueOfField(variable), mod, minValue, maxValue, canBeNull);
                requestedMethod.invoke(instanceOfRequestedConfig, (requestedMethod.getParameterTypes()[0].isArray() ? array : Array.get(array, 0)));
            }
            else
            {
                requestedMethod.invoke(instanceOfRequestedConfig);
            }
        }
        catch (Exception /*| InvocationTargetException | IllegalAccessException | IllegalArgumentException*/ e)
        {
            System.err.println(getAttachedConfig().getSimpleName() + ": " + "Requested method " + annotationOfVariable.setParameterMethod() + " call error while invoke! " + e.getMessage() + ";");
        }
        finally
        {
            if (originalAccessable != null)
            {
                requestedMethod.setAccessible(originalAccessable);
            }
        }
    }

    private void tryToAssignVariable(Object instanceOfRequestedConfig, ConfigParameter annotationOfVariable, Field variable, StatSet parameters)
    {
        final Double mod = annotationOfVariable.multiplyMod() == 1 ? null : annotationOfVariable.multiplyMod();
        final Double minValue = annotationOfVariable.minValue() == -1 ? null : annotationOfVariable.minValue();
        final Double maxValue = annotationOfVariable.maxValue() == -1 ? null : annotationOfVariable.maxValue();
        final boolean canBeNull = annotationOfVariable.canBeNull();
        final Object valueInParams = variable.getType().isArray() ? getArrayObject(parameters.getString(annotationOfVariable.parameterName(), null), annotationOfVariable.spliterator(), variable.getName(), variable.getType().getComponentType(), getDefaultValueOfField(variable), mod, minValue, maxValue, canBeNull) :
                Collection.class.isAssignableFrom(variable.getType())
                ? getListObject(parameters.getString(annotationOfVariable.parameterName(), null), annotationOfVariable.spliterator(), variable, getDefaultValueOfField(variable), mod, minValue, maxValue, canBeNull)
                : getFineObject(parameters.getString(annotationOfVariable.parameterName(), null), variable.getName(), variable.getType(), getDefaultValueOfField(variable), mod, minValue, maxValue, canBeNull);
        if (!canBeNull && valueInParams == null)
        {
            System.err.println(getClass().getSimpleName() + ": " + "Null value for " + variable.getName() + ";");
            return;
        }
        try
        {
            variable.set(getAttachedConfig(), valueInParams);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private Object getDefaultValueOfField(Field field)
    {
        try
        {
            return field.get(field.getType());
        }
        catch (IllegalAccessException e)
        {
            return null;
        }
    }

    private Object getListObject(String paramValue, String splitter, Field variable, Object defaultObject, Double mod, Double minValue, Double maxValue, boolean canBeNull)
    {
        try
        {
            final Class<?> genericValue = (Class<?>) (((ParameterizedType) variable.getGenericType()).getActualTypeArguments()[0]);
            final Class<?> variableType = variable.getType();
            final Collection<Object> collection;
            if (variableType.equals(Set.class) || variableType.equals(HashSet.class) /*|| variableType.equals(TreeSet.class)*/)
            {
                collection = new HashSet<>();
            }
            else if (variableType.equals(List.class) || variableType.equals(ArrayList.class))
            {
                collection = new ArrayList<>();
            }
            else if (variableType.equals(LinkedList.class))
            {
                collection = new LinkedList<>();
            }
            else if (variableType.equals(Queue.class))
            {
                collection = new ConcurrentLinkedQueue<>();
            }
            else
            {
                System.err.println(getClass().getSimpleName() + ": " + "Not handled class of list object values " + variableType.getSimpleName() + ";");
                return defaultObject;
            }
            for (String value : paramValue.replaceAll("\\s+", "").split(splitter))
            {
                if (value == null || value.isEmpty() || value.isBlank())
                {
                    continue;
                }
                final Object fineObject = getFineObject(value, variable.getName(), genericValue, null, mod, minValue, maxValue, canBeNull);
                if (!canBeNull && fineObject == null)
                {
                    continue;
                }
                collection.add(fineObject);
            }
            return collection;
        }
        catch (Exception e)
        {
            System.err.println(getClass().getSimpleName() + ": " + "Error while parsing list from string " + variable.getName() + " = " + e.getMessage() + ";");
            return defaultObject;
        }
    }

    private Object getArrayObject(String paramValue, String splitter, String variableName, Class<?> genericValue, Object defaultObject, Double mod, Double minValue, Double maxValue, boolean canBeNull)
    {
        try
        {
            final String[] configValue;
            if (splitter == null)
            {
                configValue = new String[] { paramValue.replaceAll("\\s+", "") };
            }
            else
            {
                configValue = paramValue.replaceAll("\\s+", "").split(splitter);
            }
            final Object array = Array.newInstance(genericValue, configValue.length);
            for (int indexOfParam = 0; indexOfParam < configValue.length; indexOfParam++)
            {
                final Object valueInParams = getFineObject(configValue[indexOfParam], variableName, genericValue, "", mod, minValue, maxValue, canBeNull);
                Array.set(array, indexOfParam, valueInParams);
            }
            return array;
        }
        catch (Exception e)
        {
            System.err.println(getClass().getSimpleName() + ": " + "Error while parsing array from string " + variableName + " = " + e.getMessage() + ";");
            return defaultObject;
        }
    }

    private Object getFineObject(String paramValue, String variableName, Class<?> variableType, Object defaultObject, Double mod, Double minValue, Double maxValue, boolean canBeNull)
    {
        if (paramValue == null && defaultObject == null && canBeNull)
        {
            return null;
        }
        try
        {
            if (variableType.equals(String.class))
            {
                return paramValue == null ? String.valueOf(defaultObject) : paramValue;
            }
            else if (variableType.equals(boolean.class) || variableType.equals(Boolean.class))
            {
                return !canBeNull && paramValue != null && ((isDigit(paramValue) && !paramValue.equalsIgnoreCase("0")) || paramValue.equalsIgnoreCase("yes") || paramValue.equalsIgnoreCase("true"));
            }
            else if (variableType.equals(byte.class) || variableType.equals(Byte.class))
            {
                final BigDecimal decimal = getNumericValue(variableType.isPrimitive(), variableName, defaultObject, paramValue, canBeNull, mod, minValue, maxValue);
                return decimal != null ? decimal.byteValue() : null;
            }
            else if (variableType.equals(short.class) || variableType.equals(Short.class))
            {
                final BigDecimal decimal = getNumericValue(variableType.isPrimitive(), variableName, defaultObject, paramValue, canBeNull, mod, minValue, maxValue);
                return decimal != null ? decimal.shortValue() : null;
            }
            else if (variableType.equals(int.class) || variableType.equals(Integer.class))
            {
                final BigDecimal decimal = getNumericValue(variableType.isPrimitive(), variableName, defaultObject, paramValue, canBeNull, mod, minValue, maxValue);
                return decimal != null ? decimal.intValue() : null;
            }
            else if (variableType.equals(float.class) || variableType.equals(Float.class))
            {
                final BigDecimal decimal = getNumericValue(variableType.isPrimitive(), variableName, defaultObject, paramValue, canBeNull, mod, minValue, maxValue);
                return decimal != null ? decimal.floatValue() : null;
            }
            else if (variableType.equals(long.class) || variableType.equals(Long.class))
            {
                final BigDecimal decimal = getNumericValue(variableType.isPrimitive(), variableName, defaultObject, paramValue, canBeNull, mod, minValue, maxValue);
                return decimal != null ? decimal.longValue() : null;
            }
            else if (variableType.equals(double.class) || variableType.equals(Double.class))
            {
                final BigDecimal decimal = getNumericValue(variableType.isPrimitive(), variableName, defaultObject, paramValue, canBeNull, mod, minValue, maxValue);
                return decimal != null ? decimal.doubleValue() : null;
            }
            else if (variableType.equals(File.class))
            {
                return parseFile(paramValue, (defaultObject != null ? defaultObject.toString() : "."), getAttachedConfig().getSimpleName() + ": Error parsing " + variableName + ";");
            }
            else if (variableType.equals(Pattern.class))
            {
                return parsePattern(paramValue, (defaultObject != null ? defaultObject.toString() : ".*"), (getAttachedConfig().getSimpleName() + ": " + variableName + " pattern is invalid!"));
            }
            else
            {
                System.err.println(getAttachedConfig().getSimpleName() + ": " + "Not handled class of fine object values " + variableType.getSimpleName() + ";");
                return defaultObject;
            }
        }
        catch (Exception e)
        {
            System.err.println(getAttachedConfig().getSimpleName() + ": " + "Unknown exception with next field " + variableType.getSimpleName() + ";");
            return defaultObject;
        }
    }

    private BigDecimal getNumericValue(boolean isPrimitive, String nameofField, Object defaultValue, String value, boolean canBeNull, Double multiplyMod, Double min, Double max)
    {
        BigDecimal number;
        try
        {
            number = new BigDecimal(value);
        }
        catch (NullPointerException | NumberFormatException exception)
        {
            try
            {
                number = new BigDecimal(String.valueOf(defaultValue));
                System.err.println(getAttachedConfig().getSimpleName() + ": " + nameofField + " is null. Used default value " + number.toPlainString() + ";");
            }
            catch (NullPointerException | NumberFormatException defaultException)
            {
                if (!isPrimitive && canBeNull)
                {
                    System.err.println(getAttachedConfig().getSimpleName() + ": " + nameofField + " is null. Annotation allowed to be null. Return null;");
                    return null;
                }
                else
                {
                    number = new BigDecimal(0);
                    System.err.println(getAttachedConfig().getSimpleName() + ": " + nameofField + " is null. Used constructor value " + 0 + ";");
                }
            }
        }
        if (multiplyMod != null)
        {
            number = number.multiply(BigDecimal.valueOf(multiplyMod));
        }
        if (min != null)
        {
            number = number.max(BigDecimal.valueOf(min));
        }
        if (max != null)
        {
            number = number.max(BigDecimal.valueOf(max));
        }
        return number;
    }

    public StatSet getParsedData()
    {
        return _parsedParameters;
    }

    public abstract String getConfigPath();

    public abstract Class<?> getAttachedConfig();

    private static final Pattern DUMMY_PATTERN = Pattern.compile(".*");

    public static Pattern parsePattern(String requestedPattern, String defaultValue, String exceptionMessage)
    {
        if (requestedPattern != null && requestedPattern.equals(DUMMY_PATTERN.pattern()))
        {
            return DUMMY_PATTERN;
        }
        Pattern pattern = defaultValue.equals(DUMMY_PATTERN.pattern()) ? DUMMY_PATTERN : Pattern.compile(defaultValue);
        if (requestedPattern == null)
        {
            return pattern;
        }
        try
        {
            pattern = Pattern.compile(requestedPattern);
        }
        catch (PatternSyntaxException e)
        {
            System.err.println(exceptionMessage);
        }
        return pattern;
    }

    public static File parseFile(String requestedPath, String defaultValue, String exceptionMessage)
    {
        try
        {
            return new File(requestedPath.replaceAll("\\\\", "/")).getCanonicalFile();
        }
        catch (Exception e)
        {
            System.err.println(exceptionMessage);
            return new File(defaultValue.replaceAll("\\\\", "/"));
        }
    }

    public static boolean isDigit(String text)
    {
        if ((text == null) || text.isEmpty())
        {
            return false;
        }
        for (char character : text.toCharArray())
        {
            if (!Character.isDigit(character))
            {
                return false;
            }
        }
        return true;
    }
}
