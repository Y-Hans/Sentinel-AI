package com.sentinel.ai.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.sentinel.ai.core.sender.ContactLookupStatus
import com.sentinel.ai.core.sender.ContactResolution
import com.sentinel.ai.core.sender.ContactResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete Android implementation of [ContactResolver] backed by [ContactsContract].
 *
 * Encapsulates all query strategies, permission checks, phone normalization,
 * and display-name resolution behind the platform-independent domain boundary.
 */
@Singleton
class AndroidContactResolver @Inject constructor(
    @ApplicationContext private val context: Context
) : ContactResolver {

    internal var permissionChecker: (Context, String) -> Boolean = { ctx, perm ->
        runCatching {
            ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    override fun resolve(identifier: String): ContactResolution {
        val trimmed = identifier.trim()
        if (trimmed.isEmpty()) {
            return ContactResolution.invalidIdentifier(identifier)
        }

        if (!permissionChecker(context, Manifest.permission.READ_CONTACTS)) {
            Log.d(TAG, "READ_CONTACTS permission not granted")
            return ContactResolution.permissionDenied(trimmed)
        }

        return runCatching {
            // 1. Email address lookup
            if (isEmail(trimmed)) {
                val emailDisplayName = lookupEmailContactName(trimmed)
                if (emailDisplayName != null) {
                    return@runCatching ContactResolution.matchFound(
                        displayName = emailDisplayName,
                        normalizedIdentifier = trimmed.lowercase(Locale.ROOT)
                    )
                }
            }

            // 2. Phone number lookup (PhoneLookup indexed query + fallback suffix search)
            if (isPhoneNumber(trimmed)) {
                val phoneDisplayName = lookupPhoneContactName(trimmed)
                if (phoneDisplayName != null) {
                    return@runCatching ContactResolution.matchFound(
                        displayName = phoneDisplayName,
                        normalizedIdentifier = normalizeNumber(trimmed)
                    )
                }
            }

            // 3. Name lookup (case-insensitive + normalized whitespace match)
            val nameDisplayName = lookupContactByName(trimmed)
            if (nameDisplayName != null) {
                return@runCatching ContactResolution.matchFound(
                    displayName = nameDisplayName,
                    normalizedIdentifier = normalizeName(trimmed)
                )
            }

            // 4. IM / Nickname Data table lookup
            val dataDisplayName = lookupExactDataContactName(trimmed)
            if (dataDisplayName != null) {
                return@runCatching ContactResolution.matchFound(
                    displayName = dataDisplayName,
                    normalizedIdentifier = trimmed
                )
            }

            // No match found
            ContactResolution.noMatch(trimmed)
        }.getOrElse { error ->
            Log.e(TAG, "Contact resolution failed for identifier", error)
            ContactResolution.lookupError(trimmed)
        }
    }

    private fun lookupPhoneContactName(phoneNumber: String): String? {
        val contentResolver = context.contentResolver

        // Strategy 1: Targeted indexed query via PhoneLookup
        val lookupUri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        val indexedName = contentResolver.query(
            lookupUri,
            arrayOf(
                ContactsContract.PhoneLookup._ID,
                ContactsContract.PhoneLookup.DISPLAY_NAME
            ),
            null,
            null,
            null
        )?.useFirstString(ContactsContract.PhoneLookup.DISPLAY_NAME)

        if (indexedName != null) return indexedName

        // Strategy 2: Fallback query on Phone table matching suffix digits
        val normalizedDigits = phoneNumber.filter(Char::isDigit)
        if (normalizedDigits.length >= 7) {
            val searchSuffix = normalizedDigits.takeLast(10)
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                ),
                "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?",
                arrayOf("%$searchSuffix"),
                null
            )?.use { cursor ->
                val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val contactNumber = if (numberIdx >= 0) cursor.getString(numberIdx).orEmpty() else ""
                    if (phoneNumbersMatch(phoneNumber, contactNumber)) {
                        val contactName = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                        return contactName?.takeIf { it.isNotBlank() } ?: phoneNumber
                    }
                }
            }
        }

        return null
    }

    private fun lookupEmailContactName(email: String): String? {
        val uri = Uri.withAppendedPath(
            ContactsContract.CommonDataKinds.Email.CONTENT_FILTER_URI,
            Uri.encode(email)
        )
        return context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME),
            null,
            null,
            null
        ).useFirstString(ContactsContract.CommonDataKinds.Email.DISPLAY_NAME)
    }

    private fun lookupContactByName(name: String): String? {
        val normalized = normalizeName(name)
        if (normalized.isEmpty()) return null

        return context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ? COLLATE NOCASE",
            arrayOf(name.trim()),
            null
        )?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val contactName = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else ""
                if (isNameMatch(name, contactName)) {
                    return@use contactName.takeIf { it.isNotBlank() }
                }
            }
            null
        }
    }

    private fun lookupExactDataContactName(identifier: String): String? {
        val mimetypes = listOf(
            ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE
        )
        val placeholders = mimetypes.joinToString(",") { "?" }
        return context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.DISPLAY_NAME),
            "${ContactsContract.Data.DATA1} = ? AND ${ContactsContract.Data.MIMETYPE} IN ($placeholders)",
            arrayOf(identifier, *mimetypes.toTypedArray()),
            null
        ).useFirstString(ContactsContract.Data.DISPLAY_NAME)
    }

    private fun Cursor?.useFirstString(columnName: String): String? {
        this ?: return null
        return use { cursor ->
            if (!cursor.moveToFirst()) return null
            val columnIndex = cursor.getColumnIndex(columnName)
            if (columnIndex < 0) return null
            cursor.getString(columnIndex)?.takeIf { it.isNotBlank() }
        }
    }

    companion object {
        const val TAG = "AndroidContactResolver"

        private val PHONE_ALLOWED_REGEX = Regex("^[+0-9()\\-.\t ]+$")
        private val EMAIL_REGEX = Regex("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", RegexOption.IGNORE_CASE)

        /**
         * Convenience factory to resolve a contact directly from a Context.
         */
        fun resolve(context: Context, identifier: String): ContactResolution {
            return AndroidContactResolver(context).resolve(identifier)
        }

        internal fun isEmail(text: String): Boolean = EMAIL_REGEX.matches(text.trim())

        internal fun isPhoneNumber(text: String): Boolean {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return false
            if (!PHONE_ALLOWED_REGEX.matches(trimmed)) return false
            val first = trimmed.first()
            val last = trimmed.last()
            if (!(first.isDigit() || first == '+' || first == '(')) return false
            if (!(last.isDigit() || last == ')')) return false
            val digits = trimmed.filter(Char::isDigit)
            return digits.length in 7..15
        }

        internal fun normalizeNumber(number: String): String {
            val trimmed = number.trim()
            val hasPlus = trimmed.contains("+")
            val digits = trimmed.filter(Char::isDigit)
            return if (hasPlus && digits.isNotEmpty()) "+$digits" else digits
        }

        internal fun phoneNumbersMatch(a: String, b: String): Boolean {
            val na = normalizeNumber(a)
            val nb = normalizeNumber(b)
            if (na.isEmpty() || nb.isEmpty()) return false
            if (na == nb) return true

            val da = na.filter(Char::isDigit)
            val db = nb.filter(Char::isDigit)
            if (da == db) return true

            val minLength = minOf(da.length, db.length)
            if (minLength >= 7 && (da.endsWith(db) || db.endsWith(da))) {
                return true
            }
            return false
        }

        internal fun isNameMatch(a: String, b: String): Boolean {
            val na = normalizeName(a)
            val nb = normalizeName(b)

            return na.isNotEmpty() &&
                    nb.isNotEmpty() &&
                    na == nb
        }

        internal fun normalizeName(name: String): String {
            return name.lowercase(Locale.ROOT)
                .replace("[^a-z0-9 ]".toRegex(), "")
                .replace("\\s+".toRegex(), " ")
                .trim()
        }
    }
}
