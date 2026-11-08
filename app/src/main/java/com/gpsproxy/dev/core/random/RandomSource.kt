package com.gpsproxy.dev.core.random

import kotlin.random.Random

/**
 * 随机来源端口。
 *
 * 模拟定位里有两类"随机"：
 * - 每帧的精度抖动 / 海拔波动（不可复现，用于自然度）；
 * - 位置模糊的确定性偏移（同一锚点 + 同一半径必须永远得到同一结果）。
 *
 * 后者不能用随机源直接生成，但两者都需要可注入，否则相关逻辑无法做
 * 确定性单测（旧实现直接用 `Math.random()`，测试只能断言范围）。
 */
interface RandomSource {

    /** 返回 [from, until) 内的整数。 */
    fun nextInt(from: Int, until: Int): Int

    /** 返回 [0.0, 1.0) 内的双精度随机数。 */
    fun nextDouble(): Double

    /** 返回 [from, until) 内的双精度随机数。 */
    fun nextDouble(from: Double, until: Double): Double = from + nextDouble() * (until - from)

    /** 返回 [0f, 1f) 内的随机数。 */
    fun nextFloat(): Float = nextDouble().toFloat()
}

/**
 * 基于 `kotlin.random.Random` 的默认实现。
 *
 * @param seed 传 null 使用系统熵源；测试可传固定种子获得可复现序列。
 */
class DefaultRandomSource(seed: Long? = null) : RandomSource {

    private val random: Random = if (seed == null) Random.Default else Random(seed)

    override fun nextInt(from: Int, until: Int): Int = random.nextInt(from, until)

    override fun nextDouble(): Double = random.nextDouble()
}
