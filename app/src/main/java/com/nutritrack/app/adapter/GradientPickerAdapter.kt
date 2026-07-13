package com.nutritrack.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nutritrack.app.R
import com.nutritrack.app.utils.BackgroundHelper

/** Сетка плиток-градиентов для выбора фона. */
class GradientPickerAdapter(
    private val presets: List<BackgroundHelper.GradientPreset>,
    private val selectedKey: String,
    private val onPick: (BackgroundHelper.GradientPreset) -> Unit
) : RecyclerView.Adapter<GradientPickerAdapter.VH>() {

    inner class VH(val view: View) : RecyclerView.ViewHolder(view) {
        val preview: View = view.findViewById(R.id.gradientPreview)
        val name: TextView = view.findViewById(R.id.tvGradientName)
        val mark: TextView = view.findViewById(R.id.tvSelectedMark)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gradient_tile, parent, false)
        return VH(v)
    }

    override fun getItemCount() = presets.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val preset = presets[position]
        holder.preview.background = BackgroundHelper.previewDrawable(preset)
        holder.name.text = holder.view.context.getString(preset.labelRes)
        holder.mark.visibility = if (preset.key == selectedKey) View.VISIBLE else View.GONE
        holder.view.setOnClickListener { onPick(preset) }
    }
}
