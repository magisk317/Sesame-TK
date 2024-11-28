package fansirsqi.xposed.sesame.task.antForest;

import android.annotation.SuppressLint;
import de.robv.android.xposed.XposedHelpers;
import fansirsqi.xposed.sesame.data.Config;
import fansirsqi.xposed.sesame.data.RuntimeInfo;
import fansirsqi.xposed.sesame.entity.*;
import fansirsqi.xposed.sesame.hook.ApplicationHook;
import fansirsqi.xposed.sesame.hook.Toast;
import fansirsqi.xposed.sesame.model.BaseModel;
import fansirsqi.xposed.sesame.model.ModelFields;
import fansirsqi.xposed.sesame.model.ModelGroup;
import fansirsqi.xposed.sesame.model.modelFieldExt.*;
import fansirsqi.xposed.sesame.rpc.intervallimit.FixedOrRangeIntervalLimit;
import fansirsqi.xposed.sesame.rpc.intervallimit.RpcIntervalLimit;
import fansirsqi.xposed.sesame.task.ModelTask;
import fansirsqi.xposed.sesame.task.TaskCommon;
import fansirsqi.xposed.sesame.task.antFarm.AntFarm.TaskStatus;
import fansirsqi.xposed.sesame.ui.ObjReference;
import fansirsqi.xposed.sesame.util.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** 蚂蚁森林V2 */
public class AntForest extends ModelTask {

  private static final String TAG = AntForest.class.getSimpleName();

  private static final AverageMathUtil offsetTimeMath = new AverageMathUtil(5);

  private static final Set<String> AntForestTaskTypeSet;

  static {
    AntForestTaskTypeSet = new HashSet<>();
    AntForestTaskTypeSet.add("VITALITYQIANDAOPUSH"); //
    AntForestTaskTypeSet.add("ONE_CLICK_WATERING_V1"); // 给随机好友一键浇水
    AntForestTaskTypeSet.add("GYG_YUEDU_2"); // 去森林图书馆逛15s
    AntForestTaskTypeSet.add("GYG_TBRS"); // 逛一逛淘宝人生
    AntForestTaskTypeSet.add("TAOBAO_tab2_2023"); // 去淘宝看科普视频
    AntForestTaskTypeSet.add("GYG_diantao"); // 逛一逛点淘得红包
    AntForestTaskTypeSet.add("GYG-taote"); // 逛一逛淘宝特价版
    AntForestTaskTypeSet.add("NONGCHANG_20230818"); // 逛一逛淘宝芭芭农场
  }

  private final AtomicInteger taskCount = new AtomicInteger(0);

  private String selfId;

  private Integer tryCountInt;

  private Integer retryIntervalInt;

  private Integer advanceTimeInt;

  /** 执行间隔-分钟 */
  private Integer checkIntervalInt;

  private FixedOrRangeIntervalLimit collectIntervalEntity;

  private FixedOrRangeIntervalLimit doubleCollectIntervalEntity;

  /** 双击卡结束时间 */
  private volatile long doubleEndTime = 0;

  /** 隐身卡结束时间 */
  private volatile long stealthEndTime = 0;

  /** 保护罩结束时间 */
  private volatile long shieldEndTime = 0;

  private final AverageMathUtil delayTimeMath = new AverageMathUtil(5);

  private final ObjReference<Long> collectEnergyLockLimit = new ObjReference<>(0L);

  private final Object doubleCardLockObj = new Object();

  private BooleanModelField collectEnergy;
  private BooleanModelField energyRain;
  private IntegerModelField advanceTime;
  private IntegerModelField tryCount;
  private IntegerModelField retryInterval;
  private SelectModelField dontCollectList;
  private BooleanModelField collectWateringBubble;
  private BooleanModelField batchRobEnergy;
  private BooleanModelField balanceNetworkDelay;
  private BooleanModelField closeWhackMole;
  private BooleanModelField collectProp;
  private StringModelField queryInterval;
  private StringModelField collectInterval;
  private StringModelField doubleCollectInterval;
  private BooleanModelField doubleCard; // 双击卡
  private ListModelField.ListJoinCommaToStringModelField doubleCardTime; // 双击卡时间
  @Getter private IntegerModelField doubleCountLimit; // 双击卡次数限制
  private BooleanModelField doubleCardConstant; // 双击卡永动机
  private BooleanModelField stealthCard; // 隐身卡
  private BooleanModelField stealthCardConstant; // 隐身卡永动机
  private BooleanModelField shieldCard; // 保护罩
  private BooleanModelField helpFriendCollect;
  private ChoiceModelField helpFriendCollectType;
  private SelectModelField helpFriendCollectList;
  private IntegerModelField returnWater33;
  private IntegerModelField returnWater18;
  private IntegerModelField returnWater10;
  private BooleanModelField receiveForestTaskAward;
  private SelectAndCountModelField waterFriendList;
  private IntegerModelField waterFriendCount;
  private SelectModelField giveEnergyRainList;
  private BooleanModelField exchangeEnergyDoubleClick;
  @Getter private IntegerModelField exchangeEnergyDoubleClickCount;
  private BooleanModelField exchangeEnergyDoubleClickLongTime;
  @Getter private IntegerModelField exchangeEnergyDoubleClickCountLongTime;
  private BooleanModelField exchangeCollectHistoryAnimal7Days;
  private BooleanModelField exchangeCollectToFriendTimes7Days;
  private BooleanModelField exchangeEnergyShield;
  private BooleanModelField userPatrol;
  private BooleanModelField collectGiftBox;
  private BooleanModelField medicalHealthFeeds;
  private BooleanModelField sendEnergyByAction;
  private BooleanModelField combineAnimalPiece;
  private BooleanModelField consumeAnimalProp;
  private SelectModelField whoYouWantToGiveTo;
  private BooleanModelField ecoLifeTick;
  private BooleanModelField ecoLifeOpen;
  private BooleanModelField photoGuangPan;
  private TextModelField photoGuangPanBefore;
  private TextModelField photoGuangPanAfter;
  private BooleanModelField youthPrivilege;
  private BooleanModelField dailyCheckIn;

  private int totalCollected = 0;
  private int totalHelpCollected = 0;

  @Getter private Set<String> dontCollectMap = new HashSet<>();

  @Override
  public String getName() {
    return "森林";
  }

  @Override
  public ModelGroup getGroup() {
    return ModelGroup.FOREST;
  }

  @Override
  public ModelFields getFields() {
    ModelFields modelFields = new ModelFields();
    modelFields.addField(collectEnergy = new BooleanModelField("collectEnergy", "收集能量", false));
    modelFields.addField(batchRobEnergy = new BooleanModelField("batchRobEnergy", "一键收取", false));
    modelFields.addField(queryInterval = new StringModelField("queryInterval", "查询间隔(毫秒或毫秒范围)", "500-1000"));
    modelFields.addField(collectInterval = new StringModelField("collectInterval", "收取间隔(毫秒或毫秒范围)", "1000-1500"));
    modelFields.addField(doubleCollectInterval = new StringModelField("doubleCollectInterval", "双击间隔(毫秒或毫秒范围)", "50-150"));
    modelFields.addField(balanceNetworkDelay = new BooleanModelField("balanceNetworkDelay", "平衡网络延迟", true));
    modelFields.addField(advanceTime = new IntegerModelField("advanceTime", "提前时间(毫秒)", 0, Integer.MIN_VALUE, 500));
    modelFields.addField(tryCount = new IntegerModelField("tryCount", "尝试收取(次数)", 1, 0, 10));
    modelFields.addField(retryInterval = new IntegerModelField("retryInterval", "重试间隔(毫秒)", 1000, 0, 10000));
    modelFields.addField(dontCollectList = new SelectModelField("dontCollectList", "不收取能量列表", new LinkedHashSet<>(), AlipayUser::getList));

    modelFields.addField(doubleCard = new BooleanModelField("doubleCard", "双击卡 | 使用", false));
    modelFields.addField(doubleCountLimit = new IntegerModelField("doubleCountLimit", "双击卡 | 使用次数", 6));
    modelFields.addField(doubleCardTime = new ListModelField.ListJoinCommaToStringModelField("doubleCardTime", "双击卡 | 使用时间(范围)", ListUtil.newArrayList("0700-0730")));
    modelFields.addField(doubleCardConstant = new BooleanModelField("DoubleCardConstant", "双击卡 | 限时双击永动机", false));
    modelFields.addField(stealthCard = new BooleanModelField("stealthCard", "隐身卡 | 使用", false));
    modelFields.addField(stealthCardConstant = new BooleanModelField("stealthCardConstant", "隐身卡 | 限时隐身永动机", false));
    modelFields.addField(shieldCard = new BooleanModelField("shieldCard", "能量保护罩 | 使用", true));
    modelFields.addField(returnWater10 = new IntegerModelField("returnWater10", "返水 | 10克需收能量(关闭:0)", 0));
    modelFields.addField(returnWater18 = new IntegerModelField("returnWater18", "返水 | 18克需收能量(关闭:0)", 0));
    modelFields.addField(returnWater33 = new IntegerModelField("returnWater33", "返水 | 33克需收能量(关闭:0)", 0));
    modelFields.addField(waterFriendList = new SelectAndCountModelField("waterFriendList", "浇水 | 好友列表", new LinkedHashMap<>(), AlipayUser::getList));
    modelFields.addField(waterFriendCount = new IntegerModelField("waterFriendCount", "浇水 | 克数(10 18 33 66)", 66));
    modelFields.addField(helpFriendCollect = new BooleanModelField("helpFriendCollect", "复活能量 | 开启", false));
    modelFields.addField(helpFriendCollectType = new ChoiceModelField("helpFriendCollectType", "复活能量 | 动作", HelpFriendCollectType.HELP, HelpFriendCollectType.nickNames));
    modelFields.addField(helpFriendCollectList = new SelectModelField("helpFriendCollectList", "复活能量 | 好友列表", new LinkedHashSet<>(), AlipayUser::getList));
    modelFields.addField(exchangeEnergyDoubleClick = new BooleanModelField("exchangeEnergyDoubleClick", "活力值 | 兑换限时双击卡", false));
    modelFields.addField(exchangeEnergyDoubleClickCount = new IntegerModelField("exchangeEnergyDoubleClickCount", "活力值 | 兑换限时双击卡数量", 6));
    modelFields.addField(exchangeEnergyDoubleClickLongTime = new BooleanModelField("exchangeEnergyDoubleClickLongTime", "活力值 | 兑换永久双击卡", false));
    modelFields.addField(exchangeEnergyDoubleClickCountLongTime = new IntegerModelField("exchangeEnergyDoubleClickCountLongTime", "活力值 | 兑换永久双击卡数量", 6));
    modelFields.addField(exchangeEnergyShield = new BooleanModelField("exchangeEnergyShield", "活力值 | 兑换能量保护罩", false));
    modelFields.addField(exchangeCollectHistoryAnimal7Days = new BooleanModelField("exchangeCollectHistoryAnimal7Days", "活力值 | 兑换物种历史卡", false));
    modelFields.addField(exchangeCollectToFriendTimes7Days = new BooleanModelField("exchangeCollectToFriendTimes7Days", "活力值 | 兑换物种好友卡", false));
    modelFields.addField(closeWhackMole = new BooleanModelField("closeWhackMole", "自动关闭6秒拼手速", true));
    modelFields.addField(collectProp = new BooleanModelField("collectProp", "收集道具", false));
    modelFields.addField(collectWateringBubble = new BooleanModelField("collectWateringBubble", "收金球", false));
    modelFields.addField(energyRain = new BooleanModelField("energyRain", "能量雨", false));
    modelFields.addField(userPatrol = new BooleanModelField("userPatrol", "保护地巡护", false));
    modelFields.addField(combineAnimalPiece = new BooleanModelField("combineAnimalPiece", "合成动物碎片", false));
    modelFields.addField(consumeAnimalProp = new BooleanModelField("consumeAnimalProp", "派遣动物伙伴", false));
    modelFields.addField(receiveForestTaskAward = new BooleanModelField("receiveForestTaskAward", "森林任务", false));
    modelFields.addField(collectGiftBox = new BooleanModelField("collectGiftBox", "领取礼盒", false));
    modelFields.addField(medicalHealthFeeds = new BooleanModelField("medicalHealthFeeds", "健康医疗", false));
    modelFields.addField(sendEnergyByAction = new BooleanModelField("sendEnergyByAction", "森林集市", false));
    modelFields.addField(giveEnergyRainList = new SelectModelField("giveEnergyRainList", "赠送能量雨列表", new LinkedHashSet<>(), AlipayUser::getList));
    modelFields.addField(whoYouWantToGiveTo = new SelectModelField("whoYouWantToGiveTo", "赠送道具好友列表（所有可送道具）", new LinkedHashSet<>(), AlipayUser::getList));
    modelFields.addField(youthPrivilege = new BooleanModelField("youthPrivilege", "青春特权 | 森林道具领取", false));
    modelFields.addField(dailyCheckIn = new BooleanModelField("studentCheckIn", "青春特权 | 每日签到红包", false));
    modelFields.addField(ecoLifeTick = new BooleanModelField("ecoLifeTick", "绿色 | 行动打卡", false));
    modelFields.addField(ecoLifeOpen = new BooleanModelField("ecoLifeOpen", "绿色 | 自动开通", false));
    modelFields.addField(photoGuangPan = new BooleanModelField("photoGuangPan", "绿色 | 光盘行动", false));
    modelFields.addField(photoGuangPanBefore = new TextModelField("photoGuangPanBefore", "绿色 | 光盘前图片ID", ""));
    modelFields.addField(photoGuangPanAfter = new TextModelField("photoGuangPanAfter", "绿色 | 光盘后图片ID", ""));
    modelFields.addField(
        new EmptyModelField(
            "photoGuangPanClear",
            "绿色 | 清空图片ID",
            () -> {
              photoGuangPanBefore.reset();
              photoGuangPanAfter.reset();
            }));

    return modelFields;
  }

  @Override
  public Boolean check() {
    if (RuntimeInfo.getInstance().getLong(RuntimeInfo.RuntimeInfoKey.ForestPauseTime) > System.currentTimeMillis()) {
      LogUtil.record("异常等待中，暂不执行检测！");
      return false;
    }
    return true;
  }

  @Override
  public Boolean isSync() {
    return true;
  }

  @Override
  public void boot(ClassLoader classLoader) {
    super.boot(classLoader);
    FixedOrRangeIntervalLimit queryIntervalLimit = new FixedOrRangeIntervalLimit(queryInterval.getValue(), 10, 10000);
    RpcIntervalLimit.addIntervalLimit("alipay.antforest.forest.h5.queryHomePage", queryIntervalLimit);
    RpcIntervalLimit.addIntervalLimit("alipay.antforest.forest.h5.queryFriendHomePage", queryIntervalLimit);
    RpcIntervalLimit.addIntervalLimit("alipay.antmember.forest.h5.collectEnergy", 0);
    RpcIntervalLimit.addIntervalLimit("alipay.antmember.forest.h5.queryEnergyRanking", 100);
    RpcIntervalLimit.addIntervalLimit("alipay.antforest.forest.h5.fillUserRobFlag", 500);
    tryCountInt = tryCount.getValue();
    retryIntervalInt = retryInterval.getValue();
    advanceTimeInt = advanceTime.getValue();
    checkIntervalInt = BaseModel.getCheckInterval().getValue();
    dontCollectMap = dontCollectList.getValue();
    collectIntervalEntity = new FixedOrRangeIntervalLimit(collectInterval.getValue(), 50, 10000);
    doubleCollectIntervalEntity = new FixedOrRangeIntervalLimit(doubleCollectInterval.getValue(), 10, 5000);
    delayTimeMath.clear();
    AntForestRpcCall.init();
  }

