package hu.roadrecord.app.backup

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import hu.roadrecord.app.RoadRecordApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object PhotoBackupManager {
    private const val FILE_NAME = "RoadRecord-fotok.zip"
    private const val WORK_NAME = "roadrecord-photo-backup"
    private val mutex = Mutex()

    suspend fun request(context: Context): Boolean = withContext(Dispatchers.IO) {
        val app = context.applicationContext as RoadRecordApplication
        val settings = app.database.dao().settings() ?: return@withContext false
        if (settings.backupDriveTreeUri.isBlank()) return@withContext false
        if (settings.backupWifiOnly && !isWifi(context)) {
            val request = OneTimeWorkRequestBuilder<PhotoBackupWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()).build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
            return@withContext true
        }
        uploadIfChanged(context)
    }

    private suspend fun uploadIfChanged(context: Context): Boolean = mutex.withLock {
        val app = context.applicationContext as RoadRecordApplication
        val dao = app.database.dao()
        val settings = dao.settings() ?: return@withLock false
        val referenced = dao.placesNow().flatMap { listOfNotNull(it.photoPath, it.entrancePhotoPath) }.distinct().map(::File).filter { it.isFile }
        val signature = signature(referenced)
        if (signature == settings.lastPhotoBackupSignature) return@withLock true
        val temp = File(context.cacheDir, FILE_NAME)
        ZipOutputStream(temp.outputStream().buffered()).use { zip ->
            referenced.sortedBy { it.name }.forEach { file ->
                zip.putNextEntry(ZipEntry("photos/${file.name}"));file.inputStream().use { it.copyTo(zip) };zip.closeEntry()
            }
        }
        val uploaded = runCatching {
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(settings.backupDriveTreeUri)) ?: error("A Drive-mappa nem érhető el")
            val target = tree.listFiles().firstOrNull { it.name == FILE_NAME } ?: tree.createFile("application/zip", FILE_NAME.removeSuffix(".zip")) ?: error("A fotómentés nem hozható létre")
            context.contentResolver.openOutputStream(target.uri, "wt")?.use { out -> temp.inputStream().use { it.copyTo(out) } } ?: error("A fotómentés nem írható")
        }.isSuccess
        temp.delete()
        if (uploaded) dao.markPhotoBackup(signature, System.currentTimeMillis())
        uploaded
    }

    internal fun signature(files: List<File>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        files.sortedBy { it.name }.forEach { file ->
            digest.update(file.name.toByteArray());digest.update(file.length().toString().toByteArray());digest.update(file.lastModified().toString().toByteArray())
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun isWifi(context: Context): Boolean { val cm=context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager;val caps=cm.getNetworkCapabilities(cm.activeNetwork)?:return false;return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) }
}

class PhotoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork() = if (runCatching { PhotoBackupManager.request(applicationContext) }.getOrDefault(false)) Result.success() else Result.retry()
}
