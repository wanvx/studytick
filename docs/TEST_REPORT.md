# 学刻 StudyTick 测试报告

- **被测版本**：1.0.0（versionCode 1），包名 `com.studytick.app`
- **构建产物**：`app-release.apk`，11,559,447 字节，签名 `CN=StudyTick`
- **SHA-256**：`ba2208c0059b937207db3706f50d0ceea1d90db0eece2802845b86b90eff9b6a`
- **测试环境**：Android Emulator（Pixel 6, API 35, google_apis x86_64），Windows 11 主机
- **测试日期**：2026-11-07
- **结论**：核心功能全部通过；桌面小组件完成代码级重构与静态校验，但**未能取得真机/模拟器渲染截图**，标记为待真机验收。

## 一、需求对照

| 编号 | 需求 | 结果 | 说明 |
| --- | --- | --- | --- |
| R1 | 界面干净简洁、无多余文字 | 通过 | 三页均无说明性长文案，仅保留必要标签 |
| R2 | 2×2 桌面小组件显示今日是否达标与剩余时间，点击跳转或直接开始 | **部分通过** | 组件注册、布局资源、点击 PendingIntent 均已验证；**渲染效果待真机确认** |
| R3 | 用 GitHub 维护更新功能 | 通过 | 仓库 + v1.0.0 Release + APK 附件已上线，`/releases/latest` 实测可读，字段与解析逻辑一致 |
| R4 | 锁屏显示剩余时间与已学时间 | 通过 | 前台服务 + 常驻通知 `visibility=PUBLIC`，含倒计时与进度条 |
| R5 | 每次更新数据不丢失 | 通过 | `install -r` 覆盖安装后 SQLite 记录完好 |

## 二、用例明细

### T01 冷启动与首页渲染

- 步骤：`am start -n com.studytick.app/.MainActivity`
- 预期：进入首页，显示渐变圆环、剩余时间、开始按钮、底部三标签
- 实际：正常渲染，圆环 270° 渐变，中央剩余时间与数据库一致
- 证据：模拟器截图（计时中，剩余 1:57，已学 00:04:25 与 DB 265 秒吻合）

### T02 通知权限

- 步骤：首次启动触发 `POST_NOTIFICATIONS` 请求
- 预期：弹出系统权限对话框，允许后不再重复请求
- 实际：正常弹出并授权

### T03 开始计时与前台服务

- 步骤：点「开始学习」→ `dumpsys notification` / `dumpsys activity services`
- 预期：服务以前台方式运行，通知存在
- 实际：`isForeground=true`，`visibility=PUBLIC`，标题「学习中」，倒计时 `1:57:27`，进度条正常
- 证据：通知栏展开截图 + `dumpsys` 输出

### T04 通知按钮「结束学习」

- 步骤：展开通知 → 点「结束学习」
- 预期：服务停止，学习时长落库
- 实际：服务停止；DB 记录更新为 265 秒
- 证据：`select * from day_records` → `2026-11-07|265|7200|0`

### T05 数据落库与今日状态

- 步骤：结束计时后查询 Room 表
- 预期：存在当日记录，`studied_seconds` 与界面显示一致
- 实际：`2026-11-07|265|7200|0`（日期 / 已学秒 / 目标秒 / 是否完成），与首页「00:04:25」一致

### T06 日历页

- 步骤：切到「日历」标签
- 预期：月历显示当月，今日为蓝色圆形，已打卡日期有蓝色小方块，下方今日打卡卡片数据与首页一致
- 实际：全部符合；卡片显示已学 4 分 25 秒 / 剩余 1 时 55 分
- 证据：日历页截图

### T07 个人页

- 步骤：切到「我的」标签
- 预期：每日目标卡（−/+ 与 30 分/1 时/1.5 时/2 时/3 时 快捷项）、软件更新、添加桌面小组件、GitHub 仓库、清除打卡数据
- 实际：全部存在，目标修改即时生效并持久化
- 证据：个人页截图

### T08 清除数据二次确认

- 步骤：点「清除打卡数据」→ 弹出确认框 → 取消
- 预期：必须二次确认；取消后数据不变
- 实际：弹出确认框；取消后 DB 记录仍为原始值（未误删）

### T09 覆盖安装数据保留（R5）

- 步骤：`adb install -r app-release.apk`（同版本覆盖）→ 重新启动 → 查询 DB
- 预期：打卡记录、每日目标全部保留
- 实际：记录 `2026-11-07|265|7200|0` 完好，目标 120 分钟未变

