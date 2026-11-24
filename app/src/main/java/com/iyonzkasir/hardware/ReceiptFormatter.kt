package com.iyonzkasir.hardware

import android.content.Context
import android.content.Intent
import com.iyonzkasir.core.*
import com.iyonzkasir.data.local.*
import com.iyonzkasir.data.repo.PrefRepository
import java.io.ByteArrayOutputStream

object ReceiptFormatter {

    private fun cleanAscii(raw: String): String = buildString {
        raw.forEach { ch ->
            when {
                ch.code in 32..126 -> append(ch)
                ch == '\n' -> append('\n')
                ch == '•' || ch == '▸' -> append('-')
                ch == '—' || ch == '–' -> append('-')
            }
        }
    }.trim()

    private fun padLR(left: String, right: String, width: Int): String {
        val space = width - left.length - right.length
        return if (space > 0) left + " ".repeat(space) + right
        else left.take((width - right.length - 1).coerceAtLeast(1)) + " " + right
    }

    private fun padCenter(text: String, width: Int): String {
        val t = text.take(width)
        return " ".repeat(((width - t.length) / 2).coerceAtLeast(0)) + t
    }

    suspend fun buildText(
        prefs: PrefRepository,
        outlet: OutletEntity?,
        order: OrderEntity,
        items: List<OrderItemEntity>
    ): String {
        val width = if (prefs.getPaperWidth() == 80) 48 else 32
        val showAddr = prefs.isReceiptFlag(PrefRepository.R_ADDRESS, true)
        val showPhone = prefs.isReceiptFlag(PrefRepository.R_PHONE, true)
        val showStaff = prefs.isReceiptFlag(PrefRepository.R_STAFF, true)
        val showTable = prefs.isReceiptFlag(PrefRepository.R_TABLE, true)
        val showCust = prefs.isReceiptFlag(PrefRepository.R_CUSTOMER, true)
        val showQueue = prefs.isReceiptFlag(PrefRepository.R_QUEUE, true)
        val showNote = prefs.isReceiptFlag(PrefRepository.R_NOTE, true)
        val showPoints = prefs.isReceiptFlag(PrefRepository.R_POINTS, true)

        val storeName = outlet?.name?.ifBlank { order.outletName }.orEmpty().ifBlank { "iyonzkasir" }
        val sb = StringBuilder()
        sb.appendLine(padCenter(cleanAscii(storeName).uppercase(), width))
        if (showAddr && !outlet?.address.isNullOrBlank()) sb.appendLine(padCenter(cleanAscii(outlet!!.address), width))
        if (showPhone && !outlet?.phoneWa.isNullOrBlank()) sb.appendLine(padCenter("WA: ${outlet!!.phoneWa}", width))
        sb.appendLine("-".repeat(width))
        sb.appendLine(order.timestamp.formatDateTime())
        val codeLine = order.receiptCode.ifBlank { "#${order.id.take(8)}" }
        if (showStaff && order.staffName.isNotBlank()) sb.appendLine(padLR(codeLine, "Kasir: ${cleanAscii(order.staffName).take(12)}", width))
        else sb.appendLine(codeLine)
        if (showQueue && order.queueNumber > 0) sb.appendLine("Antrian: #${order.queueNumber}")
        if (showTable && order.tableNumber.isNotBlank()) sb.appendLine("Meja: ${order.tableNumber}")
        sb.appendLine("Tipe: ${OrderType.fromId(order.orderType).label}")
        if (showCust) {
            if (order.memberName.isNotBlank()) sb.appendLine("Pelanggan: ${cleanAscii(order.memberName)}")
            else if (order.customerName.isNotBlank()) sb.appendLine("Pelanggan: ${cleanAscii(order.customerName)}")
        }
        sb.appendLine("-".repeat(width))

        items.forEach { it ->
            if (it.isBundleComponent) {
                sb.appendLine("  - ${cleanAscii(it.name)} x${it.qty}")
            } else {
                sb.appendLine(cleanAscii(it.name))
                sb.appendLine(padLR("  ${it.qty} x ${it.unitPrice.toRupiah()}", it.lineTotal.toRupiah(), width))
                if (showNote && it.note.isNotBlank()) sb.appendLine("  * ${cleanAscii(it.note)}")
            }
        }
        sb.appendLine("-".repeat(width))
        sb.appendLine(padLR("Subtotal", order.subtotal.toRupiah(), width))
        if (order.discountAmount > 0L) sb.appendLine(padLR("Diskon", "-${order.discountAmount.toRupiah()}", width))
        if (order.voucherAmount > 0L) sb.appendLine(padLR("Voucher", "-${order.voucherAmount.toRupiah()}", width))
        if (order.pointsDiscountAmount > 0L) sb.appendLine(padLR("Poin", "-${order.pointsDiscountAmount.toRupiah()}", width))
        if (order.taxAmount > 0L) sb.appendLine(padLR("PPN ${order.taxPercent}%", order.taxAmount.toRupiah(), width))
        sb.appendLine(padLR("TOTAL", order.total.toRupiah(), width))
        sb.appendLine(padLR(PaymentMethod.fromId(order.paymentMethod).label, order.paidAmount.toRupiah(), width))
        if (order.changeAmount > 0L) sb.appendLine(padLR("Kembali", order.changeAmount.toRupiah(), width))
        if (showPoints && order.pointsEarned > 0) {
            sb.appendLine("-".repeat(width))
            sb.appendLine(padCenter("Poin didapat: +${order.pointsEarned}", width))
        }
        sb.appendLine("-".repeat(width))
        val footer = outlet?.footerReceipt.orEmpty().ifBlank { "Terima kasih sudah berbelanja" }
        sb.appendLine(padCenter(cleanAscii(footer), width))
        return sb.toString()
    }
    suspend fun buildEscPosOrder(
        prefs: PrefRepository,
        outlet: OutletEntity?,
        order: OrderEntity,
        items: List<OrderItemEntity>
    ): ByteArray {
        val text = buildText(prefs, outlet, order, items)
        return ByteArrayOutputStream().apply {
            write(EscPos.INIT)
            write(EscPos.ALIGN_LEFT)
            write(EscPos.SIZE_NORMAL)
            write(text.toByteArray(Charsets.US_ASCII))
            write("\n\n\n".toByteArray(Charsets.US_ASCII))
            write(EscPos.CUT)
        }.toByteArray()
    }

