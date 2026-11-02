package com.feurstagram.extension;

import android.util.Log;

import java.io.IOException;
import java.net.URI;

/**
 * Network content blocking, invoked from Instagram's TigonServiceLayer just
 * after each request URI is materialised. Throwing an IOException there makes
 * Instagram treat the request as a network failure and the surface stays empty.
 *
 * Toggleable blocks are gated on {@link Config}; analytics and commerce
 * endpoints are always blocked.
 */
public final class Block {

    private Block() {}

    /** Account/user recommendation surfaces ("Suggested for you", chaining, ...). */
    private static final String[] SUGGESTED = {
            "/discover/ayml/",
            "/discover/sectioned_ayml/",
            "/discover/chaining/",
            "/discover/recommended_accounts_for_category/",
            "/discover/suggested_businesses/",
            "/discover/recs_from_friends_suggestions/",
            "/discover/recs_from_friends_user_info/",
            "/discover/surface_with_su/",
            "/discover/fetch_suggestion_details/",
            "/discover/account_discovery/",
            "/discover/reshare_suggestions/",
            "/fbsearch/accounts_recs/",
            "/friendships/feed_favorites_suggestions/",
            "/friendships/share_to_friends_story_suggested_users/",
            "/direct_v2/search_friending_suggestions/",
            "/business/discovery/suggest_business/",
    };

    /**
     * Feed-item JSON type tokens for ad/promo units injected inline into the
     * timeline payload. Gated on the Ads toggle. {@code media_or_ad} (regular
     * posts) is deliberately absent — dropping it would empty the whole feed.
     */
    private static final String[] AD_FEED_UNITS = {
            "ad4ad",
            "intent_aware_ad_pivot",
            "stand_alone_multi_ad_pivot",
    };

    /**
     * Feed-item JSON type tokens for suggested / "netego" units injected inline
     * into the timeline payload. Gated on the Suggested toggle.
     */
    private static final String[] SUGGESTED_FEED_UNITS = {
            "stories_netego",
            "bloks_netego",
            "in_feed_survey",
            "suggested_users",
            "suggested_top_accounts",
            "suggested_igd_channels",
    };

    /** Reels recommended inline in the home feed, controlled independently of other suggestions. */
    private static final String[] REELS_FEED_UNITS = {
            "clips_netego",
    };

    /**
     * Garbage type token returned for a blocked unit. It matches no real case in
     * Instagram's feed-item parser, so the unit falls through to the parser's
     * "unknown FeedItem type" branch and is dropped without a crash.
     */
    private static final String INVALID_FEED_TYPE = "feurstagram_blocked";

    /** Ad-delivery surfaces injected into feed, stories, profile, DMs, explore, commerce. */
    private static final String[] ADS = {
            "/api/v1/ads/",
            "/feed/async_ads_ranking/",
            "/feed/contextual_multi_ads/",
            "/feed/shop_everything_feed_of_ads",
            "/feed/user_interests_contextual_feed_of_ads/",
            "/clips/ads_discover_sync_flow/",
            "/discover/chaining_experience_contextual_ads/",
            "/discover/chaining_experience_notification_ads/",
            "/direct_v2/ads_for_ctd_ads_thread_view/",
            "/direct_v2/should_show_ad_responses_tab/",
            "/profile_ads/get_profile_ads/",
            "/stories/stories_high_intent_discovery_ads/",
            "/stories/stories_intent_aware_ads/",
            "/commerce/product_collections/ads_collection_page/",
    };

    private interface BlockSettings {
        boolean feed();
        boolean stories();
        boolean storyOpening();
        boolean explore();
        boolean reels();
        boolean reelsFeed();
        boolean suggested();
        boolean ads();
    }

    private static final BlockSettings CONFIG_BLOCK_SETTINGS = new BlockSettings() {
        @Override public boolean feed() { return Config.isFeedBlocked(); }
        @Override public boolean stories() { return Config.isStoriesBlocked(); }
        @Override public boolean storyOpening() { return Config.areStoryOpeningsBlocked(); }
        @Override public boolean explore() { return Config.isExploreBlocked(); }
        @Override public boolean reels() { return Config.isReelsBlocked(); }
        @Override public boolean reelsFeed() { return Config.isFeedReelsBlocked(); }
        @Override public boolean suggested() { return Config.isSuggestedBlocked(); }
        @Override public boolean ads() { return Config.isAdsBlocked(); }
    };

