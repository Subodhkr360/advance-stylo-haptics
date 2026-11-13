package io.github.Subodhkr360.stylo2.haptics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.SeekBar;

/**
 * Authentic OnePlus / ColorOS 14 Slider Bar.
 * Matches slider.jpg:
 * - Sleek rounded capsule pill track
 * - Embedded step dots
 * - Flush white circular thumb knob whose diameter matches track height
 */
public class OnePlusSeekBar extends SeekBar {

    private final Paint mBgTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mProgressTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mActiveDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mInactiveDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mThumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF mTrackRect = new RectF();
    private final RectF mProgressRect = new RectF();

    private float mTrackHeightPx;
    private float mTrackRadiusPx;
    private float mThumbRadiusPx;
    private float mDotRadiusPx;

    public OnePlusSeekBar(Context context) {
        super(context);
        init();
    }

    public OnePlusSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public OnePlusSeekBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;
        // 18dp track height and 9dp radius gives the exact sleek capsule pill from slider.jpg
        mTrackHeightPx = 18f * density;
        mTrackRadiusPx = mTrackHeightPx / 2f;
        // Thumb radius matches track radius (9dp, diameter 18dp) so it fits flush without bulging
        mThumbRadiusPx = mTrackRadiusPx;
        mDotRadiusPx = 2.2f * density;

        updateThemeColors();

        setProgressDrawable(new ColorDrawable(Color.TRANSPARENT));
        setThumb(new ColorDrawable(Color.TRANSPARENT));
        setSplitTrack(false);
        setPadding(0, (int) (6f * density), 0, (int) (6f * density));
    }

    public void updateThemeColors() {
        try {
            Context ctx = getContext();
            int trackColor = ctx.getColor(R.color.seekbar_track_off);
            int accentColor = ctx.getColor(R.color.primary_accent);
            int inactiveDotColor = ctx.getColor(R.color.seekbar_dot_inactive);

            mBgTrackPaint.setColor(trackColor);
            mProgressTrackPaint.setColor(accentColor);
            mActiveDotPaint.setColor(Color.WHITE);
            mInactiveDotPaint.setColor(inactiveDotColor);
            mThumbPaint.setColor(Color.WHITE);
        } catch (Exception e) {
            mBgTrackPaint.setColor(Color.parseColor("#3A3A3C"));
            mProgressTrackPaint.setColor(Color.parseColor("#0084FF"));
            mActiveDotPaint.setColor(Color.WHITE);
            mInactiveDotPaint.setColor(Color.parseColor("#636366"));
            mThumbPaint.setColor(Color.WHITE);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateThemeColors();
    }

    @Override
    protected synchronized void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float density = getResources().getDisplayMetrics().density;
        int minHeight = (int) (36f * density);
        int height = resolveSizeAndState(minHeight, heightMeasureSpec, 0);
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }

        int max = getMax();
        if (max <= 0) return super.onTouchEvent(event);

        float width = getWidth();
        float x0 = mTrackRadiusPx;
        float xMax = width - mTrackRadiusPx;
        float travel = xMax - x0;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                float x = event.getX();
                float ratio = (x - x0) / travel;
                ratio = Math.max(0f, Math.min(1f, ratio));
                int newProgress = Math.round(ratio * max);
                setProgress(newProgress);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();

        float centerY = height / 2f;
        float trackTop = centerY - mTrackRadiusPx;
        float trackBottom = centerY + mTrackRadiusPx;

        float trackLeft = 0f;
        float trackRight = width;

        // 1. Inactive background track spanning full width
        mTrackRect.set(trackLeft, trackTop, trackRight, trackBottom);
        canvas.drawRoundRect(mTrackRect, mTrackRadiusPx, mTrackRadiusPx, mBgTrackPaint);

        // Calculate dot bounds: x0 and xMax centered in the end caps
        float x0 = mTrackRadiusPx;
        float xMax = width - mTrackRadiusPx;
        float travel = xMax - x0;

        int max = getMax();
        int progress = getProgress();
        float ratio = max > 0 ? (float) progress / (float) max : 0f;
        float thumbX = x0 + (travel * ratio);

        // 2. Active progress track (drawn up to thumb)
        if (progress > 0) {
            float progressRight = (progress == max) ? trackRight : (thumbX + mTrackRadiusPx);
            mProgressRect.set(trackLeft, trackTop, Math.min(trackRight, progressRight), trackBottom);
            canvas.drawRoundRect(mProgressRect, mTrackRadiusPx, mTrackRadiusPx, mProgressTrackPaint);
        }

        // 3. Embedded step dots
        if (max > 0) {
            for (int i = 0; i <= max; i++) {
                float dotX = x0 + (travel * i / max);
                Paint dotPaint = (i <= progress) ? mActiveDotPaint : mInactiveDotPaint;
                canvas.drawCircle(dotX, centerY, mDotRadiusPx, dotPaint);
            }
        }

        // 4. Solid white circular thumb knob (diameter matches track height)
        canvas.drawCircle(thumbX, centerY, mThumbRadiusPx, mThumbPaint);
    }
}
