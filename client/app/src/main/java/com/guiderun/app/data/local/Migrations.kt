package com.guiderun.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v2 → v3：run_session_stats 增加 isPaused 列（本机自动暂停语义，仅本地）。
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE run_session_stats " +
                "ADD COLUMN isPaused INTEGER NOT NULL DEFAULT 0"
        )
    }
}
