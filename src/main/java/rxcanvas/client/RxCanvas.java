package rxcanvas.client;

import static com.intendia.rxgwt.client.RxEvents.mouseDown;
import static com.intendia.rxgwt.client.RxEvents.mouseMove;
import static com.intendia.rxgwt.client.RxEvents.mouseUp;
import static com.intendia.rxgwt.client.RxEvents.touchEnd;
import static com.intendia.rxgwt.client.RxEvents.touchMove;
import static com.intendia.rxgwt.client.RxEvents.touchStart;
import static com.intendia.rxgwt.client.RxGwt.keyPress;
import static com.intendia.rxgwt.client.RxHandlers.click;
import static java.lang.Math.random;
import static java.util.Arrays.asList;
import static rx.Observable.empty;
import static rx.Observable.just;
import static rx.Observable.merge;
import static rx.Observable.timer;
import static rxcanvas.client.RxChromeCast.castMessage;

import cast.receiver.CastReceiver;
import chrome.cast.ChromeCast;
import chrome.cast.Session;
import com.google.gwt.canvas.client.Canvas;
import com.google.gwt.canvas.dom.client.Context;
import com.google.gwt.canvas.dom.client.Context2d;
import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Touch;
import com.google.gwt.event.dom.client.MouseEvent;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.RootPanel;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsType;
import rx.Observable;
import rx.functions.Action1;
import rxcanvas.client.RxChromeCast.Receiver;
import rxcanvas.client.RxChromeCast.Sender;

public class RxCanvas implements EntryPoint {
    private static final Logger log = Logger.getLogger(RxCanvas.class.getName());
    private static List<String> COLORS = asList("#828b20", "#b0ac31", "#cbc53d", "#fad779",
            "#f9e4ad", "#faf2db", "#563512", "#9b4a0b", "#d36600", "#fe8a00", "#f9a71f");
    private static String APPLICATION_ID = System.getProperty("applicationId"),
            STROKE_CHANNEL = "urn:x-cast:com.intendia.rxcanvas-gwt";

    @Override public void onModuleLoad() {
        Element body = RootPanel.getBodyElement();
        int width = body.getClientWidth();
        int height = body.getClientHeight();

        Canvas canvas = Canvas.createIfSupported();
        canvas.setWidth(width + "px");
        canvas.setHeight(height + "px");
        Context2d canvas2d = canvas.getContext2d();
        RootPanel.get().add(canvas);

        int ratio = ratio(canvas2d);
        canvas.setCoordinateSpaceWidth(width * ratio);
        canvas.setCoordinateSpaceHeight(height * ratio);
        canvas2d.scale(ratio, ratio);

        Observable<List<double[]>> mouseDiff$ = mouseMove(canvas)
                .map(e -> canvasPosition(canvas, e))
                .buffer(3, 1);

        Observable<List<double[]>> mouseDrag$ = mouseDown(canvas).compose(log("mouse down"))
                .flatMap(e -> mouseDiff$
                        .doOnSubscribe(() -> DOM.setCapture(canvas.getElement()))
                        .doOnUnsubscribe(() -> DOM.releaseCapture(canvas.getElement()))
                        .takeUntil(mouseUp(canvas).compose(log("mouse up"))));

        Observable<List<double[]>> touchDiff$ = touchMove(canvas)
                .map(e -> e.getTouches().get(0))
                .map(e -> canvasPosition(canvas, e))
                .buffer(3, 1);

        Observable<List<double[]>> touchDrag$ = touchStart(canvas).compose(log("touch start"))
                .flatMap(e -> touchDiff$
                        .doOnSubscribe(() -> DOM.setCapture(canvas.getElement()))
                        .doOnUnsubscribe(() -> DOM.releaseCapture(canvas.getElement()))
                        .takeUntil(touchEnd(canvas).compose(log("touch end"))));

        Observable<?> up$ = Observable.<Object>merge(mouseUp(canvas), touchEnd(canvas)).startWith((Object) null);
        Observable<List<double[]>> drag$ = merge(mouseDrag$, touchDrag$);

        Observable<String> paint$ = keyPress(canvas, '1').map(e -> "paint").startWith("default");
        Observable<String> erase$ = keyPress(canvas, '2').map(e -> "erase");

        // return a different color and size on each mouse down
        Observable<String> colors$ = Observable.from(COLORS).repeat();
        Observable<String> color$ = up$.zipWith(colors$, (l, r) -> r).doOnNext(n -> setStyle(body, "--color", n));
        Observable<Double> sizes$ = Observable.defer(() -> just(random() * 30 + 10)).repeat();
        Observable<Double> size$ = up$.zipWith(sizes$, (l, r) -> r).doOnNext(n -> setStyle(body, "--size", n));
        Observable<Options> options$ = Observable.combineLatest(color$, size$, Options::new).share();
        Observable<Stroke> stroke$ = drag$.withLatestFrom(options$, (diff, options) -> {
            Stroke stroke = new Stroke();
            stroke.color = options.color;
            stroke.stroke = options.stroke;
            stroke.line = diff.stream().toArray(double[][]::new);
            return stroke;
        });

        // drag painting using sequential color
        Observable<Observable<Action1<Context2d>>> painting$ = paint$
                .map(e -> stroke$.map(this::paintStroke));

        // drag erasing
        Observable<Observable<Action1<Context2d>>> erasing$ = erase$
                .map(e -> drag$.<Action1<Context2d>>map(diff -> ctx -> erase(diff, ctx)));

        Action1<Action1<Context2d>> painter = action -> action.call(canvas2d);

        // bind interactive painter
        bind("interactive painter", Observable.switchOnNext(merge(painting$, erasing$)).doOnNext(painter));

        // bind chrome cast receiver
        if (CastReceiver.isAvailable()) {
            GWT.log("Initializing chrome cast receiver…");
            Receiver receiver = new Receiver();
            Observable<Stroke> receiverChannel$ = receiver.castMessage(STROKE_CHANNEL)
                    .map(event -> RxCanvas.<Stroke>parse(event.data));
            bind("chrome cast receiver", receiverChannel$.map(this::paintStroke).doOnNext(painter));
            receiver.start();
        }

        // bind chrome cast sender
        if (ChromeCast.isAvailable()) {
            GWT.log("Initializing chrome cast sender…");
            Sender sender = new Sender(APPLICATION_ID);
            Observable.combineLatest(sender.receiverAvailable(), sender.session(), (av, se) -> av && !se.isPresent())
                    .switchMap(connectible -> {
                        if (!connectible) return empty();

                        // if connectible, show a awesome button to open chrome cast dialog
                        Panel panel = new HorizontalPanel(); panel.addStyleName("buttons");
                        Button cast = new Button("Connect to Chrome Cast"); panel.add(cast);
                        Button ignore = new Button("Not now"); panel.add(ignore);
                        Observable<Object> cancel$ = merge(click(ignore), timer(10, TimeUnit.SECONDS));
                        return click(cast).take(1).toSingle()
                                .flatMap(click -> sender.requestSession())
                                .toObservable().retry().takeUntil(cancel$)
                                .doOnSubscribe(() -> RootPanel.get().add(panel))
                                .doOnTerminate(panel::removeFromParent)
                                .doOnUnsubscribe(panel::removeFromParent);
                    }).subscribe();
            Function<Session, Observable<String>> castMessage = session -> stroke$.onBackpressureBuffer()
                    .concatMap(stroke -> castMessage(session, STROKE_CHANNEL, stringify(stroke)).toObservable());
            bind("chrome cast sender", sender.session().switchMap(s -> s.map(castMessage).orElse(empty())));
        }
    }

