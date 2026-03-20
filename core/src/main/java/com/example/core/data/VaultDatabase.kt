package com.example.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.core.data.dao.CredentialDao
import com.example.core.data.entity.EncryptedCredentialEntity
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(entities = [EncryptedCredentialEntity::class], version = 1)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun credentialDao(): CredentialDao

    companion object {
        private const val DATABASE_NAME = "vault.db"

        fun build(context: Context, passphrase: ByteArray): VaultDatabase {
            System.loadLibrary("sqlcipher")
            val factory = SupportOpenHelperFactory(passphrase)
            
            return Room.databaseBuilder(context, VaultDatabase::class.java, DATABASE_NAME)
                .openHelperFactory(factory)
                .build()
        }
    }
}
