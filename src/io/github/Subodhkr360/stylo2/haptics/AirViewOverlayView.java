package io.github.Subodhkr360.stylo2.haptics;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.View;


public class AirViewOverlayView extends View {
    public static final int STATE_IDLE = 0;
    public static final int STATE_HOVER_DOT = 1;
    public static final int STATE_SCROLL_UP = 2;
    public static final int STATE_SCROLL_DOWN = 3;

    private int mCurrentState = STATE_IDLE;
    private float mPointerX = -100f;
    private float mPointerY = -100f;
    private float mAlpha = 0f;
    private ValueAnimator mAlphaAnimator;

    private Drawable mScrollUpDrawable;
    private Drawable mScrollDownDrawable;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint mDotShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDotStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDotFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float mDensity = 1.0f;
    private float mPointerDiameterPx;
    private int mArrowSizePx;
    private int mCurrentSizeDp = -1;
    private boolean mLaserMode = false;

    private final Paint mLaserHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLaserCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public AirViewOverlayView(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
        mDensity = context.getResources().getDisplayMetrics().density;
        mCurrentSizeDp = 4;
        mPointerDiameterPx = 4 * mDensity; // 4dp solid dot default
        mArrowSizePx = (int) (22 * mDensity); // Compact OneUI arrow indicator size

        mDotShadowPaint.setColor(0x40000000);
        mDotShadowPaint.setStyle(Paint.Style.FILL);

        mDotStrokePaint.setColor(0x80000000);
        mDotStrokePaint.setStyle(Paint.Style.STROKE);

        mDotFillPaint.setColor(0xFFFFFFFF);
        mDotFillPaint.setStyle(Paint.Style.FILL);

        mLaserHaloPaint.setStyle(Paint.Style.FILL);
        mLaserCorePaint.setStyle(Paint.Style.FILL);

        try {
            mScrollUpDrawable = context.getDrawable(R.drawable.ic_hover_scroll_up);
        } catch (Throwable ignored) {}

        try {
            mScrollDownDrawable = context.getDrawable(R.drawable.ic_hover_scroll_down);
        } catch (Throwable ignored) {}
    }

    public void setLaserMode(boolean enabled) {
        mLaserMode = enabled;
        postInvalidate();
    }

    public boolean isLaserMode() {
        return mLaserMode;
    }

    public void setPointerSizeDp(int sizeDp) {
        if (mCurrentSizeDp == sizeDp && mPointerDiameterPx > 0f) return;
        mCurrentSizeDp = sizeDp;
        mDensity = getContext().getResources().getDisplayMetrics().density;
        mPointerDiameterPx = sizeDp * mDensity;
        mArrowSizePx = (int) (22 * mDensity);
        postInvalidate();
    }

    private float mSmoothedX = -100f;
    private float mSmoothedY = -100f;
    private boolean mHasInitialPoint = false;

    public void updatePointer(float x, float y, int state) {
        if (!mHasInitialPoint || mCurrentState == STATE_IDLE || mCurrentState != state) {
            mSmoothedX = x;
            mSmoothedY = y;
            mHasInitialPoint = true;
        } else {
            float dx = x - mSmoothedX;
            float dy = y - mSmoothedY;
            float distSq = dx * dx + dy * dy;

            if (distSq > 0.15f) {
                float dist = (float) Math.sqrt(distSq);
                float alpha = Math.min(0.98f, Math.max(0.82f, dist / 20.0f));
                mSmoothedX += alpha * dx;
                mSmoothedY += alpha * dy;
            }
        }

        mPointerX = mSmoothedX;
        mPointerY = mSmoothedY;

        if (mCurrentState != state) {
            mCurrentState = state;
        }

        if (state != STATE_IDLE) {
            if (mAlpha < 1.0f && (mAlphaAnimator == null || !mAlphaAnimator.isRunning())) {
                animateAlpha(1.0f, 60);
            }
        } else {
            if (mAlpha > 0f && (mAlphaAnimator == null || !mAlphaAnimator.isRunning())) {
                animateAlpha(0f, 100);
            }
        }
        postInvalidate();
    }

