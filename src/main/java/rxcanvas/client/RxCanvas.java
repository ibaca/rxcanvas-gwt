package rxcanvas.client;

import static com.intendia.rxgwt.client.RxEvents.mouseDown;
import static com.intendia.rxgwt.client.RxEvents.mouseMove;
import static com.intendia.rxgwt.client.RxEvents.mouseUp;
import static com.intendia.rxgwt.client.RxEvents.touchEnd;
import static com.intendia.rxgwt.client.RxEvents.touchMove;
import static com.intendia.rxgwt.client.RxEvents.touchStart;
import static com.intendia.rxgwt.client.RxGwt.keyPress;
import static rx.Observable.merge;

import com.google.gwt.canvas.client.Canvas;
import com.google.gwt.canvas.dom.client.Context;
import com.google.gwt.canvas.dom.client.Context2d;
import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.dom.client.Touch;
import com.google.gwt.event.dom.client.MouseEvent;
import com.google.gwt.user.client.ui.RootPanel;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import rx.Observable;
import rx.functions.Action1;

public class RxCanvas implements EntryPoint {
    private static final Logger log = Logger.getLogger(RxCanvas.class.getName());

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
                .buffer(2, 1);

        Observable<List<double[]>> mouseDrag$ = mouseDown(canvas).compose(log("mouse down"))
                .flatMap(e -> mouseDiff$.takeUntil(mouseUp(canvas).compose(log("mouse up"))));

        Observable<List<double[]>> touchDiff$ = touchMove(canvas)
                .map(e -> e.getTouches().get(0))
                .map(e -> canvasPosition(canvas, e))
                .buffer(2, 1);

        Observable<List<double[]>> touchDrag$ = touchStart(canvas).compose(log("touch down"))
                .flatMap(e -> touchDiff$.takeUntil(touchEnd(canvas).compose(log("touch up"))));

        Observable<String> paint$ = keyPress(canvas, '1').map(e -> "paint");
        Observable<String> erase$ = keyPress(canvas, '2').map(e -> "erase");

        Observable<String> tool$ = Observable.merge(paint$, erase$).startWith("paint")
                .compose(log("tool selected")).share();

        Observable<Action1<Context2d>> render$ = merge(mouseDrag$, touchDrag$)
                .withLatestFrom(tool$, (diff, tool) -> ctx -> {
                    switch (tool) {
                        case "paint": strokeLine(ctx,
                                diff.get(0)[0], diff.get(0)[1],
                                diff.get(1)[0], diff.get(1)[1]);
                            break;
                        case "erase": ctx.clearRect(
                                diff.get(0)[0] - 5, diff.get(0)[1] - 5,
                                10, 10);
                            break;
                    }
                });

        render$.subscribe(action -> action.call(canvas2d));

        RootPanel.get().add(canvas);
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

    public void strokeRect(Context2d ctx, int x, int y, int width, int height) {
        ctx.beginPath();
        ctx.rect(x, y, width, height);
        ctx.stroke();

    }

    public static void strokeLine(Context2d ctx, double x1, double y1, double x2, double y2) {
        ctx.beginPath();
        ctx.moveTo(x1, y1);
        ctx.lineTo(x2, y2);
        ctx.stroke();
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
