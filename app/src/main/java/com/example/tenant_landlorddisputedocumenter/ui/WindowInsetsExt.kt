package com.example.tenant_landlorddisputedocumenter.ui

import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar

fun MaterialToolbar.applyStatusBarInset() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val top = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        view.updatePadding(top = top)
        windowInsets
    }
    ViewCompat.requestApplyInsets(this)
}

fun AppBarLayout.applyStatusBarInset() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val top = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        view.updatePadding(top = top)
        windowInsets
    }
    ViewCompat.requestApplyInsets(this)
}

/** Walks the fragment root and applies status-bar padding to the first [AppBarLayout]. */
fun View.applyAppBarStatusBarInset() {
    when (this) {
        is AppBarLayout -> applyStatusBarInset()
        is MaterialToolbar -> applyStatusBarInset()
        is ViewGroup -> {
            for (i in 0 until childCount) {
                getChildAt(i).applyAppBarStatusBarInset()
            }
        }
    }
}
