package com.jjundev.oneclickeng.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.VennDiagram;
import java.util.List;

/**
 * Custom View for rendering a Venn diagram with two overlapping circles
 *
 * <p>Rendering guide: - Two circles with equal radius - Approximately 30% overlap - Left circle
 * items on the left - Right circle items on the right - Intersection items in the middle
 */
public class VennDiagramView extends View {

  private VennDiagram vennDiagram;

  // Paints
  private Paint leftCirclePaint;
  private Paint rightCirclePaint;
  private Paint intersectionPaint;
  private Paint labelPaint;
  private Paint itemPaint;
  private Paint strokePaint;

  // Dimensions
  private float circleRadius;
  private float leftCenterX;
  private float rightCenterX;
  private float centerY;
  private static final float OVERLAP_RATIO = 0.3f;

  public VennDiagramView(Context context) {
    super(context);
    init();
  }

  public VennDiagramView(Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public VennDiagramView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    // Left circle paint
    leftCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    leftCirclePaint.setStyle(Paint.Style.FILL);
    leftCirclePaint.setColor(0x804CAF50); // Default green with alpha

    // Right circle paint
    rightCirclePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    rightCirclePaint.setStyle(Paint.Style.FILL);
    rightCirclePaint.setColor(0x802196F3); // Default blue with alpha

    // Intersection paint
    intersectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    intersectionPaint.setStyle(Paint.Style.FILL);
    intersectionPaint.setColor(0x808BC34A); // Default light green with alpha

    // Stroke paint for circle outlines
    strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    strokePaint.setStyle(Paint.Style.STROKE);
    strokePaint.setStrokeWidth(2f);
    strokePaint.setColor(Color.DKGRAY);

    // Label paint (for word labels)
    labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    labelPaint.setTextSize(36f);
    labelPaint.setTextAlign(Paint.Align.CENTER);
    labelPaint.setTypeface(Typeface.DEFAULT_BOLD);
    labelPaint.setColor(Color.BLACK);

    // Item paint (for list items)
    itemPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    itemPaint.setTextSize(24f);
    itemPaint.setTextAlign(Paint.Align.CENTER);
    itemPaint.setColor(0xFF333333);
  }

  /** Set the Venn diagram data and refresh the view */
  public void setVennDiagram(VennDiagram diagram) {
    this.vennDiagram = diagram;
    if (diagram != null) {
      if (diagram.getLeftCircle() != null) {
        int color = diagram.getLeftCircle().getColorInt();
        leftCirclePaint.setColor(withAlpha(color, 128));
      }
      if (diagram.getRightCircle() != null) {
        int color = diagram.getRightCircle().getColorInt();
        rightCirclePaint.setColor(withAlpha(color, 128));
      }
      if (diagram.getIntersection() != null) {
        int color = diagram.getIntersection().getColorInt();
        intersectionPaint.setColor(withAlpha(color, 180));
      }
    }
    invalidate();
  }

  private int withAlpha(int color, int alpha) {
    return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    calculateDimensions(w, h);
  }

  private void calculateDimensions(int width, int height) {
    float padding = 20f;
    float availableWidth = width - 2 * padding;
    float availableHeight = height - 2 * padding;

    // Calculate radius based on available space
    // With 30% overlap, total width = 2 * radius + (1 - OVERLAP_RATIO) * 2 * radius = radius * (4 -
    // 2 * OVERLAP_RATIO)
    // Actually: total width = 2 * (2 * radius - overlap) = 2 * (2r - 0.3 * 2r) = 2 * 1.4r = 2.8r
    // So: 2 * radius * (2 - OVERLAP_RATIO) = availableWidth
    // radius = availableWidth / (2 * (2 - OVERLAP_RATIO))
    float widthBasedRadius = availableWidth / (2 * (2 - OVERLAP_RATIO));
    float heightBasedRadius = availableHeight / 2;

    circleRadius = Math.min(widthBasedRadius, heightBasedRadius);

    centerY = height / 2f;

    // Calculate center positions for 30% overlap
    float offset = circleRadius * (1 - OVERLAP_RATIO);
    leftCenterX = width / 2f - offset;
    rightCenterX = width / 2f + offset;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    if (vennDiagram == null) {
      return;
    }

    // Draw left circle
    canvas.drawCircle(leftCenterX, centerY, circleRadius, leftCirclePaint);
    canvas.drawCircle(leftCenterX, centerY, circleRadius, strokePaint);

    // Draw right circle
    canvas.drawCircle(rightCenterX, centerY, circleRadius, rightCirclePaint);
    canvas.drawCircle(rightCenterX, centerY, circleRadius, strokePaint);

    // Draw intersection with different color (overlap area)
    drawIntersection(canvas);

    // Draw labels and items
    drawLabelsAndItems(canvas);
  }

