package com.aki.beetag;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.util.TypedValue;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import java.util.List;

public class TagView extends SubsamplingScaleImageView {

    private int strokeWidth;
    private int tagCircleRadius = 0;
    private List<Tag> tagsOnImage;

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
    }

    @Override
    protected void onReady() {
        super.onReady();
        tagCircleRadius = Math.round(getWidth() / 4);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!isReady()) {
            return;
        }

        Paint paint = new Paint();
        paint.setAntiAlias(true);

        // tagging circle
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setColor(Color.argb(140, 255, 255, 255)); // white
        canvas.drawCircle(getWidth()/2, getHeight()/2, tagCircleRadius + strokeWidth, paint);
        paint.setColor(Color.argb(140, 0, 0, 0)); // black
        canvas.drawCircle(getWidth()/2, getHeight()/2, tagCircleRadius, paint);

        // tags
        paint.setTextSize(20);
        paint.setStyle(Paint.Style.FILL);
        PointF tagCenterInView;
        for (Tag tag : tagsOnImage) {
            tagCenterInView = sourceToViewCoord(tag.getCenterX(), tag.getCenterY());
            canvas.drawText("" + tag.getBeeId(), tagCenterInView.x, tagCenterInView.y, paint);
        }
    }

    public int getTagCircleRadius() {
        return tagCircleRadius;
    }

    public void setTagsOnImage(List<Tag> tags) {
        this.tagsOnImage = tags;
    }
}