  @Override
  public void run() {
    try {
      // 获取当前时间
      LogUtil.record("执行开始-蚂蚁森林");
      NotificationUtil.setStatusTextExec();

      taskCount.set(0);
      selfId = UserIdMapUtil.getCurrentUid();

      JSONObject selfHomeObj = collectSelfEnergy();
      updateSelfHomePage(selfHomeObj);
      LogUtil.other("获取[我]主页成功"+ shieldEndTime + System.currentTimeMillis());
      boolean needshield = shieldCard.getValue() && shieldEndTime < System.currentTimeMillis();
      // 获取背包对象
      JSONObject bagObject = getBag();
      // 如果需要使用保护罩
      if (needshield) useShieldCard(bagObject);

      try {
        JSONObject friendsObject = new JSONObject(AntForestRpcCall.queryEnergyRanking());
        if ("SUCCESS".equals(friendsObject.getString("resultCode"))) {
          collectFriendsEnergy(friendsObject);
          int pos = 20;
          List<String> idList = new ArrayList<>();
          JSONArray totalDatas = friendsObject.getJSONArray("totalDatas");
          while (pos < totalDatas.length()) {
            JSONObject friend = totalDatas.getJSONObject(pos);
            idList.add(friend.getString("userId"));
            pos++;
            if (pos % 20 == 0) {
              collectFriendsEnergy(idList);
              idList.clear();
            }
          }
          if (!idList.isEmpty()) {
            collectFriendsEnergy(idList);
          }
        } else {
          LogUtil.record(friendsObject.getString("resultDesc"));
        }
      } catch (Throwable t) {
        LogUtil.runtime(TAG, "queryEnergyRanking err:");
        LogUtil.printStackTrace(TAG, t);
      }

      if (!TaskCommon.IS_ENERGY_TIME && selfHomeObj != null) {
        String whackMoleStatus = selfHomeObj.optString("whackMoleStatus");
        if ("CAN_PLAY".equals(whackMoleStatus) || "CAN_INITIATIVE_PLAY".equals(whackMoleStatus) || "NEED_MORE_FRIENDS".equals(whackMoleStatus)) {
          whackMole();
        }
        boolean hasMore = false;
        do {
          if (hasMore) {
            hasMore = false;
            selfHomeObj = querySelfHome();
          }
          if (collectWateringBubble.getValue()) {
            JSONArray wateringBubbles = selfHomeObj.has("wateringBubbles") ? selfHomeObj.getJSONArray("wateringBubbles") : new JSONArray();
            if (wateringBubbles.length() > 0) {
              int collected = 0;
              for (int i = 0; i < wateringBubbles.length(); i++) {
                JSONObject wateringBubble = wateringBubbles.getJSONObject(i);
                String bizType = wateringBubble.getString("bizType");
                switch (bizType) {
                  case "jiaoshui": // 浇水
                    {
                      String str = AntForestRpcCall.collectEnergy(bizType, selfId, wateringBubble.getLong("id"));
                      JSONObject joEnergy = new JSONObject(str);
                      if ("SUCCESS".equals(joEnergy.getString("resultCode"))) {
                        JSONArray bubbles = joEnergy.getJSONArray("bubbles");
                        for (int j = 0; j < bubbles.length(); j++) {
                          collected = bubbles.getJSONObject(j).getInt("collectedEnergy");
                        }
                        if (collected > 0) {
                          String msg = "收取金球🍯浇水[" + collected + "g]";
                          LogUtil.forest(msg);
                          NotificationUtil.updateLastExecText(msg);
                          Toast.show(msg);
                          totalCollected += collected;
                          StatisticsUtil.addData(StatisticsUtil.DataType.COLLECTED, collected);
                        } else {
                          LogUtil.record("收取[我]的浇水金球失败");
                        }
                      } else {
                        LogUtil.record("收取[我]的浇水金球失败:" + joEnergy.getString("resultDesc"));
                        LogUtil.runtime(str);
                      }
                      break;
                    }
                  case "fuhuo": // 复活
                    {
                      String str = AntForestRpcCall.collectRebornEnergy();
                      JSONObject joEnergy = new JSONObject(str);
                      if ("SUCCESS".equals(joEnergy.getString("resultCode"))) {
                        collected = joEnergy.getInt("energy");
                        String msg = "收取金球🍯复活[" + collected + "g]";
                        LogUtil.forest(msg);
                        NotificationUtil.updateLastExecText(msg);
                        Toast.show(msg);
                        totalCollected += collected;
                        StatisticsUtil.addData(StatisticsUtil.DataType.COLLECTED, collected);
                      } else {
                        LogUtil.record("收取[我]的复活金球失败:" + joEnergy.getString("resultDesc"));
                        LogUtil.runtime(str);
                      }
                      break;
                    }
                  case "baohuhuizeng": // 回赠
                    {
                      String friendId = wateringBubble.getString("userId");
                      String str = AntForestRpcCall.collectEnergy(bizType, selfId, wateringBubble.getLong("id"));
                      JSONObject joEnergy = new JSONObject(str);
                      if ("SUCCESS".equals(joEnergy.getString("resultCode"))) {
                        JSONArray bubbles = joEnergy.getJSONArray("bubbles");
                        for (int j = 0; j < bubbles.length(); j++) {
                          collected = bubbles.getJSONObject(j).getInt("collectedEnergy");
                        }
                        if (collected > 0) {
                          String msg = "收取金球🍯[" + UserIdMapUtil.getMaskName(friendId) + "]复活回赠[" + collected + "g]";
                          LogUtil.forest(msg);
                          NotificationUtil.updateLastExecText(msg);
                          Toast.show(msg);
                          totalCollected += collected;
                          StatisticsUtil.addData(StatisticsUtil.DataType.COLLECTED, collected);
                        } else {
                          LogUtil.record("收取[" + UserIdMapUtil.getMaskName(friendId) + "]的复活回赠金球失败");
                        }
                      } else {
                        LogUtil.record("收取[" + UserIdMapUtil.getMaskName(friendId) + "]的复活回赠金球失败:" + joEnergy.getString("resultDesc"));
                        LogUtil.runtime(str);
                      }
                      break;
                    }
                }
                TimeUtil.sleep(1000L);
              }
              if (wateringBubbles.length() >= 20) {
                hasMore = true;
              }
            }
          }
          if (collectProp.getValue()) {
            JSONArray givenProps = selfHomeObj.has("givenProps") ? selfHomeObj.getJSONArray("givenProps") : new JSONArray();
            if (givenProps.length() > 0) {
              for (int i = 0; i < givenProps.length(); i++) {
                JSONObject jo = givenProps.getJSONObject(i);
                String giveConfigId = jo.getString("giveConfigId");
                String giveId = jo.getString("giveId");
                String propName = jo.getJSONObject("propConfig").getString("propName");
                jo = new JSONObject(AntForestRpcCall.collectProp(giveConfigId, giveId));
                if ("SUCCESS".equals(jo.getString("resultCode"))) {
                  String str = "领取道具🎭[" + propName + "]";
                  NotificationUtil.updateLastExecText(str);
                  LogUtil.forest(str);
                } else {
                  String str = "领取道具🎭[" + propName + "]失败:" + jo.getString("resultDesc");
                  LogUtil.record(str);
                  LogUtil.runtime(jo.toString());
                }
                TimeUtil.sleep(1000L);
              }
              if (givenProps.length() >= 20) {
                hasMore = true;
              }
            }
          }
        } while (hasMore);
        JSONArray usingUserProps = selfHomeObj.has("usingUserProps") ? selfHomeObj.getJSONArray("usingUserProps") : new JSONArray();
        boolean canConsumeAnimalProp = true;
        if (usingUserProps.length() > 0) {
          for (int i = 0; i < usingUserProps.length(); i++) {
            JSONObject jo = usingUserProps.getJSONObject(i);
            if (!"animal".equals(jo.getString("type"))) {
              continue;
            } else {
              canConsumeAnimalProp = false;
            }
            JSONObject extInfo = new JSONObject(jo.getString("extInfo"));
            int energy = extInfo.optInt("energy", 0);
            if (energy > 0 && !extInfo.optBoolean("isCollected")) {
              String propId = jo.getString("propSeq");
              String propType = jo.getString("propType");
              String shortDay = extInfo.getString("shortDay");
              jo = new JSONObject(AntForestRpcCall.collectAnimalRobEnergy(propId, propType, shortDay));
              if ("SUCCESS".equals(jo.getString("resultCode"))) {
                String str = "收取动物能量🦩[" + energy + "g]";
                NotificationUtil.updateLastExecText(str);
                Toast.show(str);
                LogUtil.forest(str);
              } else {
                String str = "收取动物能量🦩[" + energy + "g]失败:" + jo.getString("resultDesc");
                LogUtil.record(str);
                LogUtil.runtime(jo.toString());
              }
              try {
                TimeUtil.sleep(500);
              } catch (Exception e) {
                LogUtil.printStackTrace(e);
              }
              break;
            }
          }
        }
        if (userPatrol.getValue()) {
          queryUserPatrol();
        }
        if (combineAnimalPiece.getValue()) {
          queryAnimalAndPiece();
        }
        if (consumeAnimalProp.getValue()) {
          if (!canConsumeAnimalProp) {
            String str = "啦啦~ 已经有动物伙伴在巡护森林~";
            LogUtil.record(str);
            NotificationUtil.updateLastExecText(str);
            Toast.show(str);
          } else {
            queryAnimalPropList();
          }
        }
        popupTask();
        if (energyRain.getValue()) {
          energyRain();
        }
        if (receiveForestTaskAward.getValue()) {
          receiveTaskAward();
        }
        if (ecoLifeTick.getValue() || photoGuangPan.getValue()) {
          ecoLife();
        }
        Map<String, Integer> friendMap = waterFriendList.getValue();
        for (Map.Entry<String, Integer> friendEntry : friendMap.entrySet()) {
          String uid = friendEntry.getKey();
          if (selfId.equals(uid)) {
            continue;
          }
          Integer waterCount = friendEntry.getValue();
          if (waterCount == null || waterCount <= 0) {
            continue;
          }
          if (waterCount > 3) waterCount = 3;
          if (StatusUtil.canWaterFriendToday(uid, waterCount)) {
            try {
              String s = AntForestRpcCall.queryFriendHomePage(uid);
              Thread.sleep(100);
              JSONObject jo = new JSONObject(s);
              if ("SUCCESS".equals(jo.getString("resultCode"))) {
                String bizNo = jo.getString("bizNo");
                KVNode<Integer, Boolean> waterCountKVNode = returnFriendWater(uid, bizNo, waterCount, waterFriendCount.getValue());
                waterCount = waterCountKVNode.getKey();
                if (waterCount > 0) {
                  StatusUtil.waterFriendToday(uid, waterCount);
                }
                if (!waterCountKVNode.getValue()) {
                  break;
                }
              } else {
                LogUtil.record(jo.getString("resultDesc"));
                LogUtil.runtime(s);
              }
            } catch (Throwable t) {
              LogUtil.runtime(TAG, "waterFriendEnergy err:");
              LogUtil.printStackTrace(TAG, t);
            }
          }
        }
        Set<String> set = whoYouWantToGiveTo.getValue();
        if (!set.isEmpty()) {
          for (String userId : set) {
            if (!Objects.equals(selfId, userId)) {
              giveProp(userId);
              break;
            }
          }
        }
        if (exchangeEnergyDoubleClick.getValue() && StatusUtil.canExchangeDoubleCardToday()) {
          exchangeEnergyDoubleClickCount.getValue();
          exchangeEnergyDoubleClick();
        }
        if (exchangeEnergyDoubleClickLongTime.getValue() && StatusUtil.canExchangeDoubleCardTodayLongTime()) {
          int exchangeCount = exchangeEnergyDoubleClickCountLongTime.getValue();
          exchangeEnergyDoubleClickLongTime(exchangeCount);
        }
        // 兑换 能量保护罩
        if (exchangeEnergyShield.getValue() && StatusUtil.canExchangeEnergyShield()) {
          exchangeEnergyShield();
        }
        // 兑换 神奇物种抽历史卡机会
        if (exchangeCollectHistoryAnimal7Days.getValue() && StatusUtil.canExchangeCollectHistoryAnimal7Days()) {
          exchangeCollectHistoryAnimal7Days();
        }
        // 兑换 神奇物种抽好友卡机会
        if (exchangeCollectToFriendTimes7Days.getValue() && StatusUtil.canExchangeCollectToFriendTimes7Days()) {
          exchangeCollectToFriendTimes7Days();
        }
        /* 森林集市 */
        if (sendEnergyByAction.getValue()) {
          sendEnergyByAction("GREEN_LIFE");
          sendEnergyByAction("ANTFOREST");
        }
        if (medicalHealthFeeds.getValue()) {
          medicalHealthFeeds();
        }
        // 青春特权森林道具领取
        if (youthPrivilege.getValue()) {
          youthPrivilege();
        }
        // 青春特权每日签到红包
        if (dailyCheckIn.getValue()) {
          studentSignInRedEnvelope();
        }
      }
    } catch (Throwable t) {
      LogUtil.runtime(TAG, "AntForest.run err:");
      LogUtil.printStackTrace(TAG, t);
    } finally {
      try {
        synchronized (AntForest.this) {
          int count = taskCount.get();
          if (count > 0) {
            AntForest.this.wait(TimeUnit.MINUTES.toMillis(30));
            count = taskCount.get();
          }
          if (count > 0) {
            LogUtil.record("执行超时-蚂蚁森林");
          } else if (count == 0) {
            LogUtil.record("执行结束-蚂蚁森林");
          } else {
            LogUtil.record("执行完成-蚂蚁森林");
          }
        }
      } catch (InterruptedException ie) {
        LogUtil.runtime(TAG, "执行中断-蚂蚁森林");
      }
      StatisticsUtil.save();
      FriendWatch.save();
      NotificationUtil.updateLastExecText("收:" + totalCollected + " 帮:" + totalHelpCollected);
    }
  }

  /** 青春特权森林道具领取 */
  private void youthPrivilege() {
    try {
      // 定义任务列表，每个任务包含接口调用参数和标记信息
      List<List<String>> taskList =
          Arrays.asList(
              Arrays.asList("DNHZ_SL_college", "DAXUESHENG_SJK", "双击卡"),
              Arrays.asList("DXS_BHZ", "NENGLIANGZHAO_20230807", "保护罩"),
              Arrays.asList("DXS_JSQ", "JIASUQI_20230808", "加速器"));
      // 遍历任务列表
      for (List<String> task : taskList) {
        String queryParam = task.get(0); // 用于 queryTaskListV2 方法的第一个参数
        String receiveParam = task.get(1); // 用于 receiveTaskAwardV2 方法的第二个参数
        String taskName = task.get(2); // 标记名称
        // 调用 queryTaskListV2 方法并解析返回结果
        String queryResult = AntForestRpcCall.queryTaskListV2(queryParam);
        LogUtil.runtime("【青春特权】森林道具：" + taskName + "查询结果：" + queryResult);
        JSONObject getTaskStatusObject = new JSONObject(queryResult);
        // 获取任务信息列表
        JSONArray taskInfoList = getTaskStatusObject.getJSONArray("forestTasksNew").getJSONObject(0).getJSONArray("taskInfoList");
        // 遍历任务信息列表
        for (int i = 0; i < taskInfoList.length(); i++) {
          JSONObject taskInfo = taskInfoList.getJSONObject(i);
          JSONObject taskBaseInfo = taskInfo.getJSONObject("taskBaseInfo");
          // 检查任务类型和状态
          if (receiveParam.equals(taskBaseInfo.getString("taskType"))) {
            String taskStatus = taskBaseInfo.getString("taskStatus");
            if ("RECEIVED".equals(taskStatus)) {
              LogUtil.other("【青春特权】森林道具[" + taskName + "]已领取 ✅");
            } else if ("FINISHED".equals(taskStatus)) {
              LogUtil.forest("【青春特权】森林道具[" + taskName + "]开始领取...");
              String receiveResult = AntForestRpcCall.receiveTaskAwardV2(receiveParam);
              JSONObject resultOfReceive = new JSONObject(receiveResult);
              String resultDesc = resultOfReceive.getString("desc");
              LogUtil.forest("【青春特权】森林道具[" + taskName + "]领取结果：" + resultDesc);
            }
          }
        }
      }
    } catch (Exception e) {
      LogUtil.runtime(TAG, "youthPrivilege err:");
      LogUtil.printStackTrace(TAG, e);
    }
  }

