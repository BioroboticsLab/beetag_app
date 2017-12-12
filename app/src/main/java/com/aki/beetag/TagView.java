package com.aki.beetag;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import java.util.List;

public class TagView extends SubsamplingScaleImageView {

    private int strokeWidth;
    private int tagCircleRadius = 0;
    private List<Tag> tagsOnImage;
    private Paint paint;

    // helper variables to avoid repeated allocation while drawing
    private int bit;
    private Path path;
    private PointF tagCenterInView;
    private float tagRadiusInView;
    private RectF innerCircle;
    private RectF outerCircle;
    private float orientationDegrees;
    private RectF orientationCircle;

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
        paint = new Paint();
        paint.setAntiAlias(true);
        path = new Path();
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

        // tags
        for (Tag tag : tagsOnImage) {
            drawTagVisualization(canvas, tag);
        }

        // tagging circle
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setColor(Color.argb(140, 255, 255, 255)); // white
        canvas.drawCircle(getWidth()/2, getHeight()/2, tagCircleRadius + strokeWidth, paint);
        paint.setColor(Color.argb(140, 0, 0, 0)); // black
        canvas.drawCircle(getWidth()/2, getHeight()/2, tagCircleRadius, paint);
    }

    private void drawTagVisualization(Canvas canvas, Tag tag) {
        paint.setStyle(Paint.Style.FILL);
        tagCenterInView = sourceToViewCoord(tag.getCenterX(), tag.getCenterY());
        tagRadiusInView = tag.getRadius() * getScale();
        innerCircle = new RectF(
                tagCenterInView.x - (tagRadiusInView*1.05f),
                tagCenterInView.y - (tagRadiusInView*1.05f),
                tagCenterInView.x + (tagRadiusInView*1.05f),
                tagCenterInView.y + (tagRadiusInView*1.05f)
        );
        outerCircle = new RectF(
                tagCenterInView.x - (tagRadiusInView*1.72f),
                tagCenterInView.y - (tagRadiusInView*1.72f),
                tagCenterInView.x + (tagRadiusInView*1.72f),
                tagCenterInView.y + (tagRadiusInView*1.72f)
        );
        orientationDegrees = (float) Math.toDegrees(tag.getOrientation());
        for (int i = 0; i < 12; i++) {
            bit = (tag.getBeeId() >> i) & 1;
            if (bit == 1) {
                paint.setColor(Color.argb(255, 253, 246, 227)); // light
            } else {
                paint.setColor(Color.argb(255, 0, 43, 54)); // dark
            }
            path.arcTo(outerCircle, orientationDegrees + (i*30), 30, false);
            path.arcTo(innerCircle, (orientationDegrees + (i*30) + 30) % 360, -30, false);
            path.close();
            canvas.drawPath(path, paint);
            path.reset();
        }
        // draw orientation circle on the inside
        orientationCircle = new RectF(
                tagCenterInView.x - (tagRadiusInView*1.76f),
                tagCenterInView.y - (tagRadiusInView*1.76f),
                tagCenterInView.x + (tagRadiusInView*1.76f),
                tagCenterInView.y + (tagRadiusInView*1.76f)
        );
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(tagRadiusInView*0.08f);
        paint.setColor(Color.argb(255, 0, 43, 54)); // dark
        canvas.drawArc(orientationCircle, (orientationDegrees + 90) % 360, 180, false, paint);
        paint.setColor(Color.argb(255, 253, 246, 227)); // light
        canvas.drawArc(orientationCircle, (orientationDegrees - 90) % 360, 180, false, paint);
    }

    public int getTagCircleRadius() {
        return tagCircleRadius;
    }

    public void setTagsOnImage(List<Tag> tags) {
        this.tagsOnImage = tags;
        invalidate();
    }
}
