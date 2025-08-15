/*package motloung.koena.financeapp.provider

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.util.Log
import motloung.koena.financeapp.data.AppDb

/**
 * Read-only ContentProvider exposing Finance events to trusted apps.
 * Guarded by signature permission + runtime caller allow-list.
 */
class FinanceProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "motloung.koena.financeapp.provider"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/events")

        private const val EVENTS = 1
        private const val EVENT_ID = 2

        private val TRUSTED_PACKAGES = setOf("motloung.koena.analyticsapp") // allow-list

        private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "events", EVENTS)
            addURI(AUTHORITY, "events/#", EVENT_ID)
        }
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        // Permission gate is enforced by android:readPermission in manifest.
        // Extra defense: validate calling UID package at runtime.
        if (!isTrustedCaller()) {
            Log.w("FinanceProvider", "Rejected query from untrusted caller")
            return null
        }

        val dao = AppDb.get(requireNotNull(context)).eventDao()
        return when (matcher.match(uri)) {
            EVENTS -> {
                val c = dao.selectAllAsCursor()
                c.setNotificationUri(requireNotNull(context).contentResolver, CONTENT_URI)
                c
            }
            EVENT_ID -> {
                val id = ContentUris.parseId(uri)
                val c = dao.selectByIdAsCursor(id)
                c.setNotificationUri(requireNotNull(context).contentResolver, CONTENT_URI)
                c
            }
            else -> null
        }
    }

    override fun getType(uri: Uri): String? = when (matcher.match(uri)) {
        EVENTS -> "vnd.android.cursor.dir/vnd.$AUTHORITY.event"
        EVENT_ID -> "vnd.android.cursor.item/vnd.$AUTHORITY.event"
        else -> null
    }

    // READ-ONLY: block modifications
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("Read-only")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("Read-only")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("Read-only")

    private fun isTrustedCaller(): Boolean {
        val ctx = context ?: return false
        val pm = ctx.packageManager
        val uid = Binder.getCallingUid()
        val pkgs = pm.getPackagesForUid(uid) ?: return false
        return pkgs.any { it in TRUSTED_PACKAGES }
    }
}*/

package motloung.koena.financeapp.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import motloung.koena.financeapp.data.AppDb

/**
 * Read-only ContentProvider exposing FinanceApp events.
 * Requires callers to hold: com.example.permission.READ_FINANCE_DATA
 *
 * Manifest must include:
 * <provider
 *   android:name=".provider.FinanceProvider"
 *   android:authorities="motloung.koena.financeapp.provider"
 *   android:exported="true"
 *   android:readPermission="com.example.permission.READ_FINANCE_DATA"
 *   android:grantUriPermissions="true" />
 */
class FinanceProvider : ContentProvider() {

    companion object {
        //const val AUTHORITY = "motloung.koena.financeapp.provider"
        const val AUTHORITY = "motloung.koena.financeapp.financeprovider"

        const val PATH_EVENTS = "events"

        private const val CODE_EVENTS = 1

        private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, PATH_EVENTS, CODE_EVENTS)
        }

        // MIME types
        private const val MIME_DIR_EVENTS =
            "vnd.android.cursor.dir/vnd.motloung.koena.financeapp.event"
    }

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? = when (matcher.match(uri)) {
        CODE_EVENTS -> MIME_DIR_EVENTS
        else -> null
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val ctx = context ?: return null

        when (matcher.match(uri)) {
            CODE_EVENTS -> {
                // 1) Enforce declared permission (manifest readPermission backs this too)
                ctx.enforceCallingOrSelfPermission(
                    "com.example.permission.READ_FINANCE_DATA",
                    "Caller lacks READ_FINANCE_DATA"
                )

                // 2) (Optional) extra caller validation if you keep protectionLevel=normal
                 val uid = android.os.Binder.getCallingUid()
                 val pkgs = ctx.packageManager.getPackagesForUid(uid)?.toSet() ?: emptySet()
                 if (!pkgs.contains("motloung.koena.analyticsapp")) {
                     throw SecurityException("Unknown caller: $pkgs")
                 }

                // 3) Return a Room-backed Cursor (no coroutines, no blocking)
                val cursor = AppDb.get(ctx).eventDao().cursorAll()
                cursor.setNotificationUri(ctx.contentResolver, uri)
                return cursor
            }
            else -> return null
        }
    }

    // Read-only provider for this assignment
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("Insert not supported")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw UnsupportedOperationException("Delete not supported")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        throw UnsupportedOperationException("Update not supported")
    }
}

