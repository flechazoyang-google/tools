# Toolbox v1.3.1 — 新增指纹解锁

### ✨ 新增
- **指纹解锁**：密码箱解锁页面新增「使用指纹解锁」按钮，支持系统级生物识别验证
- 设备支持检测：无指纹硬件的设备自动隐藏该按钮
- 未录入指纹的设备不会显示该入口

### 🛠 技术
- 引入 `androidx.biometric:biometric:1.1.0`
- 封装 `BiometricAuthManager` 工具类，可在其他场景复用
- 生物识别验证通过后直接解锁密码箱，无需重复输入主密码

### 📦 下载

- [GitHub](https://github.com/flechazoyang-google/tools/releases/download/v1.3.1/toolbox-1.3.1.apk)
- [Gitee](https://gitee.com/yang-genhao/tools/releases/download/v1.3.1/toolbox-1.3.1.apk)
