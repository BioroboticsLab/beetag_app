package com.aki.beetag;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.TypedValue;

import com.davemorrissey.labs.subscaleview.ImageViewState;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import java.util.ArrayList;
import java.util.List;

public class TagView extends SubsamplingScaleImageView {

    public static final float VISUALIZATION_INNER_SCALE = 0.88f;
    public static final float VISUALIZATION_MIDDLE_SCALE = 1.0f;
    public static final float VISUALIZATION_OUTER_SCALE = 1.44f;
    private static final float VISUALIZATION_ORIENTATION_SCALE =
            (VISUALIZATION_MIDDLE_SCALE + VISUALIZATION_INNER_SCALE) / 2;

    private DecodingActivity.ViewMode viewMode;

    private int minStrokeWidth;
    private int tagCircleRadius = 0;
    private List<Tag> tagsOnImage;
    private Paint paint;

    // last view state before the user entered correction mode,
    // used to restore the view when returning to tagging mode
    private ImageViewState lastViewState;

    // helper variables to avoid repeated allocation while drawing
    private Path path;
    private PointF tagCenterInView;
    private float tagRadiusInView;
    private RectF middleCircle;
    private RectF outerCircle;
    private RectF innerCircle;
    private float orientationDegrees;
    private RectF orientationCircle;
    private ArrayList<Integer> id;


    public TagView(Context context, AttributeSet attr) {
        super(context, attr);
        initialise();
    }

    public TagView(Context context) {
        this(context, null);
    }

