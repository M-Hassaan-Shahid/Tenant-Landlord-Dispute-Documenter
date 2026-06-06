package com.example.tenant_landlorddisputedocumenter.ui.inspection

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.tenant_landlorddisputedocumenter.R
import com.example.tenant_landlorddisputedocumenter.databinding.ItemReviewInspectionBinding
import com.example.tenant_landlorddisputedocumenter.domain.model.ChecklistItem
import com.example.tenant_landlorddisputedocumenter.domain.model.ConditionRating
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionPhase
import com.example.tenant_landlorddisputedocumenter.domain.model.Photo
import com.example.tenant_landlorddisputedocumenter.ui.allowHorizontalPhotoScroll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class ReviewItemRow(
    val item: ChecklistItem,
    val roomName: String,
)

class ReviewItemAdapter(
    private val phase: InspectionPhase,
    private val scope: CoroutineScope,
    private val loadPhotos: suspend (List<String>) -> List<Photo>,
    private val onPhotoClick: (Uri?) -> Unit,
) : ListAdapter<ReviewItemRow, ReviewItemAdapter.ReviewViewHolder>(ReviewItemDiff()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val binding = ItemReviewInspectionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false,
        )
        return ReviewViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        holder.bind(getItem(position), phase, scope, loadPhotos, onPhotoClick)
    }

    class ReviewViewHolder(private val binding: ItemReviewInspectionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            row: ReviewItemRow,
            phase: InspectionPhase,
            scope: CoroutineScope,
            loadPhotos: suspend (List<String>) -> List<Photo>,
            onPhotoClick: (Uri?) -> Unit,
        ) {
            val item = row.item
            binding.textRoomName.text = row.roomName
            binding.textItemName.text = item.name

            val rating = if (phase == InspectionPhase.MOVE_IN) item.moveInRating else item.moveOutRating
            val note = if (phase == InspectionPhase.MOVE_IN) item.moveInNote else item.moveOutNote
            val photoIds = if (phase == InspectionPhase.MOVE_IN) item.moveInPhotoIds else item.moveOutPhotoIds

            if (rating != null) {
                binding.chipRating.visibility = View.VISIBLE
                binding.chipRating.text = rating.name.lowercase().replaceFirstChar { it.uppercase() }
                val (bg, text) = ratingColors(binding.root.context, rating)
                binding.chipRating.chipBackgroundColor = android.content.res.ColorStateList.valueOf(bg)
                binding.chipRating.setTextColor(text)
            } else {
                binding.chipRating.visibility = View.VISIBLE
                binding.chipRating.text = binding.root.context.getString(R.string.rating_not_rated)
            }

            if (note.isNotBlank()) {
                binding.textNote.visibility = View.VISIBLE
                binding.textNote.text = note
            } else {
                binding.textNote.visibility = View.GONE
            }

            binding.textPhotoCount.text = binding.root.context.getString(
                R.string.review_photos_tap,
                photoIds.size,
            )
            binding.photoStrip.removeAllViews()
            binding.photoStripScroll.allowHorizontalPhotoScroll()
            if (photoIds.isEmpty()) {
                binding.photoStripScroll.visibility = View.GONE
                return
            }
            binding.photoStripScroll.visibility = View.VISIBLE
            scope.launch {
                val photos = loadPhotos(photoIds)
                binding.photoStrip.removeAllViews()
                val inflater = LayoutInflater.from(binding.root.context)
                photos.forEach { photo ->
                    val thumb = inflater.inflate(R.layout.item_photo_thumb, binding.photoStrip, false)
                    val image = thumb.findViewById<ImageView>(R.id.imageThumb)
                    val loadSource = photo.localUri ?: photo.remoteUrl
                    val viewUri = loadSource?.let { Uri.parse(it) }
                    Glide.with(binding.root)
                        .load(loadSource)
                        .centerCrop()
                        .placeholder(R.drawable.ic_image)
                        .into(image)
                    thumb.isClickable = true
                    thumb.setOnClickListener { onPhotoClick(viewUri) }
                    binding.photoStrip.addView(thumb)
                }
            }
        }

        private fun ratingColors(context: android.content.Context, rating: ConditionRating): Pair<Int, Int> {
            val bgRes = when (rating) {
                ConditionRating.GOOD -> R.color.rating_good_bg
                ConditionRating.FAIR -> R.color.rating_fair_bg
                ConditionRating.DAMAGED -> R.color.rating_damaged_bg
            }
            val textRes = when (rating) {
                ConditionRating.GOOD -> R.color.rating_good
                ConditionRating.FAIR -> R.color.rating_fair
                ConditionRating.DAMAGED -> R.color.rating_damaged
            }
            return ContextCompat.getColor(context, bgRes) to ContextCompat.getColor(context, textRes)
        }
    }
}

private class ReviewItemDiff : DiffUtil.ItemCallback<ReviewItemRow>() {
    override fun areItemsTheSame(old: ReviewItemRow, new: ReviewItemRow) = old.item.id == new.item.id
    override fun areContentsTheSame(old: ReviewItemRow, new: ReviewItemRow) = old == new
}
