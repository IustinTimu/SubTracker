package com.example.proiectcmo.ui.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.proiectcmo.R;
import com.example.proiectcmo.model.CategoryStat;

import java.util.ArrayList;
import java.util.List;

public class DonutChartView extends View {
    private static final float GAP_DEGREES = 3f;
    private static final long ANIMATION_MS = 700;

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();

    private final List<CategoryStat> segments = new ArrayList<>();
    private String centerValue = "";
    private String centerLabel = "";

    private float progress = 1f;
    private boolean animatedOnce;

    public DonutChartView(Context context) {
        this(context, null);
    }

    public DonutChartView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        float scaled = getResources().getDisplayMetrics().scaledDensity;

        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeCap(Paint.Cap.ROUND);
        arcPaint.setStrokeWidth(22 * density);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(22 * density);
        trackPaint.setColor(ContextCompat.getColor(context, R.color.st_border));

        valuePaint.setTextAlign(Paint.Align.CENTER);
        valuePaint.setTextSize(24 * scaled);
        valuePaint.setFakeBoldText(true);
        valuePaint.setColor(ContextCompat.getColor(context, R.color.st_text_primary));

        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(13 * scaled);
        labelPaint.setColor(ContextCompat.getColor(context, R.color.st_text_secondary));
    }

    public void setData(List<CategoryStat> stats, String value, String label) {
        segments.clear();
        segments.addAll(stats);
        centerValue = value;
        centerLabel = label;
        if (!animatedOnce && !stats.isEmpty()) {
            animatedOnce = true;
            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(ANIMATION_MS);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(a -> {
                progress = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        } else {
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        int height = resolveSize(width, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float stroke = arcPaint.getStrokeWidth();
        float diameter = Math.min(getWidth(), getHeight()) - stroke - getPaddingTop() - getPaddingBottom();
        float left = (getWidth() - diameter) / 2f;
        float top = (getHeight() - diameter) / 2f;
        bounds.set(left, top, left + diameter, top + diameter);

        canvas.drawArc(bounds, 0, 360, false, trackPaint);

        float start = -90f;
        float gap = segments.size() > 1 ? GAP_DEGREES : 0f;
        for (CategoryStat stat : segments) {
            float sweep = stat.getFraction() * 360f * progress;
            if (sweep <= 0f) {
                continue;
            }
            arcPaint.setColor(ContextCompat.getColor(getContext(), stat.getCategory().getColorRes()));

            float capDegrees = (float) Math.toDegrees(stroke / diameter);
            float drawSweep = Math.max(0.1f, sweep - gap - capDegrees);
            canvas.drawArc(bounds, start + (gap + capDegrees) / 2f, drawSweep, false, arcPaint);
            start += sweep;
        }

        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        canvas.drawText(centerValue, centerX, centerY + valuePaint.getTextSize() * 0.2f, valuePaint);
        canvas.drawText(centerLabel, centerX, centerY + valuePaint.getTextSize() * 0.2f
                + labelPaint.getTextSize() * 1.5f, labelPaint);
    }
}
