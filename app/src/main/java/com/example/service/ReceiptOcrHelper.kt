package com.example.service

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale

data class ReceiptScanResult(
    val amount: Double? = null,
    val dateIso: String? = null,
    val suggestedCategory: String? = null,
    val merchantHint: String? = null
)

object ReceiptOcrHelper {

    suspend fun scanReceipt(context: Context, imageUri: Uri): Result<ReceiptScanResult> {
        return try {
            val image = InputImage.fromFilePath(context, imageUri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val visionText = recognizer.process(image).await()
            val fullText = visionText.text
            if (fullText.isBlank()) {
                return Result.success(ReceiptScanResult())
            }
            val lines = fullText.lines().map { it.trim() }.filter { it.isNotBlank() }
            val amount = extractTotalAmount(lines)
            val dateIso = extractIsoDate(fullText)
            val category = inferCategory(fullText)
            val merchant = lines.firstOrNull { it.length in 3..36 && it.any(Char::isLetter) }
            Result.success(
                ReceiptScanResult(
                    amount = amount,
                    dateIso = dateIso,
                    suggestedCategory = category,
                    merchantHint = merchant
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractTotalAmount(lines: List<String>): Double? {
        val priceRegex = Regex("""£?\s*(\d{1,4}\.\d{2})\b""")
        val totalKeywords = listOf("total", "amount due", "balance due", "to pay", "card", "visa", "mastercard", "sale")
        // 1. Prefer lines containing total keywords (from bottom up, as totals appear near receipt bottom)
        for (i in lines.indices.reversed()) {
            val line = lines[i]
            val lower = line.lowercase(Locale.UK)
            if (totalKeywords.any { lower.contains(it) }) {
                val match = priceRegex.findAll(line).lastOrNull()
                val v = match?.groupValues?.getOrNull(1)?.toDoubleOrNull()
                if (v != null && v in 0.5..5000.0) return v
                // Check next line if total value wrapped to next line
                val nextLine = lines.getOrNull(i + 1)
                if (nextLine != null) {
                    val nextV = priceRegex.findAll(nextLine).lastOrNull()?.groupValues?.getOrNull(1)?.toDoubleOrNull()
                    if (nextV != null && nextV in 0.5..5000.0) return nextV
                }
            }
        }
        // 2. Fallback: largest valid currency value on the receipt
        val allPrices = lines.flatMap { line ->
            priceRegex.findAll(line).mapNotNull { it.groupValues.getOrNull(1)?.toDoubleOrNull() }
        }.filter { it in 0.5..5000.0 }
        return allPrices.maxOrNull()
    }

    private fun extractIsoDate(text: String): String? {
        // Match YYYY-MM-DD
        Regex("""\b(20\d{2})[-/](0[1-9]|1[0-2])[-/](0[1-9]|[12]\d|3[01])\b""").find(text)?.let { m ->
            return "${m.groupValues[1]}-${m.groupValues[2]}-${m.groupValues[3]}"
        }
        // Match UK DD/MM/YYYY or DD-MM-YYYY or DD.MM.YY
        Regex("""\b(0?[1-9]|[12]\d|3[01])[/\-.](0?[1-9]|1[0-2])[/\-.](20\d{2}|\d{2})\b""").find(text)?.let { m ->
            val dd = m.groupValues[1].padStart(2, '0')
            val mm = m.groupValues[2].padStart(2, '0')
            val rawY = m.groupValues[3]
            val yyyy = if (rawY.length == 2) "20$rawY" else rawY
            return "$yyyy-$mm-$dd"
        }
        // Match DD MMM YYYY (e.g., 27 Nov 2026)
        Regex("""\b(0?[1-9]|[12]\d|3[01])\s+([A-Za-z]{3})\s+(20\d{2})\b""").find(text)?.let { m ->
            val parsed = runCatching {
                SimpleDateFormat("d MMM yyyy", Locale.UK).parse(m.value)
            }.getOrNull()
            if (parsed != null) {
                return SimpleDateFormat("yyyy-MM-dd", Locale.UK).format(parsed)
            }
        }
        return null
    }

    private fun inferCategory(text: String): String? {
        val lower = text.lowercase(Locale.UK)
        return when {
            listOf("shell", "bp ", "esso", "texaco", "diesel", "unleaded", "petrol", "fuel", "litre", "jet ", "asda fuel", "tesco fuel").any { lower.contains(it) } -> "Fuel"
            listOf("kwh", "supercharger", "pod point", "instavolt", "gridserve", "ionity", "ev charg", "fastned").any { lower.contains(it) } -> "Charging"
            listOf("car wash", "valet", "hand wash", "imo wash", "jet wash").any { lower.contains(it) } -> "Car Wash"
            listOf("toll", "dartford", "congestion", "ulez", "m6 toll", "bridge", "parking", "ncp", "ringgo").any { lower.contains(it) } -> "Tolls"
            listOf("halfords", "kwik fit", "mot", "tyre", "tire", "garage", "service", "oil", "brake", "wiper", "repair").any { lower.contains(it) } -> "Maintenance"
            listOf("zego", "inshur", "admiral", "insurance", "policy", "hire & reward").any { lower.contains(it) } -> "Insurance"
            else -> null
        }
    }
}
