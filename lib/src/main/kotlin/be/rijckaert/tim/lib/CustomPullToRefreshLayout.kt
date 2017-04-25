package be.rijckaert.tim.lib

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ObjectAnimator.ofInt
import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.support.v4.view.MotionEventCompat
import android.support.v4.view.ViewCompat
import android.support.v7.widget.TintTypedArray
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.MotionEvent.*
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import com.airbnb.lottie.LottieAnimationView
import java.lang.Math.abs
import java.lang.Math.min

/**
 *
 * @author Tim Rijckaert
 * @author Simon Vergauwen
 *
 * A SwipeRefreshLayout which can display a Lottie Animation.
 * Custom Attributes include:
 * - The height of the Lottie View
 * - The animation json (it should reside in the assets-folder, don't forget the '.json' extension)
 * - The maximum maximum duration of the resetting animation
 *
 * ViewState check!
 *
 * How?
 * Most code is copied from @link{SwipeRefreshLayout}.
 * `onInterceptTouchEvent()` intercepts the touch event and decides if the user is dragging or not.
 * true ==> Intercept the touch event and process it in `onTouchEvent()`
 * false ==> Pass it to its children
 * (We also prevent touch events when the lottie animation is still running) --> Maybe expose this an attribute.
 *
 * `onTouchEvent()` checks if the complete drag distance has been reached to call the `setRefreshing` if this condition has been met.
 * If the user did not surpass the threshold the view snaps back to its original position.
 *
 */
