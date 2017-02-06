package be.rijckaert.tim

import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

class SimpleAdapter(private val dataSource: Array<String>) : RecyclerView.Adapter<SimpleAdapter.SimpleViewHolder>() {
    override fun getItemCount(): Int = dataSource.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SimpleViewHolder {
        val view = TextView(parent.context)
        val viewHolder = SimpleViewHolder(view)
        return viewHolder
    }

    override fun onBindViewHolder(holder: SimpleViewHolder, position: Int) {
        holder.textView.text = dataSource[position]
    }

    class SimpleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var textView: TextView = itemView as TextView
    }
}