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
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import com.davemorrissey.labs.subscaleview.ImageViewState;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import java.util.List;

public class TagView extends SubsamplingScaleImageView {

    private int strokeWidth;
    private int tagCircleRadius = 0;
    private List<Tag> tagsOnImage;
    private Paint paint;
    private final float VISUALIZATION_INNER_SCALE = 1.1f;
    private final float VISUALIZATION_MIDDLE_SCALE = 1.72f;
    private final float VISUALIZATION_OUTER_SCALE = 1.76f;
    private enum ViewMode {
        TAGGING_MODE, EDITING_MODE
    }
    private ViewMode viewMode;
    // last view state before the user entered correction mode,
    // used to restore the view when returning to tagging mode
    private ImageViewState lastViewState;

    // helper variables to avoid repeated allocation while drawing
    private int bit;
    private Path path;
    private PointF tagCenterInView;
    private float tagRadiusInView;
    private RectF innerCircle;
    private RectF outerCircle;
    private float orientationDegrees;
    private RectF orientationCircle;
    private Tag currentlyEditedTag;

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
        final GestureDetector gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (!isReady()) {
                    return true;
                }

                PointF tap = viewToSourceCoord(e.getX(), e.getY());
                if (viewMode == ViewMode.TAGGING_MODE) {
                    Tag tappedTag = tagAtPosition(tap);
                    if (tappedTag != null) {
                        currentlyEditedTag = tappedTag;
                        float scale = (getWidth() * 0.95f) / (tappedTag.getRadius() * 2 * VISUALIZATION_OUTER_SCALE);
                        PointF center = new PointF(tappedTag.getCenterX(), tappedTag.getCenterY());
                        lastViewState = getState();
                        setViewMode(ViewMode.EDITING_MODE);
                        animateScaleAndCenter(scale, center)
                                .withEasing(EASE_IN_OUT_QUAD)
                                .withDuration(400)
                                .withInterruptible(false)
                                .start();
                        setPanEnabled(false);
                        setZoomEnabled(false);
                    }
                } else if (viewMode == ViewMode.EDITING_MODE) {
                    int toggledBit = bitSegmentAtPosition(tap, currentlyEditedTag);
                    if (toggledBit != -1) {
                        // invert bit that was tapped
                        currentlyEditedTag.setBeeId(currentlyEditedTag.getBeeId() ^ (1 << toggledBit));
                        // update view to show changed tag
                        invalidate();
                    } else {
                        setViewMode(ViewMode.TAGGING_MODE);
                        setPanEnabled(true);
                        setZoomEnabled(true);
                        currentlyEditedTag = null;
                        animateScaleAndCenter(lastViewState.getScale(), lastViewState.getCenter())
                                .withEasing(EASE_IN_OUT_QUAD)
                                .withDuration(400)
                                .withInterruptible(false)
                                .start();
                    }
                }
                return true;
            }
        });
        setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                return gestureDetector.onTouchEvent(motionEvent);
            }
        });
        viewMode = ViewMode.TAGGING_MODE;
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
        if (viewMode == ViewMode.TAGGING_MODE) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2);
            paint.setColor(Color.argb(140, 255, 255, 255)); // white
            canvas.drawCircle(getWidth() / 2, getHeight() / 2, tagCircleRadius + strokeWidth, paint);
            paint.setColor(Color.argb(140, 0, 0, 0)); // black
            canvas.drawCircle(getWidth() / 2, getHeight() / 2, tagCircleRadius, paint);
        }
    }

    public int getTagCircleRadius() {
        return tagCircleRadius;
    }

    public void setTagsOnImage(List<Tag> tags) {
        this.tagsOnImage = tags;
        invalidate();
    }

    private void drawTagVisualization(Canvas canvas, Tag tag) {
        paint.setStyle(Paint.Style.FILL);
        tagCenterInView = sourceToViewCoord(tag.getCenterX(), tag.getCenterY());
        tagRadiusInView = tag.getRadius() * getScale();
        innerCircle = new RectF(
                tagCenterInView.x - (tagRadiusInView * VISUALIZATION_INNER_SCALE),
                tagCenterInView.y - (tagRadiusInView * VISUALIZATION_INNER_SCALE),
                tagCenterInView.x + (tagRadiusInView * VISUALIZATION_INNER_SCALE),
                tagCenterInView.y + (tagRadiusInView * VISUALIZATION_INNER_SCALE)
        );
        outerCircle = new RectF(
                tagCenterInView.x - (tagRadiusInView * VISUALIZATION_MIDDLE_SCALE),
                tagCenterInView.y - (tagRadiusInView * VISUALIZATION_MIDDLE_SCALE),
                tagCenterInView.x + (tagRadiusInView * VISUALIZATION_MIDDLE_SCALE),
                tagCenterInView.y + (tagRadiusInView * VISUALIZATION_MIDDLE_SCALE)
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
        // draw orientation circle on the outside
        orientationCircle = new RectF(
                tagCenterInView.x - (tagRadiusInView * VISUALIZATION_OUTER_SCALE),
                tagCenterInView.y - (tagRadiusInView * VISUALIZATION_OUTER_SCALE),
                tagCenterInView.x + (tagRadiusInView * VISUALIZATION_OUTER_SCALE),
                tagCenterInView.y + (tagRadiusInView * VISUALIZATION_OUTER_SCALE)
        );
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(tagRadiusInView*0.08f);
        paint.setColor(Color.argb(255, 0, 43, 54)); // dark
        canvas.drawArc(orientationCircle, (orientationDegrees + 90) % 360, 180, false, paint);
        paint.setColor(Color.argb(255, 253, 246, 227)); // light
        canvas.drawArc(orientationCircle, (orientationDegrees - 90) % 360, 180, false, paint);
    }

    // returns the marked tag at the given image coordinates,
    // or null if the location does not contain a tag
    @Nullable
    private Tag tagAtPosition(PointF pos) {
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
    private int bitSegmentAtPosition(PointF pos, Tag tag) {
        PointF tagCenterToPos = new PointF(pos.x - tag.getCenterX(), pos.y - tag.getCenterY());
        float distance = tagCenterToPos.length();
        float visualizationOuterRadius = tag.getRadius() * VISUALIZATION_OUTER_SCALE;
        float visualizationInnerRadius = tag.getRadius() * VISUALIZATION_INNER_SCALE;
        if (distance < visualizationOuterRadius && distance > visualizationInnerRadius) {
            double angle = (Math.toDegrees(Math.atan2(tagCenterToPos.y, tagCenterToPos.x)) + 360) % 360;
            // rotate based on tag orientation
            angle = (angle - tag.getOrientation()) % 360;
            return (int) Math.round(Math.floor(angle / 30));
        } else {
            return -1;
        }
    }

    // sets the view mode and changes UI elements accordingly,
    // it does not enable/disable zooming, panning etc.
    private void setViewMode(ViewMode viewMode) {
        this.viewMode = viewMode;
        switch (this.viewMode) {
            case TAGGING_MODE:
                // TODO: change UI elements etc.
                break;
            case EDITING_MODE:
                // TODO: change UI elements etc.
                break;
        }
    }
}
