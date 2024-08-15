package org.index.patchdownloader.config;

import org.index.patchdownloader.config.annotations.ConfigParameter;
import org.index.patchdownloader.config.parseutils.ConfigInfoHolder;
import org.index.patchdownloader.config.parseutils.PropertiesReader;
import org.index.patchdownloader.util.parsers.IFieldParser;
import org.index.patchdownloader.util.parsers.ParseUtils;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Index
 */
public abstract class ConfigParser implements IConfigDummyLogger
{
    protected final ConfigInfoHolder _configFieldsHolder;
    private PropertiesReader _fileReader;

    private Map<String, String> _parsedValuesFromConfig;

    public ConfigParser()
    {
        _configFieldsHolder = new ConfigInfoHolder(this);
    }

    public void load()
    {
        IConfig configInstance = createANewInstance();
        parseValuesFromConfig();
        setVariablesByValuesFromConfig(configInstance);
        _configFieldsHolder.clear();
        _fileReader.close();
        _fileReader = null;
        configInstance.onLoad();
    }

    public void reload()
    {
        Map<String, String> prevValuesMap = _parsedValuesFromConfig;

        _parsedValuesFromConfig = new HashMap<>();

        _configFieldsHolder.parseFieldCollection (this);
        _configFieldsHolder.parseMethodCollection(this);

        load();

        prevValuesMap.clear();
    }

    /**
     * <ul>
     * <li>take all values from config file
     * <li>replace illegal symbols by class, which implements by field
     * <li>after replacing - will create a String with config value (which writes after = ) and store into map
     * </ul>
     * if <code>IFieldParser</code> null - will show warning and trying to set a default value, from .java file.
     * <br><br>
     * <ul>
     * <li>берет все значения из конфигурационного файла<br>
     * <li>заменяет "запрещенные" символы вызывая парсер, привязаного класса <code>IFieldParser</code><br>
     * <li>после замены - создаем String, который является значением конфигруционной строки (оно пишется после = ) и сохраняем в словарь (Map)
     * </ul>
     * если <code>IFieldParser</code> = null - показываем предупреждение и пытаемся установить значение, которое указано в .java файле.
     */
    private void parseValuesFromConfig()
    {
        _fileReader = new PropertiesReader(new File(getConfigPath()));
        _fileReader.load();
        _parsedValuesFromConfig = new HashMap<>();
        for (String configKey : _fileReader.getKeyAndByteValue().keySet())
        {
            ConfigParameter parameter               =                                           _configFieldsHolder.getConfigKeyWithAnnotation().getOrDefault(configKey             , null);
            Field           fieldWhichWillChange    = parameter             == null ? null :    _configFieldsHolder.getFieldsWithAnnotation()   .getOrDefault(configKey             , null);
            Object          defaultValue            = fieldWhichWillChange  == null ? null :    _configFieldsHolder.getOriginalFieldValueMap()  .getOrDefault(fieldWhichWillChange  , null);
            if (fieldWhichWillChange == null)
            {
                continue;
            }
            IFieldParser    fieldParser             = ParseUtils.getParserByFieldType(fieldWhichWillChange.getType());
            if (fieldParser == null && parameter.setParameterMethod().isEmpty())
            {
                IConfigDummyLogger.log(ERROR, getClass(),
                        "Cannot parse variable, because parse method does not exist. " +
                                "Key in config '" + configKey + "'. " +
                                "Field name '" + fieldWhichWillChange.getName() + "'. " +
                                "Field class '" + fieldWhichWillChange.getType().getSimpleName() + "'. " +
                                "Used default value '" + String.valueOf(defaultValue) + "';",
                        null
                );
                tryToSetVariable(configKey, fieldWhichWillChange, defaultValue);
                continue;
            }
            String          parsedConfigValue;
            if (!parameter.setParameterMethod().isEmpty())
            {
                            parsedConfigValue       = getStringAsCharBuffer(_fileReader.getKeyAndByteValue().get(configKey), _fileReader.getBytesPerChar()).strip();
            }
            else
            {
                byte[]      replacedByteArray       = fieldParser.replaceIllegalSymbols(_fileReader.getBytesPerChar(), _fileReader.getKeyAndByteValue().get(configKey));
                            parsedConfigValue       = getStringAsCharBuffer(replacedByteArray, _fileReader.getBytesPerChar()).strip();
                            parsedConfigValue       = fieldParser.replaceIllegalSymbols(parsedConfigValue);
            }
            String configKeyValue = (parameter.parameterName().isEmpty()) ? fieldWhichWillChange.getName() : configKey;
            _parsedValuesFromConfig.put(configKeyValue, parsedConfigValue);
        }
    }