  /* 青春特权每日签到红包 */
  private void studentSignInRedEnvelope() {
    try {
      // 获取当前时间
      Calendar calendar = Calendar.getInstance();
      int hour = calendar.get(Calendar.HOUR_OF_DAY); // 小时（24小时制）

      // 定义签到时间范围
      final int START_HOUR = 5;
      final int END_HOUR = 10;
      if (hour < START_HOUR) {
        LogUtil.other("【青春特权-学生签到】：5点前不执行签到 ❤️");
        return;
      }
      if (hour < END_HOUR) {
        // 当前时间在双倍积分时间内
        studentTaskHandle("双倍 🐯");
      } else {
        // 当前时间不在双倍积分时间范围内
        studentTaskHandle("非双倍 🐱");
      }
    } catch (Exception e) {
      LogUtil.runtime(TAG, "studentCheckin err:");
      LogUtil.printStackTrace(TAG, e);
    }
  }

  /**
   * 执行签到逻辑
   *
   * @param tag 上下文说明（例如时间段）
   */
  private void studentTask(String tag) {
    try {
      String result = AntForestRpcCall.studentCheckin();
      JSONObject resultJson = new JSONObject(result);
      String resultDesc = resultJson.getString("resultDesc");
      if (resultDesc.contains("不匹配")) {
        LogUtil.record("【青春特权-学生签到】" + tag + "：" + resultDesc + "可能已经签到过啦，去【青春特权】看看就着知道了");
      } else {
        LogUtil.forest("【青春特权-学生签到】" + tag + "：" + resultDesc);
      }
    } catch (Exception e) {
      LogUtil.runtime(TAG, "performCheckin err:");
      LogUtil.printStackTrace(TAG, e);
    }
  }

  /** 处理不在签到时间范围内的逻辑 */
  private void studentTaskHandle(String tag) {
    try {
      String isTasked = AntForestRpcCall.studentQqueryCheckInModel();
      JSONObject isTaskedJson = new JSONObject(isTasked);
      // 检查是否已经签到
      String action = isTaskedJson.getJSONObject("studentCheckInInfo").getString("action");
      if ("DO_TASK".equals(action)) {
        LogUtil.record("【青春特权-学生签到】：今日已签到");
      } else {
        studentTask(tag);
      }
    } catch (JSONException e) {
      LogUtil.runtime(TAG, "handleOutOfTimeCheckin JSON err:");
      LogUtil.printStackTrace(TAG, e);
    } catch (Exception e) {
      LogUtil.runtime(TAG, "handleOutOfTimeCheckin err:");
      LogUtil.printStackTrace(TAG, e);
    }
  }

  private void notifyMain() {
    if (taskCount.decrementAndGet() < 1) {
      synchronized (AntForest.this) {
        AntForest.this.notifyAll();
      }
    }
  }

  private JSONObject querySelfHome() {
    JSONObject userHomeObj = null; // 声明用户主页对象
    try {
      long start = System.currentTimeMillis(); // 记录开始时间
      // 调用远程接口获取用户主页信息并转换为 JSONObject 对象
      userHomeObj = new JSONObject(AntForestRpcCall.queryHomePage());
      long end = System.currentTimeMillis(); // 记录结束时间

      // 获取服务器时间
      long serverTime = userHomeObj.getLong("now");

      // 将服务器时间转换为可读的时间格式
      @SuppressLint("SimpleDateFormat")
      SimpleDateFormat stime = new SimpleDateFormat("HH:mm:ss");
      String formattedServerTime = stime.format(new Date(serverTime)); // 将服务器时间格式化为 hh:mm:ss

      // 计算本地与服务器时间差
      int offsetTime = offsetTimeMath.nextInteger((int) ((start + end) / 2 - serverTime));

      // 将时间差格式化为人性化的字符串
      String formattedTimeDiff = formatTimeDifference(offsetTime);

      // 记录服务器时间与本地时间差
      LogUtil.runtime("服务器时间：" + formattedServerTime + "，本地与服务器时间差：" + formattedTimeDiff);
    } catch (Throwable t) {
      // 记录异常信息
      LogUtil.printStackTrace(t);
    }
    return userHomeObj; // 返回用户主页对象
  }

  private JSONObject queryFriendHome(String userId) {
    JSONObject userHomeObj = null; // 声明用户主页对象
    try {
      long start = System.currentTimeMillis(); // 记录开始时间
      // 调用远程接口获取好友主页信息并转换为 JSONObject 对象
      userHomeObj = new JSONObject(AntForestRpcCall.queryFriendHomePage(userId));
      long end = System.currentTimeMillis(); // 记录结束时间

      // 获取服务器时间
      long serverTime = userHomeObj.getLong("now");

      // 将服务器时间转换为可读的时间格式
      @SuppressLint("SimpleDateFormat")
      SimpleDateFormat stime = new SimpleDateFormat("HH:mm:ss");
      String formattedServerTime = stime.format(new Date(serverTime)); // 将服务器时间格式化为 hh:mm:ss
      // 计算本地与服务器时间差
      int offsetTime = offsetTimeMath.nextInteger((int) ((start + end) / 2 - serverTime));

      // 将时间差格式化为人性化的字符串
      String formattedTimeDiff = formatTimeDifference(offsetTime);

      // 记录服务器时间与本地时间差
      LogUtil.runtime("服务器时间：" + formattedServerTime + "，本地与服务器时间差：" + formattedTimeDiff);
    } catch (Throwable t) {
      // 记录异常信息
      LogUtil.printStackTrace(t);
    }
    return userHomeObj; // 返回用户主页对象
  }

  // 格式化时间差为人性化的字符串
  private String formatTimeDifference(int milliseconds) {
    long seconds = Math.abs(milliseconds) / 1000; // 计算绝对值的秒数
    String sign = milliseconds >= 0 ? "+" : "-"; // 根据时间差的正负来确定符号

    // 根据秒数判断使用的单位
    if (seconds < 60) {
      return sign + seconds + "秒"; // 如果小于60秒，显示秒
    } else if (seconds < 3600) {
      long minutes = seconds / 60; // 计算分钟
      return sign + minutes + "分钟"; // 如果小于3600秒，显示分钟
    } else {
      long hours = seconds / 3600; // 计算小时
      return sign + hours + "小时"; // 否则显示小时
    }
  }

  /**
   * 收集用户自己的能量。 这个方法首先查询用户的主页信息，然后根据用户主页中的信息执行相应的操作， 如关闭“6秒拼手速”功能或执行“拼手速”游戏。最后，收集并返回用户的能量信息。
   *
   * @return 用户的能量信息，如果发生错误则返回null。
   */
  private JSONObject collectSelfEnergy() {
    try {
      // 查询用户的主页信息
      JSONObject selfHomeObj = querySelfHome();
      if (selfHomeObj != null) {
        // 如果启用了关闭“6秒拼手速”功能
        if (closeWhackMole.getValue()) {
          // 获取用户主页中的属性对象
          JSONObject propertiesObject = selfHomeObj.optJSONObject("properties");
          if (propertiesObject != null) {
            // 如果用户主页的属性中标记了“whackMole”
            if (Objects.equals("Y", propertiesObject.optString("whackMole"))) {
              // 尝试关闭“6秒拼手速”功能
              boolean success = closeWhackMole();
              LogUtil.record(success ? "6秒拼手速关闭成功" : "6秒拼手速关闭失败");
            }
          }
        }
        // 如果用户的下一个行动是“WhackMole”，则执行“拼手速”游戏
        String nextAction = selfHomeObj.optString("nextAction");
        if ("WhackMole".equalsIgnoreCase(nextAction)) {
          LogUtil.record("检测到6秒拼手速强制弹窗，先执行拼手速");
          whackMole();
        }
        // 收集并返回用户的能量信息
        return collectUserEnergy(UserIdMapUtil.getCurrentUid(), selfHomeObj);
      }
    } catch (Throwable t) {
      // 打印异常信息
      LogUtil.printStackTrace(t);
    }
    // 如果发生错误，返回null
    return null;
  }

  /**
   * 收集指定用户的能量。 这个方法查询指定用户的主页信息，然后收集并返回该好友的能量信息。
   *
   * @param userId 好友用户的ID。
   * @return 好友的能量信息，如果发生错误则返回null。
   */
  private JSONObject collectFriendEnergy(String userId) {
    try {
      // 查询好友的主页信息
      JSONObject userHomeObj = queryFriendHome(userId);
      if (userHomeObj != null) {
        // 如果查询成功，收集并返回好友的能量信息
        return collectUserEnergy(userId, userHomeObj);
      }
    } catch (Throwable t) {
      // 打印异常信息
      LogUtil.printStackTrace(t);
    }
    // 如果发生错误，返回null
    return null;
  }

  /**
   * 收取用户的蚂蚁森林能量。
   *
   * @param userId 用户ID
   * @param userHomeObj 用户主页的JSON对象，包含用户的蚂蚁森林信息
   * @return 更新后的用户主页JSON对象，如果发生异常返回null
   */
  private JSONObject collectUserEnergy(String userId, JSONObject userHomeObj) {
    try {
      // 检查用户主页的返回状态
      if (!"SUCCESS".equals(userHomeObj.getString("resultCode"))) {
        LogUtil.record(userHomeObj.getString("resultDesc")); // 记录错误信息
        return userHomeObj; // 如果失败，直接返回原对象
      }
      long serverTime = userHomeObj.getLong("now"); // 服务器当前时间
      boolean isSelf = Objects.equals(userId, selfId); // 是否为当前用户
      String userName = UserIdMapUtil.getMaskName(userId); // 获取用户的掩码名称
      LogUtil.record("进入[" + userName + "]的蚂蚁森林"); // 记录进入日志
      // 检查是否允许收取能量
      boolean isCollectEnergy = collectEnergy.getValue() && !dontCollectMap.contains(userId);
      if (isSelf) {
        // 如果是自己，更新双倍收取时间
        updateSelfHomePage(userHomeObj);
      } else {
        if (isCollectEnergy) {
          // 检查对方是否有能量罩保护
          JSONArray jaProps = userHomeObj.optJSONArray("usingUserProps");
          if (jaProps != null) {
            for (int i = 0; i < jaProps.length(); i++) {
              JSONObject joProps = jaProps.getJSONObject(i);
              if ("energyShield".equals(joProps.getString("type"))) {
                if (joProps.getLong("endTime") > serverTime) {
                  LogUtil.record("[" + userName + "]被能量罩保护着哟");
                  isCollectEnergy = false;
                  break;
                }
              }
            }
          }
        }
      }
      if (isCollectEnergy) {
        // 收集用户的能量
        JSONArray jaBubbles = userHomeObj.getJSONArray("bubbles");
        List<Long> bubbleIdList = new ArrayList<>();
        for (int i = 0; i < jaBubbles.length(); i++) {
          JSONObject bubble = jaBubbles.getJSONObject(i);
          long bubbleId = bubble.getLong("id");
          switch (CollectStatus.valueOf(bubble.getString("collectStatus"))) {
            case AVAILABLE:
              // 如果能量可收取，加入列表
              bubbleIdList.add(bubbleId);
              break;
            case WAITING:
              // 如果能量还未成熟，设置定时任务
              long produceTime = bubble.getLong("produceTime");
              if (checkIntervalInt + checkIntervalInt / 2 > produceTime - serverTime) {
                // 如果时间接近能量成熟时间，添加定时任务
                if (hasChildTask(AntForest.getEnergyTimerTid(userId, bubbleId))) {
                  break;
                }
                addChildTask(new EnergyTimerTask(userId, bubbleId, produceTime));
                LogUtil.record("添加蹲点能量⏰[" + userName + "]在[" + TimeUtil.getCommonDate(produceTime) + "]执行");
              } else {
                // 否则记录能量成熟时间
                LogUtil.runtime("用户[" + UserIdMapUtil.getMaskName(userId) + "]能量成熟时间: " + TimeUtil.getCommonDate(produceTime));
              }
              break;
          }
        }
        // 批量收取或逐一收取能量
        if (batchRobEnergy.getValue()) {
          Iterator<Long> iterator = bubbleIdList.iterator();
          List<Long> batchBubbleIdList = new ArrayList<>();
          while (iterator.hasNext()) {
            batchBubbleIdList.add(iterator.next());
            if (batchBubbleIdList.size() >= 6) {
              // 每次最多收取6个能量
              collectEnergy(new CollectEnergyEntity(userId, userHomeObj, AntForestRpcCall.getCollectBatchEnergyRpcEntity(userId, batchBubbleIdList)));
              batchBubbleIdList = new ArrayList<>();
            }
          }
          int size = batchBubbleIdList.size();
          if (size > 0) {
            // 收取剩余的能量
            if (size == 1) {
              collectEnergy(new CollectEnergyEntity(userId, userHomeObj, AntForestRpcCall.getCollectEnergyRpcEntity(null, userId, batchBubbleIdList.get(0))));
            } else {
              collectEnergy(new CollectEnergyEntity(userId, userHomeObj, AntForestRpcCall.getCollectBatchEnergyRpcEntity(userId, batchBubbleIdList)));
            }
          }
        } else {
          // 逐一收取每个能量
          for (Long bubbleId : bubbleIdList) {
            collectEnergy(new CollectEnergyEntity(userId, userHomeObj, AntForestRpcCall.getCollectEnergyRpcEntity(null, userId, bubbleId)));
          }
        }
      }
      return userHomeObj;
    } catch (Throwable t) {
      LogUtil.runtime(TAG, "collectUserEnergy err:"); // 记录错误日志
      LogUtil.printStackTrace(TAG, t); // 打印异常堆栈
    }
    return null; // 异常情况下返回 null
  }

  private void collectFriendsEnergy(List<String> idList) {
    try {
      collectFriendsEnergy(new JSONObject(AntForestRpcCall.fillUserRobFlag(new JSONArray(idList).toString())));
    } catch (Exception e) {
      LogUtil.printStackTrace(e);
    }
  }

  private void collectFriendsEnergy(JSONObject friendsObject) {
    try {
      JSONArray jaFriendRanking = friendsObject.optJSONArray("friendRanking");
      if (jaFriendRanking == null) {
        return;
      }
      for (int i = 0, len = jaFriendRanking.length(); i < len; i++) {
        try {
          JSONObject friendObject = jaFriendRanking.getJSONObject(i);
          String userId = friendObject.getString("userId");
          if (Objects.equals(userId, selfId)) {
            continue;
          }
          JSONObject userHomeObj = null;
          if (collectEnergy.getValue() && !dontCollectMap.contains(userId)) {
            boolean collectEnergy = true;
            if (!friendObject.optBoolean("canCollectEnergy")) {
              long canCollectLaterTime = friendObject.getLong("canCollectLaterTime");
              if (canCollectLaterTime <= 0 || (canCollectLaterTime - System.currentTimeMillis() > checkIntervalInt)) {
                collectEnergy = false;
              }
            }
            if (collectEnergy) {
              userHomeObj = collectFriendEnergy(userId);
            } /* else {
                  LogUtil.i("不收取[" + UserIdMapUtil.getNameById(userId) + "], userId=" + userId);
              }*/
          }
          if (helpFriendCollect.getValue() && friendObject.optBoolean("canProtectBubble") && StatusUtil.canProtectBubbleToday(selfId)) {
            boolean isHelpCollect = helpFriendCollectList.getValue().contains(userId);
            if (helpFriendCollectType.getValue() == HelpFriendCollectType.DONT_HELP) {
              isHelpCollect = !isHelpCollect;
            }
            if (isHelpCollect) {
              if (userHomeObj == null) {
                userHomeObj = queryFriendHome(userId);
              }
              if (userHomeObj != null) {
                protectFriendEnergy(userHomeObj);
              }
            }
          }
          if (collectGiftBox.getValue() && friendObject.getBoolean("canCollectGiftBox")) {
            if (userHomeObj == null) {
              userHomeObj = queryFriendHome(userId);
            }
            if (userHomeObj != null) {
              collectGiftBox(userHomeObj);
            }
          }
        } catch (Exception t) {
          LogUtil.runtime(TAG, "collectFriendEnergy err:");
          LogUtil.printStackTrace(TAG, t);
        }
      }
    } catch (Exception e) {
      LogUtil.printStackTrace(e);
    }
  }

