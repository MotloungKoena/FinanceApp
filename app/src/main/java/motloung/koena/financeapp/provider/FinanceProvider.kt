package motloung.koena.financeapp.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import motloung.koena.financeapp.data.AppDb

class FinanceProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "motloung.koena.financeapp.financeprovider"

        const val PATH_EVENTS = "events"

        private const val CODE_EVENTS = 1

        private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, PATH_EVENTS, CODE_EVENTS)
        }

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
                ctx.enforceCallingOrSelfPermission(
                    "com.example.permission.READ_FINANCE_DATA",
                    "Caller lacks READ_FINANCE_DATA"
                )

                 val uid = android.os.Binder.getCallingUid()
                 val pkgs = ctx.packageManager.getPackagesForUid(uid)?.toSet() ?: emptySet()
                 if (!pkgs.contains("motloung.koena.analyticsapp")) {
                     throw SecurityException("Unknown caller: $pkgs")
                 }

                val cursor = AppDb.get(ctx).eventDao().cursorAll()
                cursor.setNotificationUri(ctx.contentResolver, uri)
                return cursor
            }
            else -> return null
        }
    }

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