    /**
     * Will assign config value (from .ini) with config .java file in static fields<br>
     * It will used 2 methods for assign - or set variable, or call method<br>
     * in case, if annotation (which set up of variable in config.java) is null - show warn and will continue<br>
     * in case, if config value (which going from config .ini file) is null - show warn and will continue<br>
     * as result - get a boolean variable, which say variable is set or no.
     *<br><br>
     * Метод для присвоения значений из конфиг. файла .ini в переменные конфиг. файла .java как статические поля<br>
     * Он использует 2 метода для присвоения - или присвоение значения в переменную, или вызов прилогаемого метода<br>
     * в случае, если анотация (которая указана над переменной в config.java) является null - показываем предупреждение и продолжаем<br>
     * в случае, если конфиг. значение (которое берется из конфиг. .ini файла) является null - показываем предупреждение и продолжаем<br>
     * Как результат выполнения - получем boolean значение, которое говорит о успехе или провале при присвоении значения.
     *
     * @param configInstance active instance of config file with static fields and methods<br>
     *                       used for call <code>onLoad()</code> method and <code>setParameterMethod()</code> for variables<br>
     *                       активная копия файла конфигурационного файла со static полями и методами<br>
     *                       используется для вызова метода <code>onLoad()</code> и методов, указаных в <code>setParameterMethod()</code> для переменных
     */
    private void setVariablesByValuesFromConfig(IConfig configInstance)
    {
        for (String configKey : _parsedValuesFromConfig.keySet())
        {
            ConfigParameter parameter               =   _configFieldsHolder.getConfigKeyWithAnnotation().getOrDefault(configKey , null);
            String          stringValueInConfig     =   _parsedValuesFromConfig                         .getOrDefault(configKey , null);
            if (parameter == null || stringValueInConfig == null)
            {
                IConfigDummyLogger.log(ERROR, getClass(),
                        "Cannot change variable. " +
                                "Key in config '" + configKey + "'. " +
                                "Is ConfigParameter annotation null '" + (parameter == null) + "'. " +
                                "Is ConfigValue null '" + (stringValueInConfig == null) + "';",
                        null
                );
                continue;
            }
            boolean isValueAssigned = (isMethodCallAssign(parameter))
                    ? setValueAsMethod(configInstance, parameter, stringValueInConfig)
                    : setValueAsVariable(configInstance, parameter, configKey, stringValueInConfig);
            if (!isValueAssigned)
            {
                IConfigDummyLogger.log(ERROR, getClass(),
                        "Cannot assign variable. " +
                                "Key in config '" + configKey + "'. " +
                                "Used '" + (isMethodCallAssign(parameter) ? "setValueAsMethod" : "setValueAsVariable") + "'. ",
                        null
                );
            }
        }
    }

    /**
     * simple check for understand which method is required to use for set variable:
     * <ul>
     * <li>call <code>setValueAsMethod()</code></li>
     * <li>call <code>setValueAsVariable()</code></li>
     * </ul>
     * простая проверка для понимания какой именно метод использовать для присоения переменной:
     * <ul>
     * <li>вызов <code>setValueAsMethod()</code></li>
     * <li>вызов <code>setValueAsVariable()</code></li>
     * </ul>
     * @param parameter annotation, which include information of current .java variable
     * @return <code>true</code> - required a method call; <code>false</code> - required set as variable;
     */
    private static boolean isMethodCallAssign(ConfigParameter parameter)
    {
        return !(parameter.setParameterMethod().isEmpty());
    }

