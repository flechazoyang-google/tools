package com.flechazo.toolbox.feature.home

/**
 * 首页 Bento 泳道的内容选择策略（规范 `docs/UI_VISUAL_DIRECTION.md` §六）。
 *
 * 刻意做成**纯 id → id 的函数**，不碰 Compose 也不碰 DataStore：
 * "谁上首页、谁进大卡"是产品规则，值得被单测钉住，而不是埋在 ViewModel 的
 * `combine` 里靠肉眼保证。
 *
 * 三条规则：
 * 1. **大卡只给真·高频** —— 每条泳道的第一张是 2×1 Hero 卡，其余是小卡。
 *    尺寸差本身就是优先级，等大网格等于没有优先级。
 * 2. **同一工具不在两条泳道里出现两次** —— 收藏与最近使用做交叉去重。
 *    否则"刚用过的收藏工具"会在首页占掉两张卡（其中一张还是大卡）。
 * 3. **泳道有上限** —— 首页是导航台不是清单页，溢出部分交给「工具」页。
 */
internal data class HomeFeedIds(
    /** 「最近使用」的大卡（泳道第一个）。 */
    val recentHeroId: String? = null,
    /** 「最近使用」的小卡（最多 [HomeFeed.QUICK] 张）。 */
    val recentQuickIds: List<String> = emptyList(),
    /** 「收藏」的大卡；若该工具已在最近使用里出现过，则会顺延到下一个。 */
    val favoriteHeroId: String? = null,
    /** 「收藏」的小卡。 */
    val otherFavoriteIds: List<String> = emptyList(),
    /** 收藏总数——用于表头「共 N 个」，与泳道里显示几张无关。 */
    val favoriteTotal: Int = 0,
)

internal object HomeFeed {

    /** 每条泳道的大卡数量：固定 1 张。 */
    const val HERO = 1

    /** 每条泳道的小卡上限：4 张 = 2 行（2 列网格）。 */
    const val QUICK = 4

    /**
     * @param favoriteIds 已按权重排好序的收藏 id（排序由调用方负责，本函数不再排序）
     * @param recentIds   最近使用 id，越靠前越新
     */
    fun select(favoriteIds: List<String>, recentIds: List<String>): HomeFeedIds {
        val recents = recentIds.distinct()
        val recentHero = recents.firstOrNull()
        val recentQuick = recents.drop(HERO).take(QUICK)

        // 已经出现在「最近使用」里的工具，不再占用「收藏」泳道的名额
        val shownAsRecent = (listOfNotNull(recentHero) + recentQuick).toSet()

        val favorites = favoriteIds.distinct()
        val remaining = favorites.filterNot { it in shownAsRecent }

        return HomeFeedIds(
            recentHeroId = recentHero,
            recentQuickIds = recentQuick,
            favoriteHeroId = remaining.firstOrNull(),
            otherFavoriteIds = remaining.drop(HERO).take(QUICK),
            favoriteTotal = favorites.size,
        )
    }
}
