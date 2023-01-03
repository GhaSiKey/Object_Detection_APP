package mmdeploy.widget;


import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

import java.util.LinkedList;
import java.util.List;

public class PaintingView extends View {

    private final List<DrawListener> listeners = new LinkedList<>();

    public PaintingView(final Context context, AttributeSet attrs) { super(context, attrs); }

    public void setListeners(DrawListener callback) {
        listeners.add(callback);
    }

    @Override
    public synchronized void draw(Canvas canvas) {
        super.draw(canvas);
        for (DrawListener listener : listeners){
            listener.drawListener(canvas);
        }
    }

}