    private void initialise() {
        float width = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, getResources().getDisplayMetrics());
        minStrokeWidth = Math.max(1, Math.round(width));
        paint = new Paint();
        paint.setAntiAlias(true);
        path = new Path();
        viewMode = DecodingActivity.ViewMode.TAGGING_MODE;
    }

    public int getTagCircleRadius() {
        return tagCircleRadius;
    }

    public void setTagsOnImage(List<Tag> tags) {
        this.tagsOnImage = tags;
        invalidate();
    }

    public void setViewMode(DecodingActivity.ViewMode viewMode) {
        this.viewMode = viewMode;
    }

    // returns the marked tag at the given image coordinates,
    // or null if the location does not contain a tag
    @Nullable
    public Tag tagAtPosition(PointF pos) {
        for (Tag tag : tagsOnImage) {
            float distance = new PointF(pos.x - tag.getCenterX(), pos.y - tag.getCenterY()).length();
            float visualizationRadius = tag.getRadius() * VISUALIZATION_OUTER_SCALE;
            if (distance < visualizationRadius) {
                return tag;
            }
        }
        return null;
    }

    // returns the offset of the tag bit that is located at the given image position
    // (offset starting from the end)
    public int bitSegmentAtPosition(PointF pos, Tag tag) {
        PointF tagCenterToPos = new PointF(pos.x - tag.getCenterX(), pos.y - tag.getCenterY());
        float distance = tagCenterToPos.length();
        float visualizationOuterRadius = tag.getRadius() * VISUALIZATION_OUTER_SCALE;
        float visualizationInnerRadius = tag.getRadius() * VISUALIZATION_INNER_SCALE;
        if (distance < visualizationOuterRadius && distance > visualizationInnerRadius) {
            double angle = (Math.toDegrees(Math.atan2(tagCenterToPos.y, tagCenterToPos.x)) + 360) % 360;
            // rotate based on tag orientation
            angle = ((angle - Math.toDegrees(tag.getOrientation())) + 360) % 360;
            return (int) Math.round(Math.floor(angle / 30));
        } else {
            return -1;
        }
    }

    // runs a zoom and translation animation that puts the given tag
    // in the center of the screen so the user can edit it
    public void moveViewToTag(Tag tag) {
        float scale = (getWidth() * 0.95f) / (tag.getRadius() * 2 * TagView.VISUALIZATION_OUTER_SCALE);
        PointF center = new PointF(tag.getCenterX(), tag.getCenterY());
        lastViewState = getState();
        animateScaleAndCenter(scale, center)
                .withEasing(EASE_IN_OUT_QUAD)
                .withDuration(400)
                .withInterruptible(false)
                .start();
    }

    // runs a zoom and translation animation that puts the field of view
    // back to the state that it was in, before the user edited a tag
    public void moveViewBack() {
        animateScaleAndCenter(lastViewState.getScale(), lastViewState.getCenter())
                .withEasing(EASE_IN_OUT_QUAD)
                .withDuration(400)
                .withInterruptible(false)
                .start();
    }

    @Override
    protected void onReady() {
        super.onReady();
        tagCircleRadius = Math.round(getWidth() / 4);
        lastViewState = getState();
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
        if (viewMode == DecodingActivity.ViewMode.TAGGING_MODE) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            paint.setColor(Color.argb(140, 0, 0, 0)); // black
            canvas.drawCircle(getWidth() / 2, getHeight() / 2, tagCircleRadius, paint);
            paint.setStrokeWidth(1);
            paint.setColor(Color.argb(230, 255, 255, 255)); // white
            canvas.drawCircle(getWidth() / 2, getHeight() / 2, tagCircleRadius, paint);
        }
    }

    private void drawTagVisualization(Canvas canvas, Tag tag) {
        paint.setStyle(Paint.Style.FILL);
        tagCenterInView = sourceToViewCoord(tag.getCenterX(), tag.getCenterY());
        tagRadiusInView = tag.getRadius() * getScale();
        middleCircle = new RectF(
                tagCenterInView.x - (tagRadiusInView * VISUALIZATION_MIDDLE_SCALE),
                tagCenterInView.y - (tagRadiusInView * VISUALIZATION_MIDDLE_SCALE),
                tagCenterInView.x + (tagRadiusInView * VISUALIZATION_MIDDLE_SCALE),
                tagCenterInView.y + (tagRadiusInView * VISUALIZATION_MIDDLE_SCALE)
        );
        outerCircle = new RectF(
                tagCenterInView.x - (tagRadiusInView * VISUALIZATION_OUTER_SCALE),
                tagCenterInView.y - (tagRadiusInView * VISUALIZATION_OUTER_SCALE),
                tagCenterInView.x + (tagRadiusInView * VISUALIZATION_OUTER_SCALE),
                tagCenterInView.y + (tagRadiusInView * VISUALIZATION_OUTER_SCALE)
        );
        orientationDegrees = (float) Math.toDegrees(tag.getOrientation());
        id = Tag.decimalIdToBitId(tag.getBeeId());
        for (int i = 0; i < 12; i++) {
            if (id.get(i) == 1) {
                paint.setColor(Color.argb(255, 253, 246, 227)); // light
            } else {
                paint.setColor(Color.argb(255, 0, 43, 54)); // dark
            }
            path.arcTo(outerCircle, orientationDegrees + (i*30), 30, false);
            path.arcTo(middleCircle, (orientationDegrees + (i*30) + 30) % 360, -30, false);
            path.close();
            canvas.drawPath(path, paint);
            path.reset();
        }

        // orientation circle on the inside
        orientationCircle = new RectF(
                tagCenterInView.x - (tagRadiusInView * VISUALIZATION_ORIENTATION_SCALE),
                tagCenterInView.y - (tagRadiusInView * VISUALIZATION_ORIENTATION_SCALE),
                tagCenterInView.x + (tagRadiusInView * VISUALIZATION_ORIENTATION_SCALE),
                tagCenterInView.y + (tagRadiusInView * VISUALIZATION_ORIENTATION_SCALE)
        );
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth((VISUALIZATION_MIDDLE_SCALE - VISUALIZATION_INNER_SCALE) * tagRadiusInView);

        paint.setColor(Color.argb(255, 0, 43, 54)); // dark
        canvas.drawArc(orientationCircle, (orientationDegrees + 90) % 360, 180, false, paint);
        paint.setColor(Color.argb(255, 253, 246, 227)); // light
        canvas.drawArc(orientationCircle, ((orientationDegrees - 90) + 360) % 360, 180, false, paint);

        // draw outlines
        innerCircle = new RectF(
                tagCenterInView.x - (tagRadiusInView * VISUALIZATION_INNER_SCALE),
                tagCenterInView.y - (tagRadiusInView * VISUALIZATION_INNER_SCALE),
                tagCenterInView.x + (tagRadiusInView * VISUALIZATION_INNER_SCALE),
                tagCenterInView.y + (tagRadiusInView * VISUALIZATION_INNER_SCALE)
        );
        paint.setColor(Color.argb(180, 0, 0, 0)); // black
        paint.setStrokeWidth(1);
        for (int i = 0; i < 12; i++) {
            path.arcTo(outerCircle, orientationDegrees + (i*30), 30, false);
            path.arcTo(middleCircle, (orientationDegrees + (i*30) + 30) % 360, -30, false);
            canvas.drawPath(path, paint);
            path.reset();
        }
        canvas.drawArc(innerCircle, 0, 360, false, paint);
    }
}