  private void collectGiftBox(JSONObject userHomeObj) {
    try {
      JSONObject giftBoxInfo = userHomeObj.optJSONObject("giftBoxInfo");
      JSONObject userEnergy = userHomeObj.optJSONObject("userEnergy");
      String userId = userEnergy == null ? UserIdMapUtil.getCurrentUid() : userEnergy.optString("userId");
      if (giftBoxInfo != null) {
        JSONArray giftBoxList = giftBoxInfo.optJSONArray("giftBoxList");
        if (giftBoxList != null && giftBoxList.length() > 0) {
          for (int ii = 0; ii < giftBoxList.length(); ii++) {
            try {
              JSONObject giftBox = giftBoxList.getJSONObject(ii);
              String giftBoxId = giftBox.getString("giftBoxId");
              String title = giftBox.getString("title");
              JSONObject giftBoxResult = new JSONObject(AntForestRpcCall.collectFriendGiftBox(giftBoxId, userId));
              if (!"SUCCESS".equals(giftBoxResult.getString("resultCode"))) {
                LogUtil.record(giftBoxResult.getString("resultDesc"));
                LogUtil.runtime(giftBoxResult.toString());
                continue;
              }
              int energy = giftBoxResult.optInt("energy", 0);
              LogUtil.forest("礼盒能量🎁[" + UserIdMapUtil.getMaskName(userId) + "-" + title + "]#" + energy + "g");
              StatisticsUtil.addData(StatisticsUtil.DataType.COLLECTED, energy);
            } catch (Throwable t) {
              LogUtil.printStackTrace(t);
              break;
            } finally {
              TimeUtil.sleep(500);
            }
          }
        }
      }
    } catch (Exception e) {
      LogUtil.printStackTrace(e);
    }
  }

  private void protectFriendEnergy(JSONObject userHomeObj) {
    try {
      JSONArray wateringBubbles = userHomeObj.optJSONArray("wateringBubbles");
      JSONObject userEnergy = userHomeObj.optJSONObject("userEnergy");
      String userId = userEnergy == null ? UserIdMapUtil.getCurrentUid() : userEnergy.optString("userId");
      if (wateringBubbles != null && wateringBubbles.length() > 0) {
        for (int j = 0; j < wateringBubbles.length(); j++) {
          try {
            JSONObject wateringBubble = wateringBubbles.getJSONObject(j);
            if (!"fuhuo".equals(wateringBubble.getString("bizType"))) {
              continue;
            }
            if (wateringBubble.getJSONObject("extInfo").optInt("restTimes", 0) == 0) {
              StatusUtil.protectBubbleToday(selfId);
            }
            if (!wateringBubble.getBoolean("canProtect")) {
              continue;
            }
            JSONObject joProtect = new JSONObject(AntForestRpcCall.protectBubble(userId));
            if (!"SUCCESS".equals(joProtect.getString("resultCode"))) {
              LogUtil.record(joProtect.getString("resultDesc"));
              LogUtil.runtime(joProtect.toString());
              continue;
            }
            int vitalityAmount = joProtect.optInt("vitalityAmount", 0);
            int fullEnergy = wateringBubble.optInt("fullEnergy", 0);
            String str = "复活能量🚑[" + UserIdMapUtil.getMaskName(userId) + "-" + fullEnergy + "g]" + (vitalityAmount > 0 ? "#活力值+" + vitalityAmount : "");
            LogUtil.forest(str);
            totalHelpCollected += fullEnergy;
            StatisticsUtil.addData(StatisticsUtil.DataType.HELPED, fullEnergy);
            break;
          } catch (Throwable t) {
            LogUtil.printStackTrace(t);
            break;
          } finally {
            TimeUtil.sleep(500);
          }
        }
      }
    } catch (Exception e) {
      LogUtil.printStackTrace(e);
    }
  }

  private void collectEnergy(CollectEnergyEntity collectEnergyEntity) {
    collectEnergy(collectEnergyEntity, false);
  }

  private void collectEnergy(CollectEnergyEntity collectEnergyEntity, Boolean joinThread) {
    Runnable runnable =
        () -> {
          try {
            String userId = collectEnergyEntity.getUserId();
            usePropBeforeCollectEnergy(userId);
            RpcEntity rpcEntity = collectEnergyEntity.getRpcEntity();
            boolean needDouble = collectEnergyEntity.getNeedDouble();
            boolean needRetry = collectEnergyEntity.getNeedRetry();
            int tryCount = collectEnergyEntity.addTryCount();
            int collected = 0;
            long startTime;
            synchronized (collectEnergyLockLimit) {
              long sleep;
              if (needDouble) {
                collectEnergyEntity.unsetNeedDouble();
                sleep = doubleCollectIntervalEntity.getInterval() - System.currentTimeMillis() + collectEnergyLockLimit.get();
              } else if (needRetry) {
                collectEnergyEntity.unsetNeedRetry();
                sleep = retryIntervalInt - System.currentTimeMillis() + collectEnergyLockLimit.get();
              } else {
                sleep = collectIntervalEntity.getInterval() - System.currentTimeMillis() + collectEnergyLockLimit.get();
              }
              if (sleep > 0) {
                Thread.sleep(sleep);
              }
              startTime = System.currentTimeMillis();
              collectEnergyLockLimit.setForce(startTime);
            }
            ApplicationHook.requestObject(rpcEntity, 0, 0);
            long spendTime = System.currentTimeMillis() - startTime;
            if (balanceNetworkDelay.getValue()) {
              delayTimeMath.nextInteger((int) (spendTime / 3));
            }
            if (rpcEntity.getHasError()) {
              String errorCode = (String) XposedHelpers.callMethod(rpcEntity.getResponseObject(), "getString", "error");
              if ("1004".equals(errorCode)) {
                if (BaseModel.getWaitWhenException().getValue() > 0) {
                  long waitTime = System.currentTimeMillis() + BaseModel.getWaitWhenException().getValue();
                  RuntimeInfo.getInstance().put(RuntimeInfo.RuntimeInfoKey.ForestPauseTime, waitTime);
                  NotificationUtil.updateStatusText("异常");
                  LogUtil.record("触发异常,等待至" + TimeUtil.getCommonDate(waitTime));
                  return;
                }
                Thread.sleep(600 + RandomUtil.delay());
              }
              if (tryCount < tryCountInt) {
                collectEnergyEntity.setNeedRetry();
                collectEnergy(collectEnergyEntity);
              }
              return;
            }
            JSONObject jo = new JSONObject(rpcEntity.getResponseString());
            String resultCode = jo.getString("resultCode");
            if (!"SUCCESS".equalsIgnoreCase(resultCode)) {
              if ("PARAM_ILLEGAL2".equals(resultCode)) {
                LogUtil.record("[" + UserIdMapUtil.getMaskName(userId) + "]" + "能量已被收取,取消重试 错误:" + jo.getString("resultDesc"));
                return;
              }
              LogUtil.record("[" + UserIdMapUtil.getMaskName(userId) + "]" + jo.getString("resultDesc"));
              if (tryCount < tryCountInt) {
                collectEnergyEntity.setNeedRetry();
                collectEnergy(collectEnergyEntity);
              }
              return;
            }
            JSONArray jaBubbles = jo.getJSONArray("bubbles");
            int jaBubbleLength = jaBubbles.length();
            if (jaBubbleLength > 1) {
              List<Long> newBubbleIdList = new ArrayList<>();
              for (int i = 0; i < jaBubbleLength; i++) {
                JSONObject bubble = jaBubbles.getJSONObject(i);
                if (bubble.getBoolean("canBeRobbedAgain")) {
                  newBubbleIdList.add(bubble.getLong("id"));
                }
                collected += bubble.getInt("collectedEnergy");
              }
              if (collected > 0) {
                FriendWatch.friendWatch(userId, collected);
                String str = "一键收取🎈[" + UserIdMapUtil.getMaskName(userId) + "]#" + collected + "g";
                if (needDouble) {
                  LogUtil.forest(str + "耗时[" + spendTime + "]ms[双击]");
                  NotificationUtil.updateLastExecText(str);
                  Toast.show(str + "[双击]");
                } else {
                  LogUtil.forest(str + "耗时[" + spendTime + "]ms");
                  NotificationUtil.updateLastExecText(str);
                  Toast.show(str);
                }
                totalCollected += collected;
                StatisticsUtil.addData(StatisticsUtil.DataType.COLLECTED, collected);
              } else {
                LogUtil.record("一键收取[" + UserIdMapUtil.getMaskName(userId) + "]的能量失败" + " " + "，UserID：" + userId + "，BubbleId：" + newBubbleIdList);
              }
              if (!newBubbleIdList.isEmpty()) {
                collectEnergyEntity.setRpcEntity(AntForestRpcCall.getCollectBatchEnergyRpcEntity(userId, newBubbleIdList));
                collectEnergyEntity.setNeedDouble();
                collectEnergyEntity.resetTryCount();
                collectEnergy(collectEnergyEntity);
              }
            } else if (jaBubbleLength == 1) {
              JSONObject bubble = jaBubbles.getJSONObject(0);
              collected += bubble.getInt("collectedEnergy");
              FriendWatch.friendWatch(userId, collected);
              if (collected > 0) {
                String str = "收取能量🎈[" + UserIdMapUtil.getMaskName(userId) + "]#" + collected + "g";
                if (needDouble) {
                  LogUtil.forest(str + "耗时[" + spendTime + "]ms[双击]");
                  NotificationUtil.updateLastExecText(str);
                  Toast.show(str + "[双击]");
                } else {
                  LogUtil.forest(str + "耗时[" + spendTime + "]ms");
                  NotificationUtil.updateLastExecText(str);
                  Toast.show(str);
                }
                totalCollected += collected;
                StatisticsUtil.addData(StatisticsUtil.DataType.COLLECTED, collected);
              } else {
                LogUtil.record("收取[" + UserIdMapUtil.getMaskName(userId) + "]的能量失败");
                LogUtil.runtime("，UserID：" + userId + "，BubbleId：" + bubble.getLong("id"));
              }
              if (bubble.getBoolean("canBeRobbedAgain")) {
                collectEnergyEntity.setNeedDouble();
                collectEnergyEntity.resetTryCount();
                collectEnergy(collectEnergyEntity);
                return;
              }
              JSONObject userHome = collectEnergyEntity.getUserHome();
              if (userHome == null) {
                return;
              }
              String bizNo = userHome.optString("bizNo");
              if (bizNo.isEmpty()) {
                return;
              }
              int returnCount = 0;
              if (returnWater33.getValue() > 0 && collected >= returnWater33.getValue()) {
                returnCount = 33;
              } else if (returnWater18.getValue() > 0 && collected >= returnWater18.getValue()) {
                returnCount = 18;
              } else if (returnWater10.getValue() > 0 && collected >= returnWater10.getValue()) {
                returnCount = 10;
              }
              if (returnCount > 0) {
                returnFriendWater(userId, bizNo, 1, returnCount);
              }
            }
          } catch (Exception e) {
            LogUtil.runtime("collectEnergy err:");
            LogUtil.printStackTrace(e);
          } finally {
            StatisticsUtil.save();
            NotificationUtil.updateLastExecText("收:" + totalCollected + " 帮:" + totalHelpCollected);
            notifyMain();
          }
        };
    taskCount.incrementAndGet();
    if (joinThread) {
      runnable.run();
    } else {
      addChildTask(new ChildModelTask("CE|" + collectEnergyEntity.getUserId() + "|" + runnable.hashCode(), "CE", runnable));
    }
  }

  /** 更新使用中的的道具剩余时间 */
  private void updateSelfHomePage() throws JSONException {
    String s = AntForestRpcCall.queryHomePage();
    TimeUtil.sleep(100);
    JSONObject joHomePage = new JSONObject(s);
    updateSelfHomePage(joHomePage);
  }

  /**
   * 更新使用中的的道具剩余时间
   *
   * @param joHomePage 首页 JSON 对象
   */
  private void updateSelfHomePage(JSONObject joHomePage) {
    try {
      JSONArray usingUserPropsNew = joHomePage.getJSONArray("loginUserUsingPropNew"); // 查询这两个key的列表
      if (usingUserPropsNew.length() == 0) {
        usingUserPropsNew = joHomePage.getJSONArray("usingUserPropsNew");
      }
      // 遍历得到的列表，找到双击卡的剩余时间
      for (int i = 0; i < usingUserPropsNew.length(); i++) {
        JSONObject userUsingProp = usingUserPropsNew.getJSONObject(i);
        String propGroup = userUsingProp.getString("propGroup");
        // 获取propGroup字段
        switch (propGroup) {
          case "doubleClick": // 双击卡
            doubleEndTime = userUsingProp.getLong("endTime");
            // LogUtil.forest("双击卡剩余时间⏰" + (doubleEndTime - System.currentTimeMillis()) / 1000);
            break;
          case "robExpandCard": // 不知道什么卡，偷袭卡？
            String extInfo = userUsingProp.optString("extInfo");
            if (!extInfo.isEmpty()) {
              JSONObject extInfoObj = new JSONObject(extInfo);
              double leftEnergy = Double.parseDouble(extInfoObj.optString("leftEnergy", "0"));
              if (leftEnergy > 3000 || ("true".equals(extInfoObj.optString("overLimitToday", "false")) && leftEnergy >= 1)) {
                String propId = userUsingProp.getString("propId");
                String propType = userUsingProp.getString("propType");
                JSONObject jo = new JSONObject(AntForestRpcCall.collectRobExpandEnergy(propId, propType));
                if ("SUCCESS".equals(jo.getString("resultCode"))) {
                  int collectEnergy = jo.optInt("collectEnergy");
                  LogUtil.forest("额外能量🌳收取[" + collectEnergy + "g]");
                }
              }
            }
            break;
          case "stealthCard": // 隐身卡
            stealthEndTime = userUsingProp.getLong("endTime");
            break;
          case "shield": // 能量保护罩
            shieldEndTime = userUsingProp.getLong("endTime");
            break;
        }
      }
    } catch (Throwable th) {
      LogUtil.runtime(TAG, "updateDoubleTime err:");
      LogUtil.printStackTrace(TAG, th);
    }
  }

  /* 健康医疗 16g*6能量 */
  private void medicalHealthFeeds() {
    try {
      String s = AntForestRpcCall.query_forest_energy();
      JSONObject jo = new JSONObject(s);
      int countj = 0;
      if (jo.optBoolean("success")) {
        JSONObject response = jo.getJSONObject("data").getJSONObject("response");
        JSONArray energyGeneratedList = response.optJSONArray("energyGeneratedList");
        if (energyGeneratedList != null && energyGeneratedList.length() > 0) {
          harvestForestEnergy(energyGeneratedList);
        }
        int remainBubble = response.optInt("remainBubble", 0);
        if (remainBubble > 0) {
          jo = new JSONObject(AntForestRpcCall.medical_health_feeds_query());
          Thread.sleep(300);
          if ("SUCCESS".equals(jo.getString("resultCode"))) {
            response =
                Objects.requireNonNull(jo.getJSONObject("data").getJSONObject("response").optJSONObject("COMMON_FEEDS_BLOCK_2024041200243259"))
                    .getJSONObject("data")
                    .getJSONObject("response");
            JSONArray feeds = response.optJSONArray("feeds");
            if (feeds != null && feeds.length() > 0) {
              for (int i = 0; i < feeds.length(); i++) {
                jo = feeds.optJSONObject(i);
                if (jo == null) {
                  continue;
                }
                String feedId = jo.optString("feedId", "null");
                if (!"null".equals(feedId)) {
                  jo = new JSONObject(AntForestRpcCall.produce_forest_energy(feedId));
                  TimeUtil.sleep(300);
                  if (jo.optBoolean("success")) {
                    response = jo.getJSONObject("data").getJSONObject("response");
                    int cumulativeEnergy = response.optInt("cumulativeEnergy");
                    if (cumulativeEnergy > 0) {
                      LogUtil.forest("健康医疗🚑[完成一次]");
                      countj++;
                    }
                    energyGeneratedList = response.optJSONArray("energyGeneratedList");
                    if (energyGeneratedList != null && energyGeneratedList.length() > 0) {
                      harvestForestEnergy(energyGeneratedList);
                    }
                  }
                }
                if (countj >= remainBubble) {
                  break;
                }
              }
            }
          }
        }
      } else {
        LogUtil.record(jo.getString("resultDesc"));
      }
    } catch (Throwable t) {
      LogUtil.runtime(TAG, "medicalHealthFeeds err:");
      LogUtil.printStackTrace(TAG, t);
    }
  }

