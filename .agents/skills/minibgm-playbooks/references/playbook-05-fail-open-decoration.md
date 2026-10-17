## 剧本 5：装饰性功能 Fail-Open 与硬件位图安全（Bitmap & Fail-Open）
* **规范先例**：汲取硬件位图崩溃复盘经验
* **安全规则**：
  * 纯装饰性功能（如封面微光、光晕、动效、横幅）内部异常一律用 `runCatching` 降级为“没有装饰”，绝不崩溃宿主页面；
  * 真机 Coil 默认解码为 `Config#HARDWARE`（硬件位图，禁止直接 `getPixels`）；在读取位图像素前，必须先判断并通过 `copy(Bitmap.Config.ARGB_8888, false)` 转为软件位图。
