package com.example.tenant_landlorddisputedocumenter.ui.dispute

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.tenant_landlorddisputedocumenter.navigation.DisputesListFragmentArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.tenant_landlorddisputedocumenter.ProofNestApplication
import com.example.tenant_landlorddisputedocumenter.R
import com.example.tenant_landlorddisputedocumenter.databinding.FragmentDisputesListBinding
import com.example.tenant_landlorddisputedocumenter.domain.model.Dispute
import com.example.tenant_landlorddisputedocumenter.domain.model.DisputeStatus
import com.example.tenant_landlorddisputedocumenter.domain.model.PropertyStatus
import com.example.tenant_landlorddisputedocumenter.ui.refreshPropertyInBackground
import com.example.tenant_landlorddisputedocumenter.ui.showPhotoViewer
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DisputesListFragment : Fragment() {

    private var _binding: FragmentDisputesListBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: DisputeViewModel
    private lateinit var adapter: DisputeListAdapter
    private val args: DisputesListFragmentArgs by lazy {
        DisputesListFragmentArgs.fromBundle(requireArguments())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDisputesListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val appContainer = (requireContext().applicationContext as ProofNestApplication).container
        viewModel = ViewModelProvider(this, DisputeViewModelFactory(appContainer))[DisputeViewModel::class.java]

        val propertyId = args.propertyId
        viewModel.loadForProperty(propertyId)

        refreshPropertyInBackground(propertyId)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.fabRaiseDispute.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val items = appContainer.inspectionRepository.observeAllItems(propertyId).first()
                showDisputeItemPicker(propertyId, items) {
                    Toast.makeText(requireContext(), R.string.compare_summary_empty, Toast.LENGTH_SHORT).show()
                }
            }
        }

        adapter = DisputeListAdapter(
            scope = viewLifecycleOwner.lifecycleScope,
            loadPhotos = { ids -> appContainer.inspectionRepository.getPhotos(ids) },
            onPhotoClick = { uri -> showPhotoViewer(uri) },
            onResolve = { dispute -> showResolveDialog(dispute) },
            canResolve = { dispute ->
                val uid = appContainer.authRepository.currentUserId.value
                uid != null && dispute.raisedByUid != uid
            },
        )
        binding.recyclerViewDisputes.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewDisputes.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                appContainer.inspectionRepository.observeAllItems(propertyId).collect { items ->
                    adapter.updateItemNames(items.associate { it.id to it.name })
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    viewModel.disputes,
                    appContainer.propertyRepository.observeProperty(propertyId),
                    appContainer.authRepository.currentUserId,
                ) { disputes, property, uid ->
                    DisputesUiState(disputes, property, uid)
                }.collect { state ->
                    adapter.submitList(state.disputes)
                    binding.layoutEmpty.visibility =
                        if (state.disputes.isEmpty()) View.VISIBLE else View.GONE
                    val property = state.property
                    val canRaise = when (property?.status) {
                        PropertyStatus.ACTIVE ->
                            property.tenantId == state.uid &&
                                property.moveInInspectionSubmittedAtMillis != null
                        PropertyStatus.MOVE_OUT ->
                            property.tenantId == state.uid &&
                                property.moveOutInspectionSubmittedAtMillis != null
                        else -> false
                    }
                    binding.fabRaiseDispute.visibility = if (canRaise) View.VISIBLE else View.GONE
                    binding.textEmptySubtitle.text = when {
                        canRaise -> getString(R.string.disputes_raise_hint)
                        property?.status == PropertyStatus.CLOSED ->
                            getString(R.string.disputes_closed_empty_subtitle)
                        else -> getString(R.string.no_disputes_subtitle)
                    }
                    binding.toolbar.subtitle = if (property?.status == PropertyStatus.CLOSED) {
                        getString(R.string.disputes_closed_hint)
                    } else {
                        null
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.fabRaiseDispute.isEnabled = !state.isLoading
                    state.error?.let {
                        Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                        viewModel.clearMessages()
                    }
                }
            }
        }
    }

    private fun showResolveDialog(dispute: Dispute) {
        val input = EditText(requireContext()).apply {
            hint = "Resolution note"
            setPadding(48, 32, 48, 16)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Resolve dispute")
            .setMessage("Mark this dispute as resolved or unresolved?")
            .setView(input)
            .setPositiveButton("Resolved") { _, _ ->
                viewModel.resolveDispute(dispute.id, input.text.toString(), DisputeStatus.RESOLVED)
            }
            .setNegativeButton("Unresolved") { _, _ ->
                viewModel.resolveDispute(dispute.id, input.text.toString(), DisputeStatus.UNRESOLVED)
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class DisputesUiState(
        val disputes: List<Dispute>,
        val property: com.example.tenant_landlorddisputedocumenter.domain.model.Property?,
        val uid: String?,
    )
}