  private void harvestForestEnergy(JSONArray energyGeneratedList) {
    try {
      for (int i = 0; i < energyGeneratedList.length(); i++) {
        JSONObject jo = energyGeneratedList.getJSONObject(i);
        int energy = jo.optInt("energy");
        String id = jo.getString("id");
        jo = new JSONObject(AntForestRpcCall.harvest_forest_energy(energy, id));
        TimeUtil.sleep(300);
        if (jo.optBoolean("success")) {
          LogUtil.forest("健康医疗🚑[收取能量]#" + energy + "g");
        }
      }
    } catch (Throwable t) {
      LogUtil.runtime(TAG, "harvestForestEnergy err:");
      LogUtil.printStackTrace(TAG, t);
    }
  }

  /* 6秒拼手速 打地鼠 */
  private void whackMole() {
    try {
      long startTime = System.currentTimeMillis(); // 记录开始时间
      // 调用接口获取地鼠信息并解析为 JSON 对象
      JSONObject response = new JSONObject(AntForestRpcCall.startWhackMole());

      // 检查操作是否成功
      if (response.optBoolean("success")) {
        JSONArray moleInfoArray = response.optJSONArray("moleInfo");
        if (moleInfoArray != null) {
          List<String> moleIdList = new ArrayList<>();
          for (int i = 0; i < moleInfoArray.length(); i++) {
            JSONObject mole = moleInfoArray.getJSONObject(i);
            long moleId = mole.getLong("id");
            moleIdList.add(String.valueOf(moleId)); // 收集每个地鼠的 ID
          }

          if (!moleIdList.isEmpty()) {
            String token = response.getString("token"); // 获取令牌
            long elapsedTime = System.currentTimeMillis() - startTime; // 计算已耗时间
            Thread.sleep(Math.max(0, 6000 - elapsedTime)); // 睡眠至6秒

            // 调用接口进行结算
            response = new JSONObject(AntForestRpcCall.settlementWhackMole(token, moleIdList));
            if ("SUCCESS".equals(response.getString("resultCode"))) {
              int totalEnergy = response.getInt("totalEnergy");
              LogUtil.forest("森林能量⚡[获得:6秒拼手速能量 " + totalEnergy + "g]"); // 输出获取的能量
            }
          }
        }
      } else {
        // 输出错误信息
        LogUtil.runtime(TAG, response.getJSONObject("data").toString());
      }
    } catch (Throwable t) {
      // 捕获并记录异常
      LogUtil.runtime(TAG, "whackMole err:");
      LogUtil.printStackTrace(TAG, t);
    }
  }

  /* 关闭6秒拼手速 */
  private Boolean closeWhackMole() {
    try {
      JSONObject jo = new JSONObject(AntForestRpcCall.closeWhackMole());
      if (jo.optBoolean("success")) {
        return true;
      } else {
        LogUtil.runtime(TAG, jo.getString("resultDesc"));
      }
    } catch (Throwable t) {
      LogUtil.printStackTrace(t);
    }
    return false;
  }

  /* 森林集市 */
  private void sendEnergyByAction(String sourceType) {
    try {
      JSONObject jo = new JSONObject(AntForestRpcCall.consultForSendEnergyByAction(sourceType));
      if (jo.optBoolean("success")) {
        JSONObject data = jo.getJSONObject("data");
        if (data.optBoolean("canSendEnergy", false)) {
          jo = new JSONObject(AntForestRpcCall.sendEnergyByAction(sourceType));
          if (jo.optBoolean("success")) {
            data = jo.getJSONObject("data");
            if (data.optBoolean("canSendEnergy", false)) {
              int receivedEnergyAmount = data.getInt("receivedEnergyAmount");
              LogUtil.forest("集市逛街👀[获得:能量" + receivedEnergyAmount + "g]");
            }
          }
        }
      } else {
        LogUtil.runtime(TAG, jo.getJSONObject("data").getString("resultCode"));
      }
    } catch (Throwable t) {
      LogUtil.runtime(TAG, "sendEnergyByAction err:");
      LogUtil.printStackTrace(TAG, t);
    }
  }

  private void popupTask() {
    try {
      JSONObject resData = new JSONObject(AntForestRpcCall.popupTask());
      if ("SUCCESS".equals(resData.getString("resultCode"))) {
        JSONArray forestSignVOList = resData.optJSONArray("forestSignVOList");
        if (forestSignVOList != null) {
          for (int i = 0; i < forestSignVOList.length(); i++) {
            JSONObject forestSignVO = forestSignVOList.getJSONObject(i);
            String signId = forestSignVO.getString("signId");
            String currentSignKey = forestSignVO.getString("currentSignKey");
            JSONArray signRecords = forestSignVO.getJSONArray("signRecords");
            for (int j = 0; j < signRecords.length(); j++) {
              JSONObject signRecord = signRecords.getJSONObject(j);
              String signKey = signRecord.getString("signKey");
              if (signKey.equals(currentSignKey)) {
                if (!signRecord.getBoolean("signed")) {
                  JSONObject resData2 = new JSONObject(AntForestRpcCall.antiepSign(signId, UserIdMapUtil.getCurrentUid()));
                  if ("100000000".equals(resData2.getString("code"))) {
                    LogUtil.forest("过期能量💊[" + signRecord.getInt("awardCount") + "g]");
                  }
                }
                break;
              }
            }
          }
        }
      } else {
        LogUtil.record(resData.getString("resultDesc"));
        LogUtil.runtime(resData.toString());
      }
    } catch (Throwable t) {
      LogUtil.runtime(TAG, "popupTask err:");
      LogUtil.printStackTrace(TAG, t);
    }
  }

  private KVNode<Integer, Boolean> returnFriendWater(String userId, String bizNo, int count, int waterEnergy) {
    if (bizNo == null || bizNo.isEmpty()) {
      return new KVNode<>(0, true);
    }
    int wateredTimes = 0;
    boolean isContinue = true;
    try {
      String s;
      JSONObject jo;
      int energyId = getEnergyId(waterEnergy);
      label:
      for (int waterCount = 1; waterCount <= count; waterCount++) {
        s = AntForestRpcCall.transferEnergy(userId, bizNo, energyId);
        Thread.sleep(1500);
        jo = new JSONObject(s);
        String resultCode = jo.getString("resultCode");
        switch (resultCode) {
          case "SUCCESS":
            String currentEnergy = jo.getJSONObject("treeEnergy").getString("currentEnergy");
            LogUtil.forest("好友浇水🚿[" + UserIdMapUtil.getMaskName(userId) + "]#" + waterEnergy + "g，剩余能量[" + currentEnergy + "g]");
            wateredTimes++;
            StatisticsUtil.addData(StatisticsUtil.DataType.WATERED, waterEnergy);
            break;
          case "WATERING_TIMES_LIMIT":
            LogUtil.record("好友浇水🚿今日给[" + UserIdMapUtil.getMaskName(userId) + "]浇水已达上限");
            wateredTimes = 3;
            break label;
          case "ENERGY_INSUFFICIENT":
            LogUtil.record("好友浇水🚿" + jo.getString("resultDesc"));
            isContinue = false;
            break label;
          default:
            LogUtil.record("好友浇水🚿" + jo.getString("resultDesc"));
            LogUtil.runtime(jo.toString());
            break;
        }
      }
    } catch (Throwable t) {
      LogUtil.runtime(TAG, "returnFriendWater err:");
      LogUtil.printStackTrace(TAG, t);
    }
    return new KVNode<>(wateredTimes, isContinue);
  }

  private int getEnergyId(int waterEnergy) {
    if (waterEnergy <= 0) return 0;
    if (waterEnergy >= 66) return 42;
    if (waterEnergy >= 33) return 41;
    if (waterEnergy >= 18) return 40;
    return 39;
  }

  private void exchangeEnergyDoubleClick() {
    try {
      JSONObject jo = findPropShop("CR20230516000362", "CR20230516000363");
      while (StatusUtil.canExchangeDoubleCardToday() && exchangePropShop(jo, StatusUtil.INSTANCE.getExchangeTimes() + 1)) {
        StatusUtil.exchangeDoubleCardToday(true);
        TimeUtil.sleep(1000);
      }
    } catch (Throwable t) {
      LogUtil.runtime(TAG, "exchangeEnergyDoubleClick err:");
      LogUtil.printStackTrace(TAG, t);
    }
  }

  // 兑换永久双击卡
  private void exchangeEnergyDoubleClickLongTime(int count) {
    int exchangedTimes;
    try {
      String s = AntForestRpcCall.itemList("SC_ASSETS");
      JSONObject jo = new JSONObject(s);
      String skuId = null;
      String spuId = null;
      double price = 0d;
      if (jo.optBoolean("success")) {
        JSONArray itemInfoVOList = jo.optJSONArray("itemInfoVOList");
        if (itemInfoVOList != null && itemInfoVOList.length() > 0) {
          for (int i = 0; i < itemInfoVOList.length(); i++) {
            jo = itemInfoVOList.getJSONObject(i);
            if ("能量双击卡".equals(jo.getString("spuName"))) {
              JSONArray skuModelList = jo.getJSONArray("skuModelList");
              for (int j = 0; j < skuModelList.length(); j++) {
                jo = skuModelList.getJSONObject(j);
                if ("VITALITY_ENERGY_DOUBLE_CLICK_NO_EXPIRE_2023".equals(jo.getString("rightsConfigId"))) {
                  skuId = jo.getString("skuId");
                  spuId = jo.getString("spuId");
                  price = jo.getJSONObject("price").getDouble("amount");
                  break;
                }
              }
              break;
            }
          }
        }
        if (skuId != null) {
          for (int exchangeCount = 1; exchangeCount <= count; exchangeCount++) {
            if (StatusUtil.canExchangeDoubleCardTodayLongTime()) {
              jo = new JSONObject(AntForestRpcCall.queryVitalityStoreIndex());
              if ("SUCCESS".equals(jo.getString("resultCode"))) {
                int totalVitalityAmount = jo.getJSONObject("userVitalityInfoVO").getInt("totalVitalityAmount");
                if (totalVitalityAmount > price) {
                  jo = new JSONObject(AntForestRpcCall.exchangeBenefit(spuId, skuId));
                  Thread.sleep(1000);
                  if ("SUCCESS".equals(jo.getString("resultCode"))) {
                    StatusUtil.exchangeDoubleCardTodayLongTime(true);
                    exchangedTimes = StatusUtil.INSTANCE.getExchangeTimesLongTime();
                    LogUtil.forest("活力兑换🎐[永久双击卡]#第" + exchangedTimes + "次");
                  } else {
                    LogUtil.record(jo.getString("resultDesc"));
                    LogUtil.runtime(jo.toString());
                    StatusUtil.exchangeDoubleCardTodayLongTime(false);
                    break;
                  }
                } else {
                  LogUtil.record("活力值不足，停止兑换！");
                  break;
                }
              }
            } else {
              LogUtil.record("兑换次数已到上限！");
              break;
            }
          }
        }
      } else {
        LogUtil.record(jo.getString("desc"));
        LogUtil.runtime(s);
      }
    } catch (Throwable t) {
      LogUtil.runtime(TAG, "exchangeEnergyDoubleClickLongTime err:");
      LogUtil.printStackTrace(TAG, t);
    }
  }

  // 兑换 能量保护罩
  private void exchangeEnergyShield() {
    if (exchangePropShop(findPropShop("CR20230517000497", "CR20230516000371"), 1)) {
      StatusUtil.exchangeEnergyShield();
    }
  }

  // 兑换 神奇物种抽历史卡机会
  private void exchangeCollectHistoryAnimal7Days() {
    if (exchangePropShop(findPropShop("SP20230518000022", "SK20230518000062"), 1)) {
      StatusUtil.exchangeCollectHistoryAnimal7Days();
    }
  }

  // 兑换 神奇物种抽好友卡机会
  private void exchangeCollectToFriendTimes7Days() {
    if (exchangePropShop(findPropShop("SP20230518000021", "SK20230518000061"), 1)) {
      StatusUtil.exchangeCollectToFriendTimes7Days();
    }
  }

  /** 兑换隐身卡 */
  private boolean exchangeStealthCard() {
    return exchangePropShop(findPropShop("SP20230521000082", "SK20230521000206"), 1);
  }

  /** 森林任务 */
  private void receiveTaskAward() {
    try {
      // 循环控制标志
      do {
        boolean doubleCheck = false; // 标记是否需要再次检查任务
        String response = AntForestRpcCall.queryTaskList(); // 查询任务列表
        JSONObject jsonResponse = new JSONObject(response); // 解析响应为 JSON 对象

        // 检查响应结果码是否成功
        if ("SUCCESS".equals(jsonResponse.getString("resultCode"))) {
          JSONArray forestSignVOList = jsonResponse.getJSONArray("forestSignVOList");
          JSONObject forestSignVO = forestSignVOList.getJSONObject(0);
          String currentSignKey = forestSignVO.getString("currentSignKey"); // 当前签到的 key
          JSONArray signRecords = forestSignVO.getJSONArray("signRecords"); // 签到记录

          // 遍历签到记录，判断是否需要签到
          for (int i = 0; i < signRecords.length(); i++) {
            JSONObject signRecord = signRecords.getJSONObject(i);
            String signKey = signRecord.getString("signKey");
            if (signKey.equals(currentSignKey)) {
              // 如果未签到，执行签到
              if (!signRecord.getBoolean("signed")) {
                JSONObject joSign = new JSONObject(AntForestRpcCall.vitalitySign()); // 执行签到请求
                TimeUtil.sleep(300); // 等待300毫秒
                if ("SUCCESS".equals(joSign.getString("resultCode"))) {
                  LogUtil.forest("森林签到📆");
                }
              }
              break; // 签到完成，退出循环
            }
          }

          JSONArray forestTasksNew = jsonResponse.optJSONArray("forestTasksNew");
          if (forestTasksNew == null) return; // 如果没有新任务，则返回

          // 遍历每个新任务
          for (int i = 0; i < forestTasksNew.length(); i++) {
            JSONObject forestTask = forestTasksNew.getJSONObject(i);
            JSONArray taskInfoList = forestTask.getJSONArray("taskInfoList"); // 获取任务信息列表

            // 遍历每个任务信息
            for (int j = 0; j < taskInfoList.length(); j++) {
              JSONObject taskInfo = taskInfoList.getJSONObject(j);
              JSONObject taskBaseInfo = taskInfo.getJSONObject("taskBaseInfo"); // 获取任务基本信息
              JSONObject bizInfo = new JSONObject(taskBaseInfo.getString("bizInfo")); // 获取业务信息
              String taskType = taskBaseInfo.getString("taskType"); // 获取任务类型
              String taskTitle = bizInfo.optString("taskTitle", taskType); // 获取任务标题
              String awardCount = bizInfo.optString("awardCount", "1"); // 获取奖励数量
              String sceneCode = taskBaseInfo.getString("sceneCode"); // 获取场景代码
              String taskStatus = taskBaseInfo.getString("taskStatus"); // 获取任务状态

              // 如果任务已完成，领取任务奖励
              if (TaskStatus.FINISHED.name().equals(taskStatus)) {
                JSONObject joAward = new JSONObject(AntForestRpcCall.receiveTaskAward(sceneCode, taskType)); // 领取奖励请求
                TimeUtil.sleep(500); // 等待500毫秒
                if (joAward.optBoolean("success")) {
                  LogUtil.forest("任务奖励🎖️[" + taskTitle + "]#" + awardCount + "个");
                  doubleCheck = true; // 标记需要重新检查任务
                } else {
                  LogUtil.record("领取失败，" + response); // 记录领取失败信息
                  LogUtil.runtime(joAward.toString()); // 打印奖励响应
                }
              }
              // 如果任务待完成，执行完成逻辑
              else if (TaskStatus.TODO.name().equals(taskStatus)) {
                if (bizInfo.optBoolean("autoCompleteTask", false)
                    || AntForestTaskTypeSet.contains(taskType)
                    || taskType.endsWith("_JIASUQI")
                    || taskType.endsWith("_BAOHUDI")
                    || taskType.startsWith("GYG")) {
                  // 尝试完成任务
                  JSONObject joFinishTask = new JSONObject(AntForestRpcCall.finishTask(sceneCode, taskType)); // 完成任务请求
                  TimeUtil.sleep(500); // 等待500毫秒
                  if (joFinishTask.optBoolean("success")) {
                    LogUtil.forest("森林任务🧾️[" + taskTitle + "]");
                    doubleCheck = true; // 标记需要重新检查任务
                  } else {
                    LogUtil.record("完成任务失败，" + taskTitle); // 记录完成任务失败信息
                  }
                }
                // 特殊任务处理
                else if ("DAKA_GROUP".equals(taskType) || "TEST_LEAF_TASK".equals(taskType)) {
                  JSONArray childTaskTypeList = taskInfo.optJSONArray("childTaskTypeList");
                  if (childTaskTypeList != null && childTaskTypeList.length() > 0) {
                    doChildTask(childTaskTypeList, taskTitle); // 处理子任务
                    if ("TEST_LEAF_TASK".equals(taskType)) {
                      doubleCheck = true; // 标记需要重新检查任务
                    }
                  }
                }
              }
            }
          }

          // 如果需要重新检查任务，则继续循环
          if (doubleCheck) continue;
        } else {
          LogUtil.record(jsonResponse.getString("resultDesc")); // 记录失败描述
          LogUtil.runtime(response); // 打印响应内容
        }
        break; // 退出循环
      } while (true);
    } catch (Throwable t) {
      LogUtil.runtime(TAG, "receiveTaskAward 错误:");
      LogUtil.printStackTrace(TAG, t); // 打印异常栈
    }
  }

