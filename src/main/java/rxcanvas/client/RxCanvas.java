package rxcanvas.client;

import static com.intendia.rxgwt.client.RxEvents.mouseDown;
import static com.intendia.rxgwt.client.RxEvents.mouseMove;
import static com.intendia.rxgwt.client.RxEvents.mouseUp;
import static com.intendia.rxgwt.client.RxEvents.touchEnd;
import static com.intendia.rxgwt.client.RxEvents.touchMove;
import static com.intendia.rxgwt.client.RxEvents.touchStart;
import static com.intendia.rxgwt.client.RxGwt.keyPress;
import static java.util.Arrays.asList;
import static rx.Observable.merge;

import com.google.gwt.canvas.client.Canvas;
import com.google.gwt.canvas.dom.client.Context;
import com.google.gwt.canvas.dom.client.Context2d;
import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.dom.client.Touch;
import com.google.gwt.event.dom.client.MouseEvent;
import com.google.gwt.user.client.ui.RootPanel;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import rx.Observable;
import rx.functions.Action1;

public class RxCanvas implements EntryPoint {
    private static final Logger log = Logger.getLogger(RxCanvas.class.getName());
    private static List<String> COLORS = asList("#828b20", "#b0ac31", "#cbc53d", "#fad779",
            "#f9e4ad", "#faf2db", "#563512", "#9b4a0b", "#d36600", "#fe8a00", "#f9a71f");

    @Override public void onModuleLoad() {
        int width = RootPanel.getBodyElement().getClientWidth();
        int height = RootPanel.getBodyElement().getClientHeight();

        Canvas canvas = Canvas.createIfSupported();
        canvas.setWidth(width + "px");
        canvas.setHeight(height + "px");
        Context2d canvas2d = canvas.getContext2d();

        int ratio = ratio(canvas2d);
        canvas.setCoordinateSpaceWidth(width * ratio);
        canvas.setCoordinateSpaceHeight(height * ratio);
        canvas2d.scale(ratio, ratio);

        Observable<List<double[]>> mouseDiff$ = mouseMove(canvas)
                .map(e -> canvasPosition(canvas, e))
                .buffer(3, 1);

        Observable<List<double[]>> mouseDrag$ = mouseDown(canvas).compose(log("mouse down"))
                .flatMap(e -> mouseDiff$.takeUntil(mouseUp(canvas).compose(log("mouse up"))));

        Observable<List<double[]>> touchDiff$ = touchMove(canvas)
                .map(e -> e.getTouches().get(0))
                .map(e -> canvasPosition(canvas, e))
                .buffer(2, 1);

        Observable<List<double[]>> touchDrag$ = touchStart(canvas).compose(log("touch down"))
                .flatMap(e -> touchDiff$.takeUntil(touchEnd(canvas).compose(log("touch up"))));

        Observable<Object> down$ = merge(mouseDown(canvas), touchStart(canvas));
        Observable<List<double[]>> drag$ = merge(mouseDrag$, touchDrag$);

        Observable<String> paint$ = keyPress(canvas, '1').map(e -> "paint").startWith("default");
        Observable<String> erase$ = keyPress(canvas, '2').map(e -> "erase");

        // return a different color on each mouse down

        Observable<String> color$ = down$.startWith((Object) null)
                .zipWith(Observable.from(COLORS).repeat(), (l, r) -> r);
        Observable<Double> stroke$ = down$.startWith((Object) null)
                .zipWith(Observable.from(() -> new Iterator<Double>() {
                    @Override public boolean hasNext() { return true; }
                    @Override public Double next() { return (Math.random() * 30) + 10; }
                    @Override public void remove() { }
                }), (l, r) -> r);
        Observable<Options> options$ = Observable.combineLatest(color$, stroke$, Options::new);

        // drag painting using sequential color
        Observable<Observable<Action1<Context2d>>> painting$ = paint$.map(e -> drag$
                .<Options, Action1<Context2d>>withLatestFrom(options$,
                        (diff, options) -> ctx -> paint(options.color, options.stroke, diff, ctx)));

        // drag erasing
        Observable<Observable<Action1<Context2d>>> erasing$ = erase$.map(e -> drag$
                .<Action1<Context2d>>map(diff -> ctx -> erase(diff, ctx)));

        Observable<Action1<Context2d>> render$ = Observable.switchOnNext(Observable.merge(painting$, erasing$));
        render$.subscribe(action -> action.call(canvas2d));

        RootPanel.get().add(canvas);
    }

    static class Options {
        final String color;
        final Double stroke;
        Options(String color, Double stroke) {
            this.color = color;
            this.stroke = stroke;
        }
    }

    private void erase(List<double[]> diff, Context2d ctx) {
        ctx.clearRect(diff.get(0)[0] - 5, diff.get(0)[1] - 5, 10, 10);
    }

    private void paint(String color, Double stroke, List<double[]> diff, Context2d ctx) {
        tx(ctx, ignore -> {
            ctx.beginPath();
            ctx.bezierCurveTo(
                    diff.get(0)[0], diff.get(0)[1],
                    diff.get(1)[0], diff.get(1)[1],
                    diff.get(2)[0], diff.get(2)[1]);
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

    public static native int ratio(Context context) /*-{
        var devicePixelRatio = $wnd.devicePixelRatio || 1,
            backingStoreRatio = context.backingStorePixelRatio ||
                context.webkitBackingStorePixelRatio ||
                context.mozBackingStorePixelRatio ||
                context.msBackingStorePixelRatio ||
                context.oBackingStorePixelRatio || 1,
            ratio = devicePixelRatio / backingStoreRatio;
        return ratio;
    }-*/;

    private <T> Observable.Transformer<T, T> log(String prefix) {
        if (!log.isLoggable(Level.INFO)) return o -> o;
        else return o -> o.doOnNext(n -> log.info(prefix + ": " + Objects.toString(n)));
    }
}
