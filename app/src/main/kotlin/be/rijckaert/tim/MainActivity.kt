package be.rijckaert.tim

import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.RecyclerView
import be.rijckaert.tim.lib.CustomPullToRefreshLayout
import java.lang.Thread.sleep
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    val dataSource = (1..19).map { "I hate API $it" }.toTypedArray()

    private val simpleAdapter = SimpleAdapter()
    private val SIMULATED_NETWORK_DELAY = 2 * 1000L //ms

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val recyclerView = this.findViewById(R.id.recyclerView) as RecyclerView

        (findViewById(R.id.swipe_refresh) as? CustomPullToRefreshLayout)?.let {
            it.onRefreshListener = {
                thread {
                    sleep(SIMULATED_NETWORK_DELAY * 2)
                    runOnUiThread {
                        simpleAdapter.dataSource = (20..25).map { "I love API $it" }.toTypedArray()
                        simpleAdapter.notifyDataSetChanged()

                        it.setRefreshing(false, false)
                    }
                }
            }
        }

        Handler().postDelayed({
            recyclerView.adapter = simpleAdapter
            simpleAdapter.dataSource = dataSource
            simpleAdapter.notifyDataSetChanged()
        }, SIMULATED_NETWORK_DELAY)
    }
}
