package com.example.tenant_landlorddisputedocumenter.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.util.Base64
import com.example.tenant_landlorddisputedocumenter.domain.model.ChecklistItem
import com.example.tenant_landlorddisputedocumenter.domain.model.Dispute
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionPhase
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionRoom
import com.example.tenant_landlorddisputedocumenter.domain.model.Photo
import com.example.tenant_landlorddisputedocumenter.domain.model.Property
import com.example.tenant_landlorddisputedocumenter.domain.model.RatingDelta
import com.example.tenant_landlorddisputedocumenter.domain.model.Signature
import com.example.tenant_landlorddisputedocumenter.domain.model.UserRole
import java.io.File
import java.io.FileOutputStream

/**
 * Renders a multi-page legal-style PDF report using Android's [PdfDocument].
 *
 * Page model: A4 portrait at 72 DPI (595 × 842 pt). We keep our own y-cursor and start a new page
 * when content would overflow the bottom margin.
 */
class PdfReportGenerator(private val context: Context) {

    data class ReportData(
        val property: Property,
        val landlordName: String,
        val tenantName: String,
        val rooms: List<InspectionRoom>,
        val items: List<ChecklistItem>,
        val photosById: Map<String, Photo>,
        val signatures: List<Signature>,
        val disputes: List<Dispute>,
    )

    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 36f
    private val contentWidth get() = pageWidth - margin * 2

    fun generate(data: ReportData): File {
        val doc = PdfDocument()
        val state = PageState(doc)
        state.newPage()

        renderHeader(state, data)
        renderPropertyBlock(state, data)
        renderSummary(state, data)
        renderRooms(state, data)
        renderSignatures(state, data)
        renderDisputes(state, data)
        renderFooter(state)

        state.finalizePage()

        val outDir = File(context.filesDir, "reports").apply { mkdirs() }
        val target = File(outDir, "proofnest_${data.property.id.take(8)}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(target).use { doc.writeTo(it) }
        doc.close()
        return target
    }

    // ---------------- Rendering helpers ----------------

    private inner class PageState(val doc: PdfDocument) {
        var page: PdfDocument.Page? = null
        var canvas: android.graphics.Canvas? = null
        var y: Float = margin
        var pageNumber: Int = 0

        fun newPage() {
            finalizePage()
            pageNumber++
            val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = doc.startPage(info)
            canvas = page!!.canvas
            y = margin
        }

        fun finalizePage() {
            page?.let { doc.finishPage(it) }
            page = null
            canvas = null
        }

        fun ensureSpace(needed: Float) {
            if (y + needed > pageHeight - margin - 24f) newPage()
        }
    }

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textSize = 22f
    }

