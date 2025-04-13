

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


public class ReflectionUtils {

    private ReflectionUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static Method getGetter(Class<?> clazz, String fieldName) throws NoSuchMethodException {
        return clazz.getDeclaredMethod("get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1));
    }

    public static <T> T getValue(Object object, String fieldName) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method getter = getGetter(object.getClass(), fieldName);
        return (T) getter.invoke(object);
    }

    public static Method getSetter(Class<?> clazz, String fieldName, Class<?> fieldClass) throws NoSuchMethodException {
        return clazz.getDeclaredMethod("set" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1), fieldClass);
    }

    public static void setValue(Object object, String fieldName, Object value) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method setter = getSetter(object.getClass(), fieldName, value.getClass());
        setter.invoke(object, value);
    }
}