package be.rijckaert.tim.lib

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable

class LottiePullToRefreshLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0)
    : SimplePullToRefreshLayout(context, attrs, defStyle) {

    private var animationFile: Int = -1
    private val lottieAnimationView by lazy {
        LottieAnimationView(context).apply {
            if (animationFile == -1) {
                throw IllegalStateException("Could not resolve an animation for your pull to refresh layout")
            }

            setAnimation(animationFile)
            repeatCount = LottieDrawable.INFINITE
            layoutParams = LayoutParams(ViewGroup.LayoutParams(MATCH_PARENT, triggerOffSetTop)).apply { type = ViewType.TOP_VIEW }
        }
    }

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.LottiePullToRefreshLayout, defStyle, 0).let { style ->
            animationFile = style.getResourceId(R.styleable.LottiePullToRefreshLayout_pull_to_refresh_lottieFile, -1)
            addView(lottieAnimationView)
            style.recycle()
        }

        onProgressListener {
            lottieAnimationView.progress = it
        }

        onTriggerListener {
            lottieAnimationView.resumeAnimation()
        }
    }

    override fun stopRefreshing() {
        super.stopRefreshing()
        lottieAnimationView.pauseAnimation()
    }
}
