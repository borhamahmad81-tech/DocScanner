package com.example.docscanner

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.docscanner.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    // Opens the system contact picker and returns the chosen contact's phone number
    private val pickContact = registerForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        if (uri != null) {
            val number = readPhoneNumber(uri)
            if (number != null) {
                val clean = number.filter { it.isDigit() || it == '+' }.removePrefix("+")
                binding.whatsappValue.text = clean
                getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_WHATSAPP, clean).apply()
                Toast.makeText(this, "Default contact saved", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Couldn't read that contact's number", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val savedNumber = prefs.getString(KEY_WHATSAPP, "") ?: ""
        binding.whatsappValue.text =
            if (savedNumber.isEmpty()) "No default set" else savedNumber
        binding.emailInput.setText(prefs.getString(KEY_EMAIL, ""))

        binding.chooseContactButton.setOnClickListener {
            pickContact.launch(null)
        }

        binding.clearContactButton.setOnClickListener {
            prefs.edit().remove(KEY_WHATSAPP).apply()
            binding.whatsappValue.text = "No default set"
        }

        binding.saveButton.setOnClickListener {
            prefs.edit()
                .putString(KEY_EMAIL, binding.emailInput.text.toString().trim())
                .apply()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // Given a contact URI, look up the primary phone number
    private fun readPhoneNumber(contactUri: Uri): String? {
        val idCursor = contentResolver.query(contactUri, null, null, null, null)
        idCursor?.use { c ->
            if (c.moveToFirst()) {
                val idIndex = c.getColumnIndex(ContactsContract.Contacts._ID)
                val hasNumberIndex = c.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                if (idIndex < 0) return null
                val contactId = c.getString(idIndex)
                val hasNumber = if (hasNumberIndex >= 0) c.getString(hasNumberIndex) else "1"
                if (hasNumber == "0") return null

                val phoneCursor = contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    null,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                    arrayOf(contactId),
                    null
                )
                phoneCursor?.use { pc ->
                    if (pc.moveToFirst()) {
                        val numIndex = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        if (numIndex >= 0) return pc.getString(numIndex)
                    }
                }
            }
        }
        return null
    }

    companion object {
        const val PREFS = "docscanner_prefs"
        const val KEY_WHATSAPP = "whatsapp_number"
        const val KEY_EMAIL = "email_recipient"
    }
}
