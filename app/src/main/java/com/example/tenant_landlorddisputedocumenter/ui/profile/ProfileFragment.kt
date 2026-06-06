package com.example.tenant_landlorddisputedocumenter.ui.profile

import android.content.Intent
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
import com.example.tenant_landlorddisputedocumenter.ui.auth.OnboardingActivity
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireContext().applicationContext as ProofNestApplication
        val authRepository = app.container.authRepository

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
            val appContext = requireContext().applicationContext
            val container = (appContext as ProofNestApplication).container
            authRepository.signOut()
            val intent = Intent(appContext, OnboardingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
            container.repositoryScope.launch {
                container.db.clearSessionCache()
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
