package com.example.tenant_landlorddisputedocumenter.ui.inspection

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.tenant_landlorddisputedocumenter.R
import com.example.tenant_landlorddisputedocumenter.ProofNestApplication
import com.example.tenant_landlorddisputedocumenter.navigation.InspectionFragmentArgs
import com.example.tenant_landlorddisputedocumenter.navigation.InspectionFragmentDirections
import com.example.tenant_landlorddisputedocumenter.databinding.FragmentInspectionBinding
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionPhase
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch

class InspectionFragment : Fragment() {

    private var _binding: FragmentInspectionBinding? = null
    private val binding get() = _binding!!

    private val args: InspectionFragmentArgs by lazy {
        InspectionFragmentArgs.fromBundle(requireArguments())
    }

    private val viewModel: InspectionViewModel by activityViewModels {
        val container = (requireActivity().application as ProofNestApplication).container
        InspectionViewModelFactory(container)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInspectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val propertyId = args.propertyId
        val phase = if (args.phase == "MOVE_OUT") InspectionPhase.MOVE_OUT else InspectionPhase.MOVE_IN
        viewModel.loadForProperty(propertyId, phase)

        val appContainer = (requireActivity().application as ProofNestApplication).container
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { appContainer.syncCoordinator.refreshPropertyData(propertyId) }
        }

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.title = if (phase == InspectionPhase.MOVE_OUT) {
            getString(R.string.inspection_move_out_title)
        } else {
            getString(R.string.inspection_move_in_title)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.allItems.collect { items ->
                    val rated = items.count { item ->
                        if (phase == InspectionPhase.MOVE_IN) {
                            item.moveInRating != null
                        } else {
                            item.moveOutRating != null
                        }
                    }
                    val total = items.size.coerceAtLeast(1)
                    val pct = (rated * 100) / total
                    binding.inspectionProgress.progressInspection.max = 100
                    binding.inspectionProgress.progressInspection.setProgressCompat(pct, true)
                    binding.inspectionProgress.textProgressPercent.text = "$pct%"
                }
            }
        }

        binding.buttonFinishInspection.setOnClickListener {
            viewModel.finishInspection()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.rooms.collect { rooms ->
                    val hasRooms = rooms.isNotEmpty()
                    binding.viewPagerRooms.visibility = if (hasRooms) View.VISIBLE else View.GONE
                    binding.tabLayoutRooms.visibility = if (hasRooms) View.VISIBLE else View.GONE
                    binding.buttonFinishInspection.isEnabled = hasRooms
                    if (!hasRooms) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.inspection_no_rooms),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                    if (!hasRooms) return@collect
                    val pagerAdapter = RoomPagerAdapter(this@InspectionFragment, rooms, viewModel, phase)
                    binding.viewPagerRooms.adapter = pagerAdapter
                    TabLayoutMediator(binding.tabLayoutRooms, binding.viewPagerRooms) { tab, position ->
                        tab.text = rooms[position].name
                    }.attach()
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
                    if (state.isFinished) {
                        viewModel.clearFinished()
                        findNavController().navigate(
                            InspectionFragmentDirections.actionInspectionToReviewSign(propertyId, phase.name),
                        )
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
