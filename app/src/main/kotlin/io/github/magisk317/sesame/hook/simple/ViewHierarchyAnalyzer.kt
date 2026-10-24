package io.github.magisk317.sesame.hook.simple

import io.github.magisk317.sesame.util.StructuredLog as Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

/**
 * 用于分析和遍历视图层次结构的实用工具对象。
 */
object ViewHierarchyAnalyzer {

    private const val TAG = "ViewHierarchyAnalyzer"

    /**
     * 递归记录视图层次结构以供调试。
     * @param view 开始分析的根视图。
     * @param depth 当前递归深度，用于格式化。
     */
    fun logViewHierarchy(view: View, depth: Int) {
        val indent = "  ".repeat(depth)
        val className = view.javaClass.name
        val resourceId = try {
            "ID: ${view.resources.getResourceEntryName(view.id)}"
        } catch (_: Exception) {
            "ID: (无)"
        }
        val location = IntArray(2).also { view.getLocationOnScreen(it) }
        val info = "loc=[${location[0]},${location[1]}] size=[${view.width}x${view.height}] visible=${view.isShown} enabled=${view.isEnabled}"
        var textInfo = ""
        if (view is TextView) {
            textInfo = "text='${view.text}' desc='${view.contentDescription}'"
        }

        Log.d(TAG, "$indent- $className, $resourceId, $info $textInfo")

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                logViewHierarchy(child, depth + 1)
            }
        }
    }

    /**
     * 通过从锚点文本视图遍历，查找实际的滑块按钮视图。
     * 它在第一次尝试时记录层次结构以供调试。
     * @param slideTextView "滑动验证"文本视图的 SimpleViewImage 包装器。
     * @return 找到的滑块视图，如果未找到则返回 null。
     */
    fun findActualSliderView(slideTextView: SimpleViewImage): View? {
        val parentView = slideTextView.originView.parent as? ViewGroup ?: return null

        logViewHierarchy(parentView, 0)

        // 在父容器内开始递归搜索滑块视图。
        val slider = findSliderInGroup(parentView)
        if (slider != null) {
            val loc = IntArray(2).also { slider.getLocationOnScreen(it) }
            Log.d(TAG, "找到可拖动滑块视图: ${slider.javaClass.name} 位置=[${loc[0]},${loc[1]}]")
        } else {
            Log.e(TAG, "无法找到实际的滑块视图。请检查上面的层次结构日志。")
        }
        return slider
    }

    /**
     * 在 ViewGroup 中递归搜索候选滑块视图。
     * 策略是找到一个可见的 ImageView（图标）并返回其父视图（实际可拖动的视图）。
     * @param viewGroup 要搜索的组。
     * @return 找到的滑块视图，或 null。
     */
    private fun findSliderInGroup(viewGroup: ViewGroup): View? {
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)

            // 可拖动部分是可见 ImageView 图标的父视图。
            if (child is ImageView && child.isShown) {
                Log.d(TAG, "找到滑块图标 (ImageView)。返回其父视图作为可拖动视图。")
                return child.parent as? View
            }

            // 如果未找到，递归到子 ViewGroup 中。
            if (child is ViewGroup) {
                val foundInChild = findSliderInGroup(child)
                if (foundInChild != null) {
                    return foundInChild
                }
            }
        }
        return null
    }
}
