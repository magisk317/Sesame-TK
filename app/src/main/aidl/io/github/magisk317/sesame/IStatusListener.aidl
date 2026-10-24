package io.github.magisk317.sesame;

// 状态变化回调接口
interface IStatusListener {
    // 当 Shell 类型变化时调用 (type: "Root", "Shizuku", "None" ...)
    void onStatusChanged(String type);
}