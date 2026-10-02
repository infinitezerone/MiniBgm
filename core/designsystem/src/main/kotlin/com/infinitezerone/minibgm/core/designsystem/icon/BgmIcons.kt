package com.infinitezerone.minibgm.core.designsystem.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.PlusOne
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubble
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.ExploreOff
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * MiniBgm 统一图标设计系统规范（仿 Google Now in Android 架构）。
 *
 * 全 App 业务层图标访问的单一事实源（Single Source of Truth）。
 * 严禁在业务 feature 中直接依赖或 import [androidx.compose.material.icons]，
 * 必须统一经由 [BgmIcons] 调用，实现对底层图标实现（Compose 矢量、VectorDrawable XML 或字体图元）的完全解耦。
 */
object BgmIcons {
    // ---- 1. 基础导航与朝向动作（RTL 镜像友好） ----
    val ArrowBack: ImageVector = Icons.AutoMirrored.Filled.ArrowBack
    val ArrowForward: ImageVector = Icons.AutoMirrored.Filled.ArrowForward
    val ArrowForwardIos: ImageVector = Icons.AutoMirrored.Filled.ArrowForwardIos
    val KeyboardArrowRight: ImageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight
    val KeyboardArrowDown: ImageVector = Icons.Filled.KeyboardArrowDown
    val KeyboardArrowUp: ImageVector = Icons.Filled.KeyboardArrowUp
    val Close: ImageVector = Icons.Filled.Close
    val CloseBorder: ImageVector = Icons.Outlined.Close
    val Clear: ImageVector = Icons.Filled.Clear
    val Cancel: ImageVector = Icons.Filled.Cancel

    // ---- 2. 顶级主导航与成对 Tab 状态（未选中 Outlined / 选中 Filled） ----
    val Calendar: ImageVector = Icons.Filled.CalendarMonth
    val CalendarBorder: ImageVector = Icons.Outlined.CalendarMonth
    val Schedule: ImageVector = Icons.Filled.Schedule
    val Today: ImageVector = Icons.Filled.Today
    val DateRange: ImageVector = Icons.Filled.DateRange

    val Explore: ImageVector = Icons.Filled.Explore
    val ExploreBorder: ImageVector = Icons.Outlined.Explore
    val ExploreOff: ImageVector = Icons.Outlined.ExploreOff
    val TravelExplore: ImageVector = Icons.Filled.TravelExplore

    val Search: ImageVector = Icons.Filled.Search
    val SearchBorder: ImageVector = Icons.Outlined.Search
    val SearchOff: ImageVector = Icons.Outlined.SearchOff

    val User: ImageVector = Icons.Filled.Person
    val UserBorder: ImageVector = Icons.Outlined.Person
    val AccountCircle: ImageVector = Icons.Outlined.AccountCircle

    val Settings: ImageVector = Icons.Filled.Settings
    val SettingsBorder: ImageVector = Icons.Outlined.Settings

    val Assistant: ImageVector = Icons.Filled.AutoAwesome
    val AssistantBorder: ImageVector = Icons.Outlined.SmartToy

    // ---- 3. 条目类别与媒体形态 ----
    val Tv: ImageVector = Icons.Filled.Tv
    val TvBorder: ImageVector = Icons.Outlined.Tv
    val Book: ImageVector = Icons.Filled.Book
    val Game: ImageVector = Icons.Filled.SportsEsports
    val Music: ImageVector = Icons.Filled.MusicNote
    val Movie: ImageVector = Icons.Filled.Movie
    val VideoLibrary: ImageVector = Icons.Outlined.VideoLibrary

    // ---- 4. 收藏与追踪交互 ----
    val Bookmark: ImageVector = Icons.Filled.Bookmark
    val BookmarkBorder: ImageVector = Icons.Filled.BookmarkBorder
    val Star: ImageVector = Icons.Filled.Star
    val StarBorder: ImageVector = Icons.Outlined.StarBorder
    val Favorite: ImageVector = Icons.Filled.Favorite
    val History: ImageVector = Icons.Filled.History
    val Inventory: ImageVector = Icons.Filled.Inventory2
    val InventoryBorder: ImageVector = Icons.Outlined.Inventory2
    val Check: ImageVector = Icons.Filled.Check
    val CheckBorder: ImageVector = Icons.Outlined.Check
    val CheckCircle: ImageVector = Icons.Filled.CheckCircle
    val Add: ImageVector = Icons.Filled.Add
    val AddBorder: ImageVector = Icons.Outlined.Add
    val Remove: ImageVector = Icons.Filled.Remove
    val PlusOne: ImageVector = Icons.Filled.PlusOne
    val WorkspacePremium: ImageVector = Icons.Filled.WorkspacePremium

