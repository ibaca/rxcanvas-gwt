package rxcanvas.client;

import com.google.gwt.canvas.client.Canvas;
import com.google.gwt.canvas.dom.client.Context;
import com.google.gwt.canvas.dom.client.Context2d;
import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseMoveEvent;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.RootPanel;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;
import rx.subscriptions.Subscriptions;

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

        Observable<MouseDownEvent> down = mouseDown(canvas).compose(log("down"));
        Observable<MouseUpEvent> up = mouseUp(canvas).compose(log("up"));
        Observable<List<double[]>> mouseDrag = mouseMove(canvas)
                .map(e -> new double[] {
                        e.getX() + canvas.getAbsoluteLeft(),
                        e.getY() + canvas.getAbsoluteTop() })
                .buffer(2, 1)
                .window(down, b -> up)
                .flatMap(x -> x);

        Observable<Action1<Context2d>> paint = mouseDrag
                .map(diff -> ctx -> strokeLine(ctx,
                        diff.get(0)[0], diff.get(0)[1],
                        diff.get(1)[0], diff.get(1)[1]
                ));

        paint.subscribe(action -> action.call(canvas2d));

        RootPanel.get().add(canvas);
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

    public static Observable<MouseDownEvent> mouseDown(Canvas source) {
        return Observable.create(s -> register(s, source.addMouseDownHandler(s::onNext)));
    }

    public static Observable<MouseUpEvent> mouseUp(Canvas source) {
        return Observable.create(s -> register(s, source.addMouseUpHandler(s::onNext)));
    }

    public static Observable<MouseMoveEvent> mouseMove(Canvas source) {
        return Observable.create(s -> register(s, source.addMouseMoveHandler(s::onNext)));
    }

    private static void register(Subscriber<?> s, HandlerRegistration handlerRegistration) {
        s.add(Subscriptions.create(handlerRegistration::removeHandler));
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
