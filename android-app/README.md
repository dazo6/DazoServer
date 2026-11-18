# Dazo Mobile · React Native

本项目的 Android 子模块。界面与导航由 **React Native 0.88.1 + React 19.3 + TypeScript** 实现，使用原生组件渲染，直接调用项目现有 API。配色、浅灰背景和白色卡片沿用网页端。

## 构建

需要 Node 22.13+（本机使用 Node 24）、JDK 21、Android SDK Platform 37、Build Tools 37.0.0、NDK 27.1.12297006、CMake 3.22.1。Gradle Wrapper 会下载 Gradle 9.4.1。依赖以 `pnpm-lock.yaml` 固定。

```powershell
cd android-app
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
pnpm install --frozen-lockfile
pnpm typecheck
pnpm test
.\gradlew.bat assembleDebug --max-workers=4
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`，包含 JS / Hermes bundle，安装后无需运行 Metro。该产物为调试签名，尚未配置分发签名。支持 Android 8.0+，默认包含 arm64-v8a 和 x86_64。

如需 Fast Refresh 开发，可将 `MainApplication.kt` 中 `useDevSupport` 改为 `BuildConfig.DEBUG`、将 `app/build.gradle` 中 `debuggableVariants` 改为 `['debug']`，再运行 `pnpm start` / `pnpm android`。交付配置默认以独立 APK 为准。

## GitHub 自动打包

[Android APK 工作流](../.github/workflows/android-apk.yml) 会在 `master` / `main` 的 Android 代码或工作流变更后自动运行，也支持在 [Actions](https://github.com/dazo6/DazoServer/actions/workflows/android-apk.yml) 中手动运行。构建先执行类型检查和测试，再生成包含 arm64-v8a / x86_64 的独立 APK。运行成功后，从 Artifacts 下载 `DazoMobile-运行编号`，包含 `DazoMobile.apk` 和 SHA-256 校验文件，保留 30 天。

主分支和手动构建使用仓库 Secret `ANDROID_DEBUG_KEYSTORE_BASE64` 中的固定调试密钥，支持覆盖更新当前安装包。密钥通过 GitHub 的公钥加密上传，不进入代码仓库、构建日志或 APK 附件。PR 构建使用临时调试签名，供验证使用。若迁移仓库，需要重新配置此 Secret；它是调试 keystore 文件内容的 Base64，标准调试别名 / 密码为 AndroidDebugKey / android。正式分发签名仍未配置。

## 功能与交互

- 底部主导航：首页 / 画师管理 / 自由画廊 / 更多。历史爬取记录、任务管理、用户管理和账户位于二级页面，依据接口权限显示。
- 统一下拉刷新。搜索使用输入防抖；筛选使用底部面板；卡片菜单提供管理操作。运行中的任务自动更新状态。
- 图库采用虚拟化单列流，图片无圆角、无文件名栏，左右与图片之间仅保留 2 dp；按实际比例完整显示。文件操作可通过长按进入。支持文件夹、排序、图片缩放和拖动、左右切换、视频播放、原文件和打包下载、分片上传及取消上传。
- 更多 → 设置中的“直接显示原图”控制列表是否跳过图片缩略图，视频和文件夹仍使用预览。该选项按 Host / 用户保存，位于存储与缓存信息上方。
- 大图浏览向后预缓存最多 10 张原图，跳过视频和文件夹；原图列表模式也会提前加载。预缓存每次只进行一个后台读取，移动位置后替换待加载队列，关闭页面后停止后续预取，与图片缓存共用容量限制。
- 画师创建和抓取 / 压缩开关、自由画廊增删改、历史筛选与翻页、任务手动运行、用户权限和会话管理、头像裁剪和账户修改均调用现有接口。
- 导航、筛选、分页、列表位置、图库排序和阅读文件按 Host / 用户隔离保存；图库以文件名锚点和偏移恢复，插入新文件后仍优先定位原文件。

## 本地数据

登录填写服务器根地址、用户 ID 和密码。密码不保存；会话 Cookie 通过 Android Keystore 加密，写请求使用现有 CSRF Token。Cookie 只发送给当前服务器，不发送给画师外部图片地址。

图片文件保存在应用私有缓存目录，最多 **256 MiB**；超过 **30 天未使用**的文件自动清理，超出总量时按最久未使用顺序淘汰。单张图片下载限制 64 MiB。更多 → 设置可查看用量和主动清理。视频使用系统播放器流式播放，下载文件交由 Android DownloadManager 管理，Android 10+ 保存到系统 Downloads，旧版保存在应用的外部下载目录。文件下载不计入可自动清理的图片缓存。

`src/` 为 React Native 界面、导航与状态；`app/src/main/java/com/dazo66/android/` 为 Android 入口和网络、加密、缓存、上传、图标 / 视频组件。没有 WebView，也没有保留另一套 Java 页面。

## 验证

本机已通过 TypeScript 检查、12 项领域逻辑与预缓存测试和 `assembleDebug` 构建。Android 15 模拟器通过本地模拟 API 验证了登录、导航、日期时间筛选、单列图片比例、大图左右切换、重启位置恢复、账户修改及 CSRF、三片上传、原生视频显示与倍速面板、缓存清理。缓存压力测试从约 301 MiB 自动降到约 200 MiB，最旧文件及超过 30 天的文件被淘汰。

正式服务器尚未联调；真实媒体格式、视频播放、头像上传、归档下载和管理写操作仍需连接实际 Host 验证。APK 使用调试签名。
