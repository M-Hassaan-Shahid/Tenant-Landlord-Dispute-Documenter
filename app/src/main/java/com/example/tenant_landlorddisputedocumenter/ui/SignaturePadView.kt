package com.example.tenant_landlorddisputedocumenter.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.Base64
import android.view.MotionEvent
import android.view.View
import java.io.ByteArrayOutputStream

/**
 * A simple finger-drawing signature pad backed by a bitmap. Tracks every stroke and can
 * export the result as a base64-encoded PNG for transport / storage.
 */
class SignaturePadView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 8f), 0f)
    }

    private var bitmap: Bitmap? = null
    private var bitmapCanvas: Canvas? = null
    private val activePath = Path()
    private var lastX = 0f
    private var lastY = 0f
    var hasInk: Boolean = false
        private set

    init {
        isClickable = true
        isFocusable = true
        setBackgroundColor(Color.WHITE)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
            bmp.eraseColor(Color.WHITE)
            bitmapCanvas = Canvas(bmp)
        }
        hasInk = false
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        alpha = if (enabled) 1f else 0.55f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        bitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        // baseline hint
        val y = height * 0.78f
        canvas.drawLine(24f, y, width - 24f, y, baselinePaint)
        canvas.drawPath(activePath, strokePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                activePath.reset()
                activePath.moveTo(x, y)
                lastX = x; lastY = y
            }
            MotionEvent.ACTION_MOVE -> {
                val midX = (x + lastX) / 2
                val midY = (y + lastY) / 2
                activePath.quadTo(lastX, lastY, midX, midY)
                lastX = x; lastY = y
                hasInk = true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePath.lineTo(x, y)
                bitmapCanvas?.drawPath(activePath, strokePaint)
                activePath.reset()
            }
            else -> return false
        }
        invalidate()
        return true
    }

    fun clear() {
        bitmap?.eraseColor(Color.WHITE)
        activePath.reset()
        hasInk = false
        invalidate()
    }

    /** Returns the signature as a base64-encoded PNG, or empty string if nothing was drawn. */
    fun toPngBase64(): String {
        if (!hasInk) return ""
        val bmp = bitmap ?: return ""
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
