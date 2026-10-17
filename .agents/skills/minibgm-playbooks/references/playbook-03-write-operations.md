## 剧本 3：用户写操作与提案卡（Write Operations）
* **规范先例**：`CollectionRepository`
* **安全红线**：
  * 一切写操作必须止于 `PENDING_CONFIRMATION` 提案卡，用户在 UI 明确点击确认后才落库；
  * 关键写操作必须包在 `withContext(NonCancellable)` 中，防止切页面时协程被无意取消导致数据状态不一致；
  * 凭据只存在于 `AuthTokensDataSource`；不绕过/削弱 `BgmPkce`；不剥离或覆盖 `User-Agent`。
