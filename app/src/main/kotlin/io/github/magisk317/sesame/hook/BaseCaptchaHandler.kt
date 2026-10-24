package io.github.magisk317.sesame.hook

import android.annotation.SuppressLint
import android.app.Activity
import io.github.magisk317.sesame.hook.simple.MotionEventSimulator
import io.github.magisk317.sesame.hook.simple.SimplePageManager
import io.github.magisk317.sesame.hook.simple.SimpleViewImage
import io.github.magisk317.sesame.hook.simple.ViewHierarchyAnalyzer
import io.github.magisk317.sesame.util.StructuredLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlin.random.Random

/**
 * 滑动坐标四元组，用于封装滑动起点和终点坐标。
 */
data class SlideCoordinates(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float
)

/**
 * 验证码处理程序的基类，提供处理滑动验证码的通用逻辑。
 * 该类专门用于处理目标应用验证页面上的滑动验证码。
 */
abstract class BaseCaptchaHandler {

    companion object {
        private const val TAG = "CaptchaHandler"

        // 滑动参数配置
        private const val SLIDE_START_OFFSET = 25 // 滑动起始位置偏移量（像素）
        private const val SLIDE_END_MARGIN = 20   // 滑动结束位置距离右侧的边距（像素）
        private const val SLIDE_DURATION_MIN = 500L // 最小滑动持续时间
        private const val SLIDE_DURATION_MAX = 600L // 最大滑动持续时间

        // 滑动后延迟检查是否成功
        private const val POST_SLIDE_CHECK_DELAY_MS = 2000L

        // 查找滑动验证文本的 XPath
        private const val SLIDE_VERIFY_TEXT_XPATH = "//TextView[contains(@text,'向右滑动验证')]"

        // 并发控制，防止多个处理程序同时运行
        private val captchaProcessingMutex = Mutex()
    }

    /**
     * 获取滑动路径配置键。
     * @return 用于存储滑动路径的键。
     */
    protected abstract fun getSlidePathKey(): String

    /**
     * 处理当前 Activity 中的验证码。
     * @param activity 当前 Activity 实例。
     * @param root 根视图图像。
     * @return 如果验证码处理成功返回 true，否则返回 false。
     */
    open suspend fun handleActivity(activity: Activity, root: SimpleViewImage): Boolean {
        return try {
            if (AccessDeniedRecovery.handle(activity, root)) return true
            handleSlideCaptcha(activity)
        } catch (e: Exception) {
            StructuredLog.error(TAG, "处理验证码页面时发生异常: ${e.stackTraceToString()}")
            false
        }
    }

    @SuppressLint("SuspiciousIndentation")
    private suspend fun handleSlideCaptcha(activity: Activity): Boolean {
        if (!captchaProcessingMutex.tryLock()) {
            return true // 返回 true 告知上层已处理，避免重试
        }
        try {
            val slideTextInDialog = findSlideTextInDialog() ?: run {
               // StructuredLog.captcha(TAG, "未找到滑动验证文本，跳过处理")
                return false // 未找到关键视图，返回 false 让其他处理器尝试
            }
            StructuredLog.record(TAG, "发现滑动验证文本: ${slideTextInDialog.getText()}")
            delay(500L) // 等待界面稳定
            // 执行滑动验证
            return performSlideAndVerify(activity, slideTextInDialog)
        } catch (e: Exception) {
            StructuredLog.record(TAG, "处理滑动验证码时发生错误: ${e.stackTraceToString()}")
            return false
        } finally {
            captchaProcessingMutex.unlock()
        }
    }

    /**
     * 执行滑动操作并验证结果。
     * @param activity 当前的 Activity。
     * @param slideTextView "向右滑动验证"文本的视图图像，作为查找滑块的锚点。
     * @return 如果验证码成功解除返回 true，否则返回 false。
     */
    private suspend fun performSlideAndVerify(activity: Activity, slideTextView: SimpleViewImage): Boolean {
        val sliderView = ViewHierarchyAnalyzer.findActualSliderView(slideTextView) ?: run {
            StructuredLog.record(TAG, "未能找到可操作的滑块视图，滑动无法执行。")
            return false
        }
        
        // 计算滑动坐标
        val (startX, startY, endX, endY) = calculateSlideCoordinates(activity, sliderView) ?: run {
            StructuredLog.record(TAG, "计算滑动坐标失败，滑动无法执行。")
            return false
        }

        // 随机化滑动持续时间，模拟更自然的行为
        val slideDuration = Random.nextLong(SLIDE_DURATION_MIN, SLIDE_DURATION_MAX + 1)

        // 执行滑动
        MotionEventSimulator.simulateSwipe(
            view = sliderView,
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
            duration = slideDuration
        )

        delay(POST_SLIDE_CHECK_DELAY_MS)
        return if (checkCaptchaTextGone()) {
            StructuredLog.record(TAG, "验证码文本已消失，滑动成功。")
            true
        } else {
            StructuredLog.record(TAG, "验证码文本仍然存在，滑动可能失败。")
            false
        }
    }

