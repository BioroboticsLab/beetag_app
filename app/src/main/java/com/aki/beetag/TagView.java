package com.aki.beetag;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.TypedValue;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

public class TagView extends SubsamplingScaleImageView {

    private int strokeWidth;
    private int tagCircleRadius;
    private Uri imageUri;

    public TagView(Context context, AttributeSet attr) {
        super(context, attr);
        initialise();
    }

    public TagView(Context context) {
        this(context, null);
    }

    private void initialise() {
        float width = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics());
        strokeWidth = Math.max(1, Math.round(width));
        tagCircleRadius = 0;
    }

    @Override
    protected void onReady() {
        super.onReady();
        tagCircleRadius = Math.round(getWidth() / 4);
        // TODO get tags from database to display
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!isReady()) {
            return;
        }

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setColor(Color.argb(140, 255, 255, 255)); // white
        canvas.drawCircle(getWidth()/2, getHeight()/2, tagCircleRadius + strokeWidth, paint);
        paint.setColor(Color.argb(140, 0, 0, 0)); // black
        canvas.drawCircle(getWidth()/2, getHeight()/2, tagCircleRadius, paint);
    }

    public void setImageUri(Uri imageUri) {
        this.imageUri = imageUri;
    }

    public int getTagCircleRadius() {
        return tagCircleRadius;
    }
}
