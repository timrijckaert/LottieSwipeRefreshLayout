package be.rijckaert.tim.lib

import android.content.Context
import android.support.v4.view.ViewCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.MotionEvent.*
import android.view.animation.AccelerateDecelerateInterpolator

class RecyclerView @JvmOverloads constructor(context: Context,
                                             attrs: AttributeSet? = null,
                                             defStyle: Int = 0) : RecyclerView(context, attrs, defStyle) {

    //<editor-fold desc="Fields">
    var isRefreshing: Boolean
    private val canScrollUp: Boolean get() = ViewCompat.canScrollVertically(this, -1)

    private val linearLayoutManager: LinearLayoutManager get() = layoutManager as LinearLayoutManager

    private val PULL_DOWN_THRESHOLD: Float = dpToPx(200)
    private var previousY: Float
    private var deltaY: Float
    private var changeInY: Float
    private var stopRefresh: Boolean
    private var progressIncreasing: Boolean
    private var progressCount: Int
    //</editor-fold>

    init {
        previousY = 0F
        deltaY = 0F
        changeInY = 0F
        isRefreshing = false
        stopRefresh = false
        progressIncreasing = false
        progressCount = 0

        this.setOnTouchListener { view, motionEvent ->
            this.onTouch(motionEvent)
        }
    }

    fun onTouch(event: MotionEvent): Boolean {
        when (event.action) {
            ACTION_DOWN -> handleDownEvent(event)
            ACTION_MOVE -> handleMoveEvent(event)
            ACTION_UP -> handleUpEvent(event)
        }

        return false
    }

    private fun dpToPx(dp: Int): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics)

    private fun canRefresh(): Boolean =
            linearLayoutManager.findFirstCompletelyVisibleItemPosition() <= 0
                    && deltaY >= PULL_DOWN_THRESHOLD && !isRefreshing && progressCount >= 100
                    && !canScrollUp && this.translationY != 0f

    private fun canDecreaseRefreshArea() = changeInY <= 0 && !isRefreshing

    private fun canIncreaseRefreshArea() =
            !isRefreshing
                    && !canScrollUp
                    && changeInY > 0
                    && linearLayoutManager.findFirstVisibleItemPosition() <= 0

    private fun handleDownEvent(event: MotionEvent) {
        previousY = event.rawY
        deltaY = 0f
        Log.d("Tim", "Down Event")
    }

    private fun handleUpEvent(event: MotionEvent) {
        deltaY = 0f
        previousY = deltaY

        if (!isRefreshing) {
            Log.d("Tim", "Clear the animation")
            this.clearAnimation()
        }
    }

    private fun handleMoveEvent(event: MotionEvent) {
        changeInY = event.rawY - previousY

        if (isRefreshing && canScrollUp) {
            stopRefresh = true
            isRefreshing = false

            progressIncreasing = false
            //Cancel already running animation
        }

        if (canIncreaseRefreshArea()) {
            deltaY += changeInY
            progressCount = (deltaY * 100 / PULL_DOWN_THRESHOLD).toInt()
            progressCount = if (progressCount > 100) 100 else progressCount
            if (progressCount >= 0) {
                this.translationY = dpToPx(progressCount / 2 + 10)
            }
            //increased
        }

        if (canDecreaseRefreshArea()) {
            //Show progress decrease
            deltaY += changeInY
            progressCount = (deltaY * 100 / PULL_DOWN_THRESHOLD).toInt()
            progressCount = if (progressCount < 0) 0 else progressCount
            this.translationY = dpToPx(progressCount / 2 + 10)
            //decreased
        }

        previousY = event.rawY

        if (canRefresh()) {
            deltaY = 0f
            previousY = deltaY
            isRefreshing = true
            scrollToPosition(0)

        }
    }

    fun reset() {
        animate()
                .translationY(0f)
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()

        progressIncreasing = false
    }

    interface OnRefreshListener {
        fun onRefresh()
    }
}