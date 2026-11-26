package com.quectel.m2aidemo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

internal object OcrSampleImages {
    enum class Kind {
        RECEIPT,
        MENU,
        SIGN
    }

    fun create(kind: Kind): Bitmap {
        val bitmap = Bitmap.createBitmap(1000, 700, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(238, 235, 244))

        val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(55f, 35f, 945f, 665f), 24f, 24f, paper)

        when (kind) {
            Kind.RECEIPT -> drawReceipt(canvas)
            Kind.MENU -> drawMenu(canvas)
            Kind.SIGN -> drawSign(canvas)
        }
        return bitmap
    }

    private fun drawReceipt(canvas: Canvas) {
        drawText(canvas, "M2 COFFEE", 500f, 150f, 58f, COLOR_BRAND, true, Paint.Align.CENTER)
        drawText(canvas, "ORDER RECEIPT", 500f, 205f, 28f, COLOR_MUTED, true, Paint.Align.CENTER)
        drawRule(canvas, 120f, 242f, 880f, 242f)
        drawText(canvas, "Latte", 135f, 315f, 38f, COLOR_TEXT)
        drawText(canvas, "1  x  $4.50", 865f, 315f, 38f, COLOR_TEXT, align = Paint.Align.RIGHT)
        drawText(canvas, "Americano", 135f, 385f, 38f, COLOR_TEXT)
        drawText(canvas, "1  x  $3.80", 865f, 385f, 38f, COLOR_TEXT, align = Paint.Align.RIGHT)
        drawRule(canvas, 120f, 425f, 880f, 425f)
        drawText(canvas, "TOTAL", 135f, 495f, 42f, COLOR_TEXT, true)
        drawText(canvas, "$8.30", 865f, 495f, 42f, COLOR_TEXT, true, Paint.Align.RIGHT)
        drawText(canvas, "THANK YOU", 500f, 585f, 30f, COLOR_MUTED, true, Paint.Align.CENTER)
    }

    private fun drawMenu(canvas: Canvas) {
        drawText(canvas, "DAILY CAFE MENU", 500f, 150f, 54f, COLOR_BRAND, true, Paint.Align.CENTER)
        drawRule(canvas, 120f, 205f, 880f, 205f, COLOR_BRAND)
        drawText(canvas, "Latte              $4.50", 145f, 300f, 38f, COLOR_TEXT)
        drawText(canvas, "Americano      $3.80", 145f, 375f, 38f, COLOR_TEXT)
        drawText(canvas, "SECOND DRINK HALF PRICE", 145f, 475f, 33f, COLOR_BRAND, true)
        drawText(canvas, "HOURS 8:00 AM - 8:00 PM", 145f, 555f, 31f, COLOR_MUTED)
    }

    private fun drawSign(canvas: Canvas) {
        val sign = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(38, 91, 77)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(85f, 75f, 915f, 625f), 28f, 28f, sign)
        drawText(canvas, "CITY LIBRARY", 500f, 245f, 66f, Color.WHITE, true, Paint.Align.CENTER)
        drawText(canvas, "OPEN DAILY", 500f, 350f, 54f, Color.rgb(226, 242, 231), true, Paint.Align.CENTER)
        drawText(canvas, "MON - SUN", 500f, 445f, 38f, Color.WHITE, false, Paint.Align.CENTER)
        drawText(canvas, "08:00 AM - 08:00 PM", 500f, 520f, 38f, Color.WHITE, false, Paint.Align.CENTER)
    }

    private fun drawRule(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        color: Int = COLOR_RULE
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = 3f
        }
        canvas.drawLine(left, top, right, bottom, paint)
    }

    private fun drawText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        bold: Boolean = false,
        align: Paint.Align = Paint.Align.LEFT
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = size
            this.color = color
            textAlign = align
            typeface = Typeface.create(
                "sans-serif",
                if (bold) Typeface.BOLD else Typeface.NORMAL
            )
        }
        canvas.drawText(text, x, y, paint)
    }

    private const val COLOR_BRAND = 0xFF6750A4.toInt()
    private const val COLOR_TEXT = 0xFF211B2C.toInt()
    private const val COLOR_MUTED = 0xFF6F687A.toInt()
    private const val COLOR_RULE = 0xFFEAE6F0.toInt()
}
