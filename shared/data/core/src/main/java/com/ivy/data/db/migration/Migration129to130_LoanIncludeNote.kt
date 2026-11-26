package com.ivy.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Migration129to130_LoanIncludeNote : Migration(129, 130) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE loans ADD COLUMN note TEXT")
        // The fork's committed 130.json also has the transaction location columns,
        // and the 130 -> 131 auto-migration is empty, so add them here.
        db.execSQL("ALTER TABLE transactions ADD COLUMN locationLat REAL")
        db.execSQL("ALTER TABLE transactions ADD COLUMN locationLng REAL")
        db.execSQL("ALTER TABLE transactions ADD COLUMN locationName TEXT")
    }
}