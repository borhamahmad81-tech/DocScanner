package com.example.docscanner

import android.app.Activity
import android.content.Context
import android.content.Intent
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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var lastPdf: File? = null

    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanResult?.pdf?.let { pdf ->
                val saved = savePdfLocally(pdf.uri)
                if (saved != null) {
                    lastPdf = saved
                    binding.statusText.text = "Saved: ${saved.name}\nChoose where to send it."
                    binding.sendWhatsappButton.isEnabled = true
                    binding.sendEmailButton.isEnabled = true
                }
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

    // 1. Launch ML Kit scanner (auto edge-detection + crop, multi-page)
    private fun startScan() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(20)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
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

    // 2. Save PDF into app folder
    private fun savePdfLocally(uri: Uri): File? {
        return try {
            val dir = File(getExternalFilesDir(null), "scans").apply { mkdirs() }
            val out = File(dir, "doc_${System.currentTimeMillis()}.pdf")
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(out).use { input.copyTo(it) }
            }
            out
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
            null
        }
    }

    private fun pdfUri(file: File): Uri =
        FileProvider.getUriForFile(this, "$packageName.fileprovider", file)

    // 3a. WhatsApp: offer the saved default, or let user pick someone else
    private fun onWhatsAppClicked() {
        if (lastPdf == null) return
        val default = prefs().getString(SettingsActivity.KEY_WHATSAPP, "") ?: ""

        if (default.isEmpty()) {
            // No default set -> go straight to WhatsApp's own chat picker
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

    // If number != null -> open that specific chat. If null -> WhatsApp shows chat list.
    private fun shareToWhatsApp(number: String?) {
        val pdf = lastPdf ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, pdfUri(pdf))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setPackage("com.whatsapp")
            // jid targets a specific contact's chat when we have a number
            if (!number.isNullOrEmpty()) {
                putExtra("jid", "$number@s.whatsapp.net")
            }
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            intent.setPackage("com.whatsapp.w4b")
            try {
                startActivity(intent)
            } catch (e2: Exception) {
                startActivity(Intent.createChooser(intent.setPackage(null), "Share PDF"))
            }
        }
    }

    // 3b. Email: open the user's mail app with PDF attached. No password, no SMTP.
    private fun sendEmail() {
        val pdf = lastPdf ?: return
        val default = prefs().getString(SettingsActivity.KEY_EMAIL, "") ?: ""

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, pdfUri(pdf))
            putExtra(Intent.EXTRA_SUBJECT, "Scanned document")
            putExtra(Intent.EXTRA_TEXT, "Please find the scanned document attached.")
            if (default.isNotEmpty()) {
                putExtra(Intent.EXTRA_EMAIL, arrayOf(default))
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Send via email"))
    }
}
