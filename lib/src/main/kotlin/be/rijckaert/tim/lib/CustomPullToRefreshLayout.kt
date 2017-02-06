package be.rijckaert.tim.lib

import android.content.Context
import android.os.Parcelable
import android.support.v4.view.MotionEventCompat
import android.support.v4.view.ViewCompat
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.MotionEvent.*
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import com.airbnb.lottie.LottieAnimationView

class CustomPullToRefreshLayout @JvmOverloads constructor(context: Context,
                                                          attrs: AttributeSet? = null,
                                                          defStyleAttr: Int = 0) : ViewGroup(context, attrs, defStyleAttr) {

    var isRefreshing: Boolean = false
    var onChildScrollUpCallback: OnChildScrollUpCallback? = null
    var onRefreshListener: OnRefreshListener? = null

    private var isBeingDragged: Boolean = false
    private val touchSlop by lazy { ViewConfiguration.get(context).scaledTouchSlop }
    private val target: View by lazy { getTargetView() }

    private val refreshView by lazy { LottieAnimationView(context) }
    private var targetPaddingBottom: Int = 0
    private var targetPaddingLeft: Int = 0
    private var targetPaddingRight: Int = 0

    private var targetPaddingTop: Int = 0
    private var initialMotionY: Float = 0F

    private var activePointerId: Int = 0
    private var currentOffsetTop: Int = 0

    private val totalDragDistance: Float
    private var currentDragPercent: Float = 0F
    private var fromDragPercent: Float = 0F
    private var from: Int = 0

    private val MAX_OFFSET_ANIMATION_DURATION = 700 //ms
    private val DRAG_MAX_DISTANCE: Int = 230 //dp
    private val DRAG_RATE = .85f

    init {
        if (childCount > 1) {
            throw  RuntimeException("You can attach only one child to the CustomPullToRefreshLayout!")
        }

        totalDragDistance = dpToPx(DRAG_MAX_DISTANCE)

        addView(refreshView)
        setWillNotDraw(false)
        ViewCompat.setChildrenDrawingOrderEnabled(this, true)
    }

    override fun onMeasure(width: Int, height: Int) {
        super.onMeasure(width, height)

        val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(measuredWidth - paddingLeft - paddingRight, View.MeasureSpec.EXACTLY)
        val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(measuredHeight - paddingTop - paddingBottom, View.MeasureSpec.EXACTLY)

        target.measure(widthMeasureSpec, heightMeasureSpec)
        refreshView.measure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        target.let {
            val height = measuredHeight
            val width = measuredWidth
            val left = paddingLeft
            val top = paddingTop
            val right = paddingRight
            val bottom = paddingBottom

            it.layout(left, top + it.top, left + width - right, top + height - bottom + it.top)
            refreshView.layout(left, top, left + width - right, top + height - bottom)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!isEnabled || canChildScrollUp() || isRefreshing) {
            return false
        }

        when (MotionEventCompat.getActionMasked(ev)) {
            ACTION_DOWN -> {
                setTargetOffsetTop(0)
                activePointerId = ev.getPointerId(0)
                isBeingDragged = false
                val motionY = getMotionEventY(ev)
                if (motionY == -1f) {
                    return false
                }
                initialMotionY = motionY
            }
            /*ACTION_MOVE -> {
                if (activePointerId == INVALID_POINTER_ID) {
                    return false
                }

                val y = getMotionEventY(ev)
                if (y == -1f) {
                    return false
                }
                val yDiff = y - initialMotionY
                if (yDiff > touchSlop && !isBeingDragged) {
                    isBeingDragged = true
                }
            }
            ACTION_UP, ACTION_CANCEL -> {
                isBeingDragged = false
                activePointerId = INVALID_POINTER_ID
            }
            ACTION_POINTER_UP -> {
                onSecondaryPointerUp(ev)
            }*/
        }

        return isBeingDragged
    }

    private fun onSecondaryPointerUp(motionEvent: MotionEvent) {
        val pointerIndex = MotionEventCompat.getActionIndex(motionEvent)
        val pointerId = motionEvent.getPointerId(pointerIndex)
        if (pointerId == activePointerId) {
            activePointerId = motionEvent.getPointerId(if (pointerIndex == 0) 1 else 0)
        }
    }

    private fun getMotionEventY(motionEvent: MotionEvent): Float {
        val index = motionEvent.findPointerIndex(activePointerId)
        if (index < 0) {
            return -1f
        }
        return motionEvent.getY(index)
    }

    private fun setTargetOffsetTop(offset: Int) {
        //target.offsetTopAndBottom(offset)
        //refreshView.offsetTopAndBottom(offset)
        currentOffsetTop = target.top
    }

    override fun onSaveInstanceState(): Parcelable {
        return super.onSaveInstanceState()
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        super.onRestoreInstanceState(state)
    }

    override fun onTouchEvent(motionEvent: MotionEvent): Boolean {
        if (!isBeingDragged) {
            return super.onTouchEvent(motionEvent)
        }

        when (MotionEventCompat.getActionMasked(motionEvent)) {
            ACTION_MOVE -> {
                val pointerIndex = motionEvent.findPointerIndex(activePointerId)
                if (pointerIndex != 0) {
                    return false
                }
                val x = motionEvent.getX(pointerIndex)
                val y = motionEvent.getY(pointerIndex)
                val yDiff = y - initialMotionY
                val scrollTop = yDiff * DRAG_RATE
                currentDragPercent = scrollTop / totalDragDistance
                if (currentDragPercent < 0) {
                    return false
                }
                val boundedDragPercent = Math.min(1f, Math.abs(currentDragPercent))
                val slingshotDist = totalDragDistance
                val targetY = (slingshotDist * boundedDragPercent).toInt()

                //TODO: Tell LottieView to already load a piece of the animation puzzle
                //mRefreshDrawable.setPointerPosition(x, y)
                //mRefreshDrawable.setPercent(currentDragPercent, true)

                setTargetOffsetTop(targetY - currentOffsetTop)
            }
            ACTION_POINTER_DOWN -> {
                activePointerId = motionEvent.getPointerId(MotionEventCompat.getActionIndex(motionEvent))
            }
            ACTION_POINTER_UP -> {
                onSecondaryPointerUp(motionEvent)
            }
            ACTION_UP, ACTION_CANCEL -> {
                if (activePointerId == INVALID_POINTER_ID) {
                    return false
                }
                val y = motionEvent.getY(motionEvent.findPointerIndex(activePointerId))
                val overScrollTop = (y - initialMotionY) * DRAG_RATE
                isBeingDragged = false
                if (overScrollTop > totalDragDistance) {
                    setRefreshing(true, true)
                } else {
                    isRefreshing = false
                    animateOffsetToStartPosition()
                }
                activePointerId = INVALID_POINTER_ID
            }
        }

        return true
    }

    private fun animateOffsetToStartPosition() {
        fromDragPercent = currentDragPercent
        from = currentOffsetTop
        val animationDuration = Math.abs((MAX_OFFSET_ANIMATION_DURATION * fromDragPercent).toLong())

        //reset the animation values
    }

    private fun setRefreshing(refreshing: Boolean, notify: Boolean) {
        if (isRefreshing != refreshing) {

            isRefreshing = refreshing
            if (isRefreshing) {

                //TODO: Tell LottieView to stop doing something
                //mRefreshDrawable.setPercent(1f, true)

                //from = currentOffsetTop
                //fromDragPercent = currentDragPercent
                //
                //animateToCorrectPosition.reset()
                //animateToCorrectPosition.setDuration(MAX_OFFSET_ANIMATION_DURATION.toLong())
                //animateToCorrectPosition.setInterpolator(decelerateInterpolator)
                //
                //refreshView.clearAnimation()
                //refreshView.startAnimation(animateToCorrectPosition)

                //mRefreshDrawable.start()
                if (notify) {
                    onRefreshListener?.onRefresh()
                }

                currentOffsetTop = target.top
                target.setPadding(targetPaddingLeft, targetPaddingTop, targetPaddingRight, targetPaddingBottom)
            } else {
                //animateOffsetToStartPosition()
                //mRefreshDrawable.cancelAnimation()
            }
        }
    }

    private fun canChildScrollUp(): Boolean {
        if (onChildScrollUpCallback != null) {
            onChildScrollUpCallback?.let {
                return it.canChildScrollUp(this, target)
            }
        }

        return ViewCompat.canScrollVertically(target, -1)
    }

    private fun dpToPx(dp: Int): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics)

    private fun getTargetView(): View {
        var localView: View = getChildAt(0)
        for (i in 0..childCount - 1) {
            val child = getChildAt(i)
            if (child !== refreshView) {
                localView = child
                targetPaddingBottom = localView.paddingBottom
                targetPaddingLeft = localView.paddingLeft
                targetPaddingRight = localView.paddingRight
                targetPaddingTop = localView.paddingTop
            }
        }
        return localView
    }

    interface OnChildScrollUpCallback {
        fun canChildScrollUp(parent: CustomPullToRefreshLayout, child: View?): Boolean
    }

    interface OnRefreshListener {
        fun onRefresh()
    }
}