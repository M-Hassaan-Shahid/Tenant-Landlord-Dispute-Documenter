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

/** Pads the toolbar inside the bar so the green header fills the status-bar area. */
fun AppBarLayout.applyStatusBarInset() {
    for (i in 0 until childCount) {
        when (val child = getChildAt(i)) {
            is MaterialToolbar -> child.applyStatusBarInset()
        }
    }
}

/** Walks the view tree and applies status-bar padding to toolbars / app bars. */
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
