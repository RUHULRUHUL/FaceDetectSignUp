package com.ruhul.facerecognition;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

public class ReactOverlay extends com.ruhul.facerecognition.GraphicOverlay.Graphic {
    private int mRectcolor = Color.RED;
    private float mStrokeWidth = 4.0f;
    private Paint paint;
    private com.ruhul.facerecognition.GraphicOverlay graphicOverlay;
    private Rect rect;

    public ReactOverlay(com.ruhul.facerecognition.GraphicOverlay overlay, Rect rect) {
        super(overlay);
        paint = new Paint();
        paint.setColor(mRectcolor);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(mStrokeWidth);
        this.graphicOverlay = overlay;
        this.rect = rect;
        postInvalidate();

    }

    @Override
    public void draw(Canvas canvas) {
        RectF rectF = new RectF(rect);
        rectF.left = translateX(rectF.left);
        rectF.right = translateX(rectF.right);
        rectF.top = translateX(rectF.top);
        rectF.bottom = translateX(rectF.bottom);

        canvas.drawRect(rectF, paint);
    }
}
