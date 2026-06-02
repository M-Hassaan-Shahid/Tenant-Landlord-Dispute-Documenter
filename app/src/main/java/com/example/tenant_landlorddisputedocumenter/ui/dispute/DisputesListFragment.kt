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
import com.example.tenant_landlorddisputedocumenter.databinding.FragmentDisputesListBinding
import com.example.tenant_landlorddisputedocumenter.domain.model.Dispute
import com.example.tenant_landlorddisputedocumenter.domain.model.DisputeStatus
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

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { appContainer.syncCoordinator.refreshPropertyData(propertyId) }
        }

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        adapter = DisputeListAdapter { dispute -> showResolveDialog(dispute) }
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
                viewModel.disputes.collect { list ->
                    adapter.submitList(list)
                    binding.layoutEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
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
}
