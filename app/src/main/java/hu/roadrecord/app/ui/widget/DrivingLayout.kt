package hu.roadrecord.app.ui.widget

/** DP-based host sizing. The card is always 2/3 of the previous 42 + scene-height layout. */
internal object DrivingLayout {
    const val SCENE_WIDTH = 400f
    const val SCENE_HEIGHT = 108f
    const val VAN_WIDTH = 214f
    const val VAN_BASELINE = 87f
    fun mainBandHeight(widthDp: Float): Float = (42f + minOf(widthDp * 145f / 400f, 160f)) * (2f / 3f)
}
