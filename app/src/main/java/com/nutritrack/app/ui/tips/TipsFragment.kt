package com.nutritrack.app.ui.tips

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.nutritrack.app.adapter.TipsCategoryAdapter
import com.nutritrack.app.databinding.FragmentTipsBinding
import com.nutritrack.app.utils.TipsData

class TipsFragment : Fragment() {
    private var _binding: FragmentTipsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTipsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = TipsCategoryAdapter()
        binding.rvCategories.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCategories.adapter = adapter
        adapter.submitList(TipsData.getAllCategories())
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}