package hu.roadrecord.app.ui.widget

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import hu.roadrecord.app.R

/** Cached photo-derived car + native asphalt. No per-frame bitmap, path or shader allocations. */
internal class DrivingScene(resources: Resources) {
    private val car = DrivingVanArtwork.load(resources)
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val roadPaint = Paint().apply {
        shader = LinearGradient(0f, 82f, 0f, HEIGHT,
            intArrayOf(0xFF7A8793.toInt(), 0xFF596572.toInt(), 0xFF3C4854.toInt()),
            null, Shader.TileMode.CLAMP)
    }
    private val edgePaint = Paint().apply {
        strokeWidth = .8f
        shader = LinearGradient(0f, 0f, WIDTH, 0f,
            intArrayOf(Color.TRANSPARENT, 0x707F868C, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
    }
    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE7E9EB.toInt() }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x65000000 }
    private val carWidth = DrivingLayout.VAN_WIDTH
    private val carHeight = car.body.height * carWidth / car.body.width
    private val carLeft = (WIDTH - carWidth) / 2f
    private val carTop = DrivingLayout.VAN_BASELINE - carHeight
    private val bodyBounds = RectF(carLeft, carTop, carLeft + carWidth, carTop + carHeight)
    private val rearBounds = rimBounds(339f, 680f, 49f)
    private val frontBounds = rimBounds(1340f, 682f, 49f)

    private fun rimBounds(cx: Float, cy: Float, radius: Float): RectF {
        val scale = carWidth / DrivingVanArtwork.CROP_WIDTH
        val x = carLeft + (cx - DrivingVanArtwork.CROP_LEFT) * scale
        val y = carTop + (cy - DrivingVanArtwork.CROP_TOP) * scale
        val r = radius * scale
        return RectF(x - r, y - r, x + r, y + r)
    }

    fun draw(canvas: Canvas, width: Float, height: Float, progress: Float) {
        if (width <= 0f || height <= 0f) return
        val scale = minOf(width / WIDTH, height / HEIGHT)
        canvas.save()
        canvas.translate((width - WIDTH * scale) / 2f, (height - HEIGHT * scale) / 2f)
        canvas.scale(scale, scale)
        val roadLeft = -(width / scale - WIDTH) / 2f
        val roadRight = WIDTH - roadLeft
        canvas.clipRect(roadLeft, 0f, roadRight, HEIGHT)
        canvas.drawRect(roadLeft, 82f, roadRight, HEIGHT, roadPaint)
        canvas.drawLine(roadLeft, 82f, roadRight, 82f, edgePaint)
        canvas.drawLine(roadLeft, HEIGHT - 1f, roadRight, HEIGHT - 1f, edgePaint)
        var x = DrivingMotion.roadOffset(progress) - DrivingMotion.DASH_PITCH + roadLeft
        while (x < roadRight) {
            canvas.drawRect(x, 95f, x + 22f, 97f, dashPaint)
            x += DrivingMotion.DASH_PITCH
        }
        canvas.drawOval(94f, 83f, 306f, 90f, shadowPaint)
        canvas.save()
        canvas.translate(0f, DrivingMotion.suspensionOffset(progress))
        canvas.drawBitmap(car.body, null, bodyBounds, imagePaint)
        drawRim(canvas, car.rearRim, rearBounds, progress)
        drawRim(canvas, car.frontRim, frontBounds, progress)
        canvas.restore()
        canvas.restore()
    }

    private fun drawRim(canvas: Canvas, bitmap: Bitmap, bounds: RectF, progress: Float) {
        canvas.save()
        canvas.rotate(DrivingMotion.wheelDegrees(progress), bounds.centerX(), bounds.centerY())
        canvas.drawBitmap(bitmap, null, bounds, imagePaint)
        canvas.restore()
    }

    companion object {
        const val WIDTH = DrivingLayout.SCENE_WIDTH
        const val HEIGHT = DrivingLayout.SCENE_HEIGHT
    }
}

/**
 * Shared Transit artwork, masked once at load time. The source's neutral background is excluded
 * with its measured outline; both hosts reuse the same body and circular rim bitmaps.
 */
internal object DrivingVanArtwork {
    const val CROP_LEFT = 30f
    const val CROP_TOP = 202f
    const val CROP_WIDTH = 1556f
    private const val CROP_HEIGHT = 564f
    private var cached: Artwork? = null
    data class Artwork(val body: Bitmap, val rearRim: Bitmap, val frontRim: Bitmap)

    @Synchronized
    fun load(resources: Resources): Artwork {
        cached?.let { return it }
        val source = BitmapFactory.decodeResource(resources, R.drawable.driving_transit_source,
            BitmapFactory.Options().apply { inSampleSize = 2; inScaled = false })
        val sourceBounds = RectF(0f, 0f, 1665f, 945f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val outline = Path().apply {
            moveTo(73f, 225f)
            quadTo(75f, 207f, 102f, 206f)
            lineTo(650f, 206f); lineTo(1017f, 214f)
            cubicTo(1090f, 216f, 1136f, 223f, 1186f, 267f)
            cubicTo(1248f, 317f, 1324f, 394f, 1361f, 430f)
            lineTo(1391f, 440f)
            quadTo(1516f, 467f, 1555f, 515f)
            lineTo(1574f, 585f); lineTo(1580f, 614f); lineTo(1580f, 647f)
            lineTo(1576f, 656f); lineTo(1579f, 677f)
            quadTo(1580f, 689f, 1562f, 695f)
            quadTo(1498f, 707f, 1424f, 709f)
            cubicTo(1412f, 741f, 1382f, 762f, 1340f, 762f)
            cubicTo(1297f, 762f, 1263f, 743f, 1250f, 709f)
            lineTo(1230f, 709f); lineTo(1170f, 705f); lineTo(480f, 702f)
            quadTo(458f, 741f, 421f, 739f)
            lineTo(395f, 740f)
            cubicTo(377f, 754f, 358f, 758f, 338f, 758f)
            cubicTo(297f, 758f, 263f, 733f, 255f, 687f)
            lineTo(249f, 687f); lineTo(248f, 706f); lineTo(239f, 708f)
            lineTo(238f, 691f)
            quadTo(100f, 685f, 41f, 674f)
            lineTo(33f, 668f); lineTo(32f, 642f); lineTo(39f, 640f)
            lineTo(40f, 572f); lineTo(42f, 567f); lineTo(44f, 427f)
            lineTo(49f, 424f); lineTo(55f, 332f); lineTo(53f, 328f)
            lineTo(55f, 314f); lineTo(60f, 311f)
            close()
        }
        val body = Bitmap.createBitmap(800, (800 * CROP_HEIGHT / CROP_WIDTH).toInt(), Bitmap.Config.ARGB_8888)
        Canvas(body).apply {
            // One uniform scale preserves the van's tall roof and wheel aspect ratios.
            scale(body.width / CROP_WIDTH, body.width / CROP_WIDTH)
            translate(-CROP_LEFT, -CROP_TOP)
            clipPath(outline)
            drawBitmap(source, null, sourceBounds, paint)
        }
        fun rim(cx: Float, cy: Float): Bitmap {
            val radius = 49f
            val result = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            Canvas(result).apply {
                scale(64f / (radius * 2f), 64f / (radius * 2f))
                translate(radius - cx, radius - cy)
                clipPath(Path().apply { addCircle(cx, cy, radius, Path.Direction.CW) })
                drawBitmap(source, null, sourceBounds, paint)
            }
            return result
        }
        val artwork = Artwork(body, rim(339f, 680f), rim(1340f, 682f))
        source.recycle()
        cached = artwork
        return artwork
    }
}