  private void doChildTask(JSONArray childTaskTypeList, String title) {
    try {
      for (int i = 0; i < childTaskTypeList.length(); i++) {
        JSONObject taskInfo = childTaskTypeList.getJSONObject(i);
        JSONObject taskBaseInfo = taskInfo.getJSONObject("taskBaseInfo");
        JSONObject bizInfo = new JSONObject(taskBaseInfo.getString("bizInfo"));
        String taskType = taskBaseInfo.getString("taskType");
        String taskTitle = bizInfo.optString("taskTitle", title);
        String sceneCode = taskBaseInfo.getString("sceneCode");
        String taskStatus = taskBaseInfo.getString("taskStatus");
        if (TaskStatus.TODO.name().equals(taskStatus)) {
          if (bizInfo.optBoolean("autoCompleteTask")) {
            JSONObject joFinishTask = new JSONObject(AntForestRpcCall.finishTask(sceneCode, taskType));
            TimeUtil.sleep(500);
            if (joFinishTask.optBoolean("success")) {
              LogUtil.forest("完成任务🧾️[" + taskTitle + "]");
            } else {
              LogUtil.record("完成任务" + taskTitle + "失败,");
              LogUtil.runtime(joFinishTask.toString());
            }
          }
        }
      }
    } catch (Throwable th) {
      LogUtil.runtime(TAG, "doChildTask err:");
      LogUtil.printStackTrace(TAG, th);
    }
  }

  private void startEnergyRain() {
    try {
      JSONObject jo = new JSONObject(AntForestRpcCall.startEnergyRain());
      Thread.sleep(500);
      if ("SUCCESS".equals(jo.getString("resultCode"))) {
        String token = jo.getString("token");
        JSONArray bubbleEnergyList = jo.getJSONObject("difficultyInfo").getJSONArray("bubbleEnergyList");
        int sum = 0;
        for (int i = 0; i < bubbleEnergyList.length(); i++) {
          sum += bubbleEnergyList.getInt(i);
        }
        Thread.sleep(5000L);
        if ("SUCCESS".equals(new JSONObject(AntForestRpcCall.energyRainSettlement(sum, token)).getString("resultCode"))) {
          Toast.show("获得了[" + sum + "g]能量[能量雨]");
          LogUtil.forest("收能量雨🌧️[" + sum + "g]");
        }
        Thread.sleep(500);
      }
    } catch (Throwable th) {
      LogUtil.runtime(TAG, "startEnergyRain err:");
      LogUtil.printStackTrace(TAG, th);
    }
  }

  private void energyRain() {
    try {
      JSONObject joEnergyRainHome = new JSONObject(AntForestRpcCall.queryEnergyRainHome());
      Thread.sleep(500);
      if ("SUCCESS".equals(joEnergyRainHome.getString("resultCode"))) {
        if (joEnergyRainHome.getBoolean("canPlayToday")) {
          startEnergyRain();
        }
        if (joEnergyRainHome.getBoolean("canGrantStatus")) {
          LogUtil.record("有送能量雨的机会");
          JSONObject joEnergyRainCanGrantList = new JSONObject(AntForestRpcCall.queryEnergyRainCanGrantList());
          Thread.sleep(500);
          JSONArray grantInfos = joEnergyRainCanGrantList.getJSONArray("grantInfos");
          Set<String> set = giveEnergyRainList.getValue();
          String userId;
          boolean granted = false;
          for (int j = 0; j < grantInfos.length(); j++) {
            JSONObject grantInfo = grantInfos.getJSONObject(j);
            if (grantInfo.getBoolean("canGrantedStatus")) {
              userId = grantInfo.getString("userId");
              if (set.contains(userId)) {
                JSONObject joEnergyRainChance = new JSONObject(AntForestRpcCall.grantEnergyRainChance(userId));
                TimeUtil.sleep(500);
                LogUtil.record("尝试送能量雨给【" + UserIdMapUtil.getMaskName(userId) + "】");
                granted = true;
                // 20230724能量雨调整为列表中没有可赠送的好友则不赠送
                if ("SUCCESS".equals(joEnergyRainChance.getString("resultCode"))) {
                  LogUtil.forest("送能量雨🌧️[" + UserIdMapUtil.getMaskName(userId) + "]#" + UserIdMapUtil.getMaskName(UserIdMapUtil.getCurrentUid()));
                  startEnergyRain();
                } else {
                  LogUtil.record("送能量雨失败");
                  LogUtil.runtime(joEnergyRainChance.toString());
                }
                break;
              }
            }
          }
          if (!granted) {
            LogUtil.record("没有可以送的用户");
          }
        }
      }
      joEnergyRainHome = new JSONObject(AntForestRpcCall.queryEnergyRainHome());
      Thread.sleep(500);
      if ("SUCCESS".equals(joEnergyRainHome.getString("resultCode")) && joEnergyRainHome.getBoolean("canPlayToday")) {
        startEnergyRain();
      }
    } catch (Throwable th) {
      LogUtil.runtime(TAG, "energyRain err:");
      LogUtil.printStackTrace(TAG, th);
    }
  }

  /**
   * 在收集能量之前使用道具。 这个方法检查是否需要使用增益卡，并在需要时使用相应的道具。
   *
   * @param userId 用户的ID。
   */
  private void usePropBeforeCollectEnergy(String userId) {
    try {
      // 如果是自己的账号，直接返回，不需要使用道具
      if (Objects.equals(selfId, userId)) {
        return;
      }
      // 检查是否需要使用效果卡片
      boolean needDouble = doubleCard.getValue() && doubleEndTime < System.currentTimeMillis();
      boolean needStealth = stealthCard.getValue() && stealthEndTime < System.currentTimeMillis();

      // 如果需要使用增益卡，进行同步操作
      if (needDouble || needStealth) {
        synchronized (doubleCardLockObj) {
          // 获取背包对象
          JSONObject bagObject = getBag();
          // 如果需要使用双击卡
          if (needDouble) useDoubleCard(bagObject);
          // 如果需要使用隐身卡
          if (needStealth) useStealthCard(bagObject);

        }
      }
    } catch (Exception e) {
      // 打印异常信息
      LogUtil.printStackTrace(e);
    }
  }

  /**
   * 使用双击卡道具。 这个方法检查是否满足使用双击卡的条件，如果满足，则在背包中查找并使用双击卡。
   *
   * @param bagObject 背包的JSON对象。
   */
  private void useDoubleCard(JSONObject bagObject) {
    try {
      // 检查是否有双击卡使用时间且今天可以使用双击卡
      if (hasDoubleCardTime() && StatusUtil.canDoubleToday()) {
        // 在背包中查找限时能量双击卡
        JSONObject jo = findPropBag(bagObject, "LIMIT_TIME_ENERGY_DOUBLE_CLICK");
        // 如果没有限时能量双击卡且开启了限时双击永动机
        if (jo == null && doubleCardConstant.getValue()) {
          // 在商店兑换限时能量双击卡
          if (exchangePropShop(findPropShop("CR20230516000362", "CR20230516000363"), StatusUtil.INSTANCE.getExchangeTimes() + 1)) {
            StatusUtil.exchangeDoubleCardToday(true);
            // 兑换成功后再次查找限时能量双击卡
            jo = findPropBag(bagObject, "LIMIT_TIME_ENERGY_DOUBLE_CLICK");
          }
        }
        // 如果没有找到限时能量双击卡，则查找普通能量双击卡
        if (jo == null) {
          jo = findPropBag(bagObject, "ENERGY_DOUBLE_CLICK");
        }
        // 如果找到了能量双击卡并成功使用
        if (jo != null && usePropBag(jo)) {
          // 设置双击卡结束时间
          doubleEndTime = System.currentTimeMillis() + 1000 * 60 * 5;
          // 双击卡使用次数+1
          StatusUtil.DoubleToday();
        } else {
          // 如果没有找到或使用失败，则更新双击卡时间
          updateSelfHomePage();
        }
      }
    } catch (Throwable th) {
      // 打印异常信息
      LogUtil.error(TAG + "useDoubleCard err:");
      LogUtil.printStackTrace(TAG, th);
    }
  }

  /**
   * 使用隐身卡道具。 这个方法检查是否满足使用隐身卡的条件，如果满足，则在背包中查找并使用隐身卡。
   *
   * @param bagObject 背包的JSON对象。
   */
  private void useStealthCard(JSONObject bagObject) {
    try {
      // 在背包中查找限时隐身卡
      JSONObject jo = findPropBag(bagObject, "LIMIT_TIME_STEALTH_CARD");
      // 如果没有限时隐身卡且开启了限时隐身永动机
      if (jo == null && stealthCardConstant.getValue()) {
        // 在商店兑换限时隐身卡
        if (exchangeStealthCard()) {
          // 兑换成功后再次查找限时隐身卡
          jo = findPropBag(bagObject, "LIMIT_TIME_STEALTH_CARD");
        }
      }
      // 如果没有找到限时隐身卡，则查找普通隐身卡
      if (jo == null) {
        jo = findPropBag(bagObject, "STEALTH_CARD");
      }
      // 如果找到了隐身卡并成功使用
      if (jo != null && usePropBag(jo)) {
        // 设置隐身卡结束时间
        stealthEndTime = System.currentTimeMillis() + 1000 * 60 * 60 * 24;
      } else {
        // 如果没有找到或使用失败，则更新隐身卡时间
        updateSelfHomePage();
      }
    } catch (Throwable th) {
      // 打印异常信息
      LogUtil.error(TAG + "useStealthCard err:");
      LogUtil.printStackTrace(TAG, th);
    }
  }

  /** 使用能量保护罩，一般是限时保护罩，打开 青春特权森林道具领取 */
  private void useShieldCard(JSONObject bagObject) {
    try {
      // 在背包中查询限时保护罩
      JSONObject jo = findPropBag(bagObject, "LIMIT_TIME_ENERGY_SHIELD_TREE");
      if (jo == null) {
        if (youthPrivilege.getValue()) {
          youthPrivilege();
          jo = findPropBag(bagObject, "LIMIT_TIME_ENERGY_SHIELD_TREE"); // 重新查找
        } else {
          jo = findPropBag(bagObject, "ENERGY_SHIELD"); // 尝试查找 普通保护罩，一般用不到
        }
      }
      if (jo != null && usePropBag(jo)) {
        LogUtil.forest("使用能量保护罩🛡️");
        shieldEndTime = System.currentTimeMillis() + 1000 * 60 * 60 * 24;
      } else {
        updateSelfHomePage();
      }
      LogUtil.other("保护罩剩余时间🛡️："+(stealthEndTime-System.currentTimeMillis())/1000/60/60/24+"天");
    } catch (Throwable th) {
      LogUtil.error(TAG + "useShieldCard err:");
    }
  }

  /**
   * 检查当前时间是否在设置的使用双击卡时间内
   *
   * @return 如果当前时间在双击卡的有效时间范围内，返回true；否则返回false。
   */
  private boolean hasDoubleCardTime() {
    // 获取当前时间的毫秒数
    long currentTimeMillis = System.currentTimeMillis();
    // 使用Thread工具类检查当前时间是否在双击卡设定的有效时间范围内
    return TimeUtil.checkInTimeRange(currentTimeMillis, doubleCardTime.getValue());
  }

  /**
   * 向指定用户赠送道具。 这个方法首先查询可用的道具列表，然后选择一个道具赠送给目标用户。 如果有多个道具可用，会尝试继续赠送，直到所有道具都赠送完毕。
   *
   * @param targetUserId 目标用户的ID。
   */
  private void giveProp(String targetUserId) {
    try {
      // 循环赠送道具，直到没有更多道具可赠送
      do {
        // 查询道具列表
        JSONObject propListJo = new JSONObject(AntForestRpcCall.queryPropList(true));
        // 检查查询结果是否成功
        if ("SUCCESS".equals(propListJo.getString("resultCode"))) {
          // 获取道具列表
          JSONArray forestPropVOList = propListJo.optJSONArray("forestPropVOList");
          // 如果有可用的道具
          if (forestPropVOList != null && forestPropVOList.length() > 0) {
            // 选择第一个道具
            JSONObject propJo = forestPropVOList.getJSONObject(0);
            // 获取赠送配置ID、持有数量、道具名称和道具ID
            String giveConfigId = propJo.getJSONObject("giveConfigVO").getString("giveConfigId");
            int holdsNum = propJo.optInt("holdsNum", 0);
            String propName = propJo.getJSONObject("propConfigVO").getString("propName");
            String propId = propJo.getJSONArray("propIdList").getString(0);
            // 赠送道具
            JSONObject giveResultJo = new JSONObject(AntForestRpcCall.giveProp(giveConfigId, propId, targetUserId));
            // 如果赠送成功
            if ("SUCCESS".equals(giveResultJo.getString("resultCode"))) {
              // 记录赠送成功的日志
              LogUtil.forest("赠送道具🎭[" + UserIdMapUtil.getMaskName(targetUserId) + "]#" + propName);
            } else {
              // 记录赠送失败的日志
              LogUtil.record(giveResultJo.getString("resultDesc"));
              LogUtil.runtime(giveResultJo.toString());
            }
            // 如果持有数量大于1或道具列表中有多于一个道具，则继续赠送
            if (holdsNum <= 1 && forestPropVOList.length() == 1) {
              break;
            }
          }
        } else {
          // 如果查询道具列表失败，则记录失败的日志
          LogUtil.record(propListJo.getString("resultDesc"));
          LogUtil.runtime(propListJo.toString());
        }
        // 等待1.5秒后再继续
        TimeUtil.sleep(1500);
      } while (true);
    } catch (Throwable th) {
      // 打印异常信息
      LogUtil.runtime(TAG, "giveProp err:");
      LogUtil.printStackTrace(TAG, th);
    }
  }