    /**
     * This method is a present way to assign value in static field (variable).<br>
     * Firstly we take from <code>ConfigInfoHolder.java</code> info about the field (variable)<br>
     * In case, if field will be null - we cannot set value. Return false and log the error while set value<br>
     * After check a parser class, which present as field class.<br>
     * If field parser is null - return false and log error<br>
     * When all preparations complete - obtain a defaultValue, which set in Config.java on compilation, and try to parse config value<br>
     * If parsed value is a digit - will try change parsed value by <code>ConfigParameter</code> requirements <code>(min|max|mul)</code><br>
     * After that checks the value. In case if value is null and is digit or cannot be null - returns false and log error. Trying to set default value.<br>
     * And as result - send result of method completion <code>tryToSetVariable()</code><br>
     * <br>
     * Этот метод используется для назначения значения в статические поля (переменные)<br>
     * Для начала - из класса <code>ConfigInfoHolder.java</code> получаем информацию о поле (переменной)<br>
     * В случае - если поле это null значение - мы не можем назначить значение. Возвращаем false и логгируем ошибку<br>
     * После этого проверяем доступный парсер, который класс которого получаем из типа поля<br>
     * Если парсер недсотупен - возвращаем false и логгируем ошибку<br>
     * Когда все подготовки закончены - получаем defaultValue, которое услановливается в полях Config.java при комиляции,
     *  после этого парсим значение, которое установлено в конфигурационном файле для этого поля.<br>
     * Если полученное значение - это число - меняем его значение за требованиями <code>ConfigParameter</code> - <code>(min|max|mul)</code><br>
     * После всего этого проверяем полученное значение. В случае если значение null и при этом оно цифренное или не должно быть null
     *  - возвращаем false и логируем ошибку. Так же, в этом случае пытаемся установить значение по-умолчанию.<br>
     * Итого - как результат - возвращаем результат выполнения метода, который устанавливает значение в переменену - <code>tryToSetVariable()</code><br>
     * @param configInstance not used | active instance of config file with static fields and methods<br>
     *                       не используется | активная копия файла конфигурационного файла со static полями и методами
     * @param parameter annotation up to the field (variable) which contains a primary info about config value<br>
     *                  анотация над полем (переменной), которая содержит в себе основную информацию о конфигурационном значении
     * @param stringKeyInConfig config value which set in .ini config (before = ).<br>
     *                          значение конфиг. файла, которое установлено в .ini конфиг. файле (перед = ).
     * @param stringValueInConfig config value which set in .ini config (after = ).<br>
     *                            значение конфиг. файла, которое установлено в .ini конфиг. файле (после = ).
     * @return <code>true</code> - variables is obtain a config value; <code>false</code> - cannot set a new value for requested variable
     */
    private boolean setValueAsVariable(IConfig configInstance, ConfigParameter parameter, String stringKeyInConfig, String stringValueInConfig)
    {
        Field fieldWhichWillChange = _configFieldsHolder.getFieldsWithAnnotation().getOrDefault(stringKeyInConfig, null);
        if (fieldWhichWillChange == null)
        {
            IConfigDummyLogger.log(ERROR, getClass(),
                            "Cannot parse variable, because field is not exist in '" + ConfigInfoHolder.class.getSimpleName() + "'" +
                            "Key in config '" + stringKeyInConfig + "';",
                    null);
            return false;
        }
        IFieldParser fieldParser = ParseUtils.getParserByFieldType(fieldWhichWillChange.getType());
        if (fieldParser == null)
        {
            IConfigDummyLogger.log(ERROR, getClass(),
                            "Cannot parse variable, because fieldParser is null, which not allowed by annotation. " +
                            "Key in config '" + stringKeyInConfig + "'. " +
                            "Field name '" + fieldWhichWillChange.getName() + "'. " +
                            "Field class '" + fieldWhichWillChange.getType().getSimpleName() + "';",
                    null);
            return false;
        }
        Object defaultValue = _configFieldsHolder.getOriginalFieldValueMap().getOrDefault(fieldWhichWillChange, null);
        Object parsedValue = fieldParser.parseValue(parameter, fieldWhichWillChange.getType(), stringValueInConfig, defaultValue);
        parsedValue = fieldParser.isDigitValue() ? changeDigitValueByAnnotation(parameter, fieldWhichWillChange.getType(), parsedValue, defaultValue) : parsedValue;
        if (parsedValue == null && (!parameter.canBeNull() || fieldParser.isDigitValue()))
        {
            IConfigDummyLogger.log(ERROR, getClass(),
                    (fieldParser.isDigitValue() ?
                            "Cannot parse variable, because values mark as digit, but output value is null. " :
                            "Cannot parse variable, because output value is null, which not allowed by annotation. ") +
                            "Key in config '" + stringKeyInConfig + "'. " +
                            "Field name '" + fieldWhichWillChange.getName() + "'. " +
                            "Field class '" + fieldWhichWillChange.getType().getSimpleName() + "'. " +
                            "Parse class '" + fieldParser.getClass().getSimpleName() + "'. " +
                            "Used default value '" + String.valueOf(defaultValue) + "';",
                    null);
            tryToSetVariable(stringKeyInConfig, fieldWhichWillChange, defaultValue);
            return false;
        }
        return tryToSetVariable(stringKeyInConfig, fieldWhichWillChange, parsedValue);
    }