    /** Main hook: throw if this request should be blocked. */
    public static void throwIfBlocked(URI uri) throws IOException {
        if (uri == null) return;
        String path = uri.getPath();
        if (path == null) return;

        // Debug builds only (see DebugBridge): logs every path Instagram asks for,
        // which is how a surface that started arriving over a new endpoint gets
        // found. Always false in a release build.
        if (DebugBridge.isNetworkTraceEnabled()) Log.i("FeurNet", path);

        if (shouldBlockPath(path, CONFIG_BLOCK_SETTINGS)) {
            throw blocked();
        }
    }

    static boolean shouldBlockPath(String path, boolean feed, boolean stories,
                                   boolean storyOpening, boolean explore, boolean reels,
                                   boolean reelsFeed, boolean suggested, boolean ads) {
        return shouldBlockPath(path, new BlockSettings() {
            @Override public boolean feed() { return feed; }
            @Override public boolean stories() { return stories; }
            @Override public boolean storyOpening() { return storyOpening; }
            @Override public boolean explore() { return explore; }
            @Override public boolean reels() { return reels; }
            @Override public boolean reelsFeed() { return reelsFeed; }
            @Override public boolean suggested() { return suggested; }
            @Override public boolean ads() { return ads; }
        });
    }

    static boolean shouldBlockPath(String path, boolean feed, boolean stories,
                                   boolean explore, boolean reels, boolean suggested,
                                   boolean ads) {
        return shouldBlockPath(path, feed, stories, false, explore, reels, false, suggested, ads);
    }

    private static boolean isStoryOpeningPath(String path) {
        return path.contains("/feed/reels_media/")
                || (path.contains("/feed/user/") && path.contains("/story/"));
    }

    private static boolean shouldBlockPath(String path, BlockSettings settings) {
        if (path == null) return false;

        if ((path.contains("/feed/timeline/") && settings.feed())
                || (path.contains("/feed/reels_tray") && settings.stories())
                || (isStoryOpeningPath(path) && settings.storyOpening())
                || (path.contains("/discover/topical_explore") && settings.explore())
                || ((path.contains("/clips/home/")
                || path.contains("/clips/discover")
                || path.contains("/clips/get_blend_medias/")) && settings.reels())
                || (containsAny(path, SUGGESTED) && settings.suggested())
                || (containsAny(path, ADS) && settings.ads())) {
            return true;
        }

        // --- Always-blocked analytics / commerce ---
        // No trailing slash: the family has a "_www" sibling
        // (/feed/injected_reels_media_www/) that a slash-terminated match misses.
        return path.contains("/feed/injected_reels_media")
                || path.contains("/logging/")
                || path.contains("/async_ads_privacy/")
                || path.contains("/async_critical_notices/")
                || isMediaSeen(path)
                || path.contains("/api/v1/fbupload/")
                || path.contains("/api/v1/stats/")
                || path.contains("/api/v1/commerce/")
                || path.contains("/api/v1/shopping/")
                || path.contains("/api/v1/sellable_items/");
    }

    /**
     * Feed-item parse hook: invoked from Instagram's feed-item deserialiser with
     * each item's JSON type token. Returns an invalid token when that unit should
     * be hidden (so the parser skips it), or the token unchanged otherwise.
     */
    public static String replaceFeedItemType(String key) {
        if (key == null) return null;
        // A new unit starts: whatever media it wraps is parsed right after this.
        MEDIA_IS_AD.get()[0] = false;
        boolean drop = shouldDropFeedItemType(key, CONFIG_BLOCK_SETTINGS);
        // Debug builds only (see DebugBridge): shows every unit type the timeline
        // actually contains and whether it was dropped — the way to tell a patch
        // that no longer runs from one whose unit list is out of date.
        if (DebugBridge.isNetworkTraceEnabled()) {
            Log.i("FeurFeed", (drop ? "drop " : "keep ") + key);
        }
        return drop ? INVALID_FEED_TYPE : key;
    }

    static boolean shouldDropFeedItemType(String key, boolean ads, boolean suggested,
                                          boolean reelsFeed) {
        return shouldDropFeedItemType(key, new BlockSettings() {
            @Override public boolean feed() { return false; }
            @Override public boolean stories() { return false; }
            @Override public boolean storyOpening() { return false; }
            @Override public boolean explore() { return false; }
            @Override public boolean reels() { return false; }
            @Override public boolean reelsFeed() { return reelsFeed; }
            @Override public boolean suggested() { return suggested; }
            @Override public boolean ads() { return ads; }
        });
    }

    private static boolean shouldDropFeedItemType(String key, BlockSettings settings) {
        return key != null && ((equalsAny(key, AD_FEED_UNITS) && settings.ads())
                || (equalsAny(key, SUGGESTED_FEED_UNITS) && settings.suggested())
                || (equalsAny(key, REELS_FEED_UNITS) && settings.reelsFeed()));
    }

