package com.example.tenant_landlorddisputedocumenter.ui

import android.net.Uri
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import com.bumptech.glide.Glide
import com.example.tenant_landlorddisputedocumenter.R
import com.example.tenant_landlorddisputedocumenter.domain.model.Photo

fun HorizontalScrollView.allowHorizontalPhotoScroll() {
    setOnTouchListener { v, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> v.parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                v.parent?.requestDisallowInterceptTouchEvent(false)
        }
        false
    }
}

fun LinearLayout.bindPhotoThumbs(
    photos: List<Photo>,
    inflater: LayoutInflater,
    onPhotoClick: (Uri?) -> Unit,
) {
    removeAllViews()
    photos.forEach { photo ->
        val thumb = inflater.inflate(R.layout.item_photo_thumb, this, false)
        val image = thumb.findViewById<ImageView>(R.id.imageThumb)
        val source = photo.localUri ?: photo.remoteUrl
        val viewUri = source?.let { Uri.parse(it) }
        Glide.with(this)
            .load(source)
            .centerCrop()
            .placeholder(R.drawable.ic_image)
            .into(image)
        thumb.isClickable = true
        thumb.setOnClickListener { onPhotoClick(viewUri) }
        addView(thumb)
    }
}
