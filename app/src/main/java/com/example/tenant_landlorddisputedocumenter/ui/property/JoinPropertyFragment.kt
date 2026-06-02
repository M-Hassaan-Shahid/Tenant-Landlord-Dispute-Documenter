package com.example.tenant_landlorddisputedocumenter.ui.property

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
import com.example.tenant_landlorddisputedocumenter.ProofNestApplication
import com.example.tenant_landlorddisputedocumenter.databinding.FragmentJoinPropertyBinding
import kotlinx.coroutines.launch

class JoinPropertyFragment : Fragment() {

    private var _binding: FragmentJoinPropertyBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: PropertyViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentJoinPropertyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val container = (requireContext().applicationContext as ProofNestApplication).container
        viewModel = ViewModelProvider(this, PropertyViewModelFactory(container))[PropertyViewModel::class.java]

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.buttonJoin.setOnClickListener {
            viewModel.joinProperty(binding.inputCode.text.toString())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.buttonJoin.isEnabled = !state.isLoading
                    if (state.error != null) {
                        Toast.makeText(requireContext(), state.error, Toast.LENGTH_SHORT).show()
                        viewModel.clearState()
                    }
                    if (state.successProperty != null) {
                        Toast.makeText(requireContext(), "Joined! Waiting for landlord approval.", Toast.LENGTH_LONG).show()
                        viewModel.clearState()
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
