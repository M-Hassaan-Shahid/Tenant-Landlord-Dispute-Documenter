package com.example.tenant_landlorddisputedocumenter.ui.dispute

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.tenant_landlorddisputedocumenter.R
import com.example.tenant_landlorddisputedocumenter.databinding.ItemCompareBinding
import com.example.tenant_landlorddisputedocumenter.domain.model.ChecklistItem
import com.example.tenant_landlorddisputedocumenter.domain.model.ConditionRating
import com.example.tenant_landlorddisputedocumenter.domain.model.RatingDelta

class CompareAdapter(
    private val onRaiseDispute: (ChecklistItem) -> Unit,
) : ListAdapter<ChecklistItem, CompareAdapter.CompareViewHolder>(CompareDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CompareViewHolder {
        val binding = ItemCompareBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CompareViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CompareViewHolder, position: Int) {
        holder.bind(getItem(position), onRaiseDispute)
    }

    class CompareViewHolder(private val binding: ItemCompareBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ChecklistItem, onRaiseDispute: (ChecklistItem) -> Unit) {
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
                if (delta == RatingDelta.DEGRADED) View.VISIBLE else View.GONE
            binding.buttonRaiseDispute.setOnClickListener { onRaiseDispute(item) }
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