    /**
     * JSON fields only a sponsored media carries. A sponsored post reaches the
     * timeline in the same {@code media_or_ad} unit as an organic one, so the
     * unit type cannot tell them apart; these fields can. {@code injected} holds
     * the "Sponsored" label and is on every ad, including the "ad break" ones;
     * {@code dr_ad_type} (direct-response ads) backs it up should it be renamed.
     * Organic posts carry other ad-sounding fields ({@code should_request_ads},
     * {@code is_paid_partnership}), which is why the list is this narrow.
     */
    private static final String[] AD_MEDIA_FIELDS = {
            "injected",
            "dr_ad_type",
    };

    /**
     * Whether the media being parsed on this thread is sponsored. Reset by
     * {@link #replaceFeedItemType} as each feed unit starts, set by
     * {@link #onMediaField}, read by {@link #filterFeedMedia} once the unit's
     * media is built. Parsing is synchronous on one thread, so a ThreadLocal
     * keeps concurrent feed and non-feed parses apart.
     */
    private static final ThreadLocal<boolean[]> MEDIA_IS_AD = new ThreadLocal<boolean[]>() {
        @Override protected boolean[] initialValue() { return new boolean[1]; }
    };

    /**
     * Media parse hook: called with every JSON field name of every media Instagram
     * parses, carousel children included (a child of an ad is an ad too).
     */
    public static void onMediaField(String key) {
        if (isSponsoredMediaField(key)) MEDIA_IS_AD.get()[0] = true;
    }

    static boolean isSponsoredMediaField(String key) {
        return key != null && equalsAny(key, AD_MEDIA_FIELDS);
    }

    /**
     * Feed-unit hook: called with the media a feed unit wraps, once parsed.
     * Returns null for a sponsored one when ads are blocked, which leaves the
     * unit empty — the same state as an unknown unit type, which Instagram drops.
     */
    public static Object filterFeedMedia(Object media) {
        boolean drop = media != null && shouldDropFeedMedia(MEDIA_IS_AD.get()[0], Config.isAdsBlocked());
        if (DebugBridge.isNetworkTraceEnabled()) {
            Log.i("FeurFeed", (drop ? "drop " : "keep ")
                    + (MEDIA_IS_AD.get()[0] ? "sponsored media" : "media"));
        }
        return drop ? null : media;
    }

    static boolean shouldDropFeedMedia(boolean mediaIsAd, boolean adsBlocked) {
        return mediaIsAd && adsBlocked;
    }

    /**
     * Name of the story-tray model's getter for {@code reel_type}. Obfuscated and
     * renamed every release, so the patch rewrites this body with the name it
     * finds in the model's serializer.
     */
    static String trayEntryTypeGetter() {
        return "";
    }

    /**
     * Story-tray hook: called with every entry Instagram's tray parser builds.
     * Returns null for a suggested account ({@code reel_type} "suggested_user")
     * when suggestions are blocked; the tray loop already skips entries that
     * parse to null, so the suggestion never reaches the tray. These come inside
     * the tray response itself, so the network block cannot catch them.
     */
    public static Object filterTrayEntry(Object entry) {
        if (entry == null || !Config.isSuggestedBlocked()) return entry;
        try {
            String getter = trayEntryTypeGetter();
            if (getter.isEmpty()) return entry;
            Object type = entry.getClass().getMethod(getter).invoke(entry);
            boolean drop = shouldDropTrayEntry(String.valueOf(type), true);
            if (DebugBridge.isNetworkTraceEnabled()) {
                Log.i("FeurTray", (drop ? "drop " : "keep ") + type);
            }
            return drop ? null : entry;
        } catch (Throwable t) {
            return entry; // never break the tray over a shape we did not expect
        }
    }

    static boolean shouldDropTrayEntry(String type, boolean suggestionsBlocked) {
        return suggestionsBlocked && "suggested_user".equals(type);
    }

    private static boolean equalsAny(String value, String[] options) {
        for (String option : options) {
            if (option.equals(value)) return true;
        }
        return false;
    }

    private static boolean isMediaSeen(String path) {
        return path.contains("/api/v1/media/") && path.contains("/seen");
    }

    private static boolean containsAny(String path, String[] needles) {
        for (String needle : needles) {
            if (path.contains(needle)) return true;
        }
        return false;
    }

    private static IOException blocked() {
        return new IOException("Blocked by Feurstagram");
    }
}
