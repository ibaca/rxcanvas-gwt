package chrome.cast;

import jsinterop.annotations.JsFunction;
import jsinterop.annotations.JsOverlay;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

@JsType(isNative = true, namespace = "chrome", name = "cast")
public class ChromeCast {

    public static Boolean isAvailable;
    public static int timeout;
    public static int[] version;

    public native static void initialize(ApiConfig apiConfig, A0 onInitSuccess, A1<Object> onError);
    public native static void requestSession(A1<Session> onSuccess, A1<Object> onError);

    @JsProperty(namespace = JsPackage.GLOBAL) private static Object chrome;
    @JsProperty(namespace = "chrome") private static Object cast;
    @JsOverlay public static  boolean isAvailable() { return chrome != null && cast != null && isAvailable; }

    @FunctionalInterface @JsFunction public interface A0 {
        void apply();
    }

    @FunctionalInterface @JsFunction public interface A1<T> {
        void apply(T t);
    }

    @FunctionalInterface @JsFunction public interface A2<T1, T2> {
        void apply(T1 t1, T2 t2);
    }
}
