package com.example.tenant_landlorddisputedocumenter.ui.profile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.example.tenant_landlorddisputedocumenter.ProofNestApplication
import com.example.tenant_landlorddisputedocumenter.data.local.clearSessionCache
import com.example.tenant_landlorddisputedocumenter.R
import com.example.tenant_landlorddisputedocumenter.databinding.FragmentProfileBinding
import com.example.tenant_landlorddisputedocumenter.domain.model.Outcome
import com.example.tenant_landlorddisputedocumenter.ui.auth.OnboardingActivity
import com.example.tenant_landlorddisputedocumenter.ui.fadeInIfNeeded
import com.example.tenant_landlorddisputedocumenter.ui.showPhotoViewer
import com.example.tenant_landlorddisputedocumenter.util.InputValidation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private var currentPhotoUrl: String? = null
    private var currentUser: com.example.tenant_landlorddisputedocumenter.domain.model.User? = null

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) uploadPhoto(uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.frameAvatar.clipToOutline = true
        binding.frameAvatar.outlineProvider = ViewOutlineProvider.BACKGROUND
        binding.imageAvatar.clipToOutline = true
        binding.imageAvatar.outlineProvider = ViewOutlineProvider.BACKGROUND

        val app = requireContext().applicationContext as ProofNestApplication
        val authRepository = app.container.authRepository
        val container = app.container

        val openPicker = { pickPhoto.launch("image/*") }
        binding.frameAvatar.setOnClickListener { openPicker() }
        binding.buttonChangePhoto.setOnClickListener { openPicker() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                authRepository.observeCurrentProfile().collect { user ->
                    if (user != null) {
                        currentUser = user
                        val name = user.displayName.ifBlank { "—" }
                        binding.textName.text = name
                        binding.textRole.text = user.role.name
                        binding.textEmail.text = user.email
                        binding.textPhone.text = user.phone.ifBlank { "Not set" }
                        binding.textCnic.text = user.cnic.ifBlank { "Not set" }
                        binding.textMemberSince.text =
                            com.example.tenant_landlorddisputedocumenter.util.DateUtils
                                .formatReadable(user.createdAtMillis)
                        bindAvatar(user.photoUrl, initialsFrom(name))
                    }
                }
            }
        }

        binding.buttonEditProfile.setOnClickListener { showEditProfileDialog(authRepository) }
        binding.buttonChangePassword.setOnClickListener { showChangePasswordDialog(authRepository) }

        binding.buttonLogout.setOnClickListener {
            val appContext = requireContext().applicationContext
            val appContainer = (appContext as ProofNestApplication).container
            binding.buttonLogout.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val uid = authRepository.currentUserId.value
                    withContext(Dispatchers.IO) {
                        if (uid != null) {
                            appContainer.syncCache.invalidateUser(uid)
                        }
                        appContainer.syncCache.clearAll()
                        appContainer.db.clearSessionCache()
                    }
                    authRepository.signOut()
                    startActivity(
                        Intent(appContext, OnboardingActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        },
                    )
                } catch (e: Exception) {
                    binding.buttonLogout.isEnabled = true
                    Toast.makeText(
                        requireContext(),
                        e.localizedMessage ?: getString(R.string.profile_logout_failed),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private fun showEditProfileDialog(
        authRepository: com.example.tenant_landlorddisputedocumenter.data.repository.AuthRepository,
    ) {
        val user = currentUser ?: return
        val dialogBinding = com.example.tenant_landlorddisputedocumenter.databinding
            .DialogEditProfileBinding.inflate(layoutInflater)
        dialogBinding.editName.setText(user.displayName)
        dialogBinding.editPhone.setText(user.phone)
        dialogBinding.editCnic.setText(user.cnic)

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.profile_edit_title)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.action_save, null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val name = dialogBinding.editName.text?.toString().orEmpty()
                        val phone = dialogBinding.editPhone.text?.toString().orEmpty()
                        val cnic = dialogBinding.editCnic.text?.toString().orEmpty()
                        val error = InputValidation.validateDisplayName(name)
                            ?: InputValidation.validatePhone(phone)
                            ?: InputValidation.validateCnic(cnic)
                        if (error != null) {
                            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
                            return@setOnClickListener
                        }
                        val dialog = this
                        viewLifecycleOwner.lifecycleScope.launch {
                            when (val outcome = authRepository.updateProfile(user.uid, name, phone, cnic)) {
                                is Outcome.Success -> {
                                    Toast.makeText(requireContext(), R.string.profile_saved, Toast.LENGTH_SHORT).show()
                                    dialog.dismiss()
                                }
                                is Outcome.Failure -> Toast.makeText(
                                    requireContext(),
                                    outcome.userMessage ?: getString(R.string.profile_save_failed),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    }
                }
            }
            .show()
    }

    private fun showChangePasswordDialog(
        authRepository: com.example.tenant_landlorddisputedocumenter.data.repository.AuthRepository,
    ) {
        val dialogBinding = com.example.tenant_landlorddisputedocumenter.databinding
            .DialogChangePasswordBinding.inflate(layoutInflater)

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.profile_change_password_title)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.action_save, null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val current = dialogBinding.editCurrentPassword.text?.toString().orEmpty()
                        val newPass = dialogBinding.editNewPassword.text?.toString().orEmpty()
                        val confirm = dialogBinding.editConfirmPassword.text?.toString().orEmpty()
                        if (current.isBlank()) {
                            Toast.makeText(requireContext(), R.string.profile_current_password, Toast.LENGTH_LONG).show()
                            return@setOnClickListener
                        }
                        InputValidation.validatePassword(newPass, confirm)?.let {
                            Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                            return@setOnClickListener
                        }
                        val dialog = this
                        viewLifecycleOwner.lifecycleScope.launch {
                            when (val outcome = authRepository.changePassword(current, newPass)) {
                                is Outcome.Success -> {
                                    Toast.makeText(requireContext(), R.string.profile_password_changed, Toast.LENGTH_SHORT).show()
                                    dialog.dismiss()
                                }
                                is Outcome.Failure -> Toast.makeText(
                                    requireContext(),
                                    outcome.userMessage ?: getString(R.string.profile_password_failed),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    }
                }
            }
            .show()
    }

    private fun bindAvatar(photoUrl: String?, initials: String) {
        val changed = photoUrl != currentPhotoUrl
        currentPhotoUrl = photoUrl
        if (!photoUrl.isNullOrBlank()) {
            binding.textAvatar.visibility = View.GONE
            binding.imageAvatar.visibility = View.VISIBLE
            Glide.with(this)
                .load(photoUrl)
                .circleCrop()
                .into(binding.imageAvatar)
            if (changed) binding.imageAvatar.fadeInIfNeeded()
        } else {
            binding.imageAvatar.visibility = View.GONE
            binding.textAvatar.visibility = View.VISIBLE
            binding.textAvatar.text = initials
            Glide.with(this).clear(binding.imageAvatar)
            if (changed) binding.textAvatar.fadeInIfNeeded()
        }
    }

    private fun uploadPhoto(uri: Uri) {
        val app = requireContext().applicationContext as ProofNestApplication
        val uid = app.container.authRepository.currentUserId.value ?: return

        val mime = requireContext().contentResolver.getType(uri)
        if (mime != null && mime !in InputValidation.ALLOWED_PROFILE_MIME_TYPES) {
            Toast.makeText(requireContext(), R.string.profile_photo_invalid_type, Toast.LENGTH_LONG).show()
            return
        }

        binding.buttonChangePhoto.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            Toast.makeText(requireContext(), R.string.profile_photo_uploading, Toast.LENGTH_SHORT).show()
            try {
                val cacheFile = copyUriToCache(uri)
                if (cacheFile.length() > InputValidation.MAX_PROFILE_PHOTO_BYTES) {
                    Toast.makeText(requireContext(), R.string.profile_photo_too_large, Toast.LENGTH_LONG).show()
                    return@launch
                }
                val remoteUrl = app.container.cloudinary.upload(cacheFile, "avatars/$uid")
                when (val outcome = app.container.authRepository.updatePhotoUrl(uid, remoteUrl)) {
                    is Outcome.Success ->
                        Toast.makeText(requireContext(), R.string.profile_photo_updated, Toast.LENGTH_SHORT).show()
                    is Outcome.Failure -> {
                        val message = outcome.userMessage ?: getString(R.string.profile_photo_failed)
                        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    e.localizedMessage ?: getString(R.string.profile_photo_failed),
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                binding.buttonChangePhoto.isEnabled = true
            }
        }
    }

    private fun copyUriToCache(uri: Uri): File {
        val file = File(requireContext().cacheDir, "avatar_${System.currentTimeMillis()}.jpg")
        requireContext().contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not read selected image.")
        return file
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
