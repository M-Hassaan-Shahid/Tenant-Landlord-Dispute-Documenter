package com.example.tenant_landlorddisputedocumenter.ui.rooms

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.tenant_landlorddisputedocumenter.navigation.RoomSetupFragmentArgs
import com.example.tenant_landlorddisputedocumenter.ui.refreshPropertyInBackground
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tenant_landlorddisputedocumenter.R
import com.example.tenant_landlorddisputedocumenter.ProofNestApplication
import com.example.tenant_landlorddisputedocumenter.databinding.FragmentRoomSetupBinding
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class RoomSetupFragment : Fragment() {

    private var _binding: FragmentRoomSetupBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: RoomSetupViewModel
    private lateinit var adapter: RoomAdapter
    private val args: RoomSetupFragmentArgs by lazy {
        RoomSetupFragmentArgs.fromBundle(requireArguments())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRoomSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val appContainer = (requireContext().applicationContext as ProofNestApplication).container
        viewModel = ViewModelProvider(this, RoomSetupViewModelFactory(appContainer))[RoomSetupViewModel::class.java]

        val propertyId = args.propertyId
        viewModel.loadForProperty(propertyId)

        refreshPropertyInBackground(propertyId)

        adapter = RoomAdapter(onDelete = { room -> viewModel.deleteRoom(room.id) })
        binding.recyclerViewRooms.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewRooms.adapter = adapter

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.fabAddRoom.setOnClickListener { showAddRoomDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.rooms.collect { rooms ->
                    adapter.submitList(rooms)
                    binding.textEmpty.visibility = if (rooms.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }

        // Lock the setup UI once the inspection has started
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                appContainer.propertyRepository.observeProperty(propertyId).collect { property ->
                    if (property == null) return@collect
                    val uid = appContainer.authRepository.currentUserId.value
                    val isLandlord = property.landlordId == uid
                    if (!isLandlord) {
                        binding.fabAddRoom.visibility = View.GONE
                        adapter.setDeleteEnabled(false)
                        binding.textEmpty.text = getString(R.string.rooms_landlord_only)
                        return@collect
                    }
                    val status = property.status
                    val locked = appContainer.inspectionRepository.isStructureLocked(propertyId, status)
                    binding.fabAddRoom.visibility = if (locked) View.GONE else View.VISIBLE
                    adapter.setDeleteEnabled(!locked)
                    binding.textEmpty.text = if (locked) {
                        getString(R.string.rooms_locked)
                    } else {
                        getString(R.string.rooms_empty)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    state.error?.let {
                        Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                        viewModel.clearError()
                    }
                }
            }
        }
    }

    private fun showAddRoomDialog() {
        val input = TextInputEditText(requireContext())
        input.hint = "e.g. Living Room, Kitchen..."
        AlertDialog.Builder(requireContext())
            .setTitle("Add Room")
            .setView(input)
            .setPositiveButton("Add") { _, _ -> viewModel.addRoom(input.text.toString()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
