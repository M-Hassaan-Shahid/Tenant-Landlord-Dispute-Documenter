package com.example.tenant_landlorddisputedocumenter.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.tenant_landlorddisputedocumenter.ProofNestApplication
import com.example.tenant_landlorddisputedocumenter.data.local.clearSessionCache
import com.example.tenant_landlorddisputedocumenter.databinding.FragmentProfileBinding
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val authRepository = (requireContext().applicationContext as ProofNestApplication)
            .container.authRepository

        // Observe through repository, not directly through DAO
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                authRepository.observeCurrentProfile().collect { user ->
                    if (user != null) {
                        val name = user.displayName.ifBlank { "—" }
                        binding.textName.text = name
                        binding.textRole.text = user.role.name
                        binding.textEmail.text = user.email
                        binding.textPhone.text = user.phone.ifBlank { "Not set" }
                        binding.textAvatar.text = initialsFrom(name)
                    }
                }
            }
        }

        binding.buttonLogout.setOnClickListener {
            val app = requireContext().applicationContext as ProofNestApplication
            viewLifecycleOwner.lifecycleScope.launch {
                authRepository.signOut()
                app.container.db.clearSessionCache()
                val intent = android.content.Intent(requireContext(), com.example.tenant_landlorddisputedocumenter.ui.auth.OnboardingActivity::class.java).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                requireActivity().finish()
            }
        }
    }

    private fun initialsFrom(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            parts.isEmpty() || parts.singleOrNull() == "—" -> "?"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "${parts.first().first()}${parts.last().first()}".uppercase()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
