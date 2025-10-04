package com.example.aquatrack

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aquatrack.api.RecentSaleItem

class SalesAdapter(private val items: List<RecentSaleItem>) : RecyclerView.Adapter<SalesAdapter.SaleViewHolder>() {
    class SaleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val saleInfo: TextView = view.findViewById(R.id.textSaleInfo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SaleViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sale_row, parent, false)
        return SaleViewHolder(view)
    }

    override fun onBindViewHolder(holder: SaleViewHolder, position: Int) {
        val item = items[position]
        val productName = item.product?.product_name ?: "Product ${item.product_id}"
        val date = item.created_at ?: ""
        holder.saleInfo.text = "$date | $productName | Qty ${item.sale_quantity} | Amount ${item.sales_amount}"
    }

    override fun getItemCount(): Int = items.size
}

