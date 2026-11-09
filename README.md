# 学无止境 StudyTick

一个干净、克制的每日学习打卡应用。设一个每日目标，专注计时，用日历留下每一天的痕迹。

- Kotlin + Jetpack Compose（Material 3）
- 单 Activity、三个页面，无多余装饰
- 计时基于前台服务 + 常驻通知，锁屏也能看到剩余时间
- 数据保存在本地数据库，**升级安装不丢打卡记录**

[下载最新版本](https://github.com/wanvx/studytick/releases/latest) · [测试报告](docs/TEST_REPORT.md) · [更新日志](docs/RELEASE_NOTES_v1.1.0.md)

## 界面

整体是浅色、留白充足的卡片式设计，主色 `#2F6BFF`。

- **主页**：顶部日期与状态胶囊（未开始 / 专注中 / 已完成），中央 270° 渐变圆环 + 已学时长，下方目标与剩余时间，底部「开始学习 / 结束学习」大按钮
- **日历**：上半部分月历（今日为蓝色圆形，打卡日为蓝色小方块），下半部分今日打卡卡片（已学 / 剩余 / 目标三栏 + 开始计时按钮）
- **我的**：每日学习目标（± 微调与快捷档位）、软件更新、锁屏与通知设置、添加桌面小组件、GitHub 仓库、清除打卡数据
- **通知栏 / 锁屏**：常驻通知显示「学习中」、已学时长、剩余时间、进度条与倒计时，可直接结束学习


## 功能

### 主页
- 270° 渐变圆环展示今日完成度，中心是已学时长
- 目标时长与实时剩余时间
- 一键「开始学习 / 结束学习」

### 日历打卡
- 上半部分为月历，打卡成功的日期用蓝色圆点 + 蓝色小方块标注，今日日期高亮
- 下半部分为今日打卡卡片：已学 / 剩余 / 目标三栏数据 + 开始计时按钮
- 达标后卡片状态变为「已完成今日打卡」

### 个人页
- 每日学习目标：`−/+` 微调，或直接选择 30 分 / 1 时 / 1.5 时 / 2 时 / 3 时
- 软件更新：读取 GitHub Releases，发现新版本可跳转下载
- 添加桌面小组件：一键发起系统级「添加到主屏幕」
- GitHub 仓库入口
- 清除打卡数据（二次确认，保留每日目标设置）

### 桌面小组件（2×2）
- 顶部「今日已学 xx 分」，中间灰色小字「剩余」+ 大号倒计时，下方进度条，底部「目标 / 开始」
- 计时中由系统 `Chronometer` 逐秒倒计时，不需要频繁唤醒应用
- 点击卡片打开应用；点击按钮直接开始 / 结束学习
- 添加方式：应用内「我的 → 添加桌面小组件」，或长按桌面 → 小组件 → 学刻

### 锁屏 / 通知
- 前台服务维持计时，常驻通知显示已学时长、剩余时间与进度条
- 通知使用倒计时样式，锁屏可见；通知内可直接「结束学习」
- 通知渠道为默认重要性（无声音、无震动）。若锁屏仍看不到，请检查两处：
  系统「设置 → 通知 → 学刻 → 锁屏通知」是否允许，以及是否开启了「隐藏通知内容」
- 应用内「我的 → 锁屏与通知」可直达该设置页

## 下载安装

前往 [Releases](../../releases/latest) 下载最新的 `StudyTick-x.y.z.apk`，直接安装即可（需要允许「安装未知来源应用」）。

系统要求：Android 8.0（API 26）及以上。

## 关于更新

应用内「我的 → 软件更新 → 检查」会访问本仓库的 Releases 接口，比较版本号后提示是否有新版本。

**升级不会丢数据**：打卡记录存放在应用私有数据库，设置存放在 DataStore。覆盖安装（`adb install -r` 或直接点 APK 升级）会保留全部数据；只有主动卸载才会清除。

## 从源码构建

需要 JDK 17+、Android SDK 35。

```bash
git clone https://github.com/wanvx/studytick.git
cd studytick
./gradlew assembleDebug          # 调试包：com.studytick.app.debug
./gradlew assembleRelease        # 发布包：需要签名配置，见下
```

调试包与发布包可以共存（包名分别带 / 不带 `.debug` 后缀）。

### 签名配置

仓库里**不包含**签名文件。发布包需要你在根目录创建 `keystore.properties`：

```properties
storeFile=keystore/studytick.jks
storePassword=你的密码
keyAlias=studytick
keyPassword=你的密码
```

并生成自己的 keystore：

```bash
keytool -genkeypair -v -keystore keystore/studytick.jks \
  -alias studytick -keyalg RSA -keysize 2048 -validity 10000
```

> ⚠️ **务必备份 keystore 与密码**。一旦丢失，你无法再对已安装用户发布可覆盖升级的新版本，只能换包名重新开始。

## 技术栈

| 层 | 选型 |
| --- | --- |
| UI | Jetpack Compose + Material 3（BOM 2024.12.01） |
| 数据库 | Room 2.6.1（`day_records` 表） |
| 偏好设置 | DataStore Preferences |
| 计时 | 前台服务（`specialUse`）+ 常驻通知 + `Chronometer` |
| 桌面小组件 | AppWidgetProvider + RemoteViews（原生 XML 布局） |
| 定时刷新 | WorkManager（15 分钟兜底） |
| 更新检查 | GitHub Releases API |

### 计时与数据模型

- 计时期间**不逐秒写库**：只在 DataStore 记录本次会话的 `startAt` 与 `base`，界面按 `已学 = base + (now - startAt)` 实时推算
- 结束计时时才落库一次，减少写入与耗电
- 跨天自动在当日 23:59:59 截断，不会把昨天的时长算到今天
- 小组件与通知共享同一套推算逻辑，三处显示始终一致

## 权限说明

| 权限 | 用途 |
| --- | --- |
| `POST_NOTIFICATIONS` | 显示计时通知（Android 13+ 需授权） |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | 计时期间保持前台服务 |
| `INTERNET` / `ACCESS_NETWORK_STATE` | 检查 GitHub 更新 |
| `RECEIVE_BOOT_COMPLETED` | 开机后恢复未结束的计时会话 |

应用不申请定位、通讯录、存储等无关权限，不上传任何数据。

## 已知问题

- 桌面小组件采用原生 RemoteViews 实现（早期 Glance 1.1.1 版本在部分 launcher 上会因 `Resources$NotFoundException: Resource ID #0xffffffff` 加载失败，已弃用该方案），功能完整，但在 Android 15 模拟器上尚未完成最终渲染验证，欢迎在真机上反馈
- 锁屏通知的显示同时受系统开关控制：部分厂商系统（小米 / 华为 / OPPO / vivo）默认关闭应用的锁屏通知，需在系统设置中手动允许
- 详细验证情况见 [测试报告](docs/TEST_REPORT.md)

## 更新日志

- [1.1.0](docs/RELEASE_NOTES_v1.1.0.md)：更名「学无止境」+ 新图标；新增每周学习计划与本月/连续打卡统计
- [1.0.3](docs/RELEASE_NOTES_v1.0.3.md)：小组件剩余时间统一显示到秒（时:分:秒）
- [1.0.2](docs/RELEASE_NOTES_v1.0.2.md)：桌面小组件改版；修复重装/重启后锁屏通知消失
- [1.0.1](docs/RELEASE_NOTES_v1.0.1.md)：修复锁屏不显示进度、底部导航栏与系统导航键重叠
- [1.0.0](docs/RELEASE_NOTES_v1.0.0.md)：首个版本

## License

MIT