    static void bind(String summary, Observable<?> o) {
        o.retry((cnt, e) -> {
            GWT.log("bind '" + summary + "' error: " + e);
            return true;
        }).subscribe();
    }

    static class Options {
        final String color;
        final Double stroke;
        Options(String color, Double stroke) {
            this.color = color;
            this.stroke = stroke;
        }
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Object")
    public static class Stroke {
        public String color;
        public Double stroke;
        public double[][] line;

    }

    private void erase(List<double[]> diff, Context2d ctx) {
        ctx.clearRect(diff.get(0)[0] - 5, diff.get(0)[1] - 5, 10, 10);
    }

    private Action1<Context2d> paintStroke(Stroke stroke) {
        return ctx -> paint(stroke.color, stroke.stroke, stroke.line, ctx);
    }

    private void paint(String color, Double stroke, double[][] diff, Context2d ctx) {
        tx(ctx, ignore -> {
            ctx.beginPath();
            ctx.bezierCurveTo(
                    diff[0][0], diff[0][1],
                    diff[1][0], diff[1][1],
                    diff[2][0], diff[2][1]);
            ctx.setLineCap(Context2d.LineCap.ROUND);
            ctx.setLineWidth(stroke);
            ctx.setStrokeStyle(color);
            ctx.stroke();
        });
    }

    private double[] canvasPosition(Canvas canvas, MouseEvent<?> e) {
        return new double[] { e.getRelativeX(canvas.getElement()), e.getRelativeY(canvas.getElement()) };
    }

    private double[] canvasPosition(Canvas canvas, Touch t) {
        return new double[] { t.getRelativeX(canvas.getElement()), t.getRelativeY(canvas.getElement()) };
    }

    public void tx(Context2d ctx, Action1<Context2d> draw) {
        ctx.save();
        draw.call(ctx);
        ctx.restore();
    }

    @JsMethod(namespace = "JSON") public static native String stringify(Object json);
    @JsMethod(namespace = "JSON") public static native <T> T parse(Object json);
    @JsMethod(namespace = JsPackage.GLOBAL) public static native void close();

    public static native int ratio(Context context) /*-{
        var devicePixelRatio = $wnd.devicePixelRatio || 1,
            backingStoreRatio = context.backingStorePixelRatio ||
                context.webkitBackingStorePixelRatio ||
                context.mozBackingStorePixelRatio ||
                context.msBackingStorePixelRatio ||
                context.oBackingStorePixelRatio || 1;
        return devicePixelRatio / backingStoreRatio;
    }-*/;

    public static native void setStyle(Element e, String name, Object value) /*-{
        e.style.setProperty(name, value);
    }-*/;

    private <T> Observable.Transformer<T, T> log(String prefix) {
        if (!log.isLoggable(Level.INFO)) return o -> o;
        else return o -> o.doOnNext(n -> log.info(prefix + ": " + Objects.toString(n)));
    }
}
