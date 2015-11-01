package rxcanvas.client;

import static rx.Observable.merge;
import static rxcanvas.client.RxGwt.mouseDown;
import static rxcanvas.client.RxGwt.mouseMove;
import static rxcanvas.client.RxGwt.mouseUp;
import static rxcanvas.client.RxGwt.touchEnd;
import static rxcanvas.client.RxGwt.touchMove;
import static rxcanvas.client.RxGwt.touchStart;

import com.google.gwt.canvas.client.Canvas;
import com.google.gwt.canvas.dom.client.Context;
import com.google.gwt.canvas.dom.client.Context2d;
import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.dom.client.Touch;
import com.google.gwt.event.dom.client.MouseEvent;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.RootPanel;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import rx.Observable;
import rx.functions.Action1;

public class RxCanvas implements EntryPoint {
    private static final Logger log = Logger.getLogger(RxCanvas.class.getName());

    @Override public void onModuleLoad() {
        int width = Window.getClientWidth();
        int height = Window.getClientHeight();

        Canvas canvas = Canvas.createIfSupported();
        canvas.setWidth(width + "px");
        canvas.setHeight(height + "px");
        Context2d canvas2d = canvas.getContext2d();

        int ratio = ratio(canvas2d);
        canvas.setCoordinateSpaceWidth(width * ratio);
        canvas.setCoordinateSpaceHeight(height * ratio);
        canvas2d.scale(ratio, ratio);

        tx(canvas2d, ctx -> {
            // frame
            ctx.setStrokeStyle("#ccc");
            strokeRect(ctx, 10, 10, width - 20, height - 20);
            ctx.setLineWidth(10);
            ctx.setStrokeStyle("#eee");
            strokeRect(ctx, 5, 5, width - 10, height - 10);
        });

        Observable<List<double[]>> mouseDiff = mouseMove(canvas)
                .map(e -> canvasPosition(canvas, e))
                .buffer(2, 1);

        Observable<List<double[]>> mouseDrag = mouseDown(canvas).compose(log("mouse down"))
                .flatMap(e -> mouseDiff.takeUntil(mouseUp(canvas).compose(log("mouse up"))));

        Observable<List<double[]>> touchDiff = touchMove(canvas)
                .map(e -> e.getTouches().get(0))
                .map(e -> canvasPosition(canvas, e))
                .buffer(2, 1);

        Observable<List<double[]>> touchDrag = touchStart(canvas).compose(log("touch down"))
                .flatMap(e -> touchDiff.takeUntil(touchEnd(canvas).compose(log("touch up"))));

        Observable<Action1<Context2d>> paint = merge(mouseDrag, touchDrag)
                .map(diff -> ctx -> strokeLine(ctx,
                        diff.get(0)[0], diff.get(0)[1],
                        diff.get(1)[0], diff.get(1)[1]
                ));

        paint.subscribe(action -> action.call(canvas2d));

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
        return o -> o.doOnNext(n -> log.info(prefix + ": " + Objects.toString(n)));
    }
}
