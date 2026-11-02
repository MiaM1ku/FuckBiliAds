# 冷启动优化回归测试

## 测试包与安装

- 从 [fork 的 GitHub Actions](https://github.com/MiaM1ku/FuckBiliAds/actions/workflows/test.yml) 中打开最新成功的 `Cold-start test APK` 运行，下载 `FuckBiliAds-cold-start-test` artifact 并解压 APK。
- 测试包名为 `FuckBiliAds-1.3.4-cold-start-test.apk`，由 GitHub Actions 构建，采用 **release 运行模式 + 临时 debug 钥匙签名**。每次 CI 运行生成独立测试签名。
- 推荐使用独立测试环境。正式版与测试版签名不同；出现签名冲突时，先备份模块 APK 和配置，再换装模块，换装会清除模块应用数据。保留哔哩哔哩宿主的安装及数据。
- 在 LSPosed 中启用模块，勾选对应宿主作用域，重启宿主进程。LSPatch 按现有方式重新修补测试宿主。

## 冷启动与缓存

1. 记录设备、Android、LSPosed/LSPatch、宿主版本和包名，以及当前模块开关。
2. 在相同网络、开关和设备条件下，用原版和测试版分别进行 5 次“强行停止宿主 → 打开首页”，记录到首页可交互的耗时。
3. 单独记录测试版首次启动。HD 旧缓存会自动刷新一次，后台扫描可能晚于首页显示完成；等待 `[BOOT] hooks done` 后再结束这一轮。
4. 再强行停止并启动宿主 5 次，确认 `[PERF] dexkit cache hit` 出现，HD 各次缓存命中启动应直接注册已解析的 Hook。
5. 检查缓存缺失场景时，先备份宿主缓存目录里的 `dexkit_hook_cache.properties`，仅移走这份缓存后重新启动。宿主包名不同，数据目录也相应不同；例如手机版为 `/data/user/0/tv.danmaku.bili/cache/`，HD 为 `/data/user/0/tv.danmaku.bilibilihd/cache/`。
6. 在缓存缺失的首次冷启动中直接点开视频，检查播放器浮层和关联推广。该场景用于确认后台解析完成前的功能覆盖情况，发现漏拦请一并反馈出现时机。

记录日志可在启动前运行：

```bash
adb logcat -v threadtime -s FuckBiliAds dexkitHelper
```

重点证据：

- 缓存缺失：`[PERF] dexkit cache miss`；HD 扫描日志中的 `thread=fba-dexkit`。
- 缓存命中：`[PERF] dexkit cache hit, load=...ms`。
- HD 播放页：`[PLAY] Gripper 接口特征 Hook 数量=...`。
- 启动完成：`[BOOT] hooks done` 和 `[HOOK REGISTRY]`。对比相同宿主及开关的注册成员集；HD 与手机版各用自己的原版数据作基线。

## 功能回归

- 手机版：首页推荐、开屏、播放页关联推广、关注页、小视频和模块设置入口。
- HD：首页、播放页下方/标题上方浮层、关联推广、暂停页和模块设置入口。
- LSPatch：在 APK 路径中包含修补外壳和原始宿主时，确认首次解析及后续缓存命中都覆盖 HD 播放 Hook。
- 分项开关：关闭首页/播放/关注/开屏拦截，强行停止后重新进入对应页面，核对实际行为；恢复开关后重测。
- 日志：确认主进程每次安装一组 Hook，辅助进程输出跳过记录，并记录闪退、卡启动页及首屏广告情况。

## 反馈格式

```text
设备 / Android：
框架 / 版本：
宿主包名 / 版本：
测试包 Actions 运行链接：
原版 5 次耗时：
测试版首次启动耗时：
测试版缓存命中 5 次耗时：
首页 / 开屏 / 播放 / 关注 / 小视频 / 设置入口：
关闭分项开关后的行为：
首次冷启动直接播放：
日志附件：
```

自动验证覆盖 JVM 缓存逻辑、Debug 单测及 release 模式 APK 构建。设备冷启动耗时、广告覆盖和 Hook 成员集由本清单的真机测试补齐；收到测试确认后创建上游 PR。