    private Object changeDigitValueByAnnotation(ConfigParameter parameter, Class<?> requestClass, Object parsedObject, Object defaultValue)
    {
        if (parsedObject == null)
        {
            return defaultValue;
        }
        if (parameter.multiplyMod() == 1 && parameter.minValue() == -1 && parameter.maxValue() == -1)
        {
            return parsedObject;
        }
        if (ParseUtils.getParserByFieldType(requestClass) == null)
        {
            return parsedObject;
        }
        BigDecimal bigDecimal = new BigDecimal(parsedObject.toString());
        if (parameter.multiplyMod() != 1)
        {
            bigDecimal = bigDecimal.multiply(BigDecimal.valueOf(parameter.multiplyMod()));
        }
        if (parameter.minValue() != -1)
        {
            bigDecimal = bigDecimal.max(BigDecimal.valueOf(parameter.minValue()));
        }
        if (parameter.maxValue() != -1)
        {
            bigDecimal = bigDecimal.max(BigDecimal.valueOf(parameter.maxValue()));
        }
        return ParseUtils.getParserByFieldType(requestClass).parseValueByObjectValue(requestClass, bigDecimal, defaultValue);
    }

    private boolean setValueAsMethod(IConfig configInstance, ConfigParameter parameter, String stringValueInConfig)
    {
        Method requestMethod = _configFieldsHolder.getConfigMethodMap().get(parameter.setParameterMethod());
        if (requestMethod == null)
        {
            IConfigDummyLogger.log(ERROR, getClass(),
                            "Cannot bump method, method is exists in '" + ConfigInfoHolder.class.getSimpleName() + "'" +
                            "Key in config '" + parameter.parameterName() + "'. " +
                            "Method name '" + parameter.setParameterMethod() + "';",
                    null);
            return false;
        }
        Object[] arguments = getArgumentsForMethod(requestMethod, parameter, stringValueInConfig);
        tryToBumpParameterMethod(configInstance, parameter, arguments);
        return true;
    }

    private Object[] getArgumentsForMethod(Method method, ConfigParameter parameter, String stringValueInConfig)
    {
        if (method.getParameterCount() == 0)
        {
            return new Object[0];
        }
        Object[] argumentsArray = new Object[method.getParameterCount()];
        for (int index = 0; index < method.getParameterCount(); index++)
        {
            Class<?> parameterClass = method.getParameterTypes()[index];
            if (parameterClass == ConfigParameter.class)
            {
                argumentsArray[index] = parameter;
            }
            else if (parameterClass == String.class)
            {
                argumentsArray[index] = stringValueInConfig;
            }
            else
            {
                throw new UnsupportedOperationException();
            }
        }
        return argumentsArray;
    }

    private boolean tryToSetVariable(String configKey, Field changeField, Object newValue)
    {
        boolean originalAccessing = changeField.canAccess(null);
        try
        {
            try
            {   // this block is not required, but in any case - I want put it here :D
                changeField.setAccessible(true);
            }
            catch (Exception e)
            {
                IConfigDummyLogger.log(ERROR, getClass(),
                        "While set value for variable - code throw a exception. " +
                                "Code task: '" + "trying open accessible for set variable" + "'. " +
                                "Key in config '" + configKey + "'. " +
                                "Field name '" + changeField.getName() + "'. " +
                                "Field class '" + changeField.getType().getSimpleName() + "'. " +
                                "Used value '" + String.valueOf(newValue) + "';",
                        e
                );
                return false;
            }
            changeField.set(getAttachedConfig(), newValue);
            return true;
        }
        catch (Exception e)
        {
            IConfigDummyLogger.log(ERROR, getClass(),
                    "While set value for variable - code throw a exception. " +
                            "Code task: '" + "set value for variable" + "'. " +
                            "Key in config '" + configKey + "'. " +
                            "Field name '" + changeField.getName() + "'. " +
                            "Field class '" + changeField.getType().getSimpleName() + "'. " +
                            "Used value '" + String.valueOf(newValue) + "';",
                    e
            );
            return false;
        }
        finally
        {
            changeField.setAccessible(originalAccessing);
        }
    }

