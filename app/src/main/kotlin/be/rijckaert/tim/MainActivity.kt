package be.rijckaert.tim

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    val dataSource = (0..100).map { "Random String $it" }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val recyclerView = this.findViewById(R.id.recyclerView) as RecyclerView
        with(recyclerView) {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = SimpleAdapter(dataSource)
        }
    }
}