  /**
   * 执行绿色行动任务，包括查询任务开通状态、开通绿色任务、执行打卡任务等操作。 1. 调用接口查询绿色行动的首页数据，检查是否成功。 2. 如果绿色任务尚未开通，且用户未开通绿色任务，则记录日志并返回。 3. 如果绿色任务尚未开通，且用户已开通绿色任务，则尝试开通绿色任务。 4. 开通绿色任务成功后，再次查询任务状态，并更新数据。 5.
   * 获取任务的日期标识和任务列表，执行打卡任务。 6. 如果绿色打卡设置为启用，执行 `ecoLifeTick` 方法提交打卡任务。 7. 如果光盘打卡设置为启用，执行 `photoGuangPan` 方法上传光盘照片。 8. 异常发生时，记录错误信息并打印堆栈。
   */
  private void ecoLife() {
    try {
      // 查询绿色行动首页数据
      JSONObject jsonObject = new JSONObject(AntForestRpcCall.ecolifeQueryHomePage());
      // 检查接口是否调用成功
      if (!jsonObject.optBoolean("success")) {
        LogUtil.runtime(TAG + ".ecoLife.queryHomePage", jsonObject.optString("resultDesc"));
        return;
      }
      JSONObject data = jsonObject.getJSONObject("data");
      // 判断绿色任务是否开通，若未开通且用户未启用绿色任务，则记录日志并返回
      if (!data.getBoolean("openStatus") && !ecoLifeOpen.getValue()) {
        LogUtil.forest("绿色任务☘未开通");
        return;
      } else if (!data.getBoolean("openStatus")) {
        // 如果绿色任务未开通，尝试开通绿色任务
        jsonObject = new JSONObject(AntForestRpcCall.ecolifeOpenEcolife());
        if (!jsonObject.optBoolean("success")) {
          LogUtil.runtime(TAG + ".ecoLife.openEcolife", jsonObject.optString("resultDesc"));
          return;
        }
        // 确认绿色任务开通成功
        if (!String.valueOf(true).equals(JsonUtil.getValueByPath(jsonObject, "data.opResult"))) {
          return;
        }
        LogUtil.forest("绿色任务🍀报告大人，开通成功(～￣▽￣)～可以愉快的玩耍了");
        jsonObject = new JSONObject(AntForestRpcCall.ecolifeQueryHomePage());
        data = jsonObject.getJSONObject("data");
      }
      // 获取任务日期标识和任务列表
      String dayPoint = data.getString("dayPoint");
      JSONArray actionListVO = data.getJSONArray("actionListVO");
      // 执行打卡任务
      if (ecoLifeTick.getValue()) {
        ecoLifeTick(actionListVO, dayPoint);
      }
      // 执行光盘打卡任务
      if (photoGuangPan.getValue()) {
        photoGuangPan(dayPoint);
      }
    } catch (Throwable th) {
      // 记录异常信息
      LogUtil.runtime(TAG, "ecoLife err:");
      LogUtil.printStackTrace(TAG, th);
    }
  }

  /**
   * 执行绿色行动打卡任务，遍历任务列表，依次提交每个未完成的任务。 1. 遍历给定的任务列表（`actionListVO`），每个任务项包含多个子任务。 2. 对于每个子任务，检查其是否已完成，如果未完成则提交打卡请求。 3. 特别处理任务 ID 为 "photoguangpan" 的任务，跳过该任务的打卡。 4.
   * 如果任务打卡成功，记录成功日志；否则记录失败原因。 5. 每次打卡请求后，等待 500 毫秒以避免请求过于频繁。 6. 异常发生时，记录详细的错误信息。
   *
   * @param actionListVO 任务列表，每个任务包含多个子任务
   * @param dayPoint 任务的日期标识，用于标识任务的日期
   */
  private void ecoLifeTick(JSONArray actionListVO, String dayPoint) {
    try {
      String source = "source"; // 任务来源标识
      // 遍历每个任务项
      for (int i = 0; i < actionListVO.length(); i++) {
        JSONObject actionVO = actionListVO.getJSONObject(i);
        JSONArray actionItemList = actionVO.getJSONArray("actionItemList");
        // 遍历每个任务项中的子任务
        for (int j = 0; j < actionItemList.length(); j++) {
          JSONObject actionItem = actionItemList.getJSONObject(j);
          // 检查任务项是否包含 "actionId" 字段，若没有则跳过
          if (!actionItem.has("actionId")) {
            continue;
          }
          // 检查任务项的状态，如果已完成则跳过
          if (actionItem.getBoolean("actionStatus")) {
            continue;
          }
          String actionId = actionItem.getString("actionId");
          String actionName = actionItem.getString("actionName");
          // 特殊处理任务ID为"photoguangpan"的任务，跳过不执行
          if ("photoguangpan".equals(actionId)) {
            continue;
          }
          // 提交打卡请求
          JSONObject jo = new JSONObject(AntForestRpcCall.ecolifeTick(actionId, dayPoint, source));
          // 如果任务打卡成功，记录成功日志
          if ("SUCCESS".equals(jo.getString("resultCode"))) {
            LogUtil.forest("绿色打卡🍀[" + actionName + "]"); // 成功打卡日志
          } else {
            // 记录失败原因
            LogUtil.error(TAG + jo.getString("resultDesc"));
            LogUtil.error(TAG + jo);
          }
          // 每次请求后等待500毫秒，避免请求过于频繁
          TimeUtil.sleep(500);
        }
      }
    } catch (Throwable th) {
      // 捕获异常并记录错误信息
      LogUtil.runtime(TAG, "ecoLifeTick err:");
      LogUtil.printStackTrace(TAG, th);
    }
  }

  /**
   * 执行光盘行动任务，上传餐前餐后照片并提交任务。 1. 查询当前任务的状态。 2. 如果任务未完成，检查是否已有餐前餐后照片的URL，如果没有则从接口获取并保存。 3. 上传餐前餐后照片，上传成功后提交任务，标记任务为完成。 4. 如果任务已完成，则不做任何操作。 5. 如果遇到任何错误，记录错误信息并停止执行。
   *
   * @param dayPoint 任务的日期标识，用于标识任务的日期
   */
  private void photoGuangPan(String dayPoint) {
    try {
      String source = "renwuGD"; // 任务来源标识
      // 查询今日任务状态
      String str = AntForestRpcCall.ecolifeQueryDish(source, dayPoint);
      JSONObject jsonObject = new JSONObject(str);
      // 如果请求失败，则记录错误信息并返回
      if (!jsonObject.optBoolean("success")) {
        LogUtil.runtime(TAG + ".photoGuangPan.ecolifeQueryDish", jsonObject.optString("resultDesc"));
        return;
      }
      boolean isDone = false; // 任务是否完成的标志
      // 获取餐前餐后照片的URL
      String photoGuangPanBeforeStr = photoGuangPanBefore.getValue();
      String photoGuangPanAfterStr = photoGuangPanAfter.getValue();
      // 如果没有照片URL或两者相同，需重新获取照片URL
      if (StringUtil.isEmpty(photoGuangPanBeforeStr) || StringUtil.isEmpty(photoGuangPanAfterStr) || Objects.equals(photoGuangPanBeforeStr, photoGuangPanAfterStr)) {
        // 获取任务返回的数据
        JSONObject data = jsonObject.optJSONObject("data");
        if (data != null) {
          String beforeMealsImageUrl = data.optString("beforeMealsImageUrl");
          String afterMealsImageUrl = data.optString("afterMealsImageUrl");
          // 如果餐前和餐后照片URL都存在，进行提取
          if (!StringUtil.isEmpty(beforeMealsImageUrl) && !StringUtil.isEmpty(afterMealsImageUrl)) {
            // 使用正则从URL中提取照片的路径部分
            Pattern pattern = Pattern.compile("img/(.*)/original");
            Matcher beforeMatcher = pattern.matcher(beforeMealsImageUrl);
            if (beforeMatcher.find()) {
              photoGuangPanBeforeStr = beforeMatcher.group(1);
              photoGuangPanBefore.setValue(photoGuangPanBeforeStr); // 保存餐前照片路径
            }
            Matcher afterMatcher = pattern.matcher(afterMealsImageUrl);
            if (afterMatcher.find()) {
              photoGuangPanAfterStr = afterMatcher.group(1);
              photoGuangPanAfter.setValue(photoGuangPanAfterStr); // 保存餐后照片路径
            }
            // 保存配置
            Config.save(UserIdMapUtil.getCurrentUid(), false);
            isDone = true;
          }
        }
      } else {
        // 如果已有有效的照片URL，标记任务为已完成
        isDone = true;
      }
      // 如果任务已完成，直接返回
      if ("SUCCESS".equals(JsonUtil.getValueByPath(jsonObject, "data.status"))) {
        LogUtil.other("光盘行动🍽️今日已完成");
        return;
      }
      // 如果没有完成光盘打卡，提示用户先完成
      if (!isDone) {
        LogUtil.forest("光盘行动🍽️请先完成一次光盘打卡");
        return;
      }
      // 上传餐前照片
      str = AntForestRpcCall.ecolifeUploadDishImage("BEFORE_MEALS", photoGuangPanBeforeStr, 0.16571736, 0.07448776, 0.7597949, dayPoint);
      jsonObject = new JSONObject(str);
      // 如果上传失败，记录错误信息并返回
      if (!jsonObject.optBoolean("success")) {
        LogUtil.runtime(TAG + ".photoGuangPan.uploadDishImage", jsonObject.optString("resultDesc"));
        return;
      }
      // 上传餐后照片
      str = AntForestRpcCall.ecolifeUploadDishImage("AFTER_MEALS", photoGuangPanAfterStr, 0.00040030346, 0.99891376, 0.0006858421, dayPoint);
      jsonObject = new JSONObject(str);
      // 如果上传失败，记录错误信息并返回
      if (!jsonObject.optBoolean("success")) {
        LogUtil.runtime(TAG + ".photoGuangPan.uploadDishImage", jsonObject.optString("resultDesc"));
        return;
      }
      // 提交任务
      str = AntForestRpcCall.ecolifeTick("photoguangpan", dayPoint, source);
      jsonObject = new JSONObject(str);
      // 如果提交失败，记录错误信息并返回
      if (!jsonObject.optBoolean("success")) {
        LogUtil.runtime(TAG + ".photoGuangPan.tick", jsonObject.optString("resultDesc"));
        return;
      }
      // 任务完成，输出完成日志
      LogUtil.forest("光盘行动🍽️任务完成");
    } catch (Throwable t) {
      // 捕获异常，记录错误信息和堆栈追踪
      LogUtil.runtime(TAG, "photoGuangPan err:");
      LogUtil.printStackTrace(TAG, t);
    }
  }

  /** 查询并管理用户巡护任务 */
  private void queryUserPatrol() {
    int waitTime = 500;
    try {
      do {
        // 查询当前巡护任务
        JSONObject jo = new JSONObject(AntForestRpcCall.queryUserPatrol());
        TimeUtil.sleep(waitTime); // 防止请求过于频繁
        // 如果查询成功
        if ("SUCCESS".equals(jo.getString("resultCode"))) {
          // 查询我的巡护记录
          JSONObject resData = new JSONObject(AntForestRpcCall.queryMyPatrolRecord());
          TimeUtil.sleep(waitTime); // 防止请求过于频繁
          // 如果可以切换巡护地图
          if (resData.optBoolean("canSwitch")) {
            JSONArray records = resData.getJSONArray("records");
            for (int i = 0; i < records.length(); i++) {
              JSONObject record = records.getJSONObject(i);
              JSONObject userPatrol = record.getJSONObject("userPatrol");
              // 如果存在未到达的节点，且当前模式为"silent"，则尝试切换巡护地图
              if (userPatrol.getInt("unreachedNodeCount") > 0) {
                if ("silent".equals(userPatrol.getString("mode"))) {
                  JSONObject patrolConfig = record.getJSONObject("patrolConfig");
                  String patrolId = patrolConfig.getString("patrolId");
                  resData = new JSONObject(AntForestRpcCall.switchUserPatrol(patrolId));
                  TimeUtil.sleep(waitTime); // 防止请求过于频繁
                  // 如果切换成功，打印日志并继续
                  if ("SUCCESS".equals(resData.getString("resultCode"))) {
                    LogUtil.forest("巡护⚖️-切换地图至" + patrolId);
                  }
                  continue; // 跳过当前循环
                }
                break; // 如果当前不是silent模式，则结束循环
              }
            }
          }
          // 获取用户当前巡护状态信息
          JSONObject userPatrol = jo.getJSONObject("userPatrol");
          int currentNode = userPatrol.getInt("currentNode");
          String currentStatus = userPatrol.getString("currentStatus");
          int patrolId = userPatrol.getInt("patrolId");
          JSONObject chance = userPatrol.getJSONObject("chance");
          int leftChance = chance.getInt("leftChance");
          int leftStep = chance.getInt("leftStep");
          int usedStep = chance.getInt("usedStep");
          // 当前巡护状态为"STANDING"
          if ("STANDING".equals(currentStatus)) {
            // 如果还有剩余的巡护次数，则开始巡护
            if (leftChance > 0) {
              jo = new JSONObject(AntForestRpcCall.patrolGo(currentNode, patrolId));
              TimeUtil.sleep(waitTime); // 防止请求过于频繁
              patrolKeepGoing(jo.toString(), currentNode, patrolId); // 继续巡护
              continue; // 跳过当前循环
            }
            // 如果没有剩余的巡护次数但步数足够，则兑换巡护次数
            else if (leftStep >= 2000 && usedStep < 10000) {
              jo = new JSONObject(AntForestRpcCall.exchangePatrolChance(leftStep));
              TimeUtil.sleep(waitTime); // 防止请求过于频繁
              // 兑换成功，增加巡护次数
              if ("SUCCESS".equals(jo.getString("resultCode"))) {
                int addedChance = jo.optInt("addedChance", 0);
                LogUtil.forest("步数兑换⚖️[巡护次数*" + addedChance + "]");
                continue; // 跳过当前循环
              } else {
                LogUtil.runtime(TAG, jo.getString("resultDesc"));
              }
            }
          }
          // 如果巡护状态为"GOING"，继续巡护
          else if ("GOING".equals(currentStatus)) {
            patrolKeepGoing(null, currentNode, patrolId);
          }
        } else {
          LogUtil.runtime(TAG, jo.getString("resultDesc"));
        }
        break; // 完成一次巡护任务后退出循环
      } while (true);
    } catch (Throwable t) {
      LogUtil.runtime(TAG, "queryUserPatrol err:");
      LogUtil.printStackTrace(TAG, t); // 打印异常堆栈
    }
  }

