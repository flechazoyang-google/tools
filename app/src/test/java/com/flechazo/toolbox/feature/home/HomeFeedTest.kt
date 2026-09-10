package com.flechazo.toolbox.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首页 Bento 泳道选择规则（`docs/UI_VISUAL_DIRECTION.md` §六）。
 *
 * 这里钉住的三件事，任何一条挂掉都会在首页直接看得出来：
 * 泳道上限、大卡归属、以及"同一工具不占两张卡"的交叉去重。
 */
class HomeFeedTest {

    private fun select(favorites: List<String>, recents: List<String>) =
        HomeFeed.select(favoriteIds = favorites, recentIds = recents)

    // ---- 空态 ----

    @Test
    fun freshInstallProducesEmptyFeed() {
        val feed = select(favorites = emptyList(), recents = emptyList())
        assertNull(feed.recentHeroId)
        assertTrue(feed.recentQuickIds.isEmpty())
        assertNull(feed.favoriteHeroId)
        assertTrue(feed.otherFavoriteIds.isEmpty())
        assertEquals(0, feed.favoriteTotal)
    }

    // ---- 最近使用泳道 ----

    @Test
    fun mostRecentBecomesHero() {
        val feed = select(favorites = emptyList(), recents = listOf("a", "b", "c"))
        assertEquals("a", feed.recentHeroId)
        assertEquals(listOf("b", "c"), feed.recentQuickIds)
    }

    @Test
    fun recentQuickIsCappedAtFour() {
        val recents = (1..9).map { "t$it" }
        val feed = select(favorites = emptyList(), recents = recents)
        assertEquals("t1", feed.recentHeroId)
        assertEquals(HomeFeed.QUICK, feed.recentQuickIds.size)
        assertEquals(listOf("t2", "t3", "t4", "t5"), feed.recentQuickIds)
    }

    @Test
    fun duplicateRecentsAreCollapsed() {
        val feed = select(favorites = emptyList(), recents = listOf("a", "b", "a", "b", "c"))
        assertEquals("a", feed.recentHeroId)
        assertEquals(listOf("b", "c"), feed.recentQuickIds)
    }

    // ---- 收藏泳道 ----

    @Test
    fun favoritesFillTheLaneWhenNothingUsedYet() {
        val feed = select(favorites = listOf("x", "y", "z"), recents = emptyList())
        assertEquals("x", feed.favoriteHeroId)
        assertEquals(listOf("y", "z"), feed.otherFavoriteIds)
        assertEquals(3, feed.favoriteTotal)
    }

    @Test
    fun favoriteTotalCountsEverythingNotJustWhatIsShown() {
        val favorites = (1..12).map { "f$it" }
        val feed = select(favorites = favorites, recents = emptyList())
        assertEquals(12, feed.favoriteTotal)
        // 泳道里只放得下 1 张大卡 + 4 张小卡
        assertEquals(HomeFeed.QUICK, feed.otherFavoriteIds.size)
    }

    // ---- 交叉去重（这条是本次改造的核心） ----

    @Test
    fun favoriteAlreadyShownAsRecentDoesNotOccupyTheFavoriteLane() {
        val feed = select(
            favorites = listOf("calc", "qr"),
            recents = listOf("calc", "bmi"),
        )
        // calc 已经在「最近使用」当大卡，就不该再在「收藏」占一张
        assertEquals("calc", feed.recentHeroId)
        assertEquals("qr", feed.favoriteHeroId)
        assertTrue(feed.otherFavoriteIds.isEmpty())
        // 但它仍然是"收藏之一"，总数照算
        assertEquals(2, feed.favoriteTotal)
    }

    @Test
    fun favoriteLaneFallsBackWhenEveryFavoriteIsAlreadyInRecents() {
        val feed = select(
            favorites = listOf("a", "b"),
            recents = listOf("a", "b", "c"),
        )
        assertNull(feed.favoriteHeroId)
        assertTrue(feed.otherFavoriteIds.isEmpty())
        assertEquals(2, feed.favoriteTotal)
    }

    @Test
    fun crossLaneDedupeOnlyCoversWhatIsActuallyShown() {
        // 最近使用有 7 个，但泳道只显示 5 个；第 6 个之后没露面的收藏仍应照常上卡
        val recents = listOf("r1", "r2", "r3", "r4", "r5", "r6")
        val feed = select(favorites = listOf("r6", "star"), recents = recents)
        assertEquals("r6", feed.favoriteHeroId)
        assertEquals(listOf("star"), feed.otherFavoriteIds)
    }
}
