package rxcanvas.client;

import static com.intendia.rxgwt2.client.RxGwt.retryDelay;
import static com.intendia.rxgwt2.user.RxEvents.mouseDown;
import static com.intendia.rxgwt2.user.RxEvents.mouseMove;
import static com.intendia.rxgwt2.user.RxEvents.mouseUp;
import static com.intendia.rxgwt2.user.RxEvents.touchEnd;
import static com.intendia.rxgwt2.user.RxEvents.touchMove;
import static com.intendia.rxgwt2.user.RxEvents.touchStart;
import static com.intendia.rxgwt2.user.RxHandlers.click;
import static com.intendia.rxgwt2.user.RxUser.keyPress;
import static io.reactivex.BackpressureStrategy.BUFFER;
import static io.reactivex.BackpressureStrategy.LATEST;
import static io.reactivex.Completable.complete;
import static io.reactivex.Observable.empty;
import static io.reactivex.Observable.merge;
import static io.reactivex.Observable.timer;
import static java.lang.Boolean.TRUE;
import static java.lang.Math.random;
import static java.util.Arrays.asList;
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
import io.reactivex.Completable;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.ObservableTransformer;
import io.reactivex.functions.Consumer;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsType;
import rxcanvas.client.RxChromeCast.Receiver;
import rxcanvas.client.RxChromeCast.Sender;

public class RxCanvas implements EntryPoint {
    private static final Logger log = Logger.getLogger(RxCanvas.class.getName());
    private static final List<String> COLORS = asList("#828b20", "#b0ac31", "#cbc53d", "#fad779",
            "#f9e4ad", "#faf2db", "#563512", "#9b4a0b", "#d36600", "#fe8a00", "#f9a71f");
    // use 'mvn gwt:devmode -DapplicationId=XXYYZZ' to use a local development application id
    private static final String APPLICATION_ID = System.getProperty("applicationId");
    private static final String STROKE_CHANNEL = "urn:x-cast:com.intendia.rxcanvas-gwt";

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
                        .doOnSubscribe(s -> DOM.setCapture(canvas.getElement()))
                        .doOnDispose(() -> DOM.releaseCapture(canvas.getElement()))
                        .takeUntil(mouseUp(canvas).compose(log("mouse up"))));

        Observable<List<double[]>> touchDiff$ = touchMove(canvas)
                .map(e -> e.getTouches().get(0))
                .map(e -> canvasPosition(canvas, e))
                .buffer(3, 1);

        Observable<List<double[]>> touchDrag$ = touchStart(canvas).compose(log("touch start"))
                .flatMap(e -> touchDiff$
                        .doOnSubscribe(s -> DOM.setCapture(canvas.getElement()))
                        .doOnDispose(() -> DOM.releaseCapture(canvas.getElement()))
                        .takeUntil(touchEnd(canvas).compose(log("touch end"))));

        Flowable<?> up$ = Observable.<Object>merge(mouseUp(canvas), touchEnd(canvas))
                .toFlowable(LATEST).startWith(TRUE);
        Observable<List<double[]>> drag$ = merge(mouseDrag$, touchDrag$);

        Observable<String> paint$ = keyPress(canvas, '1').map(e -> "paint").startWith("default");
        Observable<String> erase$ = keyPress(canvas, '2').map(e -> "erase");

        // return a different color and size on each mouse down
        Flowable<String> colors$ = Flowable.fromIterable(COLORS).repeat();
        Flowable<String> color$ = up$.zipWith(colors$, (l, r) -> r).doOnNext(n -> setStyle(body, "--color", n));
        Flowable<Double> sizes$ = Flowable.defer(() -> Flowable.just(random() * 30 + 10)).repeat();
        Flowable<Double> size$ = up$.zipWith(sizes$, (l, r) -> r).doOnNext(n -> setStyle(body, "--size", n));
        Observable<Options> options$ = Flowable.combineLatest(color$, size$, Options::new).toObservable().share();
        Observable<Stroke> stroke$ = drag$.withLatestFrom(options$, (diff, options) -> {
            Stroke stroke = new Stroke();
            stroke.color = options.color;
            stroke.stroke = options.stroke;
            stroke.line = diff.toArray(new double[0][]);
            return stroke;
        });

        // drag painting using sequential color
        Observable<Observable<Consumer<Context2d>>> painting$ = paint$
                .map(e -> stroke$.map(this::paintStroke));

        // drag erasing
        Observable<Observable<Consumer<Context2d>>> erasing$ = erase$
                .map(e -> drag$.map(diff -> ctx -> erase(diff, ctx)));

        Consumer<Consumer<Context2d>> painter = action -> action.accept(canvas2d);

        // bind interactive painter
        bind("interactive painter", Observable.switchOnNext(merge(painting$, erasing$)).doOnNext(painter));

        // bind chrome cast receiver
        if (CastReceiver.isAvailable()) {
            GWT.log("Initializing chrome cast receiver…");
            Receiver receiver = new Receiver();
            Observable<Stroke> receiverChannel$ = receiver.castMessage(STROKE_CHANNEL)
                    .map(event -> RxCanvas.parse(event.data));
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
                        return click(cast).lastOrError()
                                .flatMap(click -> sender.requestSession())
                                .toObservable().retry().takeUntil(cancel$)
                                .doOnSubscribe(s -> RootPanel.get().add(panel))
                                .doOnTerminate(panel::removeFromParent)
                                .doOnDispose(panel::removeFromParent);
                    }).subscribe();
            Function<Session, Completable> castMessage = session -> stroke$.toFlowable(BUFFER)
                    .flatMapSingle(stroke -> castMessage(session, STROKE_CHANNEL, stringify(stroke)), false, 1)
                    .ignoreElements();
            bind("chrome cast sender", sender.session()
                    .switchMap(s -> s.map(castMessage).orElse(complete()).toObservable()));
        }
    }

    static void bind(String summary, Observable<?> o) {
        o.ignoreElements()
                .compose(retryDelay(att -> GWT.log("bind '" + summary + "' error: " + att.err)))
                .subscribe();
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

    private Consumer<Context2d> paintStroke(Stroke stroke) {
        return ctx -> paint(stroke.color, stroke.stroke, stroke.line, ctx);
    }

    private void paint(String color, Double stroke, double[][] diff, Context2d ctx) throws Exception {
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

    public void tx(Context2d ctx, Consumer<Context2d> draw) throws Exception {
        ctx.save();
        draw.accept(ctx);
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

    private <T> ObservableTransformer<T, T> log(String prefix) {
        if (!log.isLoggable(Level.INFO)) return o -> o;
        else return o -> o.doOnNext(n -> log.info(prefix + ": " + n));
    }
}
