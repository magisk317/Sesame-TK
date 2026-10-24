package io.github.magisk317.sesame.hook.simple

import java.util.regex.Pattern

/**
 * 精简版 XPath 解析器 - 仅支持基本查询
 * 支持的语法：
 * - //android.widget.TextView[contains(@text,'xxx')]
 * - //android.widget.TextView[@text='xxx']
 * - //android.widget.TextView[@contentDescription='xxx']
 */
object SimpleXpathParser {
    
    private val XPATH_PATTERN = Pattern.compile(
        "//([\\w.]+)\\[contains\\(@(\\w+),'([^']*)'\\)]",
        Pattern.CASE_INSENSITIVE
    )
    
    private val XPATH_ATTR_PATTERN = Pattern.compile(
        "//([\\w.]+)\\[@(\\w+)='([^']*)']",
        Pattern.CASE_INSENSITIVE
    )
    
    private val TAG_PATTERN = Pattern.compile(
        "//([\\w.]+)",
        Pattern.CASE_INSENSITIVE
    )
    
    /**
     * 解析并执行 XPath 查询
     */
    fun evaluate(root: SimpleViewImage, xpath: String): List<SimpleViewImage> {
        val results = ArrayList<SimpleViewImage>()
        
        val matcher = XPATH_PATTERN.matcher(xpath)
        if (matcher.find()) {
            val className = matcher.group(1)!!
            val attrName = matcher.group(2)!!
            val attrValue = matcher.group(3)!!
            return findElements(root, className, attrName, attrValue, true)
        }
        
        val attrMatcher = XPATH_ATTR_PATTERN.matcher(xpath)
        if (attrMatcher.find()) {
            val className = attrMatcher.group(1)!!
            val attrName = attrMatcher.group(2)!!
            val attrValue = attrMatcher.group(3)!!
            return findElements(root, className, attrName, attrValue, false)
        }
        
        val tagMatcher = TAG_PATTERN.matcher(xpath)
        if (tagMatcher.find()) {
            val className = tagMatcher.group(1)!!
            return findElementsByTag(root, className)
        }
        
        return results
    }
    
    /**
     * 查找匹配指定类名和属性的元素
     */
    private fun findElements(
        root: SimpleViewImage,
        className: String,
        attrName: String,
        attrValue: String,
        contains: Boolean
    ): List<SimpleViewImage> {
        val results = ArrayList<SimpleViewImage>()
        findElementsRecursive(root, className, attrName, attrValue, contains, results)
        return results
    }
    
    /**
     * 递归查找元素
     */
    private fun findElementsRecursive(
        node: SimpleViewImage,
        className: String,
        attrName: String,
        attrValue: String,
        contains: Boolean,
        results: ArrayList<SimpleViewImage>
    ) {
        val nodeType = node.getType()
        
        if (nodeType == className || className == "*") {
            val attrValueActual = node.attribute(attrName)?.toString()
            if (attrValueActual != null) {
                val matches = if (contains) {
                    attrValueActual.contains(attrValue)
                } else {
                    attrValueActual == attrValue
                }
                if (matches) {
                    results.add(node)
                }
            }
        }
        
        for (child in node.children()) {
            findElementsRecursive(child, className, attrName, attrValue, contains, results)
        }
    }
    
    /**
     * 查找指定类名的元素
     */
    private fun findElementsByTag(root: SimpleViewImage, className: String): List<SimpleViewImage> {
        val results = ArrayList<SimpleViewImage>()
        findElementsByTagRecursive(root, className, results)
        return results
    }
    
    /**
     * 递归查找指定类名的元素
     */
    private fun findElementsByTagRecursive(
        node: SimpleViewImage,
        className: String,
        results: ArrayList<SimpleViewImage>
    ) {
        if (node.getType() == className || className == "*") {
            results.add(node)
        }
        
        for (child in node.children()) {
            findElementsByTagRecursive(child, className, results)
        }
    }
}
