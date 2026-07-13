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

        loadData()
    }

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch {
            val profile = db.profileDao().getActiveProfileSync()
            profileId = profile?.id ?: 1L
            val photos = db.progressPhotoDao().getAllForProfile(profileId)
            adapter.submit(photos)
            binding.tvEmpty.visibility = if (photos.isEmpty()) View.VISIBLE else View.GONE
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
            .setTitle("📸 Новое фото")
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
