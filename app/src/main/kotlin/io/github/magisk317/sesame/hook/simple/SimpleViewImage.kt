package io.github.magisk317.sesame.hook.simple

import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/**
 * 精简版 ViewImage - 仅保留坐标获取和 XPath 查找功能
 */
class SimpleViewImage(val originView: View) {

    private var parent: SimpleViewImage? = null
    private var indexOfParent: Int = -1
    private var children: Array<SimpleViewImage?>? = null

    companion object {
        const val TEXT = "text"
        const val CONTENT_DESCRIPTION = "contentDescription"
    }

    /**
     * 获取文本内容
     */
    fun getText(): String? {
        return if (originView is TextView) {
            originView.text?.toString()
        } else {
            originView.contentDescription?.toString()
        }
    }

    /**
     * 获取屏幕坐标
     */
    fun locationOnScreen(): IntArray {
        val location = IntArray(2)
        originView.getLocationOnScreen(location)
        return location
    }

    /**
     * 获取X坐标
     */
    fun X(): Int = locationOnScreen()[0]

    /**
     * 获取Y坐标
     */
    fun Y(): Int = locationOnScreen()[1]

    /**
     * 获取子节点数量
     */
    fun childCount(): Int {
        if (originView !is ViewGroup) {
            return 0
        }
        return originView.childCount
    }

    /**
     * 获取指定索引的子节点
     */
    fun childAt(index: Int): SimpleViewImage {
        if (childCount() < 0) {
            throw IllegalStateException("can not parse child node for none ViewGroup object!!")
        }
        if (children == null) {
            children = arrayOfNulls(childCount())
        }
        var viewImage = children!![index]
        if (viewImage != null) {
            return viewImage
        }
        val viewGroup = originView as ViewGroup
        viewImage = SimpleViewImage(viewGroup.getChildAt(index))
        viewImage.parent = this
        viewImage.indexOfParent = index
        children!![index] = viewImage
        return viewImage
    }

    /**
     * 获取父节点
     */
    fun parentNode(): SimpleViewImage? = parent

    /**
     * 获取指定层级的父节点
     */
    fun parentNode(n: Int): SimpleViewImage? {
        if (n == 1) {
            return parentNode()
        }
        return parentNode()?.parentNode(n - 1)
    }

    /**
     * 获取所有子节点
     */
    fun children(): List<SimpleViewImage> {
        if (childCount() <= 0) {
            return emptyList()
        }
        val ret = ArrayList<SimpleViewImage>(childCount())
        for (i in 0 until childCount()) {
            ret.add(childAt(i))
        }
        return ret
    }

    /**
     * 根据XPath查找单个元素
     */
    fun xpath2One(xpath: String): SimpleViewImage? {
        val results = SimpleXpathParser.evaluate(this, xpath)
        if (results.isNotEmpty()) {
            return results[0]
        }
        return SimplePageManager.tryGetTopView(xpath)
    }

    /**
     * 获取视图类型
     */
    fun getType(): String = originView.javaClass.simpleName

    /**
     * 获取属性值
     */
    fun attribute(key: String): Any? {
        return when (key) {
            TEXT -> getText()
            CONTENT_DESCRIPTION -> originView.contentDescription?.toString()
            else -> null
        }
    }
}
