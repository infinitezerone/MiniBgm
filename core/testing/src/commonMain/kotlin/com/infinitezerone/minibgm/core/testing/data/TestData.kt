package com.infinitezerone.minibgm.core.testing.data

import com.infinitezerone.minibgm.core.model.AirSchedule
import com.infinitezerone.minibgm.core.model.CollectionCount
import com.infinitezerone.minibgm.core.model.Episode
import com.infinitezerone.minibgm.core.model.Rating
import com.infinitezerone.minibgm.core.model.SiteLink
import com.infinitezerone.minibgm.core.model.Subject
import com.infinitezerone.minibgm.core.model.SubjectImages
import com.infinitezerone.minibgm.core.model.Tag
import com.infinitezerone.minibgm.core.model.UserAvatar
import com.infinitezerone.minibgm.core.model.UserProfile

val sampleSubject =
    Subject(
        id = 1001L,
        name = "葬送のフリーレン",
        nameCn = "葬送的芙莉莲",
        summary = "打倒魔王之后的勇者一行的后日谈。",
        date = "2023-09-29",
        eps = 28,
        totalEpisodes = 28,
        images =
            SubjectImages(
                large = "https://lain.bgm.tv/pic/cover/l/sample_1001.jpg",
                common = "https://lain.bgm.tv/pic/cover/c/sample_1001.jpg",
            ),
        rating =
            Rating(
                score = 8.9,
                total = 15000,
                rank = 1,
                count =
                    mapOf(
                        "1" to 15,
                        "2" to 10,
                        "3" to 25,
                        "4" to 40,
                        "5" to 120,
                        "6" to 450,
                        "7" to 1800,
                        "8" to 5200,
                        "9" to 5800,
                        "10" to 1540,
                    ),
            ),
        collection =
            CollectionCount(
                wish = 1200,
                collect = 28000,
                doing = 4500,
                onHold = 300,
                dropped = 150,
            ),
        tags =
            listOf(
                Tag(name = "日常", count = 3500),
                Tag(name = "治愈", count = 2800),
                Tag(name = "奇幻", count = 2400),
                Tag(name = "MADHOUSE", count = 1800),
            ),
    )

val sampleEpisodeList =
    listOf(
        Episode(
            id = 2001L,
            sort = 1f,
            ep = 1f,
            name = "冒険の終わり",
            nameCn = "冒险的终结",
            duration = "24:00",
            airdate = "2023-09-29",
        ),
        Episode(
            id = 2002L,
            sort = 2f,
            ep = 2f,
            name = "別に魔法じゃなくたって…",
            nameCn = "就算不是魔法也…",
            duration = "24:00",
            airdate = "2023-09-29",
        ),
    )

val sampleAirScheduleList =
    listOf(
        AirSchedule(
            bgmId = 1001L,
            title = "葬送のフリーレン",
            titleCn = "葬送的芙莉莲",
            coverUrl = "https://lain.bgm.tv/pic/cover/l/sample_1001.jpg",
            ratingScore = 8.9,
            beginUtc = "2023-09-29T14:00:00.000Z",
            weekday = 5,
            timeCst = "22:00",
            timeJst = "23:00",
            siteLinks =
                listOf(
                    SiteLink(siteName = "bilibili", displayName = "哔哩哔哩", playUrl = "https://www.bilibili.com"),
                    SiteLink(siteName = "bahamut", displayName = "巴哈姆特", playUrl = "https://ani.gamer.com.tw"),
                ),
            nextEpisodeNumber = 1,
            isAiring = true,
        ),
    )

val sampleUserProfile =
    UserProfile(
        id = 42L,
        username = "infinitezerone",
        nickname = "零一",
        avatar =
            UserAvatar(
                large = "https://lain.bgm.tv/pic/user/l/sample_user.jpg",
            ),
        sign = "Stay hungry, stay foolish.",
    )

val sampleUserProfileAlt =
    UserProfile(
        id = 999L,
        username = "alt_user",
        nickname = "马甲二号",
        avatar =
            UserAvatar(
                large = "https://lain.bgm.tv/pic/user/l/sample_alt.jpg",
            ),
        sign = "Alt account for Galgame.",
    )

val sampleUserCollection =
    com.infinitezerone.minibgm.core.model.UserCollection(
        userId = 42L,
        subjectId = 1001L,
        subjectType = 2,
        rate = 9,
        type = 3, // DOING (在看)
        comment = "神作无误！",
        epStatus = 12,
        volStatus = 0,
        updatedAt = "2026-08-31T09:00:00Z",
        subject = sampleSubject,
    )

val sampleCharacterList =
    listOf(
        com.infinitezerone.minibgm.core.model.SubjectCharacter(
            id = 3001L,
            name = "フリーレン",
            roleName = "主角",
            images =
                SubjectImages(
                    large = "https://lain.bgm.tv/pic/crt/l/sample_frieren.jpg",
                    medium = "https://lain.bgm.tv/pic/crt/m/sample_frieren.jpg",
                ),
            actors =
                listOf(
                    com.infinitezerone.minibgm.core.model.SubjectPerson(
                        id = 4001L,
                        name = "種﨑敦美",
                        type = 1,
                        relation = "声优",
                        images =
                            SubjectImages(
                                large = "https://lain.bgm.tv/pic/crt/l/sample_tanezaki.jpg",
                            ),
                    ),
                ),
        ),
        com.infinitezerone.minibgm.core.model.SubjectCharacter(
            id = 3002L,
            name = "フェルン",
            roleName = "主角",
            images =
                SubjectImages(
                    large = "https://lain.bgm.tv/pic/crt/l/sample_fern.jpg",
                ),
            actors =
                listOf(
                    com.infinitezerone.minibgm.core.model.SubjectPerson(
                        id = 4002L,
                        name = "市ノ瀬加那",
                        type = 1,
                        relation = "声优",
                    ),
                ),
        ),
    )

val samplePersonList =
    listOf(
        com.infinitezerone.minibgm.core.model.SubjectPerson(
            id = 5001L,
            name = "斎藤圭一郎",
            type = 1,
            relation = "导演",
        ),
        com.infinitezerone.minibgm.core.model.SubjectPerson(
            id = 5002L,
            name = "Evan Call",
            type = 1,
            relation = "音乐",
        ),
        com.infinitezerone.minibgm.core.model.SubjectPerson(
            id = 5003L,
            name = "マッドハウス",
            type = 2,
            relation = "动画制作",
        ),
    )

val sampleRelationList =
    listOf(
        com.infinitezerone.minibgm.core.model.SubjectRelation(
            id = 6001L,
            type = 2,
            name = "葬送のフリーレン 第2期",
            nameCn = "葬送的芙莉莲 第二季",
            relation = "续作",
            images =
                SubjectImages(
                    large = "https://lain.bgm.tv/pic/cover/l/sample_frieren_s2.jpg",
                ),
            ratingScore = 9.0,
        ),
    )
