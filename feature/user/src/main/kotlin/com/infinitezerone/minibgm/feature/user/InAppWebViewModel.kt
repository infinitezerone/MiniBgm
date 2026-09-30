package com.infinitezerone.minibgm.feature.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infinitezerone.minibgm.core.common.AppResult
import com.infinitezerone.minibgm.core.data.repository.AuthRepository
import com.infinitezerone.minibgm.core.model.InAppWebSession
import kotlinx.coroutines.launch

/**
 * 应用内网页会话 ViewModel：登录接管页与网页浏览页共用。
 *
 * 只依赖 [AuthRepository]——环回代理的启停、上游地址到环回地址的映射都在仓库层，
 * UI 只负责把 [InAppWebSession] 写进 WebView 并渲染。
 */
class InAppWebViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {
    /** 启动应用内登录会话；null 表示环回代理不可用（调用方应降级到系统浏览器）。 */
    suspend fun beginLoginSession(): InAppWebSession? = authRepository.beginInAppLogin()

    /** 启动应用内浏览会话（仅 bgm 系域名）；null 表示不适用或代理不可用。 */
    suspend fun beginBrowseSession(url: String): InAppWebSession? = authRepository.beginInAppBrowse(url)

    /** 系统浏览器兜底：生成官方授权地址（不经过环回代理）。 */
    suspend fun beginBrowserLogin(): String = authRepository.beginLogin()

    /** 交换授权码完成登录；回调在主线程。 */
    fun completeLogin(
        code: String?,
        state: String?,
        onResult: (Boolean, String?) -> Unit,
    ) {
        viewModelScope.launch {
            when (val result = authRepository.completeLogin(code, state)) {
                is AppResult.Success -> onResult(true, null)
                is AppResult.Error -> onResult(false, result.message)
                AppResult.Loading -> Unit
            }
        }
    }

    /**
     * 收摊：关闭环回代理。
     *
     * 非 suspend 且幂等——页面被销毁时也要调用，不能依赖协程作用域（那时它可能已被取消）。
     */
    fun stopSession() {
        authRepository.stopInAppWeb()
    }

    override fun onCleared() {
        stopSession()
    }
}
