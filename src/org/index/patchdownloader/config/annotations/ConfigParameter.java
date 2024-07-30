package org.index.patchdownloader.config.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Index
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConfigParameter
{
    String parameterName() default "";

    boolean ignoredParameter() default false;

    double multiplyMod() default 1;

    boolean canBeNull() default false;

    double minValue() default -1;

    double maxValue() default -1;

    /**
     * @ConfigParameter(parameterName = "GMNameColor", setParameterMethod = "getGMNameColor")
     * public static int GM_NAME_COLOR;
     *
     * private void getGMNameColor(final String value)
     * {
     *	GM_NAME_COLOR = Integer.decode("0x" + value);
     * }
     * @return
     *
     * if method will be private - make it static, because setAccessible will execute invisible exception
     * private static void setServerListType(String[] serverTypes)
     * {
     *    SERVER_LIST_TYPE = getServerTypeId(serverTypes);
     * }
     */
    String setParameterMethod() default "";

    String spliterator() default "";
}
