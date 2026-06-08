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
import com.example.tenant_landlorddisputedocumenter.ui.staggerAppear
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class DisputeListAdapter(
    private val scope: CoroutineScope,
    private val loadPhotos: suspend (List<String>) -> List<Photo>,
    private val onPhotoClick: (Uri?) -> Unit,
    private val currentUid: () -> String?,
    private val onPropose: (Dispute) -> Unit,
    private val onRespond: (Dispute) -> Unit,
) : ListAdapter<Dispute, DisputeListAdapter.Holder>(Diff()) {

    private var itemNames: Map<String, String> = emptyMap()
    private var partyNames: Map<String, String> = emptyMap()

    fun updateItemNames(names: Map<String, String>) {
        itemNames = names
        notifyDataSetChanged()
    }

    fun updatePartyNames(names: Map<String, String>) {
        if (names == partyNames) return
        partyNames = names
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemDisputeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.photoStripScroll.allowHorizontalPhotoScroll()
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.staggerAppear(position)
        holder.bind(getItem(position), itemNames, partyNames, scope, loadPhotos, onPhotoClick, currentUid, onPropose, onRespond)
    }

    class Holder(private val binding: ItemDisputeBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            dispute: Dispute,
            itemNames: Map<String, String>,
            partyNames: Map<String, String>,
            scope: CoroutineScope,
            loadPhotos: suspend (List<String>) -> List<Photo>,
            onPhotoClick: (Uri?) -> Unit,
            currentUid: () -> String?,
            onPropose: (Dispute) -> Unit,
            onRespond: (Dispute) -> Unit,
        ) {
            val label = itemNames[dispute.itemId] ?: dispute.itemId
            binding.textItemLabel.text = label
            binding.textReason.text = dispute.reason

            val raiserName = partyNames[dispute.raisedByUid]?.takeIf { it.isNotBlank() }
            if (raiserName != null) {
                binding.textRaisedBy.visibility = View.VISIBLE
                binding.textRaisedBy.text =
                    binding.root.context.getString(R.string.dispute_raised_by, raiserName)
            } else {
                binding.textRaisedBy.visibility = View.GONE
            }
            binding.chipStatus.text = when (dispute.status) {
                DisputeStatus.AWAITING_TENANT_CONFIRMATION -> "AWAITING CONFIRMATION"
                else -> dispute.status.name
            }
            val chipBg = if (dispute.status.isActive) {
                R.color.chip_pending_bg
            } else {
                R.color.chip_occupied_bg
            }
            binding.chipStatus.setChipBackgroundColorResource(chipBg)

            // Surface the landlord's proposed resolution once one exists.
            if (dispute.resolutionNote.isNotBlank()) {
                binding.textProposedResolution.visibility = View.VISIBLE
                binding.textProposedResolution.text =
                    binding.root.context.getString(R.string.dispute_proposed_prefix, dispute.resolutionNote)
            } else {
                binding.textProposedResolution.visibility = View.GONE
            }

            val uid = currentUid()
            val isRaiser = uid != null && dispute.raisedByUid == uid
            // Landlord (non-raiser) proposes; tenant (raiser) confirms/rejects.
            val canPropose = dispute.status == DisputeStatus.OPEN && uid != null && !isRaiser
            val canRespond = dispute.status == DisputeStatus.AWAITING_TENANT_CONFIRMATION && isRaiser
            when {
                canPropose -> {
                    binding.buttonResolve.visibility = View.VISIBLE
                    binding.buttonResolve.setText(R.string.dispute_propose)
                    binding.buttonResolve.setOnClickListener { onPropose(dispute) }
                }
                canRespond -> {
                    binding.buttonResolve.visibility = View.VISIBLE
                    binding.buttonResolve.setText(R.string.dispute_review)
                    binding.buttonResolve.setOnClickListener { onRespond(dispute) }
                }
                else -> {
                    binding.buttonResolve.visibility = View.GONE
                    binding.buttonResolve.setOnClickListener(null)
                }
            }

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
