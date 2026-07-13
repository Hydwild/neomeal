package com.nutritrack.app.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.nutritrack.app.R
import com.nutritrack.app.data.db.entity.ProgressPhotoEntity

class ProgressPhotoAdapter(
    private var items: List<ProgressPhotoEntity>,
    private val onDelete: (ProgressPhotoEntity) -> Unit,
    private val onClick: (ProgressPhotoEntity) -> Unit
) : RecyclerView.Adapter<ProgressPhotoAdapter.VH>() {

    fun submit(list: List<ProgressPhotoEntity>) {
        items = list
        notifyDataSetChanged()
    }

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val photo: ImageView = v.findViewById(R.id.ivPhoto)
        val date: TextView = v.findViewById(R.id.tvDate)
        val note: TextView = v.findViewById(R.id.tvNote)
        val del: TextView = v.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_progress_photo, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        Glide.with(holder.photo).load(Uri.parse(item.photoUri))
            .centerCrop().into(holder.photo)
        holder.date.text = item.date
        holder.note.text = item.note
        holder.note.visibility = if (item.note.isBlank()) View.GONE else View.VISIBLE
        holder.del.setOnClickListener { onDelete(item) }
        holder.photo.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}
