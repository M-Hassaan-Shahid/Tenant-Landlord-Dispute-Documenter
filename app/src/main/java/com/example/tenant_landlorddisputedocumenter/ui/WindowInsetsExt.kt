package com.example.tenant_landlorddisputedocumenter.ui

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.R as AppCompatR
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.example.tenant_landlorddisputedocumenter.R
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors

/**
 * Applies window insets so the status bar uses [android:statusBarColor] and the toolbar sits
 * in a fixed-height row below it, keeping the title and navigation icon vertically centered.
 */
fun AppBarLayout.applyStatusBarInset() {
    setBackgroundColor(Color.TRANSPARENT)
    setPadding(0, 0, 0, 0)
    ViewCompat.setOnApplyWindowInsetsListener(this) { _, windowInsets ->
        val top = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        for (i in 0 until childCount) {
            (getChildAt(i) as? MaterialToolbar)?.applyToolbarBelowStatusBar(top)
        }
        windowInsets
    }
    ViewCompat.requestApplyInsets(this)
}

/** Toolbars not inside an [AppBarLayout] (e.g. login, camera). */
fun MaterialToolbar.applyStandaloneToolbarInset() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val top = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        (view as MaterialToolbar).applyToolbarBelowStatusBar(top)
        windowInsets
    }
    ViewCompat.requestApplyInsets(this)
}

fun View.applyAppBarStatusBarInset() {
    when (this) {
        is AppBarLayout -> applyStatusBarInset()
        is MaterialToolbar -> if (parent !is AppBarLayout) applyStandaloneToolbarInset()
        is ViewGroup -> {
            for (i in 0 until childCount) {
                getChildAt(i).applyAppBarStatusBarInset()
            }
        }
    }
}

/** Compact green bar below the status bar — nav icon and title share this height. */
internal fun MaterialToolbar.applyToolbarBelowStatusBar(statusBarTop: Int) {
    val barHeight = resources.getDimensionPixelSize(R.dimen.toolbar_content_height)
    setBackgroundColor(MaterialColors.getColor(this, AppCompatR.attr.colorPrimary))
    minimumHeight = barHeight
    updatePadding(top = 0, bottom = 0)

    val lp = layoutParams
    if (lp is ViewGroup.MarginLayoutParams) {
        lp.topMargin = statusBarTop
        lp.height = barHeight
        layoutParams = lp
    } else if (lp != null) {
        lp.height = barHeight
        layoutParams = lp
    }
}

/** Compact bottom nav: only add gesture-bar padding, no extra internal height. */
fun View.applyBottomNavInset() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val bottom = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        view.updatePadding(bottom = bottom)
        windowInsets
    }
    ViewCompat.requestApplyInsets(this)
}
