package be.rijckaert.tim.lib

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.widget.FrameLayout

class BlaBlaViewGroup @JvmOverloads constructor(context: Context,
                                                attrs: AttributeSet? = null,
                                                defStyleAttr: Int = 0) : FrameLayout(context, attrs, defStyleAttr) {

    private val target get() = getChildAt(0)

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        target?.let {
            it.layout(0, 0, it.measuredWidth, it.measuredHeight)
        }
    }
}

fun log(message: String, tag: String = "be.rijckaert.tim.lib.") = Log.e("be.rijckaert.tim.lib.$tag", message)