package lecho.lib.hellocharts;

import java.util.List;
import java.util.Locale;

import lecho.lib.hellocharts.anim.ChartAnimator;
import lecho.lib.hellocharts.anim.ChartAnimatorV11;
import lecho.lib.hellocharts.anim.ChartAnimatorV8;
import lecho.lib.hellocharts.gestures.ChartZoomer;
import lecho.lib.hellocharts.model.AnimatedPoint;
import lecho.lib.hellocharts.model.Data;
import lecho.lib.hellocharts.model.Line;
import lecho.lib.hellocharts.utils.Config;
import lecho.lib.hellocharts.utils.Utils;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Cap;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.ScrollerCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

public class LineChart extends View {
	private static final String TAG = "LineChart";
	private static final float LINE_SMOOTHNES = 0.16f;
	private static final int DEFAULT_LINE_WIDTH_DP = 3;
	private static final int DEFAULT_POINT_RADIUS_DP = 6;
	private static final int DEFAULT_POINT_TOUCH_RADIUS_DP = 12;
	private static final int DEFAULT_POINT_PRESSED_RADIUS = DEFAULT_POINT_RADIUS_DP + 4;
	private static final int DEFAULT_POPUP_TEXT_MARGIN = 4;
	private static final int DEFAULT_TEXT_SIZE_DP = 14;
	private static final int DEFAULT_TEXT_COLOR = Color.WHITE;
	private static final int DEFAULT_AREA_TRANSPARENCY = 64;
	private static final float ZOOM_AMOUNT = 0.25f;
	private ChartCalculator mChartCalculator;
	private AxesRenderer mAxisRenderer;
	private int mPopupTextMargin;
	private Path mLinePath = new Path();
	private Paint mLinePaint = new Paint();
	private Paint mTextPaint = new Paint();
	private Data mData;
	private float mLineWidth;
	private float mPointRadius;
	private float mPointPressedRadius;
	private float mTouchRadius;
	private boolean mLinesOn = true;
	private boolean mInterpolationOn = true;
	private boolean mPointsOn = true;
	private boolean mPopupsOn = false;
	private boolean mAxesOn = true;
	private ChartAnimator mAnimator;
	private int mSelectedLineIndex = Integer.MIN_VALUE;
	private int mSelectedPointIndex = Integer.MIN_VALUE;
	private ChartZoomer mZoomer;
	private ScrollerCompat mScroller;
	private PointF mZoomFocalPoint = new PointF();// Used for double tap zoom
	private RectF mScrollerStartViewport = new RectF(); // Used only for zooms and flings
	private Point mSurfaceSizeBuffer = new Point();// Used for scroll and flings

	private OnPointClickListener mOnPointClickListener = new DummyOnPointListener();
	private ScaleGestureDetector mScaleGestureDetector = new ScaleGestureDetector(getContext(),
			new ChartScaleGestureListener());
	private GestureDetector mGestureDetector = new GestureDetector(getContext(), new ChartGestureListener());

	public LineChart(Context context) {
		this(context, null, 0);
	}

	public LineChart(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public LineChart(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initAttributes();
		initPaints();
		initAnimatiors();
		mChartCalculator = new ChartCalculator(context);
		mAxisRenderer = new AxesRenderer();
	}

	@SuppressLint("NewApi")
	private void initAttributes() {
		setLayerType(LAYER_TYPE_SOFTWARE, null);
		mLineWidth = Utils.dp2px(getContext(), DEFAULT_LINE_WIDTH_DP);
		mPointRadius = Utils.dp2px(getContext(), DEFAULT_POINT_RADIUS_DP);
		mPointPressedRadius = Utils.dp2px(getContext(), DEFAULT_POINT_PRESSED_RADIUS);
		mTouchRadius = Utils.dp2px(getContext(), DEFAULT_POINT_TOUCH_RADIUS_DP);
		mPopupTextMargin = Utils.dp2px(getContext(), DEFAULT_POPUP_TEXT_MARGIN);
		mScroller = ScrollerCompat.create(getContext());
		mZoomer = new ChartZoomer(getContext());
	}

	private void initPaints() {
		mLinePaint.setAntiAlias(true);
		mLinePaint.setStyle(Paint.Style.STROKE);
		mLinePaint.setStrokeCap(Cap.ROUND);

		mTextPaint.setAntiAlias(true);
		mTextPaint.setStyle(Paint.Style.FILL);
		mTextPaint.setStrokeWidth(1);
		mTextPaint.setTextSize(Utils.dp2px(getContext(), DEFAULT_TEXT_SIZE_DP));
	}

	private void initAnimatiors() {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			mAnimator = new ChartAnimatorV8(this, Config.DEFAULT_ANIMATION_DURATION);
		} else {
			mAnimator = new ChartAnimatorV11(this, Config.DEFAULT_ANIMATION_DURATION);
		}
	}

