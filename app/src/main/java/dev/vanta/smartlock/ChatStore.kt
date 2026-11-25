package dev.vanta.smartlock

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

data class StoredChatMessage(
    val id: String,
    val peer: String,
    val text: String,
    val outgoing: Boolean,
    val delivered: Boolean,
    val createdAt: Long
)

/** Encrypted-at-rest conversation history and durable outgoing queue. */
class ChatStore(context: Context, private val security: ChatSecurity) :
    SQLiteOpenHelper(context, "vanta_mesh.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE messages (
                id TEXT PRIMARY KEY NOT NULL,
                peer TEXT NOT NULL,
                body BLOB NOT NULL,
                outgoing INTEGER NOT NULL,
                delivered INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX messages_peer_time ON messages(peer, created_at)")
        db.execSQL("CREATE INDEX messages_outbox ON messages(peer, outgoing, delivered)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Versioned migrations will be added before any schema change is shipped.
    }

    @Synchronized
    fun add(peer: String, text: String, outgoing: Boolean): String {
        val id = UUID.randomUUID().toString()
        val values = ContentValues().apply {
            put("id", id)
            put("peer", peer)
            put("body", security.encryptAtRest(text.toByteArray(Charsets.UTF_8)))
            put("outgoing", if (outgoing) 1 else 0)
            put("delivered", if (outgoing) 0 else 1)
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insertOrThrow("messages", null, values)
        return id
    }

    @Synchronized
    fun addIncoming(id: String, peer: String, text: String) {
        val values = ContentValues().apply {
            put("id", id)
            put("peer", peer)
            put("body", security.encryptAtRest(text.toByteArray(Charsets.UTF_8)))
            put("outgoing", 0)
            put("delivered", 1)
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    @Synchronized
    fun history(peer: String, limit: Int = 500): List<StoredChatMessage> {
        val result = ArrayList<StoredChatMessage>()
        readableDatabase.query(
            "messages",
            arrayOf("id", "peer", "body", "outgoing", "delivered", "created_at"),
            "peer = ?", arrayOf(peer), null, null, "created_at ASC",
            limit.coerceIn(1, 2000).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val body = security.decryptAtRest(cursor.getBlob(2))
                result.add(
                    StoredChatMessage(
                        cursor.getString(0), cursor.getString(1), String(body, Charsets.UTF_8),
                        cursor.getInt(3) == 1, cursor.getInt(4) == 1, cursor.getLong(5)
                    )
                )
            }
        }
        return result
    }

    @Synchronized
    fun pending(peer: String): List<StoredChatMessage> =
        history(peer, 2000).filter { it.outgoing && !it.delivered }

    @Synchronized
    fun markDelivered(id: String) {
        writableDatabase.update(
            "messages", ContentValues().apply { put("delivered", 1) },
            "id = ?", arrayOf(id)
        )
    }
}