    private void tryToBumpParameterMethod(IConfig configInstance, ConfigParameter parameter, Object[] arguments)
    {
        if (parameter.setParameterMethod().isEmpty() || (!_configFieldsHolder.getConfigMethodMap().containsKey(parameter.setParameterMethod())))
        {
            return;
        }
        Method requestedMethod = _configFieldsHolder.getConfigMethodMap().get(parameter.setParameterMethod());
        boolean originalAccessing = requestedMethod.canAccess(null);
        try
        {
            try
            {   // this block is not required, but in any case - I want put it here :D
                requestedMethod.setAccessible(true);
            }
            catch (Exception e)
            {
                IConfigDummyLogger.log(ERROR, getClass(),
                        "While bump method - code throw a exception. " +
                                "Code task: '" + "trying open accessible for bump method" + "'. " +
                                "Key in config '" + parameter.parameterName() + "'. " +
                                "Method name '" + requestedMethod.getName() + "';",
                        e
                );
                return;
            }
            requestedMethod.invoke(configInstance, arguments);
        }
        catch (Exception e)
        {
            IConfigDummyLogger.log(ERROR, getClass(),
                    "While bump method - code throw a exception. " +
                            "Code task: '" + "bump method" + "'. " +
                            "Key in config '" + parameter.parameterName() + "'. " +
                            "Method name '" + requestedMethod.getName() + "' " +
                            "Arguments '" + Arrays.toString(arguments) + "';",
                    e
            );
        }
        finally
        {
            requestedMethod.setAccessible(originalAccessing);
        }
    }

    private IConfig createANewInstance()
    {
        Object configInstance = null;
        try
        {
            configInstance = getAttachedConfig().getConstructor().newInstance();
        }
        catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException | IllegalArgumentException | InstantiationException | ExceptionInInitializerError e)
        {
            IConfigDummyLogger.log(ERROR, getClass(),
                    "While creating a new instance - code throws error. " +
                            "Code task: '" + "create new instance for bump 'onLoad' and inner methods for config" + "'. " +
                            "Config class '" + getAttachedConfig().getSimpleName() + "';",
                    e
            );
        }
        if (configInstance == null)
        {
            RuntimeException exception = new RuntimeException("Error while creating new instance of config.");
            IConfigDummyLogger.log(ERROR, getClass(),
                    "While creating a new instance - code throws error. " +
                            "Code task: '" + "config instance is null" + "'. " +
                            "Config class '" + getAttachedConfig().getSimpleName() + "';",
                    exception
            );
            throw exception;
        }
        for (Class<?> interfaceClass : configInstance.getClass().getInterfaces())
        {
            if (interfaceClass == IConfig.class)
            {
                return (IConfig) configInstance;
            }
        }
        RuntimeException exception = new RuntimeException("Error while creating new instance of config.");
        IConfigDummyLogger.log(ERROR, getClass(),
                "While creating a new instance - code throws error. " +
                        "Code task: '" + "new config does not contains 'IConfig' interface" + "'. " +
                        "Config class '" + getAttachedConfig().getSimpleName() + "';",
                exception
        );
        throw exception;
    }

    public abstract String getConfigPath();

    public abstract Class<?> getAttachedConfig();

    public ConfigInfoHolder getConfigFieldsHolder()
    {
        return _configFieldsHolder;
    }

    public Map<String, String> getParsedValuesFromConfig()
    {
        return _parsedValuesFromConfig;
    }

    private static String getStringAsCharBuffer(byte[] inputArray, int bytesPerChar)
    {
        CharBuffer charBuffer = CharBuffer.allocate(inputArray.length);
        for (int index = 0; index < inputArray.length; index++)
        {
            char character = getCharFromByteArray(bytesPerChar, index, inputArray);
            index += bytesPerChar - 1;
            charBuffer.put(character);
        }
        return charBuffer.position() == charBuffer.capacity() ? new String(charBuffer.array()) : new String(Arrays.copyOfRange(charBuffer.array(), 0, charBuffer.position()));
    }

    private static char getCharFromByteArray(int bytesPerChar, int offset, byte... inputByteArray)
    {
        if (inputByteArray == null || inputByteArray.length == 0)
        {
            return Character.MIN_VALUE;
        }
        if (bytesPerChar == Byte.BYTES)
        {
            return (char) inputByteArray[offset];
        }
        else
        {
            ByteBuffer byteBuffer = ByteBuffer.allocate(bytesPerChar);
            for (int index = 0; index < bytesPerChar; index++)
            {
                byteBuffer.put(inputByteArray[offset + index]);
            }
            if (bytesPerChar == Short.BYTES)
            {
                return (char) Short.reverseBytes(byteBuffer.getShort(0));
            }
        }
        return Character.MIN_VALUE;
    }
}
