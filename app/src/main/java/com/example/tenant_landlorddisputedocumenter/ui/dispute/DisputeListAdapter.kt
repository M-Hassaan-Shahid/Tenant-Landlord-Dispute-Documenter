package com.example.tenant_landlorddisputedocumenter.ui.dispute

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.tenant_landlorddisputedocumenter.R
import com.example.tenant_landlorddisputedocumenter.databinding.ItemDisputeBinding
import com.example.tenant_landlorddisputedocumenter.domain.model.Dispute
import com.example.tenant_landlorddisputedocumenter.domain.model.DisputeStatus
import com.example.tenant_landlorddisputedocumenter.domain.model.Photo
import com.example.tenant_landlorddisputedocumenter.ui.allowHorizontalPhotoScroll
import com.example.tenant_landlorddisputedocumenter.ui.bindPhotoThumbs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class DisputeListAdapter(
    private val scope: CoroutineScope,
    private val loadPhotos: suspend (List<String>) -> List<Photo>,
    private val onPhotoClick: (Uri?) -> Unit,
    private val onResolve: (Dispute) -> Unit,
    private val canResolve: (Dispute) -> Boolean = { true },
) : ListAdapter<Dispute, DisputeListAdapter.Holder>(Diff()) {

    private var itemNames: Map<String, String> = emptyMap()

    fun updateItemNames(names: Map<String, String>) {
        itemNames = names
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemDisputeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.photoStripScroll.allowHorizontalPhotoScroll()
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position), itemNames, scope, loadPhotos, onPhotoClick, onResolve, canResolve)
    }

    class Holder(private val binding: ItemDisputeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            dispute: Dispute,
            itemNames: Map<String, String>,
            scope: CoroutineScope,
            loadPhotos: suspend (List<String>) -> List<Photo>,
            onPhotoClick: (Uri?) -> Unit,
            onResolve: (Dispute) -> Unit,
            canResolve: (Dispute) -> Boolean,
        ) {
            val label = itemNames[dispute.itemId] ?: dispute.itemId
            binding.textItemLabel.text = label
            binding.textReason.text = dispute.reason
            binding.chipStatus.text = dispute.status.name
            val chipBg = if (dispute.status == DisputeStatus.OPEN) {
                R.color.chip_pending_bg
            } else {
                R.color.chip_occupied_bg
            }
            binding.chipStatus.setChipBackgroundColorResource(chipBg)
            val open = dispute.status == DisputeStatus.OPEN
            val showResolve = open && canResolve(dispute)
            binding.buttonResolve.visibility = if (showResolve) View.VISIBLE else View.GONE
            binding.buttonResolve.setOnClickListener { if (showResolve) onResolve(dispute) }

            if (dispute.counterPhotoIds.isEmpty()) {
                binding.textEvidenceLabel.visibility = View.GONE
                binding.photoStripScroll.visibility = View.GONE
            } else {
                binding.textEvidenceLabel.visibility = View.VISIBLE
                binding.photoStripScroll.visibility = View.VISIBLE
                scope.launch {
                    val photos = loadPhotos(dispute.counterPhotoIds)
                    binding.photoStrip.bindPhotoThumbs(
                        photos,
                        LayoutInflater.from(binding.root.context),
                        onPhotoClick,
                    )
                }
            }
        }
    }

    private class Diff : DiffUtil.ItemCallback<Dispute>() {
        override fun areItemsTheSame(oldItem: Dispute, newItem: Dispute) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Dispute, newItem: Dispute) = oldItem == newItem
    }
}
