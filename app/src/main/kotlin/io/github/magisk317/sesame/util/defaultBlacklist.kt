package io.github.magisk317.sesame.util

/**
 * 默认黑名单列表（包含常见无法完成的任务）
 *
 * 使用方法：
 * 1. 检查任务是否在黑名单中（模糊匹配）：
 *    if (TaskBlacklist.isTaskInBlacklist(taskInfo)) { 跳过任务 }
 *
 * 3. 根据错误码自动添加任务到黑名单：
 *    TaskBlacklist.autoAddToBlacklist(taskId, taskTitle, errorCode)
 *
 * 4. 手动添加/移除任务：
 *    TaskBlacklist.addToBlacklist(taskId)
 *    TaskBlacklist.removeFromBlacklist(taskId)
 */
val defaultBlacklist = setOf(
    // 芝麻信用任务
    "每日施肥领水果",           // 需要淘宝操作
    "坚持种水果",              // 需要淘宝操作
    "坚持去玩休闲小游戏",       // 需要游戏操作
    "去AQapp提问",            // 需要下载APP
    "去AQ提问",               // 需要下载APP
    "坚持看直播领福利",        // 需要淘宝直播
    "去淘金币逛一逛",          // 需要淘宝操作
    "坚持攒保障金",            // 参数错误：promiseActivityExtCheck
    "芝麻租赁下单得芝麻粒",     // 需要租赁操作
    "去玩小游戏",              // 参数错误：promiseActivityExtCheck
    "浏览租赁商家小程序",       // 需要小程序操作
    "订阅小组件",              // 参数错误：promiseActivityExtCheck
    "租1笔图书",               // 参数错误：promiseActivityExtCheck
    "去订阅芝麻小组件",         // 参数错误：promiseActivityExtCheck
    "逛租赁会场",              // 操作太频繁：OP_REPEAT_CHECK
    "去花呗翻卡",               // 操作太频繁：OP_REPEAT_CHECK
    "领视频红包",              // 操作太频繁：OP_REPEAT_CHECK
    "领点餐优惠",               // 操作太频繁：OP_REPEAT_CHECK
    "去抛竿钓鱼",              // 操作太频繁：OP_REPEAT_CHECK
    "逛商家积分兑好物",         // 操作太频繁：OP_REPEAT_CHECK
    "坚持浏览乐游记",           // 操作太频繁：OP_REPEAT_CHECK
    "去体验先用后付",           // 操作太频繁：OP_REPEAT_CHECK
    "0.1元起租会员攒粒",        // 参数错误：ILLEGAL_ARGUMENT
    "完成旧衣回收得现金",        // 参数错误：ILLEGAL_ARGUMENT
    "坚持刷视频赚福利",         // 存在进行中的生活记录：PROMISE_HAS_PROCESSING_TEMPLATE
    "去领目标应用积分",           // 存在进行中的生活记录：PROMISE_HAS_PROCESSING_TEMPLATE
    "去参与花呗活动",           // 存在进行中的生活记录：PROMISE_HAS_PROCESSING_TEMPLATE
    "逛网商领福利金",           // 存在进行中的生活记录：PROMISE_HAS_PROCESSING_TEMPLATE
    "去浏览租赁大促会场",        // 存在进行中的生活记录：PROMISE_HAS_PROCESSING_TEMPLATE
    "逛一逛免费领点餐优惠",      // 存在进行中的生活记录：PROMISE_HAS_PROCESSING_TEMPLATE

    // processAlchemyTasks方法中常见任务  芝麻炼金
    "每日施肥",
    "休闲小游戏",
    "AQApp",
    "订阅炼金",
    "租游戏账号",
    "芝麻大表鸽",
    "坚持签到",
    "坚持去玩休闲小游戏",     // 参数错误：ILLEGAL_ARGUMENT
    "租游戏账号得芝麻粒",      // 参数错误：ILLEGAL_ARGUMENT

    // 芝麻炼金：已在真机逐项验证，通用 PromiseRpc pushActivity 返回
    // ILLEGAL_ARGUMENT/promiseActivityExtCheck；保留为失败标记，避免重复请求。
    "hjwf_eduka_renwu",
    "hjwf_xiangjiangshikaipao_renwu",
    "hjwf_wanxiaoyouxi30s_renwu",
    "hjwf_youxi_renwu_sanfang",
    "hjwf_langmancanting_renwu",
    "hjwf_fengkuangshuishijie_renwu",
    "hjwf_myzy_gyxj_erfang",
    "hjwf_tbbbnc_shifei_sanfang",
    "hjwf_tbxiaoxiaole_renwu_sanfang",
    "hjwf_kuaishou_shuangduan_sanfang",
    "alchemy_check_in_subscribe_task",

    // 农场任务。2026-08-09 实测：以下任务对应 finishTask 返回失败，保留为失败标记。
    "ORCHARD_NORMAL_KUAISHOU_MAX",  // 2026-08-09 code=400000040，不支持RPC调用
    "ORCHARD_NORMAL_DIAOYU1",       // 2026-08-09 未发现可用finishTask配置
    "ZHUFANG3IN1",                  // 添加农场小组件并访问
    "12172",                        // 逛助农好货得肥料
    "12173",                        // 买好货
    "70000",                        // 逛好物最高得1500肥料（XLIGHT）
    "TOUTIAO",                      // 逛一逛今日头条
    "ORCHARD_NORMAL_ZADAN10_3000",  // 2026-08-09 code=400000040，不支持RPC调用
    "TAOBAO2",                      // 逛一逛闲鱼
    "TAOBAO",                       // 2026-08-09 code=400000040，不支持RPC调用
    "ORCHARD_NORMAL_JIUYIHUISHOU_VISIT", // 旧衣服回收
    "ORCHARD_NORMAL_SHOUJISHUMAHUISHOU", // 数码回收
    "ORCHARD_NORMAL_TAB3_ZHIFA",    // 2026-08-09 code=400000040，不支持RPC调用
    "ORCHARD_NORMAL_AQ_XIAZAI",     // 下载AQ
    
    // 庄园任务。2026-08-09 实测 doFarmTask 返回100但状态仍为TODO，不能视为已完成。
    "HEART_DONATION_ADVANCED_FOOD_V2",  // 2026-08-09 resultCode=100，状态TODO，需真实捐赠/领取流程
    "HEART_DONATE",  // 2026-08-09 resultCode=100，状态TODO，需真实捐赠/领取流程
    "SHANGOU_xiadan",  // 2026-08-09 resultCode=100，状态TODO，需真实下单流程
    "OFFLINE_PAY",  // 2026-08-09 resultCode=100，状态TODO，需真实线下付款
    "ONLINE_PAY",  // 2026-08-09 resultCode=100，状态TODO，需真实线上付款
    "HUABEI_MAP_180", // 2026-08-09 resultCode=100，状态TODO，需真实花呗付款

    // 农场任务。2026-08-09 finishTask 实测失败，保留黑名单，避免重复请求。
    "ORCHARD_NCLY_GAME_IAA", // code=400000040，不支持RPC调用
    "ORCHARD_NCLY_GAME_SW", // code=400000040，不支持RPC调用
    "ANTFARM_ORCHARD_P2P_SHARER", // code=400000040，不支持RPC调用
    "ORCHARD_TEAM_SPREAD_PERSON", // code=400000040，不支持RPC调用
    "ANTFARM_ORCHARD_NORMAL_CAINIAO_DUAN", // code=400000040，不支持RPC调用
    "ORCHARD_NORMAL_XIANYU_DUAN", // code=400000040，不支持RPC调用
    "ORCHARD_NORMAL_GAODE_VISIT", // code=400000040，不支持RPC调用
    "ORCHARD_NORMAL_XIANXIAZHIFU100", // code=400000040，不支持RPC调用
    "ANTFARM_COLLECT_MANURE", // code=400000001，任务全局配置不存在
    "ANTFOREST_DEFOLIATION", // code=400000001，任务全局配置不存在
    "12739", // code=400000001，任务全局配置不存在
    "44797", // code=400000001，任务全局配置不存在

    // 芝麻粒 _zhuanhua 转化任务：pushActivity 返回 ILLEGAL_ARGUMENT，
    // 需真实游戏内操作（通关/转化），RPC 无法自动完成。2026-08-20 探测确认。
    "hjwf_dgls_zhuanhua",           // 去玩打个螺丝
    "hjwf_zgwhx_zhuanhua",          // 去玩这关我很行
    "hjwf_dageluosi_zhuanhua",      // 去玩打个螺丝（二）
    "hjwf_nuolegejian_zhuanhua",    // 去玩挪了个箭
    "zml_nuolegejian_zhuanhua",     // 去玩挪了个箭（主页面）
    "zml_dageluosi_zhuanhua",       // 去玩打个螺丝（主页面）
    "zml_qingyunjuezhifumo_liulan60s", // 玩60秒青云诀之伏魔（未验证 _liulan60s，暂跳）
    "hjwf_duansousuo_zulin_erfang", // 搜索芝麻租赁出片神器
    "zml_duansousuo_zulin_erfang",  // 搜索芝麻租赁出片神器（主页面）
    "zml_zfbyouxi_erfang",          // 去玩小游戏（Promise 无模板配置）
    "zml_eduka_renwu",              // 去点击先用后付商品
    "hjwf_wanxiaoyouxi30s_renwu",   // 玩任意游戏30秒（已在上方，此处冗余保险）

    // 森林任务 - 不支持RPC调用的任务
    "ZHRW_AQapp_202512",    // 去蚂蚁阿福健康问答
    "LSHS_huisho20_202508",  // 完成旧衣回收得能量
    "TEST_LEAF_TASK",        // 逛农场得落叶肥料
    "SHARETASK_NEW",         // 邀请1位好友助力
    "YUSHU_202511",          // 单种榆树，年年有榆
    "KTKZ_YS202511",         // 一起组团种榆树
    "mokuai_senlin_hlz",     // 去玩一玩得活力值

)
