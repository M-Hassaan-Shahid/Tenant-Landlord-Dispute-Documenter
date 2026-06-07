package com.example.tenant_landlorddisputedocumenter.ui.inspection

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tenant_landlorddisputedocumenter.ProofNestApplication
import com.example.tenant_landlorddisputedocumenter.R
import com.example.tenant_landlorddisputedocumenter.databinding.FragmentRoomChecklistBinding
import android.widget.ImageView
import android.widget.LinearLayout
import com.bumptech.glide.Glide
import com.example.tenant_landlorddisputedocumenter.domain.model.ChecklistItem
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionPhase
import com.example.tenant_landlorddisputedocumenter.ui.applyProofNestItemAnimations
import com.example.tenant_landlorddisputedocumenter.ui.showPhotoViewer
import kotlinx.coroutines.launch

class RoomChecklistFragment : Fragment() {

    private var _binding: FragmentRoomChecklistBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InspectionViewModel by activityViewModels {
        InspectionViewModelFactory(
            (requireActivity().application as ProofNestApplication).container,
        )
    }

    private lateinit var propertyId: String
    private lateinit var phase: InspectionPhase
    private lateinit var adapter: ChecklistAdapter

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { /* Room flow updates photo counts automatically */ }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentRoomChecklistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val roomId = arguments?.getString("roomId") ?: return
        propertyId = arguments?.getString("propertyId") ?: return
        val phaseArg = arguments?.getString("phase")
        phase = if (phaseArg == "MOVE_OUT") InspectionPhase.MOVE_OUT else InspectionPhase.MOVE_IN

        val appContainer = (requireContext().applicationContext as ProofNestApplication).container
        adapter = ChecklistAdapter(
            phase = phase,
            scope = viewLifecycleOwner.lifecycleScope,
            loadPhotoThumbs = { ids, strip ->
                strip.removeAllViews()
                val photos = appContainer.inspectionRepository.getPhotos(ids)
                val inflater = LayoutInflater.from(strip.context)
                photos.forEach { photo ->
                    val thumb = inflater.inflate(R.layout.item_photo_thumb, strip, false)
                    val image = thumb.findViewById<ImageView>(R.id.imageThumb)
                    val source = photo.localUri ?: photo.remoteUrl
                    val viewUri = source?.let { android.net.Uri.parse(it) }
                    Glide.with(strip)
                        .load(source)
                        .centerCrop()
                        .placeholder(R.drawable.ic_image)
                        .into(image)
                    thumb.isClickable = true
                    thumb.setOnClickListener { showPhotoViewer(viewUri) }
                    strip.addView(thumb)
                }
            },
            onRatingChanged = { itemId, rating -> viewModel.setRating(itemId, rating) },
            onNoteChanged = { itemId, note -> viewModel.setNote(itemId, note) },
            onCapturePhoto = { item -> launchCamera(item) },
        )
        binding.recyclerViewChecklist.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewChecklist.applyProofNestItemAnimations()
        binding.recyclerViewChecklist.adapter = adapter

        binding.buttonAddItem.setOnClickListener {
            val name = binding.inputItemName.text.toString()
            if (name.isNotBlank()) {
                viewModel.addItem(roomId, name)
                binding.inputItemName.setText("")
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.getItemsForRoom(roomId).collect { items ->
                    adapter.submitList(items)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                appContainer.propertyRepository.observeProperty(propertyId).collect { property ->
                    val status = property?.status ?: return@collect
                    val locked = appContainer.inspectionRepository.isStructureLocked(propertyId, status)
                    binding.buttonAddItem.visibility = if (locked) View.GONE else View.VISIBLE
                    binding.inputItemName.visibility = if (locked) View.GONE else View.VISIBLE
                }
            }
        }
    }

    private fun launchCamera(item: ChecklistItem) {
        val intent = Intent(requireContext(), CameraCaptureActivity::class.java).apply {
            putExtra(CameraCaptureActivity.EXTRA_PROPERTY_ID, propertyId)
            putExtra(CameraCaptureActivity.EXTRA_ITEM_ID, item.id)
            putExtra(CameraCaptureActivity.EXTRA_PHASE, phase.name)
        }
        captureLauncher.launch(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
