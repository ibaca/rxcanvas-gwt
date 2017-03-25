package cast.receiver;

import chrome.cast.ChromeCast;
import chrome.cast.ChromeCast.A1;
import jsinterop.annotations.JsType;

@JsType(isNative = true)
public class CastMessageBus implements ChromeCast.EventTarget {
    public A1<Event> onMessage;

    /** Sends a message to all the senders connected. */
    public native void broadcast(Object data);

    /** Sends a message to a specific sender. */
    public native void send(String senderId, Object data);

    @Override public native <E> void addEventListener(String type, A1<E> listener);
    @Override public native <E> void addEventListener(String type, A1<E> listener, boolean useCapture);
    @Override public native void removeEventListener(String type, A1<?> listener);
    @Override public native void removeEventListener(String type, A1<?> listener, boolean useCapture);

    @JsType(isNative = true)
    public static class Event<T> {
        public T data;
        public String senderId;
    }
}