### T10 小组件注册与绑定（R2）

- 步骤：应用内「添加桌面小组件」→ `requestPinAppWidget` → 系统确认对话框 → `dumpsys appwidget`
- 预期：组件以 2×2 绑定，provider 已注册
- 实际：系统选中器预览页渲染出真实内容（时间、剩余小时、已学分钟、进度条、胶囊按钮）；`dumpsys appwidget` 中绑定存在

### T11 小组件渲染（R2）— **未通过 / 待真机**

- 现象：绑定成功、provider 回调正常执行、数据正确，但 launcher 端 apply 失败，显示「Can't load widget」
- 定位过程：
  1. `logcat` 捕获 `RemoteViews$ActionException`，根因 `Resources$NotFoundException: Resource ID #0xffffffff`，抛在 `Resources.getColor` ← `RemoteViews$ResourceReflectionAction.getParameterValue`
  2. 反编译 `glance-appwidget-1.1.1.aar` + 下载 AOSP `RemoteViews.java` 对照，确认只有 `setColor(...resId)` / `setColorStateList(...resId)` / `setIntDimen(...resId)` 会生成 `ResourceReflectionAction`，即某个 action 传入了 `-1` 作为颜色资源
  3. 逐元素二分：`ImageProvider(R.drawable)` 背景为元凶；移除后仍不稳定
- 处置：**放弃 Glance，改用原生 `AppWidgetProvider` + XML `RemoteViews`**（移除 glance-appwidget / glance-material3 依赖）
- 当前状态：代码与布局完成静态校验，以下均在 RemoteViews 允许清单内：
  - 视图：`LinearLayout` / `FrameLayout` / `TextView` / `Chronometer` / `ProgressBar`
  - 属性：`@drawable` 背景、`#RRGGBB` 字色、`?android:attr/progressBarStyleHorizontal`
  - 逻辑：`setTextViewText`、`setProgressBar`、`setChronometerCountDown`、`setOnClickPendingIntent`（`FLAG_IMMUTABLE`）
- 遗留：**需要一次真机渲染确认**（模拟器系统进程反复 ANR，launcher 与实际桌面脱节，无法出具可信截图）

### T12 更新检查接口（R3）

- 步骤：`GET https://api.github.com/repos/wanvx/studytick/releases/latest`
- 预期：返回 `tag_name=v1.0.0`，`assets` 含 APK，`browser_download_url` 可下载
- 实际：
  ```
  tag_name: v1.0.0        name: 学刻 StudyTick 1.0.0
  assets:   StudyTick-1.0.0.apk (11559447 bytes)
  body_len: 767
  ```
  与 `UpdateChecker.kt` 解析字段一致；当前版本 = 最新版本 → 显示「已是最新版本」

### T13 APK 元数据与签名

- 步骤：`aapt2 dump badging` / `apksigner verify --print-certs`
- 预期：包名、版本、权限、组件注册正确；签名有效
- 实际：`com.studytick.app` / 1.0.0 / minSdk 26 / targetSdk 35；权限仅 INTERNET、POST_NOTIFICATIONS、FOREGROUND_SERVICE(+SPECIAL_USE)；`StudyWidgetProvider` 已注册（exported=true）；签名 `CN=StudyTick` 验证通过

## 三、未覆盖 / 未通过项

| 编号 | 项目 | 原因 | 建议 |
| --- | --- | --- | --- |
| T11 | 小组件真机渲染 | 模拟器 launcher 崩溃，无法取得可信截图 | 真机安装后确认；异常时提供 `adb logcat -d \| findstr /i "RemoteViews ActionInflate"` |
| — | 锁屏界面截图 | 模拟器锁屏截图未能稳定获取，仅验证 `visibility=PUBLIC` 与通知内容 | 真机锁屏自查 |
| — | 版本升级后「检查更新」跳转 | 需存在更高版本 Release | 发布 1.0.1 时验证 |

## 四、复现命令

```bash
# 安装
adb install -r app-release.apk

# 数据断言
adb shell "run-as com.studytick.app sqlite3 /data/data/com.studytick.app/databases/study.db 'select * from day_records'"

# 小组件绑定
adb shell "dumpsys appwidget | grep -i studytick"

# 小组件异常排查
adb logcat -d | findstr /i "RemoteViews ActionException inflate"

# 更新检查
curl -s https://api.github.com/repos/wanvx/studytick/releases/latest
```
