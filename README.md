# DazoServer
本人私人使用的服务端

Android 手机端位于 [android-app](android-app/README.md)，使用 React Native 构建，直接连接现有服务接口。

画廊视频首帧缩略图在首次请求时由应用内置的 FFmpeg 解码库提取并缓存，
运行服务的机器无需另行安装 `ffmpeg` 命令。打包时会包含多平台原生库，产物体积会相应增大。
