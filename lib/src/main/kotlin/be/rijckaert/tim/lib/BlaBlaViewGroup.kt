package be.rijckaert.tim.lib

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup

class BlaBlaViewGroup @JvmOverloads constructor(context: Context,
                                                attrs: AttributeSet? = null,
                                                defStyleAttr: Int = 0) : ViewGroup(context, attrs, defStyleAttr) {

    private val target get() = getChildAt(0)

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        target.let {
            val height = measuredHeight
            val width = measuredWidth
            val left = paddingLeft
            val top = paddingTop
            val right = paddingRight
            val bottom = paddingBottom

            it.layout(left, top + it.top, left + width - right, top + height - bottom + it.top)
        }
    }
}