package com.example.docscanner

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.example.docscanner.databinding.ActivityMainBinding
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayList

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // What the last scan produced
    private var lastPdf: File? = null
    private var lastJpgPages: MutableList<File> = mutableListOf()

    // The format the user picked for THIS document: "pdf" or "jpg"
    private var chosenFormat: String? = null
    // For JPG multi-page: send separate images, or one combined tall image
    private var jpgCombined: Boolean = false

    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)

            // Save the PDF
            lastPdf = scanResult?.pdf?.uri?.let { savePdfLocally(it) }

            // Save each page as a JPG too
            lastJpgPages = mutableListOf()
            scanResult?.pages?.forEachIndexed { index, page ->
                saveJpgLocally(page.imageUri, index)?.let { lastJpgPages.add(it) }
            }

            if (lastPdf != null || lastJpgPages.isNotEmpty()) {
                val pageCount = lastJpgPages.size
                binding.statusText.text = "Scanned $pageCount page(s).\nNow choose the format."
                binding.sendWhatsappButton.isEnabled = true
                binding.sendEmailButton.isEnabled = true
                // Ask format right after finishing the photos
                askFormat()
            }
        } else {
            Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.sendWhatsappButton.isEnabled = false
        binding.sendEmailButton.isEnabled = false

        binding.scanButton.setOnClickListener { startScan() }
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.sendWhatsappButton.setOnClickListener { onWhatsAppClicked() }
        binding.sendEmailButton.setOnClickListener { sendEmail() }
    }

    private fun prefs() = getSharedPreferences(SettingsActivity.PREFS, Context.MODE_PRIVATE)

    // 1. Launch scanner — request BOTH PDF and JPG so the user can pick later
    private fun startScan() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(90)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

        GmsDocumentScanning.getClient(options).getStartScanIntent(this)
            .addOnSuccessListener { sender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Cannot start scanner: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // Ask PDF or JPG right after scanning finishes
    private fun askFormat() {
        AlertDialog.Builder(this)
            .setTitle("Choose format")
            .setMessage("Send this document as PDF or as photos (JPG)?")
            .setPositiveButton("PDF") { _, _ ->
                chosenFormat = "pdf"
                binding.statusText.text = "Format: PDF\nNow choose where to send it."
            }
            .setNegativeButton("JPG (photos)") { _, _ ->
                chosenFormat = "jpg"
                if (lastJpgPages.size > 1) askJpgStyle()
                else {
                    jpgCombined = false
                    binding.statusText.text = "Format: JPG\nNow choose where to send it."
                }
            }
            .setCancelable(false)
            .show()
    }

    // For multi-page JPG: separate images or one combined tall image
    private fun askJpgStyle() {
        AlertDialog.Builder(this)
            .setTitle("JPG style")
            .setMessage("You scanned ${lastJpgPages.size} pages. Send them how?")
            .setPositiveButton("Separate images") { _, _ ->
                jpgCombined = false
                binding.statusText.text = "Format: JPG (separate)\nNow choose where to send it."
            }
            .setNegativeButton("One combined image") { _, _ ->
                jpgCombined = true
                binding.statusText.text = "Format: JPG (combined)\nNow choose where to send it."
            }
            .setCancelable(false)
            .show()
    }

    // 2a. Save PDF into app folder
    private fun savePdfLocally(uri: Uri): File? {
        return try {
            val dir = File(getExternalFilesDir(null), "scans").apply { mkdirs() }
            val out = File(dir, "doc_${System.currentTimeMillis()}.pdf")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(out).use { input.copyTo(it) }
            }
            out
        } catch (e: Exception) {
            Toast.makeText(this, "PDF save failed: ${e.message}", Toast.LENGTH_LONG).show()
            null
        }
    }

    // 2b. Save one JPG page into app folder
    private fun saveJpgLocally(uri: Uri, index: Int): File? {
        return try {
            val dir = File(getExternalFilesDir(null), "scans").apply { mkdirs() }
            val out = File(dir, "page_${System.currentTimeMillis()}_$index.jpg")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(out).use { input.copyTo(it) }
            }
            out
        } catch (e: Exception) {
            null
        }
    }

    // Build one tall image by stacking all JPG pages vertically
    private fun buildCombinedJpg(): File? {
        if (lastJpgPages.isEmpty()) return null
        return try {
            val bitmaps = lastJpgPages.map { BitmapFactory.decodeFile(it.absolutePath) }
            val width = bitmaps.maxOf { it.width }
            val totalHeight = bitmaps.sumOf { it.height }
            val combined = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(combined)
            canvas.drawColor(android.graphics.Color.WHITE)
            var y = 0
            for (bmp in bitmaps) {
                canvas.drawBitmap(bmp, 0f, y.toFloat(), null)
                y += bmp.height
            }
            val dir = File(getExternalFilesDir(null), "scans").apply { mkdirs() }
            val out = File(dir, "combined_${System.currentTimeMillis()}.jpg")
            FileOutputStream(out).use { combined.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmaps.forEach { it.recycle() }
            combined.recycle()
            out
        } catch (e: Exception) {
            Toast.makeText(this, "Combine failed: ${e.message}", Toast.LENGTH_LONG).show()
            null
        }
    }

    private fun fileUri(file: File): Uri =
        FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

    // Decide which file(s) and mime type to send based on chosen format
    // Returns Pair(mimeType, listOfUris). For JPG it may be multiple uris.
    private fun buildSendData(): Pair<String, ArrayList<Uri>>? {
        return when (chosenFormat) {
            "pdf" -> {
                val pdf = lastPdf ?: return null
                "application/pdf" to arrayListOf(fileUri(pdf))
            }
            "jpg" -> {
                if (lastJpgPages.isEmpty()) return null
                if (jpgCombined) {
                    val combined = buildCombinedJpg() ?: return null
                    "image/jpeg" to arrayListOf(fileUri(combined))
                } else {
                    val uris = ArrayList<Uri>()
                    lastJpgPages.forEach { uris.add(fileUri(it)) }
                    "image/jpeg" to uris
                }
            }
            else -> null
        }
    }

    // 3a. WhatsApp
    private fun onWhatsAppClicked() {
        if (chosenFormat == null) { askFormat(); return }
        val default = prefs().getString(SettingsActivity.KEY_WHATSAPP, "") ?: ""
        if (default.isEmpty()) {
            shareToWhatsApp(null)
        } else {
            AlertDialog.Builder(this)
                .setTitle("Send via WhatsApp")
                .setMessage("Send to your default contact ($default), or choose someone else?")
                .setPositiveButton("Default") { _, _ -> shareToWhatsApp(default) }
                .setNeutralButton("Choose someone else") { _, _ -> shareToWhatsApp(null) }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun shareToWhatsApp(number: String?) {
        val data = buildSendData() ?: return
        val (mime, uris) = data

        val intent = if (uris.size > 1) {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mime
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uris[0])
            }
        }.apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage("com.whatsapp")
            if (!number.isNullOrEmpty()) putExtra("jid", "$number@s.whatsapp.net")
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            intent.setPackage("com.whatsapp.w4b")
            try {
                startActivity(intent)
            } catch (e2: Exception) {
                startActivity(Intent.createChooser(intent.setPackage(null), "Share"))
            }
        }
    }

    // 3b. Email
    private fun sendEmail() {
        if (chosenFormat == null) { askFormat(); return }
        val data = buildSendData() ?: return
        val (mime, uris) = data
        val default = prefs().getString(SettingsActivity.KEY_EMAIL, "") ?: ""

        val intent = if (uris.size > 1) {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mime
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uris[0])
            }
        }.apply {
            putExtra(Intent.EXTRA_SUBJECT, "Scanned document")
            putExtra(Intent.EXTRA_TEXT, "Please find the scanned document attached.")
            if (default.isNotEmpty()) putExtra(Intent.EXTRA_EMAIL, arrayOf(default))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Send via email"))
    }
}
