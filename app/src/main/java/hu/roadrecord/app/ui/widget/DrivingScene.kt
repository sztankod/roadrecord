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
    private val car = DrivingCarArtwork.load(resources)
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val roadPaint = Paint().apply {
        shader = LinearGradient(0f, 108f, 0f, HEIGHT,
            intArrayOf(0xFF30343A.toInt(), 0xFF1C2025.toInt(), 0xFF14181D.toInt()),
            null, Shader.TileMode.CLAMP)
    }
    private val edgePaint = Paint().apply {
        strokeWidth = .8f
        shader = LinearGradient(0f, 0f, WIDTH, 0f,
            intArrayOf(Color.TRANSPARENT, 0x707F868C, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
    }
    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE7E9EB.toInt() }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x65000000 }
    private val carWidth = 282f
    private val carHeight = car.body.height * carWidth / car.body.width
    private val carLeft = (WIDTH - carWidth) / 2f
    private val carTop = 116f - carHeight
    private val bodyBounds = RectF(carLeft, carTop, carLeft + carWidth, carTop + carHeight)
    private val rearBounds = rimBounds(409f, 612f, 89f)
    private val frontBounds = rimBounds(1382f, 612f, 89f)

    private fun rimBounds(cx: Float, cy: Float, radius: Float): RectF {
        val scale = carWidth / DrivingCarArtwork.CROP_WIDTH
        val x = carLeft + (cx - DrivingCarArtwork.CROP_LEFT) * scale
        val y = carTop + (cy - DrivingCarArtwork.CROP_TOP) * scale
        val r = radius * scale
        return RectF(x - r, y - r, x + r, y + r)
    }

    fun draw(canvas: Canvas, width: Float, height: Float, progress: Float) {
        if (width <= 0f || height <= 0f) return
        val scale = minOf(width / WIDTH, height / HEIGHT)
        canvas.save()
        canvas.translate((width - WIDTH * scale) / 2f, (height - HEIGHT * scale) / 2f)
        canvas.scale(scale, scale)
        canvas.clipRect(0f, 0f, WIDTH, HEIGHT)
        canvas.drawRect(0f, 108f, WIDTH, HEIGHT, roadPaint)
        canvas.drawLine(0f, 108f, WIDTH, 108f, edgePaint)
        canvas.drawLine(0f, HEIGHT - 1f, WIDTH, HEIGHT - 1f, edgePaint)
        var x = DrivingMotion.roadOffset(progress) - DrivingMotion.DASH_PITCH
        while (x < WIDTH) {
            canvas.drawRect(x, 126f, x + 22f, 128f, dashPaint)
            x += DrivingMotion.DASH_PITCH
        }
        canvas.drawOval(65f, 110f, 345f, 119f, shadowPaint)
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
        const val WIDTH = 400f
        const val HEIGHT = 145f
    }
}

/**
 * ImageGen preserved the car but baked in a checkerboard. Mask once on load using its measured
 * outline. The immutable result is shared across all views (~1 MB), never regenerated per frame.
 */
internal object DrivingCarArtwork {
    const val CROP_LEFT = 80f
    const val CROP_TOP = 210f
    const val CROP_WIDTH = 1590f
    private const val CROP_HEIGHT = 524f
    private var cached: Artwork? = null
    data class Artwork(val body: Bitmap, val rearRim: Bitmap, val frontRim: Bitmap)

    @Synchronized
    fun load(resources: Resources): Artwork {
        cached?.let { return it }
        val source = BitmapFactory.decodeResource(resources, R.drawable.driving_car_source,
            BitmapFactory.Options().apply { inSampleSize = 2; inScaled = false })
        val sourceBounds = RectF(0f, 0f, 1774f, 887f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val outline = Path().apply {
            moveTo(119f, 353f)
            lineTo(251f, 335f)
            cubicTo(348f, 304f, 408f, 272f, 478f, 247f)
            lineTo(511f, 238f); lineTo(514f, 229f); quadTo(530f, 225f, 545f, 230f)
            cubicTo(659f, 208f, 772f, 210f, 868f, 217f)
            cubicTo(1027f, 224f, 1130f, 299f, 1246f, 370f)
            lineTo(1272f, 368f); lineTo(1297f, 377f)
            cubicTo(1458f, 395f, 1592f, 426f, 1637f, 474f)
            lineTo(1663f, 529f); lineTo(1664f, 570f); lineTo(1658f, 591f)
            lineTo(1663f, 615f); lineTo(1659f, 643f)
            quadTo(1591f, 662f, 1501f, 668f)
            cubicTo(1482f, 709f, 1439f, 728f, 1382f, 728f)
            cubicTo(1321f, 728f, 1279f, 703f, 1260f, 666f)
            lineTo(536f, 664f)
            cubicTo(514f, 707f, 467f, 728f, 408f, 728f)
            cubicTo(349f, 728f, 302f, 701f, 289f, 648f)
            quadTo(176f, 636f, 111f, 619f)
            quadTo(93f, 608f, 85f, 580f)
            lineTo(90f, 563f); lineTo(88f, 510f); lineTo(109f, 450f)
            lineTo(108f, 405f); lineTo(115f, 386f); close()
        }
        val body = Bitmap.createBitmap(800, (800 * CROP_HEIGHT / CROP_WIDTH).toInt(), Bitmap.Config.ARGB_8888)
        Canvas(body).apply {
            scale(body.width / CROP_WIDTH, body.height / CROP_HEIGHT)
            translate(-CROP_LEFT, -CROP_TOP)
            clipPath(outline)
            drawBitmap(source, null, sourceBounds, paint)
        }
        fun rim(cx: Float, cy: Float): Bitmap {
            val radius = 89f
            val result = Bitmap.createBitmap(92, 92, Bitmap.Config.ARGB_8888)
            Canvas(result).apply {
                scale(92f / (radius * 2f), 92f / (radius * 2f))
                translate(radius - cx, radius - cy)
                clipPath(Path().apply { addCircle(cx, cy, radius, Path.Direction.CW) })
                drawBitmap(source, null, sourceBounds, paint)
            }
            return result
        }
        val artwork = Artwork(body, rim(409f, 612f), rim(1382f, 612f))
        source.recycle()
        cached = artwork
        return artwork
    }
}
