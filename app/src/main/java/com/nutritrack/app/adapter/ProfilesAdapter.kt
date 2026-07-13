package com.nutritrack.app.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.nutritrack.app.R
import com.nutritrack.app.data.db.entity.ProfileEntity
import com.nutritrack.app.databinding.ItemProfileBinding

class ProfilesAdapter(
    private val onSwitch: (ProfileEntity) -> Unit,
    private val onEdit: (ProfileEntity) -> Unit,
    private val onDelete: (ProfileEntity) -> Unit
) : ListAdapter<ProfileEntity, ProfilesAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemProfileBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: ProfileEntity) {
            b.tvName.text = item.name
            val ctx = b.root.context
            val goalEmoji = when (item.goal) {
                "lose" -> "📉"
                "gain" -> "📈"
                "healthy" -> "🥗"
                else -> "⚖️"
            }
            val goalText = when (item.goal) {
                "lose" -> ctx.getString(R.string.goal_lose_short)
                "gain" -> ctx.getString(R.string.goal_gain_short)
                "healthy" -> ctx.getString(R.string.goal_healthy_short)
                else -> ctx.getString(R.string.goal_maintain_short)
            }
            b.tvGoal.text = "$goalEmoji  $goalText"
            b.root.alpha = if (item.isActive) 1f else 0.6f
            b.ivActive.visibility = if (item.isActive) android.view.View.VISIBLE else android.view.View.GONE
            item.photoUri?.let { Glide.with(b.root).load(Uri.parse(it)).circleCrop().into(b.ivAvatar) }
            b.root.setOnClickListener { onSwitch(item) }
            b.btnEdit.setOnClickListener { onEdit(item) }
            b.btnDelete.setOnClickListener { onDelete(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemProfileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ProfileEntity>() {
            override fun areItemsTheSame(a: ProfileEntity, b: ProfileEntity) = a.id == b.id
            override fun areContentsTheSame(a: ProfileEntity, b: ProfileEntity) = a == b
        }
    }
}