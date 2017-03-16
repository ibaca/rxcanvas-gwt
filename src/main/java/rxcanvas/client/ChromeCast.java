package rxcanvas.client;

import static rxcanvas.client.RxCanvas.stringify;

import chrome.cast.ApiConfig;
import chrome.cast.ChromeCast.A1;
import chrome.cast.Session;
import chrome.cast.SessionRequest;
import com.google.gwt.core.client.GWT;
import java.util.Optional;
import rx.Observable;
import rx.Single;
import rx.subjects.BehaviorSubject;

public class ChromeCast {
    static class Sender {
        private final BehaviorSubject<Optional<Session>> session = BehaviorSubject.create(Optional.empty());
        private final BehaviorSubject<Boolean> receiverAvailable = BehaviorSubject.create();
        private final A1<Object> onError = message -> GWT.log("onError: " + stringify(message));

        public Sender(String applicationId) {
            SessionRequest sessionRequest = new SessionRequest(applicationId);
            ApiConfig apiConfig = new ApiConfig(sessionRequest, this::setSession, receiver -> {
                GWT.log("ChromeCast receiver " + receiver);
                receiverAvailable.onNext(receiver.equals("available"));
            });
            chrome.cast.ChromeCast.initialize(apiConfig, () -> GWT.log("ChromeCast SDK initialized"), onError);
        }

        public Observable<Boolean> receiverAvailable() { return receiverAvailable; }

        public Observable<Optional<Session>> session() { return session; }

        public Single<Session> requestSession() {
            return Single.create(s -> chrome.cast.ChromeCast.requestSession(
                    success -> {
                        setSession(success);
                        s.onSuccess(success);
                    },
                    failure -> {
                        onError.apply(failure);
                        s.onError(new RuntimeException("Request session error: " + stringify(failure)));
                    }));
        }

        private void setSession(Session session) {
            String sessionId = session.sessionId;
            GWT.log("New session ID: " + sessionId);
            session.addUpdateListener(isAlive -> {
                GWT.log((isAlive ? "Session Updated" : "Session Removed") + ": " + sessionId);
                if (!isAlive) this.session.onNext(Optional.empty());
            });
            this.session.onNext(Optional.of(session));

        }
    }

    public static Single<String> castMessage(Session session, String namespace, String message) {
        return Single.create(s -> session.sendMessage(namespace, message,
                () -> s.onSuccess("success"),
                e -> s.onError(new RuntimeException("failure: " + stringify(e)))));
    }
}
