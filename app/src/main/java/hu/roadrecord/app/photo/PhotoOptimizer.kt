package hu.roadrecord.app.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File

object PhotoOptimizer {
    const val MAX_EDGE = 1200
    const val JPEG_QUALITY = 78

    data class BatchResult(val total:Int,val optimized:Int,val failed:Int,val beforeBytes:Long,val afterBytes:Long){val savedBytes get()=(beforeBytes-afterBytes).coerceAtLeast(0)}

    fun optimizeAll(files:List<File>):BatchResult{
        val existing=files.distinctBy{it.absolutePath}.filter{it.isFile}
        val before=existing.sumOf{it.length()};var optimized=0;var failed=0
        existing.forEach{file->if(optimize(file))optimized++ else failed++}
        return BatchResult(existing.size,optimized,failed,before,existing.sumOf{it.length()})
    }

    fun optimize(file: File): Boolean = runCatching {
        if (!file.isFile || file.length() == 0L) return false
        val orientation = runCatching { ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        val sample = sampleSize(bounds.outWidth, bounds.outHeight, MAX_EDGE)
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.RGB_565 }) ?: error("A fénykép nem olvasható")
        val rotated = rotate(decoded, orientation)
        val scale = minOf(1f, MAX_EDGE.toFloat() / maxOf(rotated.width, rotated.height))
        val output = if (scale < 1f) Bitmap.createScaledBitmap(rotated, (rotated.width * scale).toInt(), (rotated.height * scale).toInt(), true) else rotated
        val temp = File(file.parentFile, "${file.nameWithoutExtension}.optimized.jpg")
        temp.outputStream().buffered().use { require(output.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)) }
        if (output !== rotated) output.recycle()
        if (rotated !== decoded) rotated.recycle()
        decoded.recycle()
        temp.copyTo(file, overwrite = true)
        temp.delete()
        true
    }.getOrDefault(false)

    internal fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= maxEdge) sample *= 2
        return sample
    }

    private fun rotate(source: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return source
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }
}
