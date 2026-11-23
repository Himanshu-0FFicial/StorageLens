package com.example.data

import android.content.Context
import com.example.data.local.SettingsDataStore
import com.example.data.local.StorageSnapshotStore
import com.example.data.repository.StorageRepository

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val settingsDataStore: SettingsDataStore by lazy { SettingsDataStore(appContext) }
    val snapshotStore: StorageSnapshotStore by lazy { StorageSnapshotStore(appContext) }
    val storageRepository: StorageRepository by lazy { StorageRepository(appContext) }
}
