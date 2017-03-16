package chrome.cast.games;

import chrome.cast.ChromeCast.A1;
import chrome.cast.Session;
import jsinterop.annotations.JsType;

@JsType(isNative = true)
public class GameManagerClient {
    public static native GameManagerClient getInstanceFor(Session session,
            A1<GameManagerInstanceResult> successCallback, A1<Object> errorCallback);
}
