package com.flechazo.toolbox.core.util

/**
 * 计算 [android.graphics.BitmapFactory.Options.inSampleSize]。
 *
 * 返回 >=1 的 2 的幂，使解码后最大边 <= [maxSide]。
 *
 * 旧实现的 `while (max / (sample * 2) >= maxSide)` 只会把结果压到
 * [maxSide, 2*maxSide) 区间，一张 4032×3024 的照片在 maxSide=2048 时完全不降采样。
 *
 * 独立成纯函数以便 JVM 单测（[ImageUtils] 本身依赖 Android 类型，无法在 JVM 测试中加载）。
 */
internal fun calculateInSampleSize(width: Int, height: Int, maxSide: Int): Int {
    if (width <= 0 || height <= 0 || maxSide <= 0) return 1
    var sample = 1
    while (maxOf(width, height) / sample > maxSide) sample *= 2
    return sample
}
