# 更新记录

## 0.1.3

- 修复 JitPack 安装入口对旧 Python 运行器的依赖；Maven metadata 仍在归档前校验，消费端校验固定标签 SHA 并安装原字节。

## 0.1.2

- 新增 Kuikly 嵌入式 ScanKit 预览 View；复用进程唯一相机会话、串行释放和迟帧隔离。
- 新增 scanner-kuikly 类型化 View/事件；取景框、权限和扫码业务继续由宿主提供。
- Android 预览增加可选声音/振动反馈，构造签名与默认关闭行为保持。

## 0.1.1

- 补充 ohpm 要求的作者链接。

## 0.1.0

- 拆分 ScanKit 系统扫码、图片解码和 Kuikly 模块。
