package com.example.tenant_landlorddisputedocumenter.ui.dispute

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.tenant_landlorddisputedocumenter.R
import com.example.tenant_landlorddisputedocumenter.databinding.ItemCompareBinding
import com.example.tenant_landlorddisputedocumenter.domain.model.ChecklistItem
import com.example.tenant_landlorddisputedocumenter.domain.model.ConditionRating
import com.example.tenant_landlorddisputedocumenter.domain.model.Photo
import com.example.tenant_landlorddisputedocumenter.domain.model.RatingDelta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class CompareAdapter(
    private val scope: CoroutineScope,
    private val loadPhotos: suspend (List<String>) -> List<Photo>,
    private val onPhotoClick: (Uri?) -> Unit,
    private val onRaiseDispute: (ChecklistItem) -> Unit,
    showRaiseDispute: Boolean = false,
) : ListAdapter<ChecklistItem, CompareAdapter.CompareViewHolder>(CompareDiffCallback()) {

    var showRaiseDispute: Boolean = showRaiseDispute
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CompareViewHolder {
        val binding = ItemCompareBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CompareViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CompareViewHolder, position: Int) {
        holder.bind(getItem(position), scope, loadPhotos, onPhotoClick, onRaiseDispute, this.showRaiseDispute)
    }

    class CompareViewHolder(private val binding: ItemCompareBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: ChecklistItem,
            scope: CoroutineScope,
            loadPhotos: suspend (List<String>) -> List<Photo>,
            onPhotoClick: (Uri?) -> Unit,
            onRaiseDispute: (ChecklistItem) -> Unit,
            showRaiseDispute: Boolean,
        ) {
            val ctx = binding.root.context
            binding.textItemName.text = item.name

            binding.textMoveInRating.text = item.moveInRating?.displayLabel
                ?: ctx.getString(R.string.rating_not_rated)
            binding.textMoveOutRating.text = item.moveOutRating?.displayLabel
                ?: ctx.getString(R.string.rating_not_rated)

            binding.textMoveInRating.setTextColor(ratingColor(ctx, item.moveInRating))
            binding.textMoveOutRating.setTextColor(ratingColor(ctx, item.moveOutRating))

            binding.textMoveInNote.text = item.moveInNote.ifBlank { ctx.getString(R.string.value_dash) }
            binding.textMoveOutNote.text = item.moveOutNote.ifBlank { ctx.getString(R.string.value_dash) }
            binding.textMoveInNote.visibility =
                if (item.moveInNote.isBlank()) View.GONE else View.VISIBLE
            binding.textMoveOutNote.visibility =
                if (item.moveOutNote.isBlank()) View.GONE else View.VISIBLE

            bindPhasePhoto(scope, item.moveInPhotoIds, binding.imageMoveInThumb, binding.iconMoveInPlaceholder,
                binding.frameMoveInPhoto, loadPhotos, onPhotoClick)
            bindPhasePhoto(scope, item.moveOutPhotoIds, binding.imageMoveOutThumb, binding.iconMoveOutPlaceholder,
                binding.frameMoveOutPhoto, loadPhotos, onPhotoClick)

            val delta = item.ratingDelta()
            binding.chipChange.text = when (delta) {
                RatingDelta.DEGRADED -> ctx.getString(R.string.summary_degraded)
                RatingDelta.IMPROVED -> ctx.getString(R.string.summary_improved)
                RatingDelta.UNCHANGED -> ctx.getString(R.string.summary_unchanged)
            }
            val (chipBg, chipText) = when (delta) {
                RatingDelta.DEGRADED -> R.color.rating_damaged_bg to R.color.rating_damaged
                RatingDelta.IMPROVED -> R.color.rating_good_bg to R.color.rating_good
                RatingDelta.UNCHANGED -> R.color.proofnest_surface_variant to R.color.proofnest_on_surface_muted
            }
            binding.chipChange.chipBackgroundColor =
                ColorStateList.valueOf(ContextCompat.getColor(ctx, chipBg))
            binding.chipChange.setTextColor(ContextCompat.getColor(ctx, chipText))

            binding.buttonRaiseDispute.visibility =
                if (showRaiseDispute && delta == RatingDelta.DEGRADED) View.VISIBLE else View.GONE
            binding.buttonRaiseDispute.setOnClickListener { onRaiseDispute(item) }
        }

        private fun bindPhasePhoto(
            scope: CoroutineScope,
            photoIds: List<String>,
            image: android.widget.ImageView,
            placeholder: android.widget.ImageView,
            frame: View,
            loadPhotos: suspend (List<String>) -> List<Photo>,
            onPhotoClick: (Uri?) -> Unit,
        ) {
            if (photoIds.isEmpty()) {
                image.visibility = View.GONE
                placeholder.visibility = View.VISIBLE
                frame.setOnClickListener(null)
                frame.isClickable = false
                return
            }
            scope.launch {
                val photo = loadPhotos(photoIds).firstOrNull()
                val source = photo?.localUri ?: photo?.remoteUrl
                val viewUri = source?.let { Uri.parse(it) }
                if (source == null) {
                    image.visibility = View.GONE
                    placeholder.visibility = View.VISIBLE
                    return@launch
                }
                placeholder.visibility = View.GONE
                image.visibility = View.VISIBLE
                Glide.with(binding.root).load(source).centerCrop().into(image)
                frame.isClickable = true
                frame.setOnClickListener { onPhotoClick(viewUri) }
            }
        }

        private fun ratingColor(context: android.content.Context, rating: ConditionRating?) =
            when (rating) {
                ConditionRating.GOOD -> ContextCompat.getColor(context, R.color.rating_good)
                ConditionRating.FAIR -> ContextCompat.getColor(context, R.color.rating_fair)
                ConditionRating.DAMAGED -> ContextCompat.getColor(context, R.color.rating_damaged)
                null -> ContextCompat.getColor(context, R.color.proofnest_on_surface)
            }
    }
}

class CompareDiffCallback : DiffUtil.ItemCallback<ChecklistItem>() {
    override fun areItemsTheSame(oldItem: ChecklistItem, newItem: ChecklistItem) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: ChecklistItem, newItem: ChecklistItem) = oldItem == newItem
}
