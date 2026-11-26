package com.example.service

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.DriverExpense
import com.example.data.model.FallbackFareConfig
import com.example.data.model.RideRequest
import com.example.data.model.resolveRideFinancialSummary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExportHelper {

    private fun csvCell(raw: String): String {
        val escaped = raw.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    fun shareTripsCsv(
        context: Context,
        rides: List<RideRequest>,
        commissionRate: Double,
        fixedTripFee: Double
    ) {
        if (rides.isEmpty()) {
            Toast.makeText(context, "No completed trips to export.", Toast.LENGTH_SHORT).show()
            return
        }
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.UK)
        val timeFmt = SimpleDateFormat("HH:mm", Locale.UK)
        val sb = StringBuilder()
        sb.appendLine("Date,Time,Passenger,Pickup,Dropoff,Distance(mi),BaseFare(GBP),PlatformFee(GBP),Tip(GBP),NetEarnings(GBP),PaymentMethod")
        rides.forEach { ride ->
            val ts = Date(maxOf(ride.completedAtMs, ride.createdAtMs))
            val summary = resolveRideFinancialSummary(ride, FallbackFareConfig(commissionRate, fixedTripFee))
            sb.appendLine(
                listOf(
                    csvCell(dateFmt.format(ts)),
                    csvCell(timeFmt.format(ts)),
                    csvCell(ride.passengerName),
                    csvCell(ride.pickupAddress),
                    csvCell(ride.dropoffAddress),
                    "%.2f".format(Locale.US, ride.distanceMiles),
                    "%.2f".format(Locale.US, summary.baseFare),
                    "%.2f".format(Locale.US, summary.platformFee),
                    "%.2f".format(Locale.US, summary.tipAmount),
                    "%.2f".format(Locale.US, summary.driverEarnings),
                    csvCell(ride.paymentMethod.uppercase())
                ).joinToString(",")
            )
        }
        shareCsvString(
            context = context,
            fileName = "AnyRoller_Trips_${SimpleDateFormat("yyyyMMdd", Locale.UK).format(Date())}.csv",
            csvContent = sb.toString(),
            chooserTitle = "Export AnyRoller Trips CSV (HMRC)"
        )
    }

    fun shareExpensesCsv(context: Context, expenses: List<DriverExpense>) {
        if (expenses.isEmpty()) {
            Toast.makeText(context, "No expenses logged to export.", Toast.LENGTH_SHORT).show()
            return
        }
        val sb = StringBuilder()
        sb.appendLine("Date,Category,Amount(GBP),Notes,ReceiptURL")
        expenses.forEach { exp ->
            sb.appendLine(
                listOf(
                    csvCell(exp.date),
                    csvCell(exp.category),
                    "%.2f".format(Locale.US, exp.amount),
                    csvCell(exp.notes),
                    csvCell(exp.receiptImage ?: "")
                ).joinToString(",")
            )
        }
        shareCsvString(
            context = context,
            fileName = "AnyRoller_Expenses_${SimpleDateFormat("yyyyMMdd", Locale.UK).format(Date())}.csv",
            csvContent = sb.toString(),
            chooserTitle = "Export AnyRoller Expenses CSV (HMRC)"
        )
    }

    private fun shareCsvString(
        context: Context,
        fileName: String,
        csvContent: String,
        chooserTitle: String
    ) {
        runCatching {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(exportDir, fileName)
            file.writeText(csvContent, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }.onFailure {
            Toast.makeText(context, "Could not share CSV file on this device.", Toast.LENGTH_SHORT).show()
        }
    }
}
