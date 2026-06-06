package com.example.tenant_landlorddisputedocumenter.ui

import android.app.Dialog
import android.net.Uri
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.example.tenant_landlorddisputedocumenter.databinding.DialogPhotoViewerBinding

fun Fragment.showPhotoViewer(uri: Uri?) {
    if (uri == null) return
    val binding = DialogPhotoViewerBinding.inflate(LayoutInflater.from(requireContext()))
    Glide.with(this)
        .load(uri)
        .fitCenter()
        .into(binding.imageFull)
    Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
        setContentView(binding.root)
        binding.buttonClose.setOnClickListener { dismiss() }
        binding.imageFull.setOnClickListener { dismiss() }
        window?.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        show()
    }
}
