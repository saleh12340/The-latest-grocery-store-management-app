package com.saleh.alazzi.grocery.printing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint

data class ReceiptItem(
    val name: String,
    val quantity: String,
    val unitPrice: Long,
    val total: Long
)

data class ReceiptData(
    val storeName: String = "بقالة العزي للمواد الغذائية",
    val invoiceType: String,
    val invoiceNumber: String,
    val dateTime: String,
    val partyName: String?,
    val items: List<ReceiptItem>,
    val subtotal: Long,
    val discount: Long,
    val total: Long,
    val paid: Long,
    val remaining: Long,
    val accountBalance: Long? = null
)

object ReceiptRenderer {
    private data class Block(val text: String, val size: Float, val bold: Boolean, val centered: Boolean)

    fun render(data: ReceiptData, paperWidthDots: Int): Bitmap {
        require(paperWidthDots == 384 || paperWidthDots == 576) {
            "Paper width must be 384 dots (58mm) or 576 dots (80mm)"
        }
        val horizontalPadding = 14
        val contentWidth = paperWidthDots - horizontalPadding * 2
        val blocks = buildList {
            add(Block(data.storeName, 30f, true, true))
            add(Block(data.invoiceType, 24f, true, true))
            add(Block("رقم الفاتورة: ${data.invoiceNumber}", 20f, true, false))
            add(Block("التاريخ: ${data.dateTime}", 18f, false, false))
            data.partyName?.takeIf { it.isNotBlank() }?.let {
                add(Block("العميل / المورد: $it", 18f, false, false))
            }
            add(Block("--------------------------------", 16f, false, true))
            data.items.forEach { item ->
                add(Block(item.name, 20f, true, false))
                add(Block("الكمية: ${item.quantity}    سعر الوحدة: ${item.unitPrice}", 17f, false, false))
                add(Block("الإجمالي: ${item.total}", 18f, true, false))
            }
            add(Block("--------------------------------", 16f, false, true))
            add(Block("المجموع: ${data.subtotal}", 19f, false, false))
            if (data.discount > 0) add(Block("الخصم: ${data.discount}", 18f, false, false))
            add(Block("الإجمالي النهائي: ${data.total}", 24f, true, false))
            add(Block("المدفوع: ${data.paid}", 19f, false, false))
            add(Block("متبقي الفاتورة: ${data.remaining}", 21f, true, false))
            data.accountBalance?.let { add(Block("الرصيد التراكمي: $it", 18f, true, false)) }
            add(Block("شكراً لتعاملكم معنا", 19f, true, true))
            add(Block("بقالة العزي", 17f, false, true))
        }

        val layouts = blocks.map { block ->
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = block.size
                typeface = if (block.bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                else Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val layout = StaticLayout.Builder.obtain(block.text, 0, block.text.length, paint, contentWidth)
                .setAlignment(if (block.centered) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .setIncludePad(false)
                .setLineSpacing(2f, 1f)
                .build()
            layout to paint
        }
        val totalHeight = layouts.sumOf { it.first.height } + 36
        val bitmap = Bitmap.createBitmap(paperWidthDots, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        var y = 16f
        layouts.forEach { (layout, _) ->
            canvas.save()
            canvas.translate(horizontalPadding.toFloat(), y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + 5f
        }
        return bitmap
    }
}
