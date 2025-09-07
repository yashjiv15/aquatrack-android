package com.example.aquatrack

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.aquatrack.api.CreateDispatchResponse

class DispatchAdapter(private val dispatches: MutableList<CreateDispatchResponse>) : RecyclerView.Adapter<DispatchAdapter.DispatchViewHolder>() {
    class DispatchViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val productText: TextView = itemView.findViewById(R.id.textDispatchProduct)
        val quantityText: TextView = itemView.findViewById(R.id.textDispatchQuantity)
        val dateText: TextView = itemView.findViewById(R.id.textDispatchDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DispatchViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_dispatch, parent, false)
        return DispatchViewHolder(view)
    }

    override fun onBindViewHolder(holder: DispatchViewHolder, position: Int) {
        val dispatch = dispatches[position]
        holder.productText.text = dispatch.product?.product_name ?: ""
        holder.quantityText.text = dispatch.dispatch_quantity?.toString() ?: ""
        // Only show date part (YYYY-MM-DD)
        holder.dateText.text = dispatch.created_at?.substring(0, 10) ?: ""
    }

    override fun getItemCount(): Int = dispatches.size

    fun setItems(newItems: List<CreateDispatchResponse>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = dispatches.size
            override fun getNewListSize(): Int = newItems.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val old = dispatches[oldItemPosition].dispatch_id
                val new = newItems[newItemPosition].dispatch_id
                return old != null && new != null && old == new
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return dispatches[oldItemPosition] == newItems[newItemPosition]
            }
        })
        dispatches.clear()
        dispatches.addAll(newItems)
        diff.dispatchUpdatesTo(this)
    }
}