  /**
   * 持续巡护森林，直到巡护状态不再是“进行中”
   *
   * @param s 巡护请求的响应字符串，若为null将重新请求
   * @param nodeIndex 当前节点索引
   * @param patrolId 巡护任务ID
   */
  private void patrolKeepGoing(String s, int nodeIndex, int patrolId) {
    try {
      // 持续巡护，直到状态不是“进行中”
      do {
        // 若响应字符串为空，则重新请求巡护数据
        if (s == null) {
          s = AntForestRpcCall.patrolKeepGoing(nodeIndex, patrolId, "image");
        }
        // 解析响应结果
        JSONObject jo = new JSONObject(s);
        if (!"SUCCESS".equals(jo.getString("resultCode"))) {
          LogUtil.runtime(TAG, jo.getString("resultDesc"));
          break;
        }
        // 获取巡护事件和用户巡护数据
        JSONArray events = jo.optJSONArray("events");
        if (events == null || events.length() == 0) {
          return; // 无事件，退出循环
        }
        JSONObject userPatrol = jo.getJSONObject("userPatrol");
        int currentNode = userPatrol.getInt("currentNode");
        // 获取奖励信息，并处理动物碎片奖励
        JSONObject event = events.getJSONObject(0);
        JSONObject rewardInfo = event.optJSONObject("rewardInfo");
        if (rewardInfo != null) {
          JSONObject animalProp = rewardInfo.optJSONObject("animalProp");
          if (animalProp != null) {
            JSONObject animal = animalProp.optJSONObject("animal");
            if (animal != null) {
              LogUtil.forest("巡护森林🏇🏻[" + animal.getString("name") + "碎片]");
            }
          }
        }
        // 如果巡护状态不是“进行中”，则退出循环
        if (!"GOING".equals(jo.getString("currentStatus"))) {
          return;
        }
        // 请求继续巡护
        JSONObject materialInfo = event.getJSONObject("materialInfo");
        String materialType = materialInfo.optString("materialType", "image");
        s = AntForestRpcCall.patrolKeepGoing(currentNode, patrolId, materialType);
        TimeUtil.sleep(100); // 等待100毫秒后继续巡护
      } while (true);
    } catch (Throwable t) {
      LogUtil.runtime(TAG, "patrolKeepGoing err:");
      LogUtil.printStackTrace(TAG, t);
    }
  }

  /** 查询可派遣的伙伴 */
  private void queryAnimalPropList() {
    try {
      // 查询动物属性列表
      JSONObject jo = new JSONObject(AntForestRpcCall.queryAnimalPropList());
      if (!"SUCCESS".equals(jo.getString("resultCode"))) {
        LogUtil.runtime(TAG, jo.getString("resultDesc"));
        return;
      }
      // 获取所有动物属性并选择可以派遣的伙伴
      JSONArray animalProps = jo.getJSONArray("animalProps");
      JSONObject bestAnimalProp = null;
      for (int i = 0; i < animalProps.length(); i++) {
        jo = animalProps.getJSONObject(i);
        if (bestAnimalProp == null || jo.getJSONObject("main").getInt("holdsNum") > bestAnimalProp.getJSONObject("main").getInt("holdsNum")) {
          bestAnimalProp = jo; // 选择最大数量的伙伴
        }
      }
      // 派遣伙伴
      consumeAnimalProp(bestAnimalProp);
    } catch (Throwable t) {
      LogUtil.runtime(TAG, "queryAnimalPropList err:");
      LogUtil.printStackTrace(TAG, t);
    }
  }

  /**
   * 派遣伙伴进行巡护
   *
   * @param animalProp 选择的动物属性
   */
  private void consumeAnimalProp(JSONObject animalProp) {
    if (animalProp == null) return; // 如果没有可派遣的伙伴，则返回

    try {
      // 获取伙伴的属性信息
      String propGroup = animalProp.getJSONObject("main").getString("propGroup");
      String propType = animalProp.getJSONObject("main").getString("propType");
      String name = animalProp.getJSONObject("partner").getString("name");

      // 调用API进行伙伴派遣
      JSONObject jo = new JSONObject(AntForestRpcCall.consumeProp(propGroup, propType, false));
      if ("SUCCESS".equals(jo.getString("resultCode"))) {
        LogUtil.forest("巡护派遣🐆[" + name + "]");
      } else {
        LogUtil.runtime(TAG, jo.getString("resultDesc"));
      }
    } catch (Throwable t) {
      LogUtil.runtime(TAG, "consumeAnimalProp err:");
      LogUtil.printStackTrace(TAG, t);
    }
  }

  /** 查询动物及碎片信息，并尝试合成可合成的动物碎片。 */
  private void queryAnimalAndPiece() {
    try {
      // 调用远程接口查询动物及碎片信息
      JSONObject response = new JSONObject(AntForestRpcCall.queryAnimalAndPiece(0));
      String resultCode = response.optString("resultCode");
      // 检查接口调用是否成功
      if (!"SUCCESS".equals(resultCode)) {
        LogUtil.runtime(TAG, "查询失败: " + response.optString("resultDesc"));
        return;
      }
      // 获取动物属性列表
      JSONArray animalProps = response.optJSONArray("animalProps");
      if (animalProps == null || animalProps.length() == 0) {
        LogUtil.runtime(TAG, "动物属性列表为空");
        return;
      }
      // 遍历动物属性
      for (int i = 0; i < animalProps.length(); i++) {
        JSONObject animalObject = animalProps.optJSONObject(i);
        if (animalObject == null) {
          continue;
        }
        JSONArray pieces = animalObject.optJSONArray("pieces");
        if (pieces == null || pieces.length() == 0) {
          LogUtil.runtime(TAG, "动物碎片列表为空");
          continue;
        }
        int animalId = Objects.requireNonNull(animalObject.optJSONObject("animal")).optInt("id", -1);
        if (animalId == -1) {
          LogUtil.runtime(TAG, "动物ID缺失");
          continue;
        }
        // 检查碎片是否满足合成条件
        if (canCombinePieces(pieces)) {
          combineAnimalPiece(animalId);
        }
      }
    } catch (Exception e) {
      LogUtil.runtime(TAG, "查询动物及碎片信息时发生错误:");
      LogUtil.printStackTrace(TAG, e);
    }
  }

  /**
   * 检查碎片是否满足合成条件。
   *
   * @param pieces 动物碎片数组
   * @return 如果所有碎片满足合成条件，返回 true；否则返回 false
   */
  private boolean canCombinePieces(JSONArray pieces) {
    for (int j = 0; j < pieces.length(); j++) {
      JSONObject pieceObject = pieces.optJSONObject(j);
      if (pieceObject == null || pieceObject.optInt("holdsNum", 0) <= 0) {
        return false;
      }
    }
    return true;
  }

  /**
   * 合成动物碎片。
   *
   * @param animalId 动物ID
   */
  private void combineAnimalPiece(int animalId) {
    try {
      while (true) {
        // 查询动物及碎片信息
        JSONObject response = new JSONObject(AntForestRpcCall.queryAnimalAndPiece(animalId));
        String resultCode = response.optString("resultCode");
        if (!"SUCCESS".equals(resultCode)) {
          LogUtil.runtime(TAG, "查询失败: " + response.optString("resultDesc"));
          break;
        }
        JSONArray animalProps = response.optJSONArray("animalProps");
        if (animalProps == null || animalProps.length() == 0) {
          LogUtil.runtime(TAG, "动物属性数据为空");
          break;
        }
        // 获取第一个动物的属性
        JSONObject animalProp = animalProps.getJSONObject(0);
        JSONObject animal = animalProp.optJSONObject("animal");
        assert animal != null;
        int id = animal.optInt("id", -1);
        String name = animal.optString("name", "未知动物");
        // 获取碎片信息
        JSONArray pieces = animalProp.optJSONArray("pieces");
        if (pieces == null || pieces.length() == 0) {
          LogUtil.runtime(TAG, "碎片数据为空");
          break;
        }
        boolean canCombineAnimalPiece = true;
        JSONArray piecePropIds = new JSONArray();
        // 检查所有碎片是否可用
        for (int j = 0; j < pieces.length(); j++) {
          JSONObject piece = pieces.optJSONObject(j);
          if (piece == null || piece.optInt("holdsNum", 0) <= 0) {
            canCombineAnimalPiece = false;
            LogUtil.runtime(TAG, "碎片不足，无法合成动物");
            break;
          }
          // 添加第一个道具ID
          piecePropIds.put(Objects.requireNonNull(piece.optJSONArray("propIdList")).optString(0, ""));
        }
        // 如果所有碎片可用，则尝试合成
        if (canCombineAnimalPiece) {
          JSONObject combineResponse = new JSONObject(AntForestRpcCall.combineAnimalPiece(id, piecePropIds.toString()));
          resultCode = combineResponse.optString("resultCode");
          if ("SUCCESS".equals(resultCode)) {
            LogUtil.forest("成功合成动物💡: [" + name + "]");
            animalId = id;
            TimeUtil.sleep(100); // 等待一段时间再查询
            continue;
          } else {
            LogUtil.runtime(TAG, "合成失败: " + combineResponse.optString("resultDesc"));
          }
        }
        break; // 如果不能合成或合成失败，跳出循环
      }
    } catch (Exception e) {
      LogUtil.runtime(TAG, "合成动物碎片时发生错误:");
      LogUtil.printStackTrace(TAG, e);
    }
  }

  /** 获取背包信息 */
  private JSONObject getBag() {
    try {
      // 获取背包信息
      JSONObject bagObject = new JSONObject(AntForestRpcCall.queryPropList(false));
      if (!"SUCCESS".equals(bagObject.getString("resultCode"))) {
        LogUtil.record(bagObject.getString("resultDesc"));
        LogUtil.runtime(bagObject.toString());
        return null;
      }
      return bagObject;
    } catch (Throwable th) {
      LogUtil.runtime(TAG, "findPropBag err:");
      LogUtil.printStackTrace(TAG, th);
    }
    return null;
  }

  /**
   * 查找背包道具
   *
   * @param bagObject 背包对象
   * @param propType 道具类型 LIMIT_TIME_ENERGY_SHIELD_TREE,...
   */
  private JSONObject findPropBag(JSONObject bagObject, String propType) {
    JSONObject prop = null;
    try {
      // 遍历背包查找道具
      JSONArray forestPropVOList = bagObject.getJSONArray("forestPropVOList");
      for (int i = 0; i < forestPropVOList.length(); i++) {
        JSONObject forestPropVO = forestPropVOList.getJSONObject(i);
        if (forestPropVO.getString("propType").equals(propType)) {
          prop = forestPropVO;
          break;
        }
      }
    } catch (Throwable th) {
      LogUtil.runtime(TAG, "findPropBag err:");
      LogUtil.printStackTrace(TAG, th);
    }
    return prop;
  }

  /*
   * 使用背包道具
   * prop
   * propGroup, propType, holdsNum, propIdList[], propConfigVO[propName]
   */
  private boolean usePropBag(JSONObject prop) {
    if (prop == null) {
      LogUtil.record("要使用的道具不存在！");
      return false;
    }
    try {
      // 使用道具
      JSONObject jo = new JSONObject(AntForestRpcCall.consumeProp(prop.getJSONArray("propIdList").getString(0), prop.getString("propType")));
      if ("SUCCESS".equals(jo.getString("resultCode"))) {
        LogUtil.forest("使用道具🎭[" + prop.getJSONObject("propConfigVO").getString("propName") + "]");
        return true;
      } else {
        LogUtil.record(jo.getString("resultDesc"));
        LogUtil.runtime(jo.toString());
        return false;
      }
    } catch (Throwable th) {
      LogUtil.runtime(TAG, "usePropBag err:");
      LogUtil.printStackTrace(TAG, th);
      return false;
    }
  }

  /** 查找商店道具 sku spuId, skuId, skuName, exchangedCount, price[amount] */
  private JSONObject findPropShop(String spuId, String skuId) {
    JSONObject sku;
    try {
      // 获取商店信息
      JSONObject itemListResponse = new JSONObject(AntForestRpcCall.itemList("SC_ASSETS"));
      if (!itemListResponse.optBoolean("success")) {
        LogUtil.record(itemListResponse.getString("desc"));
        LogUtil.runtime(itemListResponse.toString());
        return null;
      }
      // 遍历商店查找道具
      JSONArray itemInfoVOList = itemListResponse.optJSONArray("itemInfoVOList");
      if (itemInfoVOList == null) {
        return null;
      }
      for (int i = 0; i < itemInfoVOList.length(); i++) {
        JSONObject itemInfo = itemInfoVOList.getJSONObject(i);
        if (spuId.equals(itemInfo.optString("spuId"))) {
          JSONArray skuModelList = itemInfo.optJSONArray("skuModelList");
          if (skuModelList != null) {
            for (int j = 0; j < skuModelList.length(); j++) {
              JSONObject skuModel = skuModelList.getJSONObject(j);
              if (skuId.equals(skuModel.optString("skuId"))) {
                sku = skuModel;
                return sku; // 找到后立即返回，无需继续遍历
              }
            }
          }
          break; // 如果找到spuId，但未找到对应的skuId，则无需继续遍历其他spuId
        }
      }
    } catch (Exception e) {
      LogUtil.runtime("findPropShop", "Error finding prop in shop:");
      LogUtil.printStackTrace("findPropShop", e);
    }
    return null;
  }

  /** 兑换商店道具 活力值 sku spuId, skuId, skuName, exchangedCount, price[amount] exchangedCount == 0...... */
  private boolean exchangePropShop(JSONObject sku, int exchangedCount) {
    if (sku == null) {
      LogUtil.record("要兑换的道具不存在！");
      return false;
    }
    try {
      // 获取活力值信息
      JSONObject jo = new JSONObject(AntForestRpcCall.queryVitalityStoreIndex());
      if (!"SUCCESS".equals(jo.getString("resultCode"))) {
        return false;
      }
      // 活力值小于兑换花费，返回
      if (jo.getJSONObject("userVitalityInfoVO").getInt("totalVitalityAmount") < sku.getJSONObject("price").getDouble("amount")) {
        LogUtil.record("活力值不足，停止兑换[" + sku.getString("skuName") + "]！");
        return false;
      }
      // 活力值兑换道具
      jo = new JSONObject(AntForestRpcCall.exchangeBenefit(sku.getString("spuId"), sku.getString("skuId")));
      if ("SUCCESS".equals(jo.getString("resultCode"))) {
        LogUtil.forest("活力兑换🎐[" + sku.getString("skuName") + "]#第" + exchangedCount + "次");
        return true;
      } else {
        LogUtil.record(jo.getString("resultDesc"));
        LogUtil.runtime(jo.toString());
        return false;
      }
    } catch (Throwable th) {
      LogUtil.runtime(TAG, "exchangePropShop err:");
      LogUtil.printStackTrace(TAG, th);
      return false;
    }
  }

  /** 收取状态的枚举类型 */
  public enum CollectStatus {
    /** 可收取状态 */
    AVAILABLE,
    /** 等待状态 */
    WAITING,
    /** 能量不足状态 */
    INSUFFICIENT,
    /** 已被抢状态 */
    ROBBED
  }

  /** 能量定时任务类型 */
  private class EnergyTimerTask extends ChildModelTask {
    /** 用户ID */
    private final String userId;

    /** 能量ID */
    private final long bubbleId;

    /** 产生时间 */
    private final long produceTime;

    /** 实例化一个新的能量收取定时任务 */
    EnergyTimerTask(String uid, long bid, long pt) {
      // 调用父类构造方法，传入任务ID和提前执行时间
      super(AntForest.getEnergyTimerTid(uid, bid), pt - advanceTimeInt);
      // 初始化用户ID
      userId = uid;
      // 初始化能量ID
      bubbleId = bid;
      // 初始化能量的产生时间
      produceTime = pt;
    }

    @Override
    public Runnable setRunnable() {
      return () -> {
        String userName = UserIdMapUtil.getMaskName(userId);
        int averageInteger = offsetTimeMath.getAverageInteger();
        long readyTime = produceTime - advanceTimeInt + averageInteger - delayTimeMath.getAverageInteger() - System.currentTimeMillis() + 70;
        if (readyTime > 0) {
          try {
            Thread.sleep(readyTime);
          } catch (InterruptedException e) {
            LogUtil.runtime("终止[" + userName + "]蹲点收取任务, 任务ID[" + getId() + "]");
            return;
          }
        }
        LogUtil.record("执行蹲点收取⏰ 任务ID " + getId() + " [" + userName + "]" + "时差[" + averageInteger + "]ms" + "提前[" + advanceTimeInt + "]ms");
        collectEnergy(new CollectEnergyEntity(userId, null, AntForestRpcCall.getCollectEnergyRpcEntity(null, userId, bubbleId)), true);
      };
    }
  }

  /** 获取能量收取任务ID */
  public static String getEnergyTimerTid(String uid, long bid) {
    return "BT|" + uid + "|" + bid;
  }

  public interface HelpFriendCollectType {

    int HELP = 0;
    int DONT_HELP = 1;

    String[] nickNames = {"选中复活", "选中不复活"};
  }
}