    suspend fun buildTestPrintEscPos(prefs: PrefRepository, outlet: OutletEntity?): ByteArray {
        val paperMm = prefs.getPaperWidth()
        val width = if (paperMm == 80) 48 else 32
        val name = cleanAscii(outlet?.name ?: "iyonzkasir").uppercase()
        val body = buildString {
            appendLine(padCenter("TEST PRINT", width))
            appendLine(padCenter(name, width))
            appendLine("-".repeat(width))
            appendLine("Ukuran kertas : ${paperMm}mm")
            appendLine("Waktu         : ${System.currentTimeMillis().formatDateTime()}")
            appendLine("Status        : OK")
            appendLine("-".repeat(width))
            appendLine(padCenter("Printer siap digunakan", width))
        }
        return ByteArrayOutputStream().apply {
            write(EscPos.INIT)
            write(body.toByteArray(Charsets.US_ASCII))
            write("\n\n\n".toByteArray(Charsets.US_ASCII))
            write(EscPos.CUT)
        }.toByteArray()
    }

    suspend fun buildShiftEscPos(prefs: PrefRepository, shift: ShiftEntity): ByteArray {
        val width = if (prefs.getPaperWidth() == 80) 48 else 32
        val body = buildString {
            appendLine(padCenter("REKAP SHIFT KASIR", width))
            appendLine(padCenter(cleanAscii(shift.outletName), width))
            appendLine(padCenter("Kasir: ${cleanAscii(shift.staffName)}", width))
            appendLine("-".repeat(width))
            appendLine(padLR("Buka", shift.openedAt.formatDateTime(), width))
            appendLine(padLR("Tutup", shift.closedAt.formatDateTime(), width))
            appendLine("-".repeat(width))
            appendLine(padLR("Transaksi", "${shift.txCount}x", width))
            appendLine(padLR("Total Jualan", shift.totalSales.toRupiah(), width))
            appendLine(padLR("Tunai", shift.cashSales.toRupiah(), width))
            appendLine(padLR("Non-Tunai", shift.nonCashSales.toRupiah(), width))
            if (shift.kasbonSales > 0L) appendLine(padLR("Kasbon", shift.kasbonSales.toRupiah(), width))
            appendLine("-".repeat(width))
            appendLine(padLR("Modal Awal", shift.openingCash.toRupiah(), width))
            appendLine(padLR("Uang Fisik", shift.actualCash.toRupiah(), width))
            val diffStr = (if (shift.cashDiff >= 0L) "+" else "") + shift.cashDiff.toRupiah()
            appendLine(padLR("Selisih", diffStr, width))
            if (shift.note.isNotBlank()) {
                appendLine("-".repeat(width))
                appendLine("Catatan: ${cleanAscii(shift.note)}")
            }
        }
        return ByteArrayOutputStream().apply {
            write(EscPos.INIT)
            write(body.toByteArray(Charsets.US_ASCII))
            write("\n\n\n".toByteArray(Charsets.US_ASCII))
            write(EscPos.CUT)
        }.toByteArray()
    }

    fun shareReceiptText(context: Context, receiptText: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, receiptText)
            }
            context.startActivity(Intent.createChooser(intent, "Kirim Nota via..."))
        }
    }
}
