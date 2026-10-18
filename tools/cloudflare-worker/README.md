# 弹弹play 聚合弹幕 Cloudflare Worker 边缘缓存网关部署指南

本目录包含 MiniBgm 官方推荐的弹幕 BFF（Backend For Frontend）边缘代理网关脚本，遵循弹弹play官方《开放弹幕网络接入指南》（v2 标准）。

## 核心特性
1. **凭据安全保密**：`AppSecret` 仅存放在 Cloudflare Secrets 内部，不暴露在任何客户端安装包或公网网络包中；
2. **边缘强缓存（Edge Cache）**：
   - 分集弹幕内容（`/api/v2/comment/{epId}`）缓存 **7 天**；
   - 分集/番剧搜索结果（`/api/v2/search/...`）缓存 **24 小时**；
   - 极大节省官方 API 配额（95%+ 请求直接由 Cloudflare 边缘毫秒级直出，0 配额消耗）；
3. **双模自适应（BYOK 支持）**：若用户客户端自行配置了私有 `AppId`，自动透明透传直连，不消耗公共网关配额；
4. **防盗刷保护**：拦截非 MiniBgm 客户端请求，保护个人配额不被爬虫脚本盗用。

---

## 快速部署方式

### 方式 A：本地命令行极速部署（推荐，借助已配置的 wrangler）

无需打开网页，在当前目录下通过官方 CLI 工具 `npx wrangler` 三步完成：

1. **登录 Cloudflare**：
   ```bash
   npx wrangler login
   ```
   *(浏览器会自动打开，点击「Allow」授权即可)*

2. **录入密文凭据（自动加密保存至 Cloudflare Secrets，不落本地代码）**：
   ```bash
   npx wrangler secret put DANDAN_APP_ID
   # 提示输入时粘贴你的 弹弹play AppId

   npx wrangler secret put DANDAN_APP_SECRET
   # 提示输入时粘贴你的 弹弹play AppSecret
   ```

3. **一键发布**：
   ```bash
   npx wrangler deploy
   ```
   终端会直接打出你的线上访问地址，例如：
   `https://minibgm-danmaku-proxy.<your-subdomain>.workers.dev`

---

### 方式 B：Cloudflare 网页控制台手动部署

1. **创建 Worker**：
   - 登录 [Cloudflare 控制台](https://dash.cloudflare.com/)；
   - 导航至 **Workers & Pages** -> 点击 **Create Application** -> **Create Worker**；
   - 为 Worker 起名（例如 `minibgm-danmaku-proxy`），点击 **Deploy**。

2. **粘贴代码**：
   - 进入该 Worker 的详情页，点击右上角 **Edit code**；
   - 清空默认代码，将本目录下的 [`danmaku-proxy.js`](./danmaku-proxy.js) 完整内容粘贴进去；
   - 点击右上角 **Save and deploy**。

3. **配置加密环境变量 (Secrets)**：
   - 返回 Worker 详情页，进入 **Settings** 选项卡 -> **Variables and Secrets**；
   - 在 **Secrets** 区域点击 **Add**，添加以下两条加密变量：
     - 变量名：`DANDAN_APP_ID`，值填入你申请到的 弹弹play **AppId**；
     - 变量名：`DANDAN_APP_SECRET`，值填入你申请到的 弹弹play **AppSecret**；
   - 保存即可生效。

---

## 客户端接入
部署成功后，你将获得一个类似于 `https://minibgm-danmaku-proxy.<your-subdomain>.workers.dev` 的地址：
- 在 MiniBgm App 中打开 **「我的」->「设置」->「弹幕设置」**；
- 在 **「API 端点」** 中填入你的 Worker 域名即可享受全自动签名与边缘极速秒开！