    public void hideImmediately() {
        if (mAlphaAnimator != null) {
            mAlphaAnimator.cancel();
        }
        mAlpha = 0f;
        mCurrentState = STATE_IDLE;
        mHasInitialPoint = false;
        postInvalidate();
    }

    private void animateAlpha(float targetAlpha, long durationMs) {
        if (mAlphaAnimator != null) {
            mAlphaAnimator.cancel();
        }
        mAlphaAnimator = ValueAnimator.ofFloat(mAlpha, targetAlpha);
        mAlphaAnimator.setDuration(durationMs);
        mAlphaAnimator.addUpdateListener(animation -> {
            mAlpha = (float) animation.getAnimatedValue();
            invalidate();
        });
        mAlphaAnimator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mAlpha <= 0.01f || mCurrentState == STATE_IDLE) {
            return;
        }

        mPaint.setAlpha((int) (mAlpha * 255));

        if (mCurrentState == STATE_HOVER_DOT) {
            if (mLaserMode) {
                // Presentation Laser Pointer mode: glowing red laser beam dot
                mLaserHaloPaint.setColor(0x35FF1744);
                mLaserHaloPaint.setAlpha((int) (mAlpha * 55));
                canvas.drawCircle(mPointerX, mPointerY, 11 * mDensity, mLaserHaloPaint);

                mLaserHaloPaint.setColor(0x90FF1744);
                mLaserHaloPaint.setAlpha((int) (mAlpha * 144));
                canvas.drawCircle(mPointerX, mPointerY, 5.5f * mDensity, mLaserHaloPaint);

                mLaserCorePaint.setColor(0xFFFF5252);
                mLaserCorePaint.setAlpha((int) (mAlpha * 255));
                canvas.drawCircle(mPointerX, mPointerY, 2.8f * mDensity, mLaserCorePaint);
            } else {
                float radius = mPointerDiameterPx / 2f;
                float strokeWidth = Math.max(0.75f, Math.min(mDensity * 0.9f, radius * 0.32f));
                mDotStrokePaint.setStrokeWidth(strokeWidth);

                // 1. Soft subtle ambient shadow for visibility on dark/mixed backgrounds
                float shadowOffset = Math.max(0.4f, Math.min(mDensity * 0.75f, radius * 0.15f));
                float shadowRadius = radius + Math.max(0.4f, Math.min(mDensity * 0.5f, radius * 0.12f));
                mDotShadowPaint.setColor(0x40000000);
                mDotShadowPaint.setAlpha((int) (mAlpha * 64));
                canvas.drawCircle(mPointerX, mPointerY + shadowOffset, shadowRadius, mDotShadowPaint);

                // 2. High-contrast dark stroke (crisp semi-transparent border, ensures 100% visibility on white paper)
                mDotStrokePaint.setColor(0x80000000);
                mDotStrokePaint.setAlpha((int) (mAlpha * 140));
                canvas.drawCircle(mPointerX, mPointerY, radius, mDotStrokePaint);

                // 3. Crisp solid white core
                mDotFillPaint.setColor(0xFFFFFFFF);
                mDotFillPaint.setAlpha((int) (mAlpha * 255));
                canvas.drawCircle(mPointerX, mPointerY, Math.max(0.4f, radius - strokeWidth * 0.5f), mDotFillPaint);
            }
        } else if (mCurrentState == STATE_SCROLL_UP) {
            if (mScrollUpDrawable != null) {
                int half = mArrowSizePx / 2;
                mScrollUpDrawable.setBounds(
                    (int) (mPointerX - half),
                    (int) (mPointerY - half),
                    (int) (mPointerX + half),
                    (int) (mPointerY + half)
                );
                mScrollUpDrawable.setAlpha((int) (mAlpha * 255));
                mScrollUpDrawable.draw(canvas);
            }
        } else if (mCurrentState == STATE_SCROLL_DOWN) {
            if (mScrollDownDrawable != null) {
                int half = mArrowSizePx / 2;
                mScrollDownDrawable.setBounds(
                    (int) (mPointerX - half),
                    (int) (mPointerY - half),
                    (int) (mPointerX + half),
                    (int) (mPointerY + half)
                );
                mScrollDownDrawable.setAlpha((int) (mAlpha * 255));
                mScrollDownDrawable.draw(canvas);
            }
        }
    }
}
