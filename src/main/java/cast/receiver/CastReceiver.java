package cast.receiver;

import jsinterop.annotations.JsOverlay;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

@JsType(isNative = true, namespace = "cast", name = "receiver")
public class CastReceiver {
    public static String VERSION;
    @JsProperty(namespace = JsPackage.GLOBAL) public static Object cast;
    @JsOverlay public static boolean isAvailable() { return cast != null; }
}
