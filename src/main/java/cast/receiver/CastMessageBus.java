package cast.receiver;

import chrome.cast.ChromeCast.A1;
import jsinterop.annotations.JsType;

@JsType(isNative = true)
public class CastMessageBus {
    public A1<Event> onMessage;
    public native void send(String senderId, Object data);

    @JsType(isNative = true)
    public static class Event<T> {
        public T data;
        public String senderId;
    }
}
