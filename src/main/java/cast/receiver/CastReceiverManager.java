package cast.receiver;

import cast.receiver.system.Sender;
import cast.receiver.system.SystemVolumeData;
import chrome.cast.ChromeCast.A1;
import jsinterop.annotations.JsType;

@JsType(isNative = true)
public class CastReceiverManager {
    public A1<ReadyEvent> onReady;
    public A1<SenderConnectedEvent> onSenderConnected;
    public A1<SenderDisconnectedEvent> onSenderDisconnected;
    public A1<SystemVolumeChangedEvent> onSystemVolumeChanged;
    public native void setApplicationState(String statusText);
    public native Sender getSender(String senderId);
    public native Sender[] getSenders();
    public native CastMessageBus getCastMessageBus(String namespace);
    public native CastMessageBus getCastMessageBus(String namespace, String messageType);
    public native void start();
    public native void start(Config config);
    public native void strop();

    public static native CastReceiverManager getInstance();

    @JsType(isNative = true)
    public static class Event<T> {
        public String eventType;
        public T data;
    }

    @JsType(isNative = true)
    public static class ReadyEvent extends Event {
    }

    @JsType(isNative = true)
    public static class SenderConnectedEvent extends Event<String> {
        public String senderId;
        public String userAgent;
    }

    @JsType(isNative = true)
    public static class SenderDisconnectedEvent extends Event {
        public String senderId;
        public String userAgent;
        public String reason;
    }

    @JsType(isNative = true)
    public static class SystemVolumeChangedEvent extends Event<SystemVolumeData> {
    }

    @JsType(isNative = true)
    public static class Config {
        public int maxInactivity;
        public String statusText;
        public int maxPlayers;
    }
}