  private void drawIntersection(Canvas canvas) {
    if (vennDiagram.getIntersection() == null) {
      return;
    }

    // Create paths for both circles
    Path leftPath = new Path();
    leftPath.addCircle(leftCenterX, centerY, circleRadius, Path.Direction.CW);

    Path rightPath = new Path();
    rightPath.addCircle(rightCenterX, centerY, circleRadius, Path.Direction.CW);

    // Intersection is where both paths overlap
    Path intersectionPath = new Path();
    intersectionPath.op(leftPath, rightPath, Path.Op.INTERSECT);

    canvas.drawPath(intersectionPath, intersectionPaint);
  }

  private void drawLabelsAndItems(Canvas canvas) {
    float labelOffset = circleRadius * 0.7f;
    float itemStartY = centerY - circleRadius * 0.3f;
    float itemLineHeight = 28f;

    // Left circle label and items
    if (vennDiagram.getLeftCircle() != null) {
      String leftWord = vennDiagram.getLeftCircle().getWord();
      if (leftWord != null) {
        canvas.drawText(
            leftWord, leftCenterX - labelOffset * 0.3f, centerY - circleRadius * 0.5f, labelPaint);
      }

      List<String> leftItems = vennDiagram.getLeftCircle().getItems();
      if (leftItems != null) {
        float y = itemStartY;
        float x = leftCenterX - circleRadius * 0.4f;
        for (String item : leftItems) {
          canvas.drawText("• " + item, x, y, itemPaint);
          y += itemLineHeight;
        }
      }
    }

    // Right circle label and items
    if (vennDiagram.getRightCircle() != null) {
      String rightWord = vennDiagram.getRightCircle().getWord();
      if (rightWord != null) {
        canvas.drawText(
            rightWord,
            rightCenterX + labelOffset * 0.3f,
            centerY - circleRadius * 0.5f,
            labelPaint);
      }

      List<String> rightItems = vennDiagram.getRightCircle().getItems();
      if (rightItems != null) {
        float y = itemStartY;
        float x = rightCenterX + circleRadius * 0.4f;
        for (String item : rightItems) {
          canvas.drawText("• " + item, x, y, itemPaint);
          y += itemLineHeight;
        }
      }
    }

    // Intersection items
    if (vennDiagram.getIntersection() != null) {
      List<String> intersectionItems = vennDiagram.getIntersection().getItems();
      if (intersectionItems != null) {
        float y = centerY + circleRadius * 0.2f;
        float x = (leftCenterX + rightCenterX) / 2f;
        for (String item : intersectionItems) {
          canvas.drawText(item, x, y, itemPaint);
          y += itemLineHeight;
        }
      }
    }
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int desiredWidth = 400;
    int desiredHeight = 200;

    int widthMode = MeasureSpec.getMode(widthMeasureSpec);
    int widthSize = MeasureSpec.getSize(widthMeasureSpec);
    int heightMode = MeasureSpec.getMode(heightMeasureSpec);
    int heightSize = MeasureSpec.getSize(heightMeasureSpec);

    int width;
    int height;

    if (widthMode == MeasureSpec.EXACTLY) {
      width = widthSize;
    } else if (widthMode == MeasureSpec.AT_MOST) {
      width = Math.min(desiredWidth, widthSize);
    } else {
      width = desiredWidth;
    }

    if (heightMode == MeasureSpec.EXACTLY) {
      height = heightSize;
    } else if (heightMode == MeasureSpec.AT_MOST) {
      height = Math.min(desiredHeight, heightSize);
    } else {
      height = desiredHeight;
    }

    setMeasuredDimension(width, height);
  }
}