	@Override
	protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
		super.onSizeChanged(width, height, oldWidth, oldHeight);
		// TODO mPointRadus can change, recalculate in setter
		mChartCalculator.calculateContentArea(this);
		mChartCalculator.calculateViewport(mData);
	}

	// Automatically calculates Y axis values.
	// private Axis calculateYAxis(int numberOfSteps) {
	// if (numberOfSteps < 2) {
	// throw new IllegalArgumentException("Number or steps have to be grater or equal 2");
	// }
	// List<Float> values = new ArrayList<Float>();
	// final float range = mData.getMaxYValue() - mData.getMinYValue();
	// final float tickRange = range / (numberOfSteps - 1);
	// final float x = (float) Math.ceil(Math.log10(tickRange) - 1);
	// final float pow10x = (float) Math.pow(10, x);
	// final float roundedTickRange = (float) Math.ceil(tickRange / pow10x) * pow10x;
	// float value = mData.getMinYValue();
	// while (value <= mData.getMaxYValue()) {
	// values.add(value);
	// value += roundedTickRange;
	// }
	// Axis yAxis = new Axis();
	// yAxis.setValues(values);
	// return yAxis;
	// }

	@Override
	protected void onDraw(Canvas canvas) {
		long time = System.nanoTime();
		super.onDraw(canvas);
		if (mAxesOn) {
			mAxisRenderer.drawAxisX(getContext(), canvas, mData.axisX, mChartCalculator);
			mAxisRenderer.drawAxisY(getContext(), canvas, mData.axisY, mChartCalculator);
		}
		int clipRestoreCount = canvas.save();
		mChartCalculator.calculateClippingArea();// only if zoom is enabled
		canvas.clipRect(mChartCalculator.mClippingRect);
		if (mLinesOn) {
			drawLines(canvas);
		}
		if (mPointsOn) {
			drawPoints(canvas);
		}
		canvas.restoreToCount(clipRestoreCount);
		Log.v(TAG, "onDraw [ms]: " + (System.nanoTime() - time) / 1000000f);
	}

	private void drawLines(Canvas canvas) {
		mLinePaint.setStrokeWidth(mLineWidth);
		for (Line line : mData.lines) {
			if (mInterpolationOn) {
				drawSmoothPath(canvas, line);
			} else {
				drawPath(canvas, line);
			}
			mLinePath.reset();
		}
	}

	// TODO Drawing points can be done in the same loop as drawing lines but it may cause problems in the future. Reuse
	// calculated X/Y;
	private void drawPoints(Canvas canvas) {
		for (Line line : mData.lines) {
			mTextPaint.setColor(line.color);
			for (AnimatedPoint animatedPoint : line.animatedPoints) {
				final float rawValueX = mChartCalculator.calculateRawX(animatedPoint.point.x);
				final float rawValueY = mChartCalculator.calculateRawY(animatedPoint.point.y);
				canvas.drawCircle(rawValueX, rawValueY, mPointRadius, mTextPaint);
				if (mPopupsOn) {
					drawValuePopup(canvas, mPopupTextMargin, animatedPoint.point.y, rawValueX, rawValueY);
				}
			}
		}
		if (mSelectedLineIndex >= 0 && mSelectedPointIndex >= 0) {
			final Line line = mData.lines.get(mSelectedLineIndex);
			final AnimatedPoint animatedPoint = line.animatedPoints.get(mSelectedPointIndex);
			final float rawValueX = mChartCalculator.calculateRawX(animatedPoint.point.x);
			final float rawValueY = mChartCalculator.calculateRawY(animatedPoint.point.y);
			mTextPaint.setColor(line.color);
			canvas.drawCircle(rawValueX, rawValueY, mPointPressedRadius, mTextPaint);
			if (mPopupsOn) {
				drawValuePopup(canvas, mPopupTextMargin, animatedPoint.point.y, rawValueX, rawValueY);
			}
		}
	}

	private void drawValuePopup(Canvas canvas, float offset, float valueY, float rawValueX, float rawValueY) {
		mTextPaint.setTextAlign(Align.LEFT);
		final String text = String.format(Locale.ENGLISH, Config.DEFAULT_VALUE_FORMAT, valueY);
		final Rect textBounds = new Rect();
		mTextPaint.getTextBounds(text, 0, text.length(), textBounds);
		float left = rawValueX + offset;
		float right = rawValueX + offset + textBounds.width() + mPopupTextMargin * 2;
		float top = rawValueY - offset - textBounds.height() - mPopupTextMargin * 2;
		float bottom = rawValueY - offset;
		if (top < getPaddingTop() + mChartCalculator.mCommonMargin) {
			top = rawValueY + offset;
			bottom = rawValueY + offset + textBounds.height() + mPopupTextMargin * 2;
		}
		if (right > getWidth() - getPaddingRight() - mChartCalculator.mCommonMargin) {
			left = rawValueX - offset - textBounds.width() - mPopupTextMargin * 2;
			right = rawValueX - offset;
		}
		final RectF popup = new RectF(left, top, right, bottom);
		canvas.drawRoundRect(popup, mPopupTextMargin, mPopupTextMargin, mTextPaint);
		final int color = mTextPaint.getColor();
		mTextPaint.setColor(DEFAULT_TEXT_COLOR);
		canvas.drawText(text, left + mPopupTextMargin, bottom - mPopupTextMargin, mTextPaint);
		mTextPaint.setColor(color);
	}

	private void drawPath(Canvas canvas, final Line line) {
		int valueIndex = 0;
		for (AnimatedPoint animatedPoint : line.animatedPoints) {
			final float rawValueX = mChartCalculator.calculateRawX(animatedPoint.point.x);
			final float rawValueY = mChartCalculator.calculateRawY(animatedPoint.point.y);
			if (valueIndex == 0) {
				mLinePath.moveTo(rawValueX, rawValueY);
			} else {
				mLinePath.lineTo(rawValueX, rawValueY);
			}
			++valueIndex;
		}
		mLinePaint.setColor(line.color);
		canvas.drawPath(mLinePath, mLinePaint);
		drawArea(canvas);
	}

	private void drawSmoothPath(Canvas canvas, final Line line) {
		final int lineSize = line.animatedPoints.size();
		float previousPointX = Float.NaN;
		float previousPointY = Float.NaN;
		float currentPointX = Float.NaN;
		float currentPointY = Float.NaN;
		float nextPointX = Float.NaN;
		float nextPointY = Float.NaN;
		for (int valueIndex = 0; valueIndex < lineSize - 1; ++valueIndex) {
			if (Float.isNaN(currentPointX)) {
				AnimatedPoint animatedPoint = line.animatedPoints.get(valueIndex);
				currentPointX = mChartCalculator.calculateRawX(animatedPoint.point.x);
				currentPointY = mChartCalculator.calculateRawY(animatedPoint.point.y);
			}
			if (Float.isNaN(previousPointX)) {
				if (valueIndex > 0) {
					AnimatedPoint animatedPoint = line.animatedPoints.get(valueIndex - 1);
					previousPointX = mChartCalculator.calculateRawX(animatedPoint.point.x);
					previousPointY = mChartCalculator.calculateRawY(animatedPoint.point.y);
				} else {
					previousPointX = currentPointX;
					previousPointY = currentPointY;
				}
			}
			if (Float.isNaN(nextPointX)) {
				AnimatedPoint animatedPoint = line.animatedPoints.get(valueIndex + 1);
				nextPointX = mChartCalculator.calculateRawX(animatedPoint.point.x);
				nextPointY = mChartCalculator.calculateRawY(animatedPoint.point.y);
			}
			// afterNextPoint is always new one or it is equal nextPoint.
			final float afterNextPointX;
			final float afterNextPointY;
			if (valueIndex < lineSize - 2) {
				AnimatedPoint animatedPoint = line.animatedPoints.get(valueIndex + 2);
				afterNextPointX = mChartCalculator.calculateRawX(animatedPoint.point.x);
				afterNextPointY = mChartCalculator.calculateRawY(animatedPoint.point.y);
			} else {
				afterNextPointX = nextPointX;
				afterNextPointY = nextPointY;
			}
			// Calculate control points.
			final float firstDiffX = (nextPointX - previousPointX);
			final float firstDiffY = (nextPointY - previousPointY);
			final float secondDiffX = (afterNextPointX - currentPointX);
			final float secondDiffY = (afterNextPointY - currentPointY);
			final float firstControlPointX = currentPointX + (LINE_SMOOTHNES * firstDiffX);
			final float firstControlPointY = currentPointY + (LINE_SMOOTHNES * firstDiffY);
			final float secondControlPointX = nextPointX - (LINE_SMOOTHNES * secondDiffX);
			final float secondControlPointY = nextPointY - (LINE_SMOOTHNES * secondDiffY);
			// Move to start point.
			if (valueIndex == 0) {
				mLinePath.moveTo(currentPointX, currentPointY);
			}
			mLinePath.cubicTo(firstControlPointX, firstControlPointY, secondControlPointX, secondControlPointY,
					nextPointX, nextPointY);
			// Shift values to prevent recalculation of values that have been already calculated.
			previousPointX = currentPointX;
			previousPointY = currentPointY;
			currentPointX = nextPointX;
			currentPointY = nextPointY;
			nextPointX = afterNextPointX;
			nextPointY = afterNextPointY;
		}
		mLinePaint.setColor(line.color);
		canvas.drawPath(mLinePath, mLinePaint);
		drawArea(canvas);
	}

	private void drawArea(Canvas canvas) {
		mLinePaint.setStyle(Paint.Style.FILL);
		mLinePaint.setAlpha(DEFAULT_AREA_TRANSPARENCY);
		mLinePath.lineTo(mChartCalculator.mContentRect.right, mChartCalculator.mContentRect.bottom);
		mLinePath.lineTo(mChartCalculator.mContentRect.left, mChartCalculator.mContentRect.bottom);
		mLinePath.close();
		canvas.drawPath(mLinePath, mLinePaint);
		mLinePaint.setStyle(Paint.Style.STROKE);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		mScaleGestureDetector.onTouchEvent(event);
		mGestureDetector.onTouchEvent(event);
		if (!mPointsOn) {
			// No point - no touch events.
			return true;
		}
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			// Select only the first value within touched area.
			// Reverse loop to starts with the line drawn on top.
			for (int lineIndex = mData.lines.size() - 1; lineIndex >= 0; --lineIndex) {
				int valueIndex = 0;
				for (AnimatedPoint animatedPoint : mData.lines.get(lineIndex).animatedPoints) {
					final float rawX = mChartCalculator.calculateRawX(animatedPoint.point.x);
					final float rawY = mChartCalculator.calculateRawY(animatedPoint.point.y);
					if (Utils.isInArea(rawX, rawY, event.getX(), event.getY(), mTouchRadius)) {
						mSelectedLineIndex = lineIndex;
						mSelectedPointIndex = valueIndex;
						invalidate();
						return true;
					}
					++valueIndex;
				}
			}
			return true;
		case MotionEvent.ACTION_UP:
			// If value was selected call click listener and clear selection.
			if (mSelectedPointIndex >= 0) {
				final Line line = mData.lines.get(mSelectedLineIndex);
				final AnimatedPoint animatedPoint = line.animatedPoints.get(mSelectedPointIndex);
				mOnPointClickListener.onPointClick(mSelectedLineIndex, mSelectedPointIndex, animatedPoint.point.x,
						animatedPoint.point.y);
				mSelectedLineIndex = Integer.MIN_VALUE;
				mSelectedPointIndex = Integer.MIN_VALUE;
				invalidate();
			}
			return true;
		case MotionEvent.ACTION_MOVE:
			// Clear selection if user is now touching outside touch area.
			if (mSelectedPointIndex >= 0) {
				final Line line = mData.lines.get(mSelectedLineIndex);
				final AnimatedPoint animatedPoint = line.animatedPoints.get(mSelectedPointIndex);
				final float rawX = mChartCalculator.calculateRawX(animatedPoint.point.x);
				final float rawY = mChartCalculator.calculateRawY(animatedPoint.point.y);
				if (!Utils.isInArea(rawX, rawY, event.getX(), event.getY(), mTouchRadius)) {
					mSelectedLineIndex = Integer.MIN_VALUE;
					mSelectedPointIndex = Integer.MIN_VALUE;
					invalidate();
				}
			}
			return true;
		case MotionEvent.ACTION_CANCEL:
			// Clear selection
			if (mSelectedPointIndex >= 0) {
				mSelectedLineIndex = Integer.MIN_VALUE;
				mSelectedPointIndex = Integer.MIN_VALUE;
				invalidate();
			}
			return true;
		default:
			return true;
		}
	}

	@Override
	public void computeScroll() {
		super.computeScroll();
		if (mScroller.computeScrollOffset()) {
			// The scroller isn't finished, meaning a fling or programmatic pan operation is
			// currently active.
			mChartCalculator.computeScrollSurfaceSize(mSurfaceSizeBuffer);
			float currXRange = mChartCalculator.mMaximumViewport.left + mChartCalculator.mMaximumViewport.width()
					* mScroller.getCurrX() / mSurfaceSizeBuffer.x;
			float currYRange = mChartCalculator.mMaximumViewport.bottom - mChartCalculator.mMaximumViewport.height()
					* mScroller.getCurrY() / mSurfaceSizeBuffer.y;
			mChartCalculator.setViewportBottomLeft(currXRange, currYRange, this);
		}

		if (mZoomer.computeZoom()) {
			// Performs the zoom since a zoom is in progress (either programmatically or via
			// double-touch).
			final float newWidth = (1.0f - mZoomer.getCurrZoom()) * mScrollerStartViewport.width();
			final float newHeight = (1.0f - mZoomer.getCurrZoom()) * mScrollerStartViewport.height();
			final float pointWithinViewportX = (mZoomFocalPoint.x - mScrollerStartViewport.left)
					/ mScrollerStartViewport.width();
			final float pointWithinViewportY = (mZoomFocalPoint.y - mScrollerStartViewport.top)
					/ mScrollerStartViewport.height();
			mChartCalculator.mCurrentViewport.left = mZoomFocalPoint.x - newWidth * pointWithinViewportX;
			mChartCalculator.mCurrentViewport.top = mZoomFocalPoint.y - newHeight * pointWithinViewportY;
			mChartCalculator.mCurrentViewport.right = mZoomFocalPoint.x + newWidth * (1 - pointWithinViewportX);
			mChartCalculator.mCurrentViewport.bottom = mZoomFocalPoint.y + newHeight * (1 - pointWithinViewportY);
			mChartCalculator.constrainViewport();
			ViewCompat.postInvalidateOnAnimation(this);
		}
	}

	private void fling(int velocityX, int velocityY) {
		// Flings use math in pixels (as opposed to math based on the viewport).
		mChartCalculator.computeScrollSurfaceSize(mSurfaceSizeBuffer);
		mScrollerStartViewport.set(mChartCalculator.mCurrentViewport);
		int startX = (int) (mSurfaceSizeBuffer.x
				* (mScrollerStartViewport.left - mChartCalculator.mMaximumViewport.left) / mChartCalculator.mMaximumViewport
				.width());
		int startY = (int) (mSurfaceSizeBuffer.y
				* (mChartCalculator.mMaximumViewport.bottom - mScrollerStartViewport.bottom) / mChartCalculator.mMaximumViewport
				.height());
		mScroller.abortAnimation();// probably should be mScroller.forceFinish but compat doesn't have that method.
		mScroller.fling(startX, startY, velocityX, velocityY, 0,
				mSurfaceSizeBuffer.x - mChartCalculator.mContentRect.width(), 0, mSurfaceSizeBuffer.y
						- mChartCalculator.mContentRect.height(), mChartCalculator.mContentRect.width() / 2,
				mChartCalculator.mContentRect.height() / 2);
		ViewCompat.postInvalidateOnAnimation(this);
	}

	public void setData(final Data data) {
		mData = data;
		mData.calculateRanges();
		mChartCalculator.calculateAxesMargins(getContext(), mAxisRenderer, mData);
		mChartCalculator.calculateViewport(mData);
		ViewCompat.postInvalidateOnAnimation(LineChart.this);
	}

	public Data getData() {
		return mData;
	}

	public void animationUpdate(float scale) {
		for (AnimatedPoint animatedPoint : mData.lines.get(0).animatedPoints) {
			animatedPoint.update(scale);
		}
		mData.calculateRanges();
		mChartCalculator.calculateViewport(mData);
		ViewCompat.postInvalidateOnAnimation(LineChart.this);
	}

	public void animateSeries(int index, List<lecho.lib.hellocharts.model.Point> points) {
		mAnimator.cancelAnimation();
		mData.updateLineTarget(index, points);
		mAnimator.startAnimation();
	}

	public void updateSeries(int index, List<lecho.lib.hellocharts.model.Point> points) {
		mData.updateLine(index, points);
		ViewCompat.postInvalidateOnAnimation(LineChart.this);
	}

	public void setOnPointClickListener(OnPointClickListener listener) {
		if (null == listener) {
			mOnPointClickListener = new DummyOnPointListener();
		} else {
			mOnPointClickListener = listener;
		}
	}

	// Just empty listener to avoid NPE checks.
	private static class DummyOnPointListener implements OnPointClickListener {

		@Override
		public void onPointClick(int selectedSeriesIndex, int selectedValueIndex, float x, float y) {
			// Do nothing.
		}

	}

	private class ChartScaleGestureListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
		private PointF viewportFocus = new PointF();

		@Override
		public boolean onScale(ScaleGestureDetector detector) {
			/**
			 * Smaller viewport means bigger zoom so for zoomIn scale should have value <1, for zoomOout >1
			 */
			float scale = 2.0f - detector.getScaleFactor();
			final float newWidth = scale * mChartCalculator.mCurrentViewport.width();
			final float newHeight = scale * mChartCalculator.mCurrentViewport.height();
			final float focusX = detector.getFocusX();
			final float focusY = detector.getFocusY();
			mChartCalculator.rawPixelsToDataPoint(focusX, focusY, viewportFocus);
			mChartCalculator.mCurrentViewport.left = viewportFocus.x - (focusX - mChartCalculator.mContentRect.left)
					* (newWidth / mChartCalculator.mContentRect.width());
			mChartCalculator.mCurrentViewport.top = viewportFocus.y - (mChartCalculator.mContentRect.bottom - focusY)
					* (newHeight / mChartCalculator.mContentRect.height());
			mChartCalculator.mCurrentViewport.right = mChartCalculator.mCurrentViewport.left + newWidth;
			mChartCalculator.mCurrentViewport.bottom = mChartCalculator.mCurrentViewport.top + newHeight;
			mChartCalculator.constrainViewport();
			ViewCompat.postInvalidateOnAnimation(LineChart.this);
			return true;
		}
	}

	private class ChartGestureListener extends GestureDetector.SimpleOnGestureListener {
		@Override
		public boolean onDown(MotionEvent e) {
			mScrollerStartViewport.set(mChartCalculator.mCurrentViewport);
			mScroller.abortAnimation();
			ViewCompat.postInvalidateOnAnimation(LineChart.this);
			return true;
		}

		@Override
		public boolean onDoubleTap(MotionEvent e) {
			mZoomer.forceFinished(true);
			if (mChartCalculator.rawPixelsToDataPoint(e.getX(), e.getY(), mZoomFocalPoint)) {
				mZoomer.startZoom(ZOOM_AMOUNT);
			}
			ViewCompat.postInvalidateOnAnimation(LineChart.this);
			return true;
		}

		@Override
		public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
			// Scrolling uses math based on the viewport (as opposed to math using pixels).
			/**
			 * Pixel offset is the offset in screen pixels, while viewport offset is the offset within the current
			 * viewport. For additional information on surface sizes and pixel offsets, see the docs for {@link
			 * computeScrollSurfaceSize()}. For additional information about the viewport, see the comments for
			 * {@link mCurrentViewport}.
			 */
			float viewportOffsetX = distanceX * mChartCalculator.mCurrentViewport.width()
					/ mChartCalculator.mContentRect.width();
			float viewportOffsetY = -distanceY * mChartCalculator.mCurrentViewport.height()
					/ mChartCalculator.mContentRect.height();
			mChartCalculator.computeScrollSurfaceSize(mSurfaceSizeBuffer);
			mChartCalculator.setViewportBottomLeft(mChartCalculator.mCurrentViewport.left + viewportOffsetX,
					mChartCalculator.mCurrentViewport.bottom + viewportOffsetY, LineChart.this);
			return true;
		}

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
			fling((int) -velocityX, (int) -velocityY);
			return true;
		}
	}

}