    private val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textSize = 14f
    }

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 11f
    }

    private val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = 9f
    }

    private fun drawText(state: PageState, text: String, paint: Paint, lineHeight: Float = paint.textSize * 1.4f) {
        val wrapped = wrap(text, paint, contentWidth)
        wrapped.forEach { line ->
            state.ensureSpace(lineHeight)
            state.canvas?.drawText(line, margin, state.y + paint.textSize, paint)
            state.y += lineHeight
        }
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val words = text.split(' ')
        val lines = mutableListOf<String>()
        val current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) current.replace(0, current.length, candidate)
            else {
                if (current.isNotEmpty()) lines += current.toString()
                current.replace(0, current.length, word)
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines
    }

    private fun spacer(state: PageState, height: Float) {
        state.ensureSpace(height)
        state.y += height
    }

    private fun renderHeader(state: PageState, data: ReportData) {
        drawText(state, "ProofNest — Property Inspection Report", titlePaint)
        drawText(state, "Generated ${DateUtils.formatReadable(System.currentTimeMillis())}", mutedPaint)
        spacer(state, 12f)
        state.canvas?.drawLine(margin, state.y, pageWidth - margin, state.y, mutedPaint)
        spacer(state, 16f)
    }

    private fun renderPropertyBlock(state: PageState, data: ReportData) {
        drawText(state, "Property", sectionPaint)
        drawText(state, data.property.address, bodyPaint)
        drawText(
            state,
            "Lease: ${DateUtils.formatShortDate(data.property.leaseStartMillis)} → ${DateUtils.formatShortDate(data.property.leaseEndMillis)}",
            bodyPaint,
        )
        drawText(state, "Rent: ${"%.0f".format(data.property.rent)}    Deposit: ${"%.0f".format(data.property.deposit)}", bodyPaint)
        drawText(state, "Invite code: ${data.property.inviteCode}    Status: ${data.property.status.name}", bodyPaint)
        spacer(state, 6f)
        drawText(state, "Landlord: ${data.landlordName}", bodyPaint)
        drawText(state, "Tenant:   ${data.tenantName}", bodyPaint)
        spacer(state, 16f)
    }

    private fun renderSummary(state: PageState, data: ReportData) {
        drawText(state, "Damage Summary", sectionPaint)
        val degraded = data.items.filter { it.ratingDelta() == RatingDelta.DEGRADED }
        val improved = data.items.filter { it.ratingDelta() == RatingDelta.IMPROVED }
        val unchanged = data.items.filter { it.ratingDelta() == RatingDelta.UNCHANGED }
        drawText(state, "Unchanged: ${unchanged.size}    Improved: ${improved.size}    Degraded: ${degraded.size}", bodyPaint)
        if (degraded.isNotEmpty()) {
            spacer(state, 4f)
            drawText(state, "Degraded items:", bodyPaint)
            degraded.forEach { item ->
                drawText(
                    state,
                    "  • ${item.name}: ${item.moveInRating?.displayLabel ?: "?"} → ${item.moveOutRating?.displayLabel ?: "?"}",
                    bodyPaint,
                )
            }
        }
        spacer(state, 16f)
    }

    private fun renderRooms(state: PageState, data: ReportData) {
        data.rooms.sortedBy { it.sortOrder }.forEach { room ->
            state.ensureSpace(40f)
            drawText(state, "Room: ${room.name}", sectionPaint)
            val items = data.items.filter { it.roomId == room.id }
            if (items.isEmpty()) {
                drawText(state, "  (no checklist items)", mutedPaint)
            } else {
                items.forEach { item -> renderItem(state, item, data) }
            }
            spacer(state, 10f)
        }
    }

    private fun renderItem(state: PageState, item: ChecklistItem, data: ReportData) {
        state.ensureSpace(120f)
        drawText(state, "Item: ${item.name}", bodyPaint.copy(bold = true))
        drawText(
            state,
            "Move-in:  ${item.moveInRating?.displayLabel ?: "—"}    Move-out: ${item.moveOutRating?.displayLabel ?: "—"}    Δ: ${item.ratingDelta()}",
            bodyPaint,
        )
        if (item.moveInNote.isNotBlank()) drawText(state, "Move-in note:  ${item.moveInNote}", bodyPaint)
        if (item.moveOutNote.isNotBlank()) drawText(state, "Move-out note: ${item.moveOutNote}", bodyPaint)

        renderItemPhotoCompare(state, item, data)
        spacer(state, 10f)
    }

    private fun renderItemPhotoCompare(state: PageState, item: ChecklistItem, data: ReportData) {
        val moveIn = item.moveInPhotoIds.mapNotNull(data.photosById::get)
        val moveOut = item.moveOutPhotoIds.mapNotNull(data.photosById::get)
        if (moveIn.isEmpty() && moveOut.isEmpty()) return

        val gap = 12f
        val colW = (contentWidth - gap) / 2f
        val thumbSize = minOf(colW - 8f, 140f)
        val pairCount = maxOf(moveIn.size, moveOut.size).coerceAtMost(4)
        val rowHeight = thumbSize + 28f

        state.ensureSpace(20f + rowHeight * pairCount)
        drawText(state, "Photos — move-in vs move-out", mutedPaint)

        val headerY = state.y
        state.canvas?.drawText("Move-in", margin, headerY + mutedPaint.textSize, mutedPaint)
        state.canvas?.drawText("Move-out", margin + colW + gap, headerY + mutedPaint.textSize, mutedPaint)
        state.y = headerY + 16f

        for (i in 0 until pairCount) {
            state.ensureSpace(rowHeight)
            val rowY = state.y
            moveIn.getOrNull(i)?.let { drawPhotoInColumn(state, it, margin, rowY, thumbSize) }
            moveOut.getOrNull(i)?.let { drawPhotoInColumn(state, it, margin + colW + gap, rowY, thumbSize) }
            state.y = rowY + rowHeight
        }
    }

    private fun drawPhotoInColumn(state: PageState, photo: Photo, x: Float, y: Float, size: Float) {
        val bmp = decodeThumb(photo, size.toInt())
        if (bmp != null) {
            state.canvas?.drawBitmap(
                bmp,
                null,
                Rect(x.toInt(), y.toInt(), (x + size).toInt(), (y + size).toInt()),
                null,
            )
            bmp.recycle()
        }
        val caption = DateUtils.formatShortDate(photo.capturedAtMillis)
        state.canvas?.drawText(caption, x, y + size + 10f, mutedPaint)
        val gps = if (photo.latitude != null) {
            "GPS ${"%.4f".format(photo.latitude)}, ${"%.4f".format(photo.longitude ?: 0.0)}"
        } else {
            "GPS —"
        }
        state.canvas?.drawText(gps, x, y + size + 20f, mutedPaint)
    }

    private fun decodeThumb(photo: Photo, side: Int): Bitmap? {
        val path = photo.localUri?.removePrefix("file://")
        if (path != null) {
            val file = File(path)
            if (file.exists()) {
                return decodeBitmapFile(file.absolutePath, side)
            }
        }
        val remote = photo.remoteUrl ?: return null
        return runCatching {
            val bytes = java.net.URL(remote).openStream().use { it.readBytes() }
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            opts.inSampleSize = calcInSample(opts, side, side)
            opts.inJustDecodeBounds = false
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }.getOrNull()
    }

    private fun decodeBitmapFile(path: String, side: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        opts.inSampleSize = calcInSample(opts, side, side)
        opts.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(path, opts)
    }

    private fun calcInSample(opts: BitmapFactory.Options, reqW: Int, reqH: Int): Int {
        val (w, h) = opts.outWidth to opts.outHeight
        var sample = 1
        while ((w / sample) > reqW * 2 || (h / sample) > reqH * 2) sample *= 2
        return sample
    }

    private fun Paint.copy(bold: Boolean): Paint = Paint(this).apply {
        typeface = Typeface.create(typeface, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun renderSignatures(state: PageState, data: ReportData) {
        state.ensureSpace(60f)
        drawText(state, "Signatures", sectionPaint)
        renderSignaturePairRow(
            state,
            "Move-in",
            findSig(data, UserRole.LANDLORD, InspectionPhase.MOVE_IN),
            findSig(data, UserRole.TENANT, InspectionPhase.MOVE_IN),
        )
        renderSignaturePairRow(
            state,
            "Move-out",
            findSig(data, UserRole.LANDLORD, InspectionPhase.MOVE_OUT),
            findSig(data, UserRole.TENANT, InspectionPhase.MOVE_OUT),
        )
        spacer(state, 16f)
    }

    private fun findSig(data: ReportData, role: UserRole, phase: InspectionPhase): Signature? =
        data.signatures.firstOrNull { it.signerRole == role && it.phase == phase }

    private fun renderSignaturePairRow(
        state: PageState,
        phaseLabel: String,
        landlordSig: Signature?,
        tenantSig: Signature?,
    ) {
        if (landlordSig == null && tenantSig == null) return

        val gap = 12f
        val colW = (contentWidth - gap) / 2f
        val sigWidth = minOf(colW - 8f, 220f)
        state.ensureSpace(120f)

        drawText(state, phaseLabel, bodyPaint.copy(bold = true))
        val headerY = state.y
        state.canvas?.drawText("Landlord", margin, headerY + bodyPaint.textSize, bodyPaint)
        state.canvas?.drawText("Tenant", margin + colW + gap, headerY + bodyPaint.textSize, bodyPaint)
        val contentY = headerY + 18f

        val leftHeight = drawSignatureInColumn(state, landlordSig, margin, contentY, sigWidth)
        val rightHeight = drawSignatureInColumn(state, tenantSig, margin + colW + gap, contentY, sigWidth)
        state.y = contentY + maxOf(leftHeight, rightHeight) + 12f
    }

    private fun drawSignatureInColumn(
        state: PageState,
        sig: Signature?,
        x: Float,
        y: Float,
        maxWidth: Float,
    ): Float {
        if (sig == null) {
            state.canvas?.drawText("(not signed)", x, y + bodyPaint.textSize, mutedPaint)
            return bodyPaint.textSize + 8f
        }
        val bmp = decodeSignatureBitmap(sig) ?: run {
            state.canvas?.drawText("(signature unavailable)", x, y + mutedPaint.textSize, mutedPaint)
            return mutedPaint.textSize + 8f
        }
        val h = (bmp.height.toFloat() / bmp.width.toFloat()) * maxWidth
        val drawH = minOf(h, 72f)
        val drawW = if (h > drawH) maxWidth * (drawH / h) else maxWidth
        state.canvas?.drawBitmap(
            bmp,
            null,
            Rect(x.toInt(), y.toInt(), (x + drawW).toInt(), (y + drawH).toInt()),
            null,
        )
        bmp.recycle()
        state.canvas?.drawText(
            "Signed ${DateUtils.formatReadable(sig.signedAtMillis)}",
            x,
            y + drawH + 12f,
            mutedPaint,
        )
        return drawH + 22f
    }

    private fun renderDisputes(state: PageState, data: ReportData) {
        if (data.disputes.isEmpty()) return
        state.ensureSpace(40f)
        drawText(state, "Disputes (${data.disputes.size})", sectionPaint)
        data.disputes.forEach { dispute ->
            val item = data.items.firstOrNull { it.id == dispute.itemId }
            drawText(state, "• ${item?.name ?: dispute.itemId} — ${dispute.status}", bodyPaint.copy(bold = true))
            drawText(state, "  Raised by ${dispute.raisedByRole} on ${DateUtils.formatShortDate(dispute.raisedAtMillis)}", mutedPaint)
            drawText(state, "  Reason: ${dispute.reason}", bodyPaint)
            if (dispute.counterNote.isNotBlank()) drawText(state, "  Counter: ${dispute.counterNote}", bodyPaint)
            if (dispute.resolutionNote.isNotBlank()) drawText(state, "  Resolution: ${dispute.resolutionNote}", bodyPaint)
            spacer(state, 6f)
        }
    }

    private fun decodeSignatureBitmap(sig: Signature): Bitmap? {
        if (sig.pngBase64.isNotBlank()) {
            return runCatching {
                val bytes = Base64.decode(sig.pngBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }
        val url = sig.remoteUrl ?: return null
        return runCatching {
            val bytes = java.net.URL(url).openStream().use { it.readBytes() }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    private fun renderFooter(state: PageState) {
        spacer(state, 12f)
        state.canvas?.drawLine(margin, state.y, pageWidth - margin, state.y, mutedPaint)
        spacer(state, 8f)
        drawText(state, "Generated by ProofNest — Bilateral, timestamped property records.", mutedPaint)
    }
}
