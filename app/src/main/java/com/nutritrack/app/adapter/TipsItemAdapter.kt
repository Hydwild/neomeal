package com.nutritrack.app.adapter

import com.nutritrack.app.R

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.nutritrack.app.databinding.ItemTipBinding
import com.nutritrack.app.utils.TipItem

class TipsItemAdapter : ListAdapter<TipItem, TipsItemAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemTipBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: TipItem) {
            b.tvIcon.text = item.icon
            b.tvTitle.text = com.nutritrack.app.utils.DataLabel.tr(b.root.context, item.title)
            b.tvBody.text = com.nutritrack.app.utils.DataLabel.tr(b.root.context, item.body)

            // Toggle expand
            b.tvBody.visibility = android.view.View.GONE
            b.root.setOnClickListener {
                b.tvBody.visibility = if (b.tvBody.visibility == android.view.View.GONE)
                    android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemTipBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<TipItem>() {
            override fun areItemsTheSame(a: TipItem, b: TipItem) = a.title == b.title
            override fun areContentsTheSame(a: TipItem, b: TipItem) = a == b
        }
    }
}