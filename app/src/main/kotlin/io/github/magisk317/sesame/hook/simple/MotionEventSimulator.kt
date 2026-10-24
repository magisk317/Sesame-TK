package io.github.magisk317.sesame.hook.simple

import io.github.magisk317.sesame.util.TimeFormatter
import android.os.SystemClock
import io.github.magisk317.sesame.util.StructuredLog as Log
import android.view.MotionEvent
import android.view.View
import io.github.magisk317.sesame.util.GlobalThreadPools
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * 模拟系统级 MotionEvent 的工具类.
 * 用于执行如滑动等自动化操作.
 */
object MotionEventSimulator {

    private const val TAG = "MotionEventSimulator"

    /**
     * 异步模拟一个从起点到终点的滑动操作.
     *
     * @param view 要在其上执行滑动操作的视图 (通常很滑块本身).
     * @param startX 滑动的屏幕绝对 X 坐标起点.
     * @param startY 滑动的屏幕绝对 Y 坐标起点.
     * @param endX 滑动的屏幕绝对 X 坐标终点.
     * @param endY 滑动的屏幕绝对 Y 坐标终点.
     * @param duration 滑动动画的总时长 (毫秒).
     */
    fun simulateSwipe(
        view: View,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        duration: Long = 800L // 模拟一个比较自然的滑动时长
    ) {
        // 确保所有UI操作都在主线程执行
        GlobalThreadPools.execute(context = Dispatchers.Main) {
            Log.i(TAG, "准备在视图 ${view.javaClass.simpleName} 上模拟滑动")
            Log.d(TAG, "从 ($startX, $startY) -> ($endX, $endY)，持续时间: ${TimeFormatter.formatDuration(duration)}")
            if (!view.isShown || !view.isEnabled) {
                Log.e(TAG, "滑动失败: 目标视图不可见或未启用.")
                return@execute
            }
            val downTime = SystemClock.uptimeMillis()
            try {
                // 1. 发送 ACTION_DOWN 事件，标志着手指按下
                dispatchTouchEvent(view, MotionEvent.ACTION_DOWN, startX, startY, downTime, downTime)
                delay(Random.nextLong(30, 80)) // 按下后短暂延迟，更像人
                // 2. 模拟 ACTION_MOVE 事件序列，构造滑动轨迹
                val steps = 15 // 将滑动轨迹分为 15 步
                val stepDuration = (duration - 100) / steps
                val xStep = (endX - startX) / steps
                val yStep = (endY - startY) / steps
                for (i in 1..steps) {
                    val currentX = startX + xStep * i + Random.nextInt(-3, 4) // 增加微小随机抖动
                    val currentY = startY + yStep * i + Random.nextInt(-2, 3)
                    val eventTime = downTime + (stepDuration * i)
                    dispatchTouchEvent(view, MotionEvent.ACTION_MOVE, currentX, currentY, downTime, eventTime)
                    delay(stepDuration)
                }
                // 3. 发送 ACTION_UP 事件，标志着手指抬起
                val upTime = downTime + duration
                dispatchTouchEvent(view, MotionEvent.ACTION_UP, endX, endY, downTime, upTime)
                Log.i(TAG, "模拟滑动事件序列发送完毕.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "派发触摸事件时发生异常", e)
            }
        }
    }

    /**
     * 辅助函数，用于创建和派发 MotionEvent.
     */
    private fun dispatchTouchEvent(
        view: View,
        action: Int,
        x: Float,
        y: Float,
        downTime: Long,
        eventTime: Long
    ) {
        val properties = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_FINGER
        })
        val cords = arrayOf(MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = 1f
            size = 1f
        })
        val event = MotionEvent.obtain(downTime, eventTime, action, 1, properties, cords, 0, 0, 1f, 1f, 0, 0, 0, 0)
        view.dispatchTouchEvent(event)
        event.recycle()
    }
}
