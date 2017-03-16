package chrome.cast;

import chrome.cast.ChromeCast.A0;
import chrome.cast.ChromeCast.A1;
import chrome.cast.ChromeCast.A2;
import jsinterop.annotations.JsType;

@JsType(isNative = true)
public class Session {
    public String sessionId;
    public String appId;
    public String displayName;
    public Receiver receiver;
    /** @param listener (isAlive) */
    public native void addUpdateListener(A1<Boolean> listener);
    /** @param listener (namespace, message) */
    public native void addMessageListener(String namespace, A2<String, String> listener);
    public native void sendMessage(String namespace, String message, A0 onSuccess, A1<Object> onError);
    public native void stop(A0 onSuccess, A1<Object> onError);
}
