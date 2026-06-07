package com.example.tenant_landlorddisputedocumenter.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import com.airbnb.lottie.LottieAnimationView
import com.facebook.shimmer.ShimmerFrameLayout

private const val STAGGER_MS = 45L
private const val FADE_DURATION_MS = 280L
private const val SLIDE_DISTANCE_DP = 16f

fun View.fadeInSlideUp(delayMs: Long = 0L, durationMs: Long = FADE_DURATION_MS) {
    alpha = 0f
    translationY = SLIDE_DISTANCE_DP * resources.displayMetrics.density
    animate()
        .alpha(1f)
        .translationY(0f)
        .setStartDelay(delayMs)
        .setDuration(durationMs)
        .setInterpolator(DecelerateInterpolator())
        .start()
}

fun View.shake() {
    animate().cancel()
    translationX = 0f
    animate()
        .translationX(12f)
        .setDuration(50)
        .withEndAction {
            animate().translationX(-12f).setDuration(50).withEndAction {
                animate().translationX(8f).setDuration(40).withEndAction {
                    animate().translationX(0f).setDuration(40).start()
                }.start()
            }.start()
        }
        .start()
}

fun View.pulse(scale: Float = 1.06f) {
    animate()
        .scaleX(scale)
        .scaleY(scale)
        .setDuration(120)
        .withEndAction {
            animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        }
        .start()
}

fun View.scaleInFab() {
    scaleX = 0f
    scaleY = 0f
    alpha = 0f
    visibility = View.VISIBLE
    animate()
        .scaleX(1f)
        .scaleY(1f)
        .alpha(1f)
        .setDuration(260)
        .setInterpolator(OvershootInterpolator(1.1f))
        .start()
}

fun TextView.crossfadeTo(newText: CharSequence) {
    if (text == newText) return
    animate()
        .alpha(0f)
        .setDuration(120)
        .withEndAction {
            text = newText
            animate().alpha(1f).setDuration(180).start()
        }
        .start()
}

fun RecyclerView.applyProofNestItemAnimations() {
    itemAnimator = DefaultItemAnimator().apply {
        addDuration = 280
        removeDuration = 220
        moveDuration = 280
        changeDuration = 220
    }
}

fun RecyclerView.ViewHolder.staggerAppear(position: Int) {
    val view = itemView
    view.alpha = 0f
    view.translationY = 20f * view.resources.displayMetrics.density
    view.animate()
        .alpha(1f)
        .translationY(0f)
        .setStartDelay((position.coerceAtMost(12)) * STAGGER_MS)
        .setDuration(FADE_DURATION_MS)
        .setInterpolator(DecelerateInterpolator())
        .start()
}

fun LottieAnimationView.playOnce(onEnd: (() -> Unit)? = null) {
    repeatCount = 0
    if (onEnd != null) {
        addAnimatorListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                removeAnimatorListener(this)
                onEnd()
            }
        })
    }
    playAnimation()
}

fun ShimmerFrameLayout.showShimmer(visible: Boolean) {
    if (visible) {
        visibility = View.VISIBLE
        startShimmer()
    } else {
        stopShimmer()
        visibility = View.GONE
    }
}

fun View.fadeInIfNeeded() {
    if (alpha >= 1f && translationY == 0f) return
    fadeInSlideUp()
}