    // ---- 5. 内容操作与视图切换 ----
    val Edit: ImageVector = Icons.Filled.Edit
    val EditBorder: ImageVector = Icons.Outlined.Edit
    val Delete: ImageVector = Icons.Filled.DeleteOutline
    val DeleteBorder: ImageVector = Icons.Outlined.Delete
    val Refresh: ImageVector = Icons.Filled.Refresh
    val RefreshBorder: ImageVector = Icons.Outlined.Refresh
    val Sync: ImageVector = Icons.Filled.Sync
    val Share: ImageVector = Icons.Filled.Share
    val Download: ImageVector = Icons.Filled.Download
    val Upload: ImageVector = Icons.Filled.FileUpload
    val ContentCopy: ImageVector = Icons.Outlined.ContentCopy
    val ContentPaste: ImageVector = Icons.Filled.ContentPaste
    val OpenInBrowser: ImageVector = Icons.Outlined.OpenInBrowser
    val OpenInNew: ImageVector = Icons.AutoMirrored.Filled.OpenInNew
    val FilterList: ImageVector = Icons.Filled.FilterList
    val Sort: ImageVector = Icons.AutoMirrored.Filled.Sort
    val SwapVert: ImageVector = Icons.Filled.SwapVert
    val List: ImageVector = Icons.AutoMirrored.Filled.List
    val ViewList: ImageVector = Icons.AutoMirrored.Filled.ViewList
    val GridView: ImageVector = Icons.Filled.GridView
    val ViewCarousel: ImageVector = Icons.Filled.ViewCarousel
    val FormatListNumbered: ImageVector = Icons.Filled.FormatListNumbered
    val CleaningServices: ImageVector = Icons.Filled.CleaningServices

    // ---- 6. 播放器与多媒体控制 ----
    val Play: ImageVector = Icons.Filled.PlayArrow
    val PlayCircle: ImageVector = Icons.Filled.PlayCircleOutline
    val Pause: ImageVector = Icons.Filled.Pause
    val Stop: ImageVector = Icons.Filled.Stop
    val Forward10: ImageVector = Icons.Filled.Forward10
    val Replay10: ImageVector = Icons.Filled.Replay10
    val FastForward: ImageVector = Icons.Filled.FastForward
    val FastRewind: ImageVector = Icons.Filled.FastRewind
    val Replay: ImageVector = Icons.Filled.Replay
    val Fullscreen: ImageVector = Icons.Filled.Fullscreen
    val FullscreenExit: ImageVector = Icons.Filled.FullscreenExit
    val PictureInPicture: ImageVector = Icons.Filled.PictureInPictureAlt
    val VolumeUp: ImageVector = Icons.AutoMirrored.Filled.VolumeUp
    val VolumeMute: ImageVector = Icons.AutoMirrored.Filled.VolumeMute

    // ---- 7. 社区与交流 ----
    val ChatBubble: ImageVector = Icons.Filled.ChatBubble
    val ChatBubbleOutline: ImageVector = Icons.Filled.ChatBubbleOutline
    val ChatBubbleBorder: ImageVector = Icons.Outlined.ChatBubble
    val Forum: ImageVector = Icons.Filled.Forum
    val Send: ImageVector = Icons.AutoMirrored.Filled.Send
    val FormatQuote: ImageVector = Icons.Filled.FormatQuote
    val Group: ImageVector = Icons.Filled.Group
    val PersonAdd: ImageVector = Icons.Filled.PersonAdd
    val ManageAccounts: ImageVector = Icons.Filled.ManageAccounts
    val Logout: ImageVector = Icons.AutoMirrored.Filled.Logout
    val NotificationsActive: ImageVector = Icons.Filled.NotificationsActive

    // ---- 8. 系统状态与视觉反馈 ----
    val Info: ImageVector = Icons.Filled.Info
    val Warning: ImageVector = Icons.Filled.Warning
    val ErrorOutline: ImageVector = Icons.Filled.ErrorOutline
    val BugReport: ImageVector = Icons.Filled.BugReport
    val Visibility: ImageVector = Icons.Filled.Visibility
    val VisibilityOff: ImageVector = Icons.Filled.VisibilityOff
    val Lock: ImageVector = Icons.Filled.Lock
    val LockOpen: ImageVector = Icons.Filled.LockOpen
    val Key: ImageVector = Icons.Filled.Key
    val FolderOpen: ImageVector = Icons.Filled.FolderOpen
    val CloudQueue: ImageVector = Icons.Filled.CloudQueue
    val CloudOff: ImageVector = Icons.Filled.CloudOff
    val BrokenImage: ImageVector = Icons.Filled.BrokenImage
    val Bolt: ImageVector = Icons.Filled.Bolt
    val Trending: ImageVector = Icons.Filled.LocalFireDepartment
    val DarkMode: ImageVector = Icons.Filled.DarkMode
    val BrightnessLow: ImageVector = Icons.Filled.BrightnessLow
    val Palette: ImageVector = Icons.Filled.Palette
    val Language: ImageVector = Icons.Filled.Language
    val ZoomIn: ImageVector = Icons.Filled.ZoomIn
}
