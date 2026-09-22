package com.infinitezerone.minibgm.core.model

import kotlinx.serialization.Serializable

/**
 * 站点探测结论：把一个域名（或接口地址）变成一条可直接落库的取源规则。
 *
 * 只认**确定性信号**——标准 MacCMS V10 采集接口（`/api.php/provide/vod/`）返回
 * `{"code":…,"list":[…]}`。探不出来就是探不出来，不做猜测、不交给模型兜底；
 * 那些站点仍可走「添加」手填或让助手走探查 SOP。
 *
 * @property endpointUrl 命中接口的地址（不含查询串），如 `https://example.com/api.php/provide/vod/`
 * @property ruleTemplate 可直接存进规则的模板：`…?ac=detail&wd={title}`
 * @property siteName 默认规则名（取站点主机名）；用户可改
 * @property sampleCount 探测时接口返回的条目数，用来告诉用户"这个站是活的"
 */
@Serializable
data class MacCmsProbeResult(
    val endpointUrl: String,
    val ruleTemplate: String,
    val siteName: String,
    val sampleCount: Int = 0,
)
