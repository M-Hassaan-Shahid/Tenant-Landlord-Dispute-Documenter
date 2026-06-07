package com.example.tenant_landlorddisputedocumenter.ui.inspection

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.tenant_landlorddisputedocumenter.R
import com.example.tenant_landlorddisputedocumenter.databinding.ItemChecklistBinding
import com.example.tenant_landlorddisputedocumenter.domain.model.ChecklistItem
import com.example.tenant_landlorddisputedocumenter.domain.model.ConditionRating
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionPhase
import android.net.Uri
import android.widget.LinearLayout
import com.example.tenant_landlorddisputedocumenter.ui.allowHorizontalPhotoScroll
import com.example.tenant_landlorddisputedocumenter.ui.pulse
import com.example.tenant_landlorddisputedocumenter.ui.staggerAppear
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ChecklistAdapter(
    private val phase: InspectionPhase,
    private val scope: CoroutineScope,
    private val loadPhotoThumbs: suspend (List<String>, LinearLayout) -> Unit,
    private val onRatingChanged: (itemId: String, rating: ConditionRating) -> Unit,
    private val onNoteChanged: (itemId: String, note: String) -> Unit,
    private val onCapturePhoto: (ChecklistItem) -> Unit,
    private val onPhotoClick: (Uri?) -> Unit = {},
) : ListAdapter<ChecklistItem, ChecklistAdapter.ChecklistViewHolder>(ChecklistDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChecklistViewHolder {
        val binding = ItemChecklistBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ChecklistViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChecklistViewHolder, position: Int) {
        holder.bind(
            getItem(position),
            phase,
            onPhotoThumbs = { ids, strip -> scope.launch { loadPhotoThumbs(ids, strip) } },
            onRatingChanged = onRatingChanged,
            onNoteChanged = onNoteChanged,
            onCapturePhoto = onCapturePhoto,
            onPhotoClick = onPhotoClick,
        )
    }

    class ChecklistViewHolder(private val binding: ItemChecklistBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var noteWatcher: TextWatcher? = null
        private var boundItemId: String? = null

        fun bind(
            item: ChecklistItem,
            phase: InspectionPhase,
            onPhotoThumbs: (List<String>, LinearLayout) -> Unit,
            onRatingChanged: (String, ConditionRating) -> Unit,
            onNoteChanged: (String, String) -> Unit,
            onCapturePhoto: (ChecklistItem) -> Unit,
            onPhotoClick: (Uri?) -> Unit,
        ) {
            binding.textItemName.text = item.name

            val photoIds = if (phase == InspectionPhase.MOVE_IN) item.moveInPhotoIds else item.moveOutPhotoIds
            binding.textPhotoCount.text = binding.root.context.getString(
                R.string.photos_count,
                photoIds.size,
            )
            bindPhotoStrip(binding, photoIds, onPhotoThumbs, onPhotoClick)

            val currentRating = if (phase == InspectionPhase.MOVE_IN) item.moveInRating else item.moveOutRating
            binding.chipGroupRating.setOnCheckedStateChangeListener(null)
            binding.chipGroupRating.check(
                when (currentRating) {
                    ConditionRating.GOOD -> R.id.chipGood
                    ConditionRating.FAIR -> R.id.chipFair
                    ConditionRating.DAMAGED -> R.id.chipPoor
                    null -> ViewGroup.NO_ID
                },
            )
            binding.chipGroupRating.setOnCheckedStateChangeListener { _, checkedIds ->
                val rating = when (checkedIds.firstOrNull()) {
                    R.id.chipGood -> ConditionRating.GOOD
                    R.id.chipFair -> ConditionRating.FAIR
                    R.id.chipPoor -> ConditionRating.DAMAGED
                    else -> return@setOnCheckedStateChangeListener
                }
                applyRatingChipStyles(binding, rating)
                binding.chipGroupRating.pulse()
                onRatingChanged(item.id, rating)
            }
            applyRatingChipStyles(binding, currentRating)

            val currentNote = if (phase == InspectionPhase.MOVE_IN) item.moveInNote else item.moveOutNote
            noteWatcher?.let { binding.inputNotes.removeTextChangedListener(it) }
            if (boundItemId != item.id) {
                binding.inputNotes.setText(currentNote)
                boundItemId = item.id
            }
            noteWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    onNoteChanged(item.id, s?.toString().orEmpty())
                }
            }
            binding.inputNotes.addTextChangedListener(noteWatcher)

            binding.buttonCapture.setOnClickListener { onCapturePhoto(item) }
        }

        private fun bindPhotoStrip(
            binding: ItemChecklistBinding,
            photoIds: List<String>,
            onPhotoThumbs: (List<String>, LinearLayout) -> Unit,
            onPhotoClick: (Uri?) -> Unit,
        ) {
            binding.photoStrip.removeAllViews()
            binding.photoStripScroll.allowHorizontalPhotoScroll()
            if (photoIds.isEmpty()) {
                binding.photoStripScroll.visibility = View.GONE
                return
            }
            binding.photoStripScroll.visibility = View.VISIBLE
            onPhotoThumbs(photoIds.take(4), binding.photoStrip)
        }

        private fun applyRatingChipStyles(binding: ItemChecklistBinding, selected: ConditionRating?) {
            styleRatingChip(binding.chipGood, ConditionRating.GOOD, selected)
            styleRatingChip(binding.chipFair, ConditionRating.FAIR, selected)
            styleRatingChip(binding.chipPoor, ConditionRating.DAMAGED, selected)
        }

        private fun styleRatingChip(chip: Chip, rating: ConditionRating, selected: ConditionRating?) {
            val ctx = chip.context
            val isSelected = selected == rating
            val (bg, stroke, text) = when (rating) {
                ConditionRating.GOOD -> Triple(
                    R.color.rating_good_bg,
                    R.color.rating_good,
                    R.color.rating_good,
                )
                ConditionRating.FAIR -> Triple(
                    R.color.rating_fair_bg,
                    R.color.rating_fair,
                    R.color.rating_fair,
                )
                ConditionRating.DAMAGED -> Triple(
                    R.color.rating_damaged_bg,
                    R.color.rating_damaged,
                    R.color.rating_damaged,
                )
            }
            if (isSelected) {
                chip.chipBackgroundColor = ColorStateList.valueOf(ContextCompat.getColor(ctx, bg))
                chip.chipStrokeColor = ColorStateList.valueOf(ContextCompat.getColor(ctx, stroke))
                chip.chipStrokeWidth = ctx.resources.displayMetrics.density
                chip.setTextColor(ContextCompat.getColor(ctx, text))
            } else {
                chip.chipBackgroundColor = ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, R.color.proofnest_surface),
                )
                chip.chipStrokeColor = ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, R.color.proofnest_surface_variant),
                )
                chip.chipStrokeWidth = ctx.resources.displayMetrics.density
                chip.setTextColor(ContextCompat.getColor(ctx, R.color.proofnest_on_surface))
            }
        }
    }
}

class ChecklistDiffCallback : DiffUtil.ItemCallback<ChecklistItem>() {
    override fun areItemsTheSame(oldItem: ChecklistItem, newItem: ChecklistItem) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: ChecklistItem, newItem: ChecklistItem) = oldItem == newItem
}
