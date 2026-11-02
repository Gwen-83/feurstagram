package com.feurstagram.extension;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BlockTest {

    @Test
    public void routeBlocksRespectTheirToggles() {
        assertTrue(blocks("/api/v1/feed/timeline/", true, false, false, false, false, false));
        assertFalse(blocks("/api/v1/feed/timeline/", false, false, false, false, false, false));

        assertTrue(blocks("/api/v1/feed/reels_tray/", false, true, false, false, false, false));
        assertTrue(blocks("/api/v1/discover/topical_explore/", false, false, true, false, false, false));
        assertTrue(blocks("/api/v1/clips/home/", false, false, false, true, false, false));
        assertTrue(blocks("/api/v1/discover/ayml/", false, false, false, false, true, false));
        assertTrue(blocks("/api/v1/ads/", false, false, false, false, false, true));

        assertFalse(blocks("/api/v1/feed/user/", true, true, true, true, true, true));
    }

    @Test
    public void storiesTrayAndStoryOpeningAreIndependent() {
        assertTrue(Block.shouldBlockPath("/api/v1/feed/reels_tray/", false, true,
                false, false, false, false, false, false));
        assertFalse(Block.shouldBlockPath("/api/v1/feed/user/123/story/", false, true,
                false, false, false, false, false, false));
        assertTrue(Block.shouldBlockPath("/api/v1/feed/user/123/story/", false, false,
                true, false, false, false, false, false));
        assertTrue(Block.shouldBlockPath("/api/v1/feed/reels_media/", false, false,
                true, false, false, false, false, false));
    }

    @Test
    public void telemetryAndCommerceRoutesRemainAlwaysBlocked() {
        assertTrue(blocks("/api/v1/logging/", false, false, false, false, false, false));
        assertTrue(blocks("/api/v1/commerce/products/", false, false, false, false, false, false));
        assertTrue(blocks("/api/v1/feed/injected_reels_media_www/", false, false, false, false, false, false));
    }

    @Test
    public void feedUnitsRespectAdAndSuggestionToggles() {
        assertTrue(Block.shouldDropFeedItemType("ad4ad", true, false, false));
        assertFalse(Block.shouldDropFeedItemType("ad4ad", false, false, false));
        assertFalse(Block.shouldDropFeedItemType("clips_netego", false, true, false));
        assertFalse(Block.shouldDropFeedItemType("clips_netego", false, false, false));
        assertTrue(Block.shouldDropFeedItemType("clips_netego", false, false, true));
        assertFalse(Block.shouldDropFeedItemType("media_or_ad", true, true, true));
        assertFalse(Block.shouldDropFeedItemType(null, true, true, true));
    }

    @Test
    public void sponsoredMediaAndTraySuggestionsRespectTheirToggles() {
        assertTrue(Block.isSponsoredMediaField("injected"));
        assertTrue(Block.isSponsoredMediaField("dr_ad_type"));
        assertFalse(Block.isSponsoredMediaField("is_paid_partnership"));
        assertTrue(Block.shouldDropFeedMedia(true, true));
        assertFalse(Block.shouldDropFeedMedia(true, false));
        assertFalse(Block.shouldDropFeedMedia(false, true));

        assertTrue(Block.shouldDropTrayEntry("suggested_user", true));
        assertFalse(Block.shouldDropTrayEntry("suggested_user", false));
        assertFalse(Block.shouldDropTrayEntry("following", true));
    }

    private static boolean blocks(String path, boolean feed, boolean stories,
                                  boolean explore, boolean reels, boolean suggested,
                                  boolean ads) {
        return Block.shouldBlockPath(path, feed, stories, explore, reels, suggested, ads);
    }
}
