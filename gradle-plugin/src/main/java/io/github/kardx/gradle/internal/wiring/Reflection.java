package io.github.kardx.gradle.internal.wiring;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.gradle.api.GradleException;

/** 다른 플러그인 클래스에 컴파일 의존을 두지 않으려고 리플렉션으로 호출한다. */
final class Reflection {
    private Reflection() {
    }

    /** 메서드가 없거나 호출이 실패하면 어떤 플러그인·메서드인지 담은 {@link GradleException} 을 던진다. */
    static Object invoke(Object target, String name, Class<?>[] types, Object... args) {
        try {
            Method method = target.getClass().getMethod(name, types);

            return method.invoke(target, args);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new GradleException("Unsupported " + target.getClass().getName() + ": no " + name, e);
        } catch (InvocationTargetException e) {
            throw new GradleException("Failed to call " + name + " on " + target.getClass().getName(), e.getCause());
        }
    }

    /**
     * 인자 없는 메서드를 부른다. 플러그인 버전에 따라 없을 수 있는 메서드용: 메서드가 없으면 null.
     * 메서드가 있는데 부를 수 없거나 메서드 안에서 실패하면 {@link #invoke} 처럼 원인을 담은 {@link GradleException} 을 던진다.
     */
    static Object invokeIfPresent(Object target, String name) {
        Method method;

        try {
            method = target.getClass()
                .getMethod(name);
        } catch (NoSuchMethodException e) {
            // 이 버전의 플러그인에는 없는 선택적 메서드다. 호출한 쪽이 기본값을 쓴다
            return null;
        }

        try {
            return method.invoke(target);
        } catch (IllegalAccessException e) {
            throw new GradleException("Unsupported " + target.getClass().getName() + ": cannot call " + name, e);
        } catch (InvocationTargetException e) {
            throw new GradleException("Failed to call " + name + " on " + target.getClass().getName(), e.getCause());
        }
    }

    /**
     * 메서드가 있으면 부르고 true, 없으면 false. 플러그인 버전에 따라 없을 수 있는 setter 용. 호출이 실패하면 {@link #invoke} 처럼
     * {@link GradleException}.
     */
    static boolean invokeIfPresent(Object target, String name, Class<?>[] types, Object... args) {
        try {
            target.getClass()
                .getMethod(name, types);
        } catch (NoSuchMethodException e) {
            // 이 버전의 플러그인에는 없는 선택적 메서드다. 호출한 쪽이 건너뛴다
            return false;
        }

        invoke(target, name, types, args);

        return true;
    }
}