class CustomPullToRefreshLayout @JvmOverloads constructor(context: Context,
                                                          attrs: AttributeSet? = null,
                                                          defStyleAttr: Int = 0) : ViewGroup(context, attrs, defStyleAttr) {

    //<editor-fold desc="Fields & State Keeping">
    var isRefreshing: Boolean = false
    val onCanChildStillScrollUp: ((CustomPullToRefreshLayout, View?) -> Boolean)? = null
    var onRefreshListener: (() -> Unit)? = null

    private var isBeingDragged: Boolean = false

    private val touchSlop by lazy { ViewConfiguration.get(context).scaledTouchSlop }

    //Think RecyclerView
    private val target by lazy {
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
        localView
    }
    private val refreshView by lazy {
        LottieAnimationView(context).apply {
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setAnimation(ANIMATION_RESOURCE_NAME)
            loop(true)
        }
    }

    private val resetRefreshAnimation: AnimatorListenerAdapter by lazy {
        object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                refreshView.top = -REFRESH_VIEW_HEIGHT
                refreshView.bottom = 0
            }

            override fun onAnimationCancel(animation: Animator?) {
                refreshView.top = -REFRESH_VIEW_HEIGHT
                refreshView.bottom = 0
            }
        }
    }

    private var targetPaddingBottom: Int = 0
    private var targetPaddingLeft: Int = 0
    private var targetPaddingRight: Int = 0
    private var targetPaddingTop: Int = 0

    private var initialMotionY: Float = 0f
    private var activePointerId: Int = 0

    private val currentOffsetTop: Int
        inline get() = target.top

    private val totalDragDistance: Float

    private var currentDragPercent: Float = 0f
    private var fromDragPercent: Float = 0f

    private val DRAG_RATE = .85f

    private val EXTRA_SUPER_STATE = "be.rijckaert.tim.lib.CustomPullToRefreshLayout.EXTRA_SUPER_STATE"
    private val EXTRA_IS_REFRESHING = "be.rijckaert.tim.lib.CustomPullToRefreshLayout.EXTRA_IS_REFRESHING"

    //Customizable Properties
    private val DEFAULT_REFRESH_VIEW_HEIGHT = 300
    private val DEFAULT_OFFSET_ANIMATION_DURATION = 400

    private var REFRESH_VIEW_HEIGHT = DEFAULT_REFRESH_VIEW_HEIGHT
    private var MAX_OFFSET_ANIMATION_DURATION = DEFAULT_OFFSET_ANIMATION_DURATION //ms
    private var ANIMATION_RESOURCE_NAME = "pull_to_refresh.json"
    //</editor-fold>

    private val DRAG_MAX_DISTANCE by lazy { pxTodp(REFRESH_VIEW_HEIGHT).toInt() } //dp

    init {
        val styledAttributes = TintTypedArray.obtainStyledAttributes(context, attrs, R.styleable.CustomPullToRefreshLayout, 0, 0)
        initializeRefreshViewHeight(styledAttributes)
        initializeLottieAnimation(styledAttributes)
        initializeMaxOffSetAnimationDuration(styledAttributes)
        styledAttributes.recycle()

        checkPreconditions()

        totalDragDistance = dpToPx(DRAG_MAX_DISTANCE)

        addView(refreshView)

        //This ViewGroup does not draw things on the canvas
        setWillNotDraw(false)
    }

    fun setRefreshing(refreshing: Boolean, notify: Boolean = false) {
        if (isRefreshing != refreshing) {
            isRefreshing = refreshing
            if (isRefreshing) {
                refreshView.playAnimation()
                if (notify) {
                    onRefreshListener?.invoke()
                }

                fromDragPercent = currentDragPercent
                target.setPadding(targetPaddingLeft, targetPaddingTop, targetPaddingRight, targetPaddingBottom)
            } else {
                animateOffsetToStartPosition()
            }
        }
    }

    //<editor-fold desc="View Rendering">
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        target.let {
            val height = measuredHeight
            val width = measuredWidth
            val left = paddingLeft
            val top = paddingTop
            val right = paddingRight
            val bottom = paddingBottom

            it.layout(left, top + it.top, left + width - right, top + height - bottom + it.top)

            //Our refresh animation is above our first child
            refreshView.layout(left, -REFRESH_VIEW_HEIGHT, width, top)
        }
    }
    //</editor-fold>

    //<editor-fold desc="Save State">
    override fun onSaveInstanceState(): Parcelable {
        val bundle = Bundle()
        bundle.putParcelable(EXTRA_SUPER_STATE, super.onSaveInstanceState())
        bundle.putBoolean(EXTRA_IS_REFRESHING, isRefreshing)
        return bundle
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            super.onRestoreInstanceState(state.getParcelable<Parcelable>(EXTRA_SUPER_STATE))
            if (state.getBoolean(EXTRA_IS_REFRESHING)) {
                post {
                    setRefreshing(true)
                }
            }
        }
    }
    //</editor-fold>

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        //Prevent the list from scrolling while a pull to refresh animation is ongoing
        if (isRefreshing) {
            return true
        }

        //Ignore scroll touch events when the user is not on the top of the list
        if (!isEnabled || canChildScrollUp()) {
            return false
        }

        when (MotionEventCompat.getActionMasked(ev)) {
            ACTION_DOWN -> {
                activePointerId = ev.getPointerId(0)
                isBeingDragged = false
                val motionY = getMotionEventY(ev)
                if (motionY == -1f) {
                    return false
                }
                initialMotionY = motionY
            }
            ACTION_MOVE -> {
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
            }
        }

        //Return true to steal motion events from the children and have
        //them dispatched to this ViewGroup through onTouchEvent()
        return isBeingDragged
    }

    override fun onTouchEvent(motionEvent: MotionEvent): Boolean {
        //If the user is still moving the pointer don't respond yet
        if (!isBeingDragged) {
            return super.onTouchEvent(motionEvent)
        }

        when (MotionEventCompat.getActionMasked(motionEvent)) {
            ACTION_MOVE -> {
                val pointerIndex = motionEvent.findPointerIndex(activePointerId)
                if (pointerIndex != 0) {
                    return false
                }

                val y = motionEvent.getY(pointerIndex)

                val yDiff = y - initialMotionY
                val scrollTop = yDiff * DRAG_RATE
                currentDragPercent = scrollTop / totalDragDistance
                if (currentDragPercent < 0) {
                    return false
                }
                val boundedDragPercent = min(1f, abs(currentDragPercent))
                val slingshotDist = totalDragDistance
                val targetY = (slingshotDist * boundedDragPercent).toInt()

                refreshView.progress = boundedDragPercent

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

    //<editor-fold desc="Helper Functions">
    private fun initializeMaxOffSetAnimationDuration(styledAttributes: TintTypedArray) {
        val durationStyleableInt = R.styleable.CustomPullToRefreshLayout_maxResetAnimationDuration
        if (styledAttributes.hasValue(durationStyleableInt)) {
            MAX_OFFSET_ANIMATION_DURATION = styledAttributes.getInteger(durationStyleableInt, DEFAULT_OFFSET_ANIMATION_DURATION)
        }
    }

    private fun initializeLottieAnimation(styledAttributes: TintTypedArray) {
        ANIMATION_RESOURCE_NAME = styledAttributes.getString(R.styleable.CustomPullToRefreshLayout_lottieAnimation)
    }

    private fun initializeRefreshViewHeight(styledAttributes: TintTypedArray) {
        val animationHeightStyleableInt = R.styleable.CustomPullToRefreshLayout_lottieAnimationHeight
        if (styledAttributes.hasValue(animationHeightStyleableInt)) {
            REFRESH_VIEW_HEIGHT = styledAttributes.getInteger(animationHeightStyleableInt, DEFAULT_REFRESH_VIEW_HEIGHT)
        }
    }

    /**
     * Prevent our ViewGroup from having more than one child.
     */
    private fun checkPreconditions() {
        if (childCount > 1) {
            throw IllegalStateException("You can attach only one child to the CustomPullToRefreshLayout!")
        }
    }

    private fun setTargetOffsetTop(offset: Int) {
        target.offsetTopAndBottom(offset)
        refreshView.offsetTopAndBottom(offset)
    }

    private fun animateOffsetToStartPosition() {
        fromDragPercent = currentDragPercent

        refreshView.cancelAnimation()

        //calculated value to decide how long the reset animation should take
        val animationDuration = abs((MAX_OFFSET_ANIMATION_DURATION * fromDragPercent).toLong())

        val refreshResetAnimation: ObjectAnimator =
                ofInt(refreshView, "top", -REFRESH_VIEW_HEIGHT)
                        .apply {
                            addListener(resetRefreshAnimation)
                            duration = animationDuration
                        }
        val targetResetAnimation = ofInt(target, "top", 0).apply { duration = animationDuration }
        AnimatorSet().apply { playTogether(refreshResetAnimation, targetResetAnimation) }.start()
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

    /**
     * Checks whether the target can scroll up vertically. ref. Are we at the beginning of the list or not.
     */
    private fun canChildScrollUp(): Boolean {
        onCanChildStillScrollUp?.let {
            return it(this, target)
        }
        return ViewCompat.canScrollVertically(target, -1)
    }

    private fun dpToPx(dp: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics)

    private fun pxTodp(px: Int) = px / resources.displayMetrics.density
    //</editor-fold>
}