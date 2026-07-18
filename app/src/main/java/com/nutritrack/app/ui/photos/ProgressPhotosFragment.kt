package com.nutritrack.app.ui.photos

import com.nutritrack.app.R

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.nutritrack.app.NutriApp
import com.nutritrack.app.adapter.ProgressPhotoAdapter
import com.nutritrack.app.data.db.entity.ProgressPhotoEntity
import com.nutritrack.app.databinding.FragmentProgressPhotosBinding
import com.nutritrack.app.utils.DateUtils
import kotlinx.coroutines.launch

/**
 * Фото прогресса: галерея фото тела по датам. Добавление из галереи, заметка, удаление.
 */
class ProgressPhotosFragment : Fragment() {

    private var _binding: FragmentProgressPhotosBinding? = null
    private val binding get() = _binding!!

    private val db by lazy { (requireActivity().application as NutriApp).database }
    private var profileId: Long = 1L

    private lateinit var adapter: ProgressPhotoAdapter

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) askNoteAndSave(uri)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProgressPhotosBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ProgressPhotoAdapter(emptyList(), onDelete = { confirmDelete(it) }, onClick = { showFull(it) })
        binding.rvPhotos.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvPhotos.adapter = adapter

        binding.btnAddPhoto.setOnClickListener { pickImage.launch("image/*") }
        binding.btnCompare.setOnClickListener { showCompare() }

        loadData()
    }

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch {
            val profile = db.profileDao().getActiveProfileSync()
            profileId = profile?.id ?: 1L
            val photos = db.progressPhotoDao().getAllForProfile(profileId)
            adapter.submit(photos)
            binding.tvEmpty.visibility = if (photos.isEmpty()) View.VISIBLE else View.GONE
            binding.btnCompare.visibility = if (photos.size >= 2) View.VISIBLE else View.GONE
        }
    }

 /** Сравнение «до/после»: самое старое фото рядом с самым новым. */
    private fun showCompare() {
        viewLifecycleOwner.lifecycleScope.launch {
            val photos = db.progressPhotoDao().getAllForProfile(profileId)
                .sortedBy { it.date }
            if (photos.size < 2) return@launch
            val first = photos.first()
            val last = photos.last()

            val ctx = requireContext()
            fun sideColumn(p: ProgressPhotoEntity, labelRes: Int): View {
                val col = android.widget.LinearLayout(ctx).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                val iv = android.widget.ImageView(ctx).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(280))
                    scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                }
                com.bumptech.glide.Glide.with(this@ProgressPhotosFragment)
                    .load(Uri.parse(p.photoUri)).into(iv)
                col.addView(iv)
                col.addView(android.widget.TextView(ctx).apply {
                    text = "${getString(labelRes)}\n${p.date}"
                    gravity = android.view.Gravity.CENTER
                    setTextColor(android.graphics.Color.WHITE)
                    textSize = 12f
                    setPadding(0, dp(8), 0, 0)
                })
                return col
            }

            val row = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                setPadding(dp(12), dp(12), dp(12), dp(4))
                addView(sideColumn(first, R.string.photos_before))
                addView(android.view.View(ctx).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(dp(8), 1)
                })
                addView(sideColumn(last, R.string.photos_after))
            }

            val d = androidx.appcompat.app.AlertDialog.Builder(ctx)
                .setTitle(getString(R.string.photos_compare_title))
                .setView(row)
                .setPositiveButton(getString(R.string.c_close), null)
                .create()
            d.setOnShowListener { com.nutritrack.app.utils.DialogHelper.tint(d, ctx) }
            d.show()
        }
    }

    private fun askNoteAndSave(source: Uri) {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.ph_note_hint)
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.parseColor("#9E9EB0"))
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.ph_new_photo))
            .setView(input)
            .setPositiveButton(getString(R.string.c_save)) { _, _ ->
                savePhoto(source, input.text.toString().trim())
            }
            .setNegativeButton(getString(R.string.c_cancel), null)
            .show()
    }

    private fun savePhoto(source: Uri, note: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val savedUri = copyToInternal(source)
            if (savedUri == null) {
                android.widget.Toast.makeText(requireContext(),
                    getString(R.string.ph_save_fail), android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            db.progressPhotoDao().insert(
                ProgressPhotoEntity(
                    photoUri = savedUri, date = DateUtils.today(),
                    note = note, profileId = profileId
                )
            )
            loadData()
            android.widget.Toast.makeText(requireContext(),
                "✅ Фото сохранено", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToInternal(source: Uri): String? {
        return try {
            val dir = java.io.File(requireContext().filesDir, "progress_photos")
            if (!dir.exists()) dir.mkdirs()
            val dest = java.io.File(dir, "photo_${System.currentTimeMillis()}.jpg")
            requireContext().contentResolver.openInputStream(source)?.use { input ->
                java.io.FileOutputStream(dest).use { output -> input.copyTo(output) }
            }
            Uri.fromFile(dest).toString()
        } catch (e: Exception) {
            null
        }
    }

    private fun confirmDelete(photo: ProgressPhotoEntity) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.ph_delete_q))
            .setMessage(getString(R.string.ph_irreversible))
            .setPositiveButton(getString(R.string.c_delete)) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    db.progressPhotoDao().deleteById(photo.id)
                    // удаляем файл
                    try { java.io.File(Uri.parse(photo.photoUri).path ?: "").delete() } catch (e: Exception) { com.nutritrack.app.utils.AppLog.e("delete photo file failed", e) }
                    loadData()
                }
            }
            .setNegativeButton(getString(R.string.c_cancel), null)
            .show()
    }

    private fun showFull(photo: ProgressPhotoEntity) {
        val iv = android.widget.ImageView(requireContext()).apply {
            com.bumptech.glide.Glide.with(this).load(Uri.parse(photo.photoUri)).into(this)
            adjustViewBounds = true
        }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("${photo.date}${if (photo.note.isNotBlank()) " · ${photo.note}" else ""}")
            .setView(iv)
            .setPositiveButton(getString(R.string.c_close), null)
            .show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
