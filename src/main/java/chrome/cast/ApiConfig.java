package chrome.cast;

import jsinterop.annotations.JsType;

@JsType(isNative = true)
public class ApiConfig {
    public ApiConfig(SessionRequest sessionRequest,
            ChromeCast.A1<Session> sessionListener,
            ChromeCast.A1<String> receiverListener) {
    }
}
