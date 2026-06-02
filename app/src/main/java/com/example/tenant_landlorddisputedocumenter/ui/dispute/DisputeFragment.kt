package com.example.tenant_landlorddisputedocumenter.ui.dispute

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.tenant_landlorddisputedocumenter.navigation.DisputeFragmentArgs
import com.bumptech.glide.Glide
import com.example.tenant_landlorddisputedocumenter.R
import com.example.tenant_landlorddisputedocumenter.ProofNestApplication
import com.example.tenant_landlorddisputedocumenter.databinding.FragmentDisputeBinding
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionPhase
import com.example.tenant_landlorddisputedocumenter.ui.inspection.CameraCaptureActivity
import kotlinx.coroutines.launch

class DisputeFragment : Fragment() {

    private var _binding: FragmentDisputeBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: DisputeViewModel
    private val args: DisputeFragmentArgs by lazy {
        DisputeFragmentArgs.fromBundle(requireArguments())
    }
    private lateinit var propertyId: String
    private lateinit var itemId: String

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val photoId = result.data?.getStringExtra(CameraCaptureActivity.RESULT_PHOTO_ID)
        val photoUri = result.data?.getStringExtra(CameraCaptureActivity.RESULT_PHOTO_URI)
        if (photoId != null) {
            viewModel.setCounterPhoto(photoId)
            binding.placeholderEvidence.visibility = View.GONE
            binding.imageEvidence.visibility = View.VISIBLE
            photoUri?.let { Glide.with(this).load(it).into(binding.imageEvidence) }
            Toast.makeText(requireContext(), "Counter-evidence photo attached.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDisputeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val appContainer = (requireContext().applicationContext as ProofNestApplication).container
        viewModel = ViewModelProvider(this, DisputeViewModelFactory(appContainer))[DisputeViewModel::class.java]

        propertyId = args.propertyId
        itemId = args.itemId
        val itemName = args.itemName

        viewModel.loadForProperty(propertyId)
        viewModel.targetItemId = itemId

        binding.textItemName.text = getString(R.string.dispute_item_label, itemName)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.buttonAttachPhoto.setOnClickListener {
            val intent = Intent(requireContext(), CameraCaptureActivity::class.java).apply {
                putExtra(CameraCaptureActivity.EXTRA_PROPERTY_ID, propertyId)
                putExtra(CameraCaptureActivity.EXTRA_ITEM_ID, itemId)
                putExtra(CameraCaptureActivity.EXTRA_PHASE, InspectionPhase.MOVE_OUT.name)
            }
            captureLauncher.launch(intent)
        }

        binding.buttonSubmitDispute.setOnClickListener {
            viewModel.submitDispute(binding.inputDisputeReason.text.toString())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.buttonSubmitDispute.isEnabled = !state.isLoading
                    state.error?.let {
                        Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                        viewModel.clearMessages()
                    }
                    if (state.submitted) {
                        Toast.makeText(requireContext(), "Dispute submitted successfully.", Toast.LENGTH_LONG).show()
                        viewModel.clearMessages()
                        findNavController().navigateUp()
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
