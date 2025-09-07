package com.example.aquatrack

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.aquatrack.api.RecentExpenseItem

class ExpenseAdapter(private val expenses: MutableList<RecentExpenseItem>) : RecyclerView.Adapter<ExpenseAdapter.ExpenseViewHolder>() {
    class ExpenseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ctx = itemView.context
        private val idDesc = ctx.resources.getIdentifier("textExpenseDesc", "id", ctx.packageName)
        private val idAmount = ctx.resources.getIdentifier("textExpenseAmount", "id", ctx.packageName)
        private val idDate = ctx.resources.getIdentifier("textExpenseDate", "id", ctx.packageName)
        val descText: TextView = itemView.findViewById(idDesc)
        val amountText: TextView = itemView.findViewById(idAmount)
        val dateText: TextView = itemView.findViewById(idDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val ctx = parent.context
        val layoutId = ctx.resources.getIdentifier("item_expense", "layout", ctx.packageName)
        val view = LayoutInflater.from(ctx).inflate(layoutId, parent, false)
        return ExpenseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        val expense = expenses[position]
        holder.descText.text = expense.description
        holder.amountText.text = expense.amount.toString()
        // Only show date part (YYYY-MM-DD)
        holder.dateText.text = expense.created_at?.substring(0, 10) ?: ""
    }

    override fun getItemCount(): Int = expenses.size

    fun setItems(newItems: List<RecentExpenseItem>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = expenses.size
            override fun getNewListSize(): Int = newItems.size
            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return expenses[oldItemPosition].expense_id == newItems[newItemPosition].expense_id
            }
            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return expenses[oldItemPosition] == newItems[newItemPosition]
            }
        })
        expenses.clear()
        expenses.addAll(newItems)
        diff.dispatchUpdatesTo(this)
    }
}
