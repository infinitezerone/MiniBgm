package com.infinitezerone.minibgm.core.designsystem.theme

/**
 * 内容图片的宽高比令牌（width / height）。
 *
 * - [BGM_POSTER_ASPECT_RATIO]：条目海报（新番网格、搜索结果、排期卡、AI 提案卡等）。
 *   当前全站统一为 0.7（约 5:7）；若设计上要对齐标准 2:3（0.667），改这一个常量即可全站生效。
 * - [BGM_PORTRAIT_ASPECT_RATIO]：人物立绘（声优/角色头像），比海报更瘦高一点。
 *
 * 严禁在 feature 里手写比例字面量——比例属于令牌，改版只改这里。
 */
const val BGM_POSTER_ASPECT_RATIO = 0.7f

/** 人物立绘宽高比（3:4 偏方，略瘦于海报） */
const val BGM_PORTRAIT_ASPECT_RATIO = 0.72f
