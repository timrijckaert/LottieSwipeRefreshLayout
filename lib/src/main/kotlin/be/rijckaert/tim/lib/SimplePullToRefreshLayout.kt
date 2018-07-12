package be.rijckaert.tim.lib

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.animation.DecelerateInterpolator

@SuppressLint("DrawAllocation")
open class SimplePullToRefreshLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0)
    : ViewGroup(context, attrs, defStyle), RefreshView {

    var triggerOffSetTop = 0
        private set
    var maxOffSetTop = 0
        private set

    private var downX = 0F
    private var downY = 0F

    private var offsetY = 0F
    private var lastPullFraction = 0F

    private var currentState: State = State.IDLE

    private val onProgressListeners: MutableCollection<(Float) -> Unit> = mutableListOf()
    private val onTriggerListeners: MutableCollection<() -> Unit> = mutableListOf()

    companion object {
        private const val STICKY_FACTOR = 0.66F
        private const val STICKY_MULTIPLIER = 0.75F
        private const val ROLL_BACK_DURATION = 500L
    }

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.SimplePullToRefreshLayout, defStyle, 0).let {
            val defaultValue = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, -1f, context.resources.displayMetrics).toInt()

            triggerOffSetTop = it.getDimensionPixelOffset(R.styleable.SimplePullToRefreshLayout_trigger_offset_top, defaultValue)
            maxOffSetTop = it.getDimensionPixelOffset(R.styleable.SimplePullToRefreshLayout_max_offset_top, defaultValue)
            it.recycle()
        }
    }

    private lateinit var topChildView: ChildView
    private lateinit var contentChildView: ChildView

    override fun onFinishInflate() {
        super.onFinishInflate()

        if (childCount != 2) {
            throw IllegalStateException("Only a topView and a contentView are allowed. Exactly 2 children are expected, but was $childCount")
        }

        (0 until childCount).map {
            val child = getChildAt(it)
            val layoutParams = child.layoutParams as LayoutParams
            when (layoutParams.type) {
                SimplePullToRefreshLayout.ViewType.UNKNOWN -> throw IllegalStateException("Could not parse layout type")
                SimplePullToRefreshLayout.ViewType.TOP_VIEW -> topChildView = ChildView(child)
                SimplePullToRefreshLayout.ViewType.CONTENT -> contentChildView = ChildView(child)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        fun measureChild(childView: ChildView, widthMeasureSpec: Int, heightMeasureSpec: Int) {
            measureChildWithMargins(childView.view, widthMeasureSpec, 0, heightMeasureSpec, 0)
        }

        fun setInitialValues() {
            val topView = topChildView.view
            val layoutParams = topView.layoutParams as LayoutParams
            val topViewHeight = topView.measuredHeight + layoutParams.topMargin + layoutParams.bottomMargin
            topChildView = topChildView.copy(positionAttr = PositionAttr(height = topViewHeight))

            triggerOffSetTop = if (triggerOffSetTop < 0) topViewHeight / 2 else triggerOffSetTop
            maxOffSetTop = if (maxOffSetTop < 0) topViewHeight else maxOffSetTop
        }

        measureChild(topChildView, widthMeasureSpec, heightMeasureSpec)
        measureChild(contentChildView, widthMeasureSpec, heightMeasureSpec)

        setInitialValues()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        fun layoutTopView() {
            val topView = topChildView.view
            val topViewAttr = topChildView.positionAttr

            val lp = topView.layoutParams as LayoutParams
            val left: Int = paddingLeft + lp.leftMargin
            val top: Int = (paddingTop + lp.topMargin) - topViewAttr.height
            val right: Int = left + topView.measuredWidth
            val bottom = 0

            topChildView = topChildView.copy(positionAttr = PositionAttr(left = left, top = top, right = right, bottom = bottom))
            topView.layout(left, top, right, bottom)
        }

        fun layoutContentView() {
            val contentView = contentChildView.view

            val lp = contentView.layoutParams as LayoutParams
            val left: Int = paddingLeft + lp.leftMargin
            val top: Int = paddingTop + lp.topMargin
            val right: Int = left + contentView.measuredWidth
            val bottom: Int = top + contentView.measuredHeight

            contentChildView = contentChildView.copy(positionAttr = PositionAttr(left = left, top = top, right = right, bottom = bottom))
            contentView.layout(left, top, right, bottom)
        }

        layoutTopView()
        layoutContentView()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        fun checkIfScrolledFurther(ev: MotionEvent, dy: Float, dx: Float) =
                if (!contentChildView.view.canScrollVertically(-1)) {
                    ev.y > downY && Math.abs(dy) > Math.abs(dx)
                } else {
                    false
                }

        var shouldStealTouchEvents = false

        if (currentState != State.IDLE) {
            shouldStealTouchEvents = false
        }

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                shouldStealTouchEvents = checkIfScrolledFurther(ev, dy, dx)
            }
        }

        return shouldStealTouchEvents
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        var handledTouchEvent = true

        if (currentState != State.IDLE) {
            handledTouchEvent = false
        }

        parent.requestDisallowInterceptTouchEvent(true)
        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                offsetY = (event.y - downY) * (1 - STICKY_FACTOR * STICKY_MULTIPLIER)
                move()
            }
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_UP -> {
                currentState = State.ROLLING
                stopRefreshing()
            }
        }

        return handledTouchEvent
    }

    private fun move() {
        val pullFraction: Float = if (offsetY == 0F) 0F else if (triggerOffSetTop > offsetY) offsetY / triggerOffSetTop else 1F
        offsetY = if (offsetY < 0) 0f else if (offsetY > maxOffSetTop) maxOffSetTop.toFloat() else offsetY

        onProgressListeners.forEach { it(pullFraction) }
        lastPullFraction = pullFraction

        topChildView.view.y = topChildView.positionAttr.top + offsetY
        contentChildView.view.y = contentChildView.positionAttr.top + offsetY
    }

    override fun stopRefreshing() {
        val rollBackOffset = if (offsetY > triggerOffSetTop) offsetY - triggerOffSetTop else offsetY
        val triggerOffset = if (rollBackOffset != offsetY) triggerOffSetTop else 0

        ValueAnimator.ofFloat(1F, 0F).apply {
            duration = ROLL_BACK_DURATION
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                topChildView.view.y = topChildView.positionAttr.top + triggerOffset + rollBackOffset * animatedValue as Float
                contentChildView.view.y = contentChildView.positionAttr.top + triggerOffset + rollBackOffset * animatedValue as Float
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    if (triggerOffset != 0 && currentState == State.ROLLING) {
                        currentState = State.TRIGGERING
                        offsetY = triggerOffset.toFloat()
                        onTriggerListeners.forEach { it() }
                    } else {
                        currentState = State.IDLE
                        offsetY = 0f
                    }
                }
            })
            start()
        }
    }

    //<editor-fold desc="Helpers">
    fun onProgressListener(onProgressListener: (Float) -> Unit) {
        onProgressListeners.add(onProgressListener)
    }

    fun onTriggerListener(onTriggerListener: () -> Unit) {
        onTriggerListeners.add(onTriggerListener)
    }

    fun removeOnTriggerListener(onTriggerListener: () -> Unit) {
        onTriggerListeners.remove(onTriggerListener)
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?) = null != p && p is LayoutParams

    override fun generateDefaultLayoutParams() = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    override fun generateLayoutParams(attrs: AttributeSet?) = LayoutParams(context, attrs)

    override fun generateLayoutParams(p: ViewGroup.LayoutParams?) = LayoutParams(p)

    class LayoutParams : ViewGroup.MarginLayoutParams {
        var type: ViewType = ViewType.UNKNOWN

        constructor(c: Context, attrs: AttributeSet?) : super(c, attrs) {
            c.theme.obtainStyledAttributes(attrs, R.styleable.EasyPullLayout_LayoutParams, 0, 0).let {
                type = ViewType.fromValue(it.getInt(R.styleable.EasyPullLayout_LayoutParams_layout_type, ViewType.UNKNOWN.value))
                it.recycle()
            }
        }

        constructor(width: Int, height: Int) : super(width, height)
        constructor(source: MarginLayoutParams) : super(source)
        constructor(source: ViewGroup.LayoutParams) : super(source)
    }

    enum class ViewType(val value: Int) {
        UNKNOWN(-1),
        TOP_VIEW(0),
        CONTENT(1);

        companion object {
            fun fromValue(value: Int) = values().first { it.value == value }
        }
    }

    enum class State {
        IDLE,
        ROLLING,
        TRIGGERING
    }

    data class ChildView(val view: View, val positionAttr: PositionAttr = PositionAttr())
    data class PositionAttr(val left: Int = 0, val top: Int = 0, val right: Int = 0, val bottom: Int = 0, val height: Int = 0)
    //</editor-fold>
}
