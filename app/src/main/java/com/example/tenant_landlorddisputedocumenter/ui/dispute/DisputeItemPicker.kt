package com.example.tenant_landlorddisputedocumenter.ui.dispute

import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.tenant_landlorddisputedocumenter.R
import com.example.tenant_landlorddisputedocumenter.domain.model.ChecklistItem

fun Fragment.showDisputeItemPicker(
    propertyId: String,
    items: List<ChecklistItem>,
    onEmpty: () -> Unit = {},
) {
    if (items.isEmpty()) {
        onEmpty()
        return
    }
    val names = items.map { it.name }.toTypedArray()
    AlertDialog.Builder(requireContext())
        .setTitle(R.string.dispute_pick_item_title)
        .setItems(names) { _, which ->
            val item = items[which]
            findNavController().navigate(
                R.id.navigation_dispute,
                bundleOf(
                    "propertyId" to propertyId,
                    "itemId" to item.id,
                    "itemName" to item.name,
                ),
            )
        }
        .setNegativeButton(R.string.action_cancel, null)
        .show()
}
