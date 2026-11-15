package io.github.Subodhkr360.stylo2.haptics;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class AirCommandArcView extends FrameLayout {
    private static final String TAG = "AirCommandArcView";

    public interface OnToolClickListener {
        void onToolClick(String toolId);
    }

    public static class ToolModel {
        public final String id;
        public final String title;
        public final int iconRes;
        public final Drawable customIcon;
        public final int circleBgRes;

        public ToolModel(String id, String title, int iconRes, int circleBgRes) {
            this.id = id;
            this.title = title;
            this.iconRes = iconRes;
            this.customIcon = null;
            this.circleBgRes = circleBgRes;
        }

        public ToolModel(String id, String title, Drawable customIcon) {
            this.id = id;
            this.title = title;
            this.iconRes = 0;
            this.customIcon = customIcon;
            this.circleBgRes = R.drawable.bg_tool_circle_blue;
        }
    }

    private final Context mContext;
    private final float mDensity;
    private final int mTouchSlop;

    private final List<ToolModel> mTools = new ArrayList<>();
    private final List<View> mItemViews = new ArrayList<>();

    private OnToolClickListener mClickListener;
    private Runnable mOnCloseListener;

    private boolean mIsRightSide = true;
    private float mCenterX = 0f;
    private float mCenterY = 0f;
    private int mScreenW = 0;
    private int mScreenH = 0;

    private float mRadiusPx;
    private float mDeltaTheta;
    private float mBaseAngle;
    private float mRotationOffset = 0f;
    private float mMinRotationOffset = 0f;
    private float mMaxRotationOffset = 0f;

    private FrameLayout mHubButton;
    private boolean mIsClosing = false;
    private boolean mShowLabels = true;

    // Drag rotation
    private float mDownRawX = 0f;
    private float mDownRawY = 0f;
    private float mDownAngle = 0f;
    private float mStartRotationOffset = 0f;
    private boolean mIsDragging = false;
    private long mDownTime = 0;
    private int mLastHapticStep = 0;

    public AirCommandArcView(Context context) {
        super(context);
        mContext = context;
        mDensity = context.getResources().getDisplayMetrics().density;
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClipChildren(false);
        setClipToPadding(false);
    }

    public void setup(List<ToolModel> tools, boolean isRightSide, int bubbleCenterX, int bubbleCenterY,
                      int screenW, int screenH, boolean showLabels,
                      OnToolClickListener clickListener, Runnable onCloseListener) {
        mTools.clear();
        mTools.addAll(tools);
        mIsRightSide = isRightSide;
        mScreenW = screenW;
        mScreenH = screenH;
        mCenterX = bubbleCenterX;
        mCenterY = bubbleCenterY;
        mShowLabels = showLabels;
        mClickListener = clickListener;
        mOnCloseListener = onCloseListener;
        mIsClosing = false;
        mRotationOffset = 0f;

        mRadiusPx = 144 * mDensity;
        int count = mTools.size();
        if (count <= 4) {
            mDeltaTheta = (float) Math.toRadians(30.0);
        } else if (count == 5) {
            mDeltaTheta = (float) Math.toRadians(26.0);
        } else {
            mDeltaTheta = (float) Math.toRadians(23.0);
        }

        // Base horizontal orientation (pointing into screen)
        float baseH = mIsRightSide ? (float) Math.PI : 0f;

        // Anti-clipping edge calculation:
        // Adjust base angle so tools curve into available screen space if bubble is near top/bottom
        float bottomClearance = (mScreenH - 72 * mDensity) - mCenterY;
        float topClearance = mCenterY - (72 * mDensity);
        float visibleHalfSpan = Math.min((float) Math.toRadians(52.0), ((count - 1) / 2.0f) * mDeltaTheta);
        float halfArcHeight = mRadiusPx * (float) Math.sin(visibleHalfSpan);

        float bias = 0f;
        if (bottomClearance < halfArcHeight) {
            float deficit = halfArcHeight - bottomClearance;
            bias = (float) Math.asin(Math.max(-0.90f, Math.min(0.90f, deficit / mRadiusPx)));
            mBaseAngle = mIsRightSide ? (baseH + bias) : (baseH - bias);
        } else if (topClearance < halfArcHeight) {
            float deficit = halfArcHeight - topClearance;
            bias = (float) Math.asin(Math.max(-0.90f, Math.min(0.90f, deficit / mRadiusPx)));
            mBaseAngle = mIsRightSide ? (baseH - bias) : (baseH + bias);
        } else {
            mBaseAngle = baseH;
        }

        // Bounded rotation range to scroll all tools
        float centerIndex = (count - 1) / 2.0f;
        float pad = mDeltaTheta * 0.4f;
        mMinRotationOffset = -centerIndex * mDeltaTheta - pad;
        mMaxRotationOffset = centerIndex * mDeltaTheta + pad;

        buildViews();
        expandWithAnimation();
    }

    private void buildViews() {
        removeAllViews();
        mItemViews.clear();

        // 1. Center Hub Button (at bubble position)
        int hubSize = (int) (44 * mDensity);
        mHubButton = new FrameLayout(mContext);
        mHubButton.setBackgroundResource(R.drawable.bg_air_command_bubble);
        mHubButton.setElevation(10 * mDensity);

        ImageView hubIcon = new ImageView(mContext);
        hubIcon.setImageResource(R.drawable.ic_tool_close);
        int p = (int) (11 * mDensity);
        hubIcon.setPadding(p, p, p, p);
        mHubButton.addView(hubIcon, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        LayoutParams hubParams = new LayoutParams(hubSize, hubSize);
        hubParams.leftMargin = (int) (mCenterX - hubSize / 2f);
        hubParams.topMargin = (int) (mCenterY - hubSize / 2f);
        addView(mHubButton, hubParams);

        // 2. Tool Items along the arc
        int circleSize = (int) (44 * mDensity);
        int gap = mShowLabels ? (int) (8 * mDensity) : 0;

        for (int i = 0; i < mTools.size(); i++) {
            final ToolModel tool = mTools.get(i);

            LinearLayout row = new LinearLayout(mContext);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setClipChildren(false);
            row.setClipToPadding(false);

            TextView label = new TextView(mContext);
            label.setText(tool.title);
            label.setTextColor(Color.WHITE);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            label.getPaint().setFakeBoldText(true);
            label.setBackgroundResource(R.drawable.bg_air_command_pill);
            label.setElevation(6 * mDensity);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setMaxWidth((int) (115 * mDensity));
            label.setPadding((int) (10 * mDensity), (int) (4 * mDensity), (int) (10 * mDensity), (int) (4 * mDensity));
            if (!mShowLabels) {
                label.setVisibility(View.GONE);
            }

            FrameLayout iconCircle = new FrameLayout(mContext);
            iconCircle.setBackgroundResource(tool.circleBgRes);
            iconCircle.setElevation(8 * mDensity);

            ImageView icon = new ImageView(mContext);
            if (tool.customIcon != null) {
                icon.setImageDrawable(tool.customIcon);
                int cp = (int) (9 * mDensity);
                icon.setPadding(cp, cp, cp, cp);
            } else {
                icon.setImageResource(tool.iconRes);
                int cp = (int) (11 * mDensity);
                icon.setPadding(cp, cp, cp, cp);
            }
            iconCircle.addView(icon, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

            LinearLayout.LayoutParams circleParams = new LinearLayout.LayoutParams(circleSize, circleSize);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);

            if (mIsRightSide) {
                if (mShowLabels) {
                    labelParams.setMargins(0, 0, gap, 0);
                    row.addView(label, labelParams);
                }
                row.addView(iconCircle, circleParams);
            } else {
                row.addView(iconCircle, circleParams);
                if (mShowLabels) {
                    labelParams.setMargins(gap, 0, 0, 0);
                    row.addView(label, labelParams);
                }
            }

            LayoutParams rowParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            addView(row, rowParams);
            mItemViews.add(row);
        }
    }

    private void updateItemPositions() {
        int count = mTools.size();
        float centerIndex = (count - 1) / 2.0f;
        float circleRadius = 22 * mDensity;

        float minY = 48 * mDensity;
        float maxY = mScreenH - 64 * mDensity;

        float maxApertureRad = (float) Math.toRadians(52.0);
        float fadeApertureRad = (float) Math.toRadians(68.0);

        for (int i = 0; i < count; i++) {
            View view = mItemViews.get(i);
            float angle = mBaseAngle + (i - centerIndex) * mDeltaTheta + mRotationOffset;

            // Angular deviation from base horizontal angle
            float dAngle = angle - mBaseAngle;
            while (dAngle > Math.PI) dAngle -= 2 * Math.PI;
            while (dAngle < -Math.PI) dAngle += 2 * Math.PI;
            float absAngle = Math.abs(dAngle);

            // Anchor point is the center of the icon circle
            float anchorX = mCenterX + mRadiusPx * (float) Math.cos(angle);
            float anchorY = mCenterY + mRadiusPx * (float) Math.sin(angle);

            // Position row so its icon circle center aligns with (anchorX, anchorY)
            int rowW = view.getWidth();
            int rowH = view.getHeight();
            if (rowW == 0 || rowH == 0) {
                view.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
                rowW = view.getMeasuredWidth();
                rowH = view.getMeasuredHeight();
            }

            float finalX;
            float finalY = anchorY - (rowH / 2f);

            if (mIsRightSide) {
                finalX = anchorX - rowW + circleRadius;
            } else {
                finalX = anchorX - circleRadius;
            }

            view.setTranslationX(finalX);
            view.setTranslationY(finalY);

            // Arc windowing & alpha calculation
            float alpha = 1.0f;
            float scale = 1.0f;

            if (absAngle > fadeApertureRad) {
                alpha = 0f;
                scale = 0.5f;
            } else if (absAngle > maxApertureRad) {
                float progress = (absAngle - maxApertureRad) / (fadeApertureRad - maxApertureRad);
                alpha = 1.0f - progress;
                scale = 1.0f - (0.45f * progress);
            }

            // Screen boundary clamping & smooth alpha fade
            if (anchorY < minY) {
                alpha = Math.min(alpha, Math.max(0.0f, 1.0f - (minY - anchorY) / (36 * mDensity)));
            } else if (anchorY > maxY) {
                alpha = Math.min(alpha, Math.max(0.0f, 1.0f - (anchorY - maxY) / (36 * mDensity)));
            }

            view.setScaleX(scale);
            view.setScaleY(scale);
            view.setAlpha(alpha);
            view.setVisibility(alpha > 0.05f ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void expandWithAnimation() {
        mHubButton.setRotation(mIsRightSide ? -135f : 135f);
        mHubButton.setScaleX(0.2f);
        mHubButton.setScaleY(0.2f);
        mHubButton.setAlpha(0f);
        mHubButton.animate()
                .rotation(0f)
                .scaleX(1.0f)
                .scaleY(1.0f)
                .alpha(1.0f)
                .setDuration(260)
                .setInterpolator(new DecelerateInterpolator(1.4f))
                .start();

        int count = mTools.size();
        float centerIndex = (count - 1) / 2.0f;
        float circleRadius = 22 * mDensity;

        float maxApertureRad = (float) Math.toRadians(52.0);
        float fadeApertureRad = (float) Math.toRadians(68.0);

        for (int i = 0; i < count; i++) {
            final View view = mItemViews.get(i);
            float angle = mBaseAngle + (i - centerIndex) * mDeltaTheta + mRotationOffset;

            float dAngle = angle - mBaseAngle;
            while (dAngle > Math.PI) dAngle -= 2 * Math.PI;
            while (dAngle < -Math.PI) dAngle += 2 * Math.PI;
            float absAngle = Math.abs(dAngle);

            float anchorX = mCenterX + mRadiusPx * (float) Math.cos(angle);
            float anchorY = mCenterY + mRadiusPx * (float) Math.sin(angle);

            view.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
            int rowW = view.getMeasuredWidth();
            int rowH = view.getMeasuredHeight();

            float finalX = mIsRightSide ? (anchorX - rowW + circleRadius) : (anchorX - circleRadius);
            float finalY = anchorY - (rowH / 2f);

            float targetAlpha = 1.0f;
            float targetScale = 1.0f;
            if (absAngle > fadeApertureRad) {
                targetAlpha = 0f;
                targetScale = 0.5f;
            } else if (absAngle > maxApertureRad) {
                float progress = (absAngle - maxApertureRad) / (fadeApertureRad - maxApertureRad);
                targetAlpha = 1.0f - progress;
                targetScale = 1.0f - (0.45f * progress);
            }

            // Start at center hub
            float startX = mIsRightSide ? (mCenterX - rowW + circleRadius) : (mCenterX - circleRadius);
            float startY = mCenterY - (rowH / 2f);

            view.setTranslationX(startX);
            view.setTranslationY(startY);
            view.setScaleX(0.2f);
            view.setScaleY(0.2f);
            view.setAlpha(0.0f);
            view.setVisibility(targetAlpha > 0.05f ? View.VISIBLE : View.INVISIBLE);

            if (targetAlpha > 0.05f) {
                long delay = (long) (22 * Math.abs(i - centerIndex));
                view.animate()
                        .translationX(finalX)
                        .translationY(finalY)
                        .scaleX(targetScale)
                        .scaleY(targetScale)
                        .alpha(targetAlpha)
                        .setDuration(260)
                        .setStartDelay(delay)
                        .setInterpolator(new DecelerateInterpolator(1.4f))
                        .start();
            } else {
                view.setTranslationX(finalX);
                view.setTranslationY(finalY);
                view.setScaleX(targetScale);
                view.setScaleY(targetScale);
                view.setAlpha(0f);
            }
        }
    }

    public void collapseWithAnimation(Runnable afterCollapse) {
        if (mIsClosing) return;
        mIsClosing = true;

        mHubButton.animate()
                .rotation(mIsRightSide ? 135f : -135f)
                .scaleX(0.2f)
                .scaleY(0.2f)
                .alpha(0.0f)
                .setDuration(190)
                .setInterpolator(new AccelerateInterpolator(1.4f))
                .start();

        int count = mItemViews.size();
        float circleRadius = 22 * mDensity;

        int animatedCount = 0;
        for (int i = 0; i < count; i++) {
            View view = mItemViews.get(i);
            if (view.getVisibility() == View.VISIBLE && view.getAlpha() > 0.05f) {
                animatedCount++;
            }
        }

        if (animatedCount == 0) {
            if (afterCollapse != null) afterCollapse.run();
            if (mOnCloseListener != null) mOnCloseListener.run();
            return;
        }

        final int totalAnimated = animatedCount;
        final int[] finishedCount = new int[]{0};

        for (int i = 0; i < count; i++) {
            View view = mItemViews.get(i);
            int rowW = view.getWidth() > 0 ? view.getWidth() : view.getMeasuredWidth();
            int rowH = view.getHeight() > 0 ? view.getHeight() : view.getMeasuredHeight();

            float startX = mIsRightSide ? (mCenterX - rowW + circleRadius) : (mCenterX - circleRadius);
            float startY = mCenterY - (rowH / 2f);

            if (view.getVisibility() == View.VISIBLE && view.getAlpha() > 0.05f) {
                view.animate()
                        .translationX(startX)
                        .translationY(startY)
                        .scaleX(0.2f)
                        .scaleY(0.2f)
                        .alpha(0.0f)
                        .setDuration(190)
                        .setInterpolator(new AccelerateInterpolator(1.4f))
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                finishedCount[0]++;
                                if (finishedCount[0] >= totalAnimated) {
                                    if (afterCollapse != null) {
                                        afterCollapse.run();
                                    }
                                    if (mOnCloseListener != null) {
                                        mOnCloseListener.run();
                                    }
                                }
                            }
                        })
                        .start();
            } else {
                view.setVisibility(View.INVISIBLE);
                view.setAlpha(0f);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mIsClosing) return true;

        float rawX = event.getRawX();
        float rawY = event.getRawY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mDownRawX = rawX;
                mDownRawY = rawY;
                mDownTime = System.currentTimeMillis();
                mDownAngle = (float) Math.atan2(rawY - mCenterY, rawX - mCenterX);
                mStartRotationOffset = mRotationOffset;
                mIsDragging = false;
                mLastHapticStep = Math.round(mRotationOffset / mDeltaTheta);
                return true;

            case MotionEvent.ACTION_MOVE:
                float dist = (float) Math.hypot(rawX - mDownRawX, rawY - mDownRawY);
                if (!mIsDragging && dist > mTouchSlop) {
                    mIsDragging = true;
                }

                if (mIsDragging) {
                    float currAngle = (float) Math.atan2(rawY - mCenterY, rawX - mCenterX);
                    float dAngle = currAngle - mDownAngle;
                    // Normalize angle difference across +/- PI boundary
                    while (dAngle > Math.PI) dAngle -= 2 * Math.PI;
                    while (dAngle < -Math.PI) dAngle += 2 * Math.PI;

                    mRotationOffset = mStartRotationOffset + dAngle;
                    mRotationOffset = Math.max(mMinRotationOffset, Math.min(mMaxRotationOffset, mRotationOffset));
                    updateItemPositions();

                    int step = Math.round(mRotationOffset / mDeltaTheta);
                    if (step != mLastHapticStep) {
                        mLastHapticStep = step;
                        StylusHapticsManager mgr = StylusHapticsManager.getInstance(mContext);
                        if (mgr != null) {
                            mgr.triggerPattern(StylusHapticsManager.PATTERN_BALLPOINT, 1);
                        }
                        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                long duration = System.currentTimeMillis() - mDownTime;
                if (!mIsDragging && duration < 350) {
                    // Tap on Hub Button
                    if (isTouchInsideView(mHubButton, rawX, rawY)) {
                        performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
                        collapseWithAnimation(null);
                        return true;
                    }

                    // Tap on Tool Items
                    for (int i = 0; i < mItemViews.size(); i++) {
                        View item = mItemViews.get(i);
                        if (item.getVisibility() == View.VISIBLE && item.getAlpha() > 0.4f
                                && isTouchInsideView(item, rawX, rawY)) {
                            final String toolId = mTools.get(i).id;
                            Log.i(TAG, "Touch up: tool tap detected for " + toolId);
                            StylusHapticsManager mgr = StylusHapticsManager.getInstance(mContext);
                            if (mgr != null) {
                                mgr.triggerPattern(StylusHapticsManager.PATTERN_BALLPOINT, 3);
                            }
                            performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK);
                            collapseWithAnimation(() -> {
                                if (mClickListener != null) {
                                    mClickListener.onToolClick(toolId);
                                }
                            });
                            return true;
                        }
                    }

                    // Tapped outside on scrim
                    Log.i(TAG, "Touch up: tapped outside on scrim at (" + rawX + "," + rawY + ")");
                    collapseWithAnimation(null);
                    return true;
                } else if (mIsDragging) {
                    // Snap to nearest item slot
                    float centerIndex = (mTools.size() - 1) / 2.0f;
                    float targetOffset = Math.round(mRotationOffset / mDeltaTheta) * mDeltaTheta;
                    targetOffset = Math.max(-centerIndex * mDeltaTheta, Math.min(centerIndex * mDeltaTheta, targetOffset));
                    animateRotationTo(targetOffset);
                }
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void animateRotationTo(float target) {
        ValueAnimator anim = ValueAnimator.ofFloat(mRotationOffset, target);
        anim.setDuration(220);
        anim.setInterpolator(new DecelerateInterpolator(1.5f));
        anim.addUpdateListener(animation -> {
            mRotationOffset = (float) animation.getAnimatedValue();
            updateItemPositions();
        });
        anim.start();
    }

    private boolean isTouchInsideView(View v, float rawX, float rawY) {
        if (v == null || v.getVisibility() != View.VISIBLE || v.getAlpha() < 0.25f) return false;
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        int pad = (int) (14 * mDensity);
        Rect rect = new Rect(loc[0] - pad, loc[1] - pad, loc[0] + v.getWidth() + pad, loc[1] + v.getHeight() + pad);
        return rect.contains((int) rawX, (int) rawY);
    }
}
