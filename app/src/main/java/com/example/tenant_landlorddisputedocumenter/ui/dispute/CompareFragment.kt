package com.example.tenant_landlorddisputedocumenter.ui.dispute

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tenant_landlorddisputedocumenter.R
import com.example.tenant_landlorddisputedocumenter.ProofNestApplication
import com.example.tenant_landlorddisputedocumenter.databinding.FragmentCompareBinding
import com.example.tenant_landlorddisputedocumenter.navigation.CompareFragmentArgs
import com.example.tenant_landlorddisputedocumenter.navigation.CompareFragmentDirections
import com.example.tenant_landlorddisputedocumenter.domain.model.ChecklistItem
import com.example.tenant_landlorddisputedocumenter.domain.model.PropertyFlowPolicy
import com.example.tenant_landlorddisputedocumenter.domain.model.RatingDelta
import com.example.tenant_landlorddisputedocumenter.ui.applyProofNestItemAnimations
import com.example.tenant_landlorddisputedocumenter.ui.guardPropertyAccess
import com.example.tenant_landlorddisputedocumenter.ui.navigateAnimated
import com.example.tenant_landlorddisputedocumenter.ui.showPhotoViewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CompareFragment : Fragment() {

    private var _binding: FragmentCompareBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: DisputeViewModel
    private lateinit var adapter: CompareAdapter
    private val args: CompareFragmentArgs by lazy {
        CompareFragmentArgs.fromBundle(requireArguments())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCompareBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val appContainer = (requireContext().applicationContext as ProofNestApplication).container
        viewModel = ViewModelProvider(this, DisputeViewModelFactory(appContainer))[DisputeViewModel::class.java]

        val propertyId = args.propertyId
        viewModel.loadForProperty(propertyId)

        adapter = CompareAdapter(
            scope = viewLifecycleOwner.lifecycleScope,
            loadPhotos = { ids -> appContainer.inspectionRepository.getPhotos(ids) },
            onPhotoClick = { uri -> showPhotoViewer(uri) },
            onRaiseDispute = { item -> navigateToDispute(propertyId, item) },
        )
        guardPropertyAccess(propertyId, PropertyFlowPolicy::compareAccess) { property ->
            val uid = appContainer.authRepository.currentUserId.value
            adapter.showRaiseDispute = PropertyFlowPolicy.canShowRaiseDispute(property, uid)
        }
        binding.recyclerViewComparison.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewComparison.applyProofNestItemAnimations()
        binding.recyclerViewComparison.adapter = adapter

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.swipeRefresh.setColorSchemeResources(R.color.proofnest_primary)
        binding.swipeRefresh.setOnRefreshListener { syncCompareData(propertyId, force = true, showSpinner = true) }

        binding.buttonGenerateReport.setOnClickListener {
            findNavController().navigate(
                CompareFragmentDirections.actionCompareToReport(propertyId),
            )
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                appContainer.inspectionRepository.observeAllItems(propertyId).collect { items ->
                    val compared = items.filter {
                        it.moveInRating != null && it.moveOutRating != null
                    }
                    adapter.submitList(compared)

                    val degradedCount = compared.count { it.ratingDelta() == RatingDelta.DEGRADED }
                    binding.textDamageSummary.text = when {
                        compared.isEmpty() -> getString(R.string.compare_summary_empty)
                        degradedCount == 0 -> getString(R.string.compare_summary_ok)
                        else -> getString(R.string.compare_summary_degraded, degradedCount)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    state.error?.let {
                        Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                        viewModel.clearMessages()
                    }
                }
            }
        }

        syncCompareData(propertyId, force = false, showSpinner = false)
    }

    private fun syncCompareData(propertyId: String, force: Boolean, showSpinner: Boolean) {
        val appContainer = (requireContext().applicationContext as ProofNestApplication).container
        if (showSpinner) {
            _binding?.swipeRefresh?.isRefreshing = true
        }
        appContainer.repositoryScope.launch {
            val sync = appContainer.syncCoordinator.syncForCompare(propertyId, force = force)
            if (!showSpinner) return@launch
            withContext(Dispatchers.Main) {
                _binding?.swipeRefresh?.isRefreshing = false
                if (!sync.succeeded && isAdded) {
                    Toast.makeText(
                        requireContext(),
                        sync.errors.firstOrNull() ?: getString(R.string.sync_had_issues),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    private fun navigateToDispute(propertyId: String, item: ChecklistItem) {
        findNavController().navigateAnimated(
            CompareFragmentDirections.actionCompareToDispute(propertyId, item.id, item.name),
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