    /**
     * 计算滑动验证码的坐标参数。
     * 
     * @param activity 当前Activity，用于获取屏幕信息
     * @param sliderView 滑块视图
     * @return 包含(startX, startY, endX, endY)的四元组，如果计算失败返回null
     */
    private fun calculateSlideCoordinates(activity: Activity, sliderView: android.view.View): SlideCoordinates? {
        // 获取滑动区域的整体容器（滑块的父容器）
        val slideContainer = sliderView.parent as? android.view.ViewGroup ?: run {
          //  StructuredLog.captcha(TAG, "未能找到滑块容器")
            return null
        }
        
        // 获取屏幕尺寸信息
        val displayMetrics = activity.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        // 计算滑动区域的边界
        val containerLocation = IntArray(2)
        slideContainer.getLocationOnScreen(containerLocation)
        val containerX = containerLocation[0]
        val containerY = containerLocation[1]
        val containerWidth = slideContainer.width
        val containerHeight = slideContainer.height

        // 计算滑块位置
        val sliderLocation = IntArray(2)
        sliderView.getLocationOnScreen(sliderLocation)
        val sliderX = sliderLocation[0]
        val sliderY = sliderLocation[1]
        val sliderWidth = sliderView.width
        val sliderHeight = sliderView.height

        // 计算滑动起点（滑块中心稍微偏右，模拟手指按住滑块）
        val startX = sliderX + sliderWidth / 2f + SLIDE_START_OFFSET.toFloat() + Random.nextInt(-3, 4) // 添加随机偏移
        val startY = sliderY + sliderHeight / 2f + Random.nextInt(-2, 3)

        // 计算滑动终点
        val containerRightEdge = containerX + containerWidth
        val maxEndX = screenWidth - 50f // 距离屏幕右边缘50像素
        
        // 计算理想的滑动终点（容器右端减去边距）
        var endX = containerRightEdge - SLIDE_END_MARGIN.toFloat() + Random.nextInt(-5, 6) // 添加随机偏移
        
        // 确保滑动终点不超过屏幕边界
        if (endX > maxEndX) {
            endX = maxEndX
            StructuredLog.record(TAG, "调整滑动终点以适配屏幕边界")
        }
        // 确保滑动距离足够（至少滑块宽度的1.5倍）
        val minSlideDistance = sliderWidth * 1.5f
        val actualSlideDistance = endX - startX
        if (actualSlideDistance < minSlideDistance) {
            endX = startX + minSlideDistance + Random.nextInt(-3, 4) // 添加随机偏移
            StructuredLog.record(TAG, "调整滑动距离至最小要求: ${minSlideDistance}px")
        }
        val endY = startY // 保持水平滑动
        // 输出详细的调试信息
        StructuredLog.record(TAG, "屏幕信息: 尺寸=${screenWidth}x$screenHeight")
        StructuredLog.record(TAG, "滑动区域信息: 容器位置=[$containerX,$containerY], 尺寸=${containerWidth}x$containerHeight")
        StructuredLog.record(TAG, "滑块信息: 位置=[$sliderX,$sliderY], 尺寸=${sliderWidth}x${sliderHeight}")
        StructuredLog.record(TAG, "计算结果: 起点=[$startX,$startY], 终点=[$endX,$endY], 滑动距离=${endX-startX}px")

        ApplicationHook.sendBroadcastShell(
            getSlidePathKey(),
            "input swipe " +
                "${startX.toInt()} ${startY.toInt()} " +
                "${endX.toInt()} ${endY.toInt()} " +
                Random.nextLong(SLIDE_DURATION_MIN, SLIDE_DURATION_MAX + 1)
        )
        return SlideCoordinates(startX, startY, endX, endY)
    }

    /**
     * 检查验证码验证文本是否已从视图中消失。
     * @return 如果文本已消失返回 true，如果仍然存在返回 false。
     */
    private fun checkCaptchaTextGone(): Boolean {
        val slideTextInDialog = findSlideTextInDialog()
        return if (slideTextInDialog == null) {
            StructuredLog.record(TAG, "验证码文本已消失 (在对话框中未找到)。")
            true
        } else {
            StructuredLog.record(TAG, "验证码文本仍然存在 (在对话框中找到)。")
            false
        }
    }

    /**
     * 在对话框视图中查找滑动验证文本。
     * @return 如果找到则返回文本视图的 SimpleViewImage，否则返回 null。
     */
    private fun findSlideTextInDialog(): SimpleViewImage? {
        return try {
          //  StructuredLog.captcha(TAG, "尝试通过 XPath 查找滑动验证文本: $SLIDE_VERIFY_TEXT_XPATH")
            SimplePageManager.tryGetTopView(SLIDE_VERIFY_TEXT_XPATH)
        } catch (e: Exception) {
            StructuredLog.record(TAG, "由于异常导致查找验证码文本失败: ${e.stackTraceToString()}")
            null
        }
    }
}
