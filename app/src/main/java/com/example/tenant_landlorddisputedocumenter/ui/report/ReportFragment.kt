package com.example.tenant_landlorddisputedocumenter.ui.report

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.tenant_landlorddisputedocumenter.navigation.ReportFragmentArgs
import com.example.tenant_landlorddisputedocumenter.ProofNestApplication
import com.example.tenant_landlorddisputedocumenter.databinding.FragmentReportBinding
import com.example.tenant_landlorddisputedocumenter.domain.model.DisputeStatus
import com.example.tenant_landlorddisputedocumenter.domain.model.PropertyFlowPolicy
import com.example.tenant_landlorddisputedocumenter.domain.model.RatingDelta
import com.example.tenant_landlorddisputedocumenter.ui.guardPropertyAccess
import com.example.tenant_landlorddisputedocumenter.ui.playOnce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReportFragment : Fragment() {

    private var _binding: FragmentReportBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ReportViewModel
    private val args: ReportFragmentArgs by lazy {
        ReportFragmentArgs.fromBundle(requireArguments())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val appContainer = (requireContext().applicationContext as ProofNestApplication).container
        viewModel = ViewModelProvider(this, ReportViewModelFactory(appContainer))[ReportViewModel::class.java]

        val propertyId = args.propertyId
        guardPropertyAccess(propertyId, PropertyFlowPolicy::reportAccess) { }

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.lottieReport.setAnimation(com.example.tenant_landlorddisputedocumenter.R.raw.lottie_loading)

        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { appContainer.syncCoordinator.syncPropertyForReport(propertyId) }
        }

        binding.buttonPreview.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val items = appContainer.inspectionRepository.observeAllItems(propertyId).first()
                val disputes = appContainer.disputeRepository.observeForProperty(propertyId).first()
                val openDisputes = disputes.count {
                    it.status.isActive || it.status == DisputeStatus.UNRESOLVED
                }
                val compared = items.filter { it.moveInRating != null && it.moveOutRating != null }
                val degraded = compared.count { it.ratingDelta() == RatingDelta.DEGRADED }
                val summary = buildString {
                    appendLine("Items with both phases: ${compared.size}")
                    appendLine("Degraded items: $degraded")
                    appendLine("Open disputes: $openDisputes")
                    appendLine()
                    compared.take(12).forEach { item ->
                        appendLine("• ${item.name}: ${item.moveInRating?.name} → ${item.moveOutRating?.name}")
                    }
                    if (compared.size > 12) appendLine("… and ${compared.size - 12} more")
                }
                AlertDialog.Builder(requireContext())
                    .setTitle("Report preview")
                    .setMessage(summary.ifBlank { "No comparable items yet." })
                    .setPositiveButton("OK", null)
                    .show()
            }
        }

        binding.buttonExport.setOnClickListener {
            viewModel.generateReport(propertyId)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.buttonExport.isEnabled = !state.isGenerating
                    binding.buttonExport.text = if (state.isGenerating) "Generating…" else "Generate & Share PDF"
                    if (state.isGenerating) {
                        binding.lottieReport.visibility = View.VISIBLE
                        binding.lottieReport.playAnimation()
                    } else {
                        binding.lottieReport.cancelAnimation()
                        binding.lottieReport.visibility = View.GONE
                    }

                    state.error?.let {
                        Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                        viewModel.clearMessages()
                    }

                    state.reportFile?.let { file ->
                        binding.lottieReport.setAnimation(com.example.tenant_landlorddisputedocumenter.R.raw.lottie_success)
                        binding.lottieReport.visibility = View.VISIBLE
                        binding.lottieReport.playOnce()
                        viewModel.clearMessages()
                        val uri = FileProvider.getUriForFile(
                            requireContext(),
                            "${requireContext().packageName}.fileprovider",
                            file
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(shareIntent, "Share Inspection Report"))
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
