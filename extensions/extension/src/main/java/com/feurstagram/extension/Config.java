package com.feurstagram.extension;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Runtime configuration backed by SharedPreferences ("feurstagram_prefs").
 *
 * Independent block toggles (feed, explore, reels, stories, instants, notes,
 * suggested, ads) plus the permanent "hardcore" lock. The application Context
 * is resolved reflectively so no patched class needs to hand us one.
 */
public final class Config {

    private static final String PREFS = "feurstagram_prefs";
    private static final Map<String, Object> MEMORY_PREFS = new HashMap<>();

    /** Set by setting interactions, then checked against the page-session snapshot. */
    private static boolean sNeedsRestart;

    /**
     * Snapshot of every block_* toggle captured when the settings page opens.
     * The permanent lock only freezes surfaces that were already blocked at this
     * snapshot, so a mis-toggle made during the current session can still be
     * undone until Done restarts the app. Null until beginSettingsSession() runs.
     */
    private static HashMap<String, Boolean> sBaseline;
    private static HashMap<String, ?> sSettingsPreferences;

    private Config() {}

    /** Resolve the process Application context via ActivityThread, or null. */
    public static Context getAppContext() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object app = activityThread.getMethod("currentApplication").invoke(null);
            return (Context) app;
        } catch (Throwable t) {
            return null;
        }
    }

    private static SharedPreferences prefs() {
        Context context = getAppContext();
        return context == null ? null : context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @SuppressWarnings("unchecked")
    private static <T> T memoryValue(String key, T fallback) {
        Object value = MEMORY_PREFS.get(key);
        return value == null ? fallback : (T) value;
    }

    public static boolean getBlocked(String key, boolean defaultValue) {
        SharedPreferences prefs = prefs();
        if (prefs == null) {
            Object value = MEMORY_PREFS.get(key);
            return value instanceof Boolean ? (Boolean) value : defaultValue;
        }
        return prefs.getBoolean(key, defaultValue);
    }

    public static void setBlocked(String key, boolean value) {
        // Hardcore lock only forbids *revealing* a surface. Hiding one is always
        // allowed so users can still tighten. A surface may be revealed only when
        // it was already visible at the start of the current settings session
        // (undo of a mis-toggle); one hidden at session open stays frozen until a
        // reinstall.
        boolean hardcoreMode = isHardcoreMode();
        boolean hiddenAtBaseline = hardcoreMode && isLockable(key)
                && !hidesSurface(key, value) && wasHiddenAtBaseline(key);
        if (!isLockedChangeAllowed(key, value, hardcoreMode, hiddenAtBaseline)) return;
        SharedPreferences prefs = prefs();
        if (prefs != null) {
            prefs.edit().putBoolean(key, value).apply();
            return;
        }
        MEMORY_PREFS.put(key, value);
    }

    static boolean isLockedChangeAllowed(String key, boolean value,
                                         boolean hardcoreMode, boolean hiddenAtBaseline) {
        return !hardcoreMode || !isLockable(key) || hidesSurface(key, value) || !hiddenAtBaseline;
    }

    /**
     * Toggles the permanent lock can freeze: the content blocks and the
     * navigation-bar icons. The nav icons are cosmetic, but hiding one is how
     * people cut off a surface they keep falling back into (the Direct tab's
     * search behaves like Explore), so leaving them freely re-enabled defeated
     * the lock — issue #93.
     */
    public static boolean isLockable(String key) {
        return key != null && (key.startsWith("block_") || key.startsWith("nav_show_"));
    }

    /**
     * Whether this value leaves the surface hidden. The two families store
     * opposite polarities: {@code block_*} is true when hidden, {@code nav_show_*}
     * is true when shown.
     */
    public static boolean hidesSurface(String key, boolean value) {
        if (key == null) return false;
        return key.startsWith("nav_show_") ? !value : value;
    }

    public static boolean isHardcoreMode() {
        return getBlocked("hardcore_mode", false);
    }

    public static void enableHardcoreMode() {
        SharedPreferences prefs = prefs();
        if (prefs == null) return;
        prefs.edit().putBoolean("hardcore_mode", true).apply();
    }

    /**
     * Whether Instagram's window is pinned to SDR (COLOR_MODE_DEFAULT) instead of
     * the HDR mode Instagram forces on some accounts. On by default: the HDR
     * window mode only lifts the black floor of the dark UI (washed-out fond) with
     * no visible benefit. Not a block_* surface, so the permanent lock ignores it.
     */
    public static boolean isForceSdr() { return getBlocked("force_sdr", true); }

    /**
     * Whether the home feed is restricted to accounts you follow (chronological
     * "Following" feed) instead of the recommended feed. Off by default. Not a
     * block_* surface, so the permanent lock never freezes it.
     */
    public static boolean isFollowingFeedOnly() {
        return getBlocked("limit_following_feed", false);
    }

    public static boolean isFeedBlocked()      { return getBlocked("block_feed", true); }
    public static boolean isExploreBlocked()   { return getBlocked("block_explore", true); }
    public static boolean isReelsBlocked()     { return getBlocked("block_reels", true); }
    /** Follows the old suggested-content toggle until the user chooses independently. */
    public static boolean isFeedReelsBlocked() {
        return getBlocked("block_reels_feed", isSuggestedBlocked());
    }
    /**
     * Whether the "Friends" tab and its avatar facepile are hidden from the Reels
     * viewer's header. On by default: Instagram gates that entry point behind a
     * server flag ({@code friends_lane_floating_pogs_entrypoint_enabled}) only
     * reachable from an internal developer menu, so the only way out is to drop
     * it from the view tree — issue #94.
     */
    public static boolean isFriendsLaneBlocked() { return getBlocked("block_friends_lane", true); }
    public static boolean isStoriesBlocked()   { return getBlocked("block_stories", false); }
    public static boolean areStoryOpeningsBlocked() {
        return getBlocked("block_story_opening", false);
    }
    public static boolean isInstantsBlocked()  { return getBlocked("block_instants", true); }
    public static boolean isNotesBlocked()     { return getBlocked("block_notes", true); }
    public static boolean isSuggestedBlocked() { return getBlocked("block_suggested", true); }
    public static boolean isAdsBlocked()       { return getBlocked("block_ads", true); }

    public static final class SettingsProfile {
        private final String id;
        private final String title;
        private final String description;
        private final String[] keys;
        private final boolean[] values;

        private SettingsProfile(String id, String title, String description,
                                String[] keys, boolean[] values) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.keys = keys;
            this.values = values;
        }

        public String id() { return id; }
        public String title() { return title; }
        public String description() { return description; }
        public int size() { return keys.length; }
        public String keyAt(int index) { return keys[index]; }
        public boolean valueAt(int index) { return values[index]; }
    }

    private static final String[] PROFILE_KEYS = {
            "block_feed", "limit_following_feed", "block_explore", "block_reels",
            "block_reels_feed", "block_friends_lane", "block_stories", "block_story_opening",
            "block_instants", "block_notes", "block_suggested", "block_ads",
            "block_notifications", "nav_show_home", "nav_show_search", "nav_show_reels",
            "nav_show_create", "nav_show_direct", "nav_show_profile",
    };

    private static final SettingsProfile[] SETTINGS_PROFILES = {
            new SettingsProfile("messages", "Messages only",
                    "Keep conversations available; hide the main browsing surfaces.",
                    PROFILE_KEYS,
                    new boolean[]{true, false, true, true, true, true, true, true,
                            true, true, true, true, true, false, false, false, false, true, false}),
            new SettingsProfile("following", "Following only",
                    "Keep the Following feed and Stories tray; hide recommendations and Reels.",
                    PROFILE_KEYS,
                    new boolean[]{false, true, true, true, true, true, false, false,
                            true, true, true, true, false, true, false, false, false, true, true}),
            new SettingsProfile("strict", "Strict mode",
                    "Hide feeds, discovery, Reels, Stories and extra entry points.",
                    PROFILE_KEYS,
                    new boolean[]{true, false, true, true, true, true, true, true,
                            true, true, true, true, true, true, false, false, false, true, false}),
    };

    public static SettingsProfile[] settingsProfiles() {
        return SETTINGS_PROFILES.clone();
    }

    public static SettingsProfile settingsProfile(String id) {
        if (id == null) return null;
        for (SettingsProfile profile : SETTINGS_PROFILES) {
            if (profile.id.equals(id)) return profile;
        }
        return null;
    }

    public static boolean canApplyProfile(String id) {
        SettingsProfile profile = settingsProfile(id);
        if (profile == null) return false;
        boolean hardcore = isHardcoreMode();
        for (int i = 0; i < profile.size(); i++) {
            String key = profile.keyAt(i);
            boolean value = profile.valueAt(i);
            boolean hiddenAtBaseline = hardcore && isLockable(key)
                    && !hidesSurface(key, value) && wasHiddenAtBaseline(key);
            if (!isLockedChangeAllowed(key, value, hardcore, hiddenAtBaseline)) return false;
        }
        return true;
    }

    /** Applies only the profile's explicitly listed settings; other preferences stay untouched. */
    public static boolean applyProfile(String id) {
        SettingsProfile profile = settingsProfile(id);
        if (profile == null || !canApplyProfile(id)) return false;
        SharedPreferences prefs = prefs();
        if (prefs == null) return false;
        SharedPreferences.Editor editor = prefs.edit();
        for (int i = 0; i < profile.size(); i++) {
            editor.putBoolean(profile.keyAt(i), profile.valueAt(i));
        }
        editor.apply();
        setNeedsRestart();
        return true;
    }

    /**
     * Whether the notifications ("heart") button in the feed header is hidden.
     * Off by default: it's an opt-in distraction cut, and people expect their
     * notifications entry-point to still be there after an update.
     */
    public static boolean isNotificationsButtonBlocked() { return getBlocked("block_notifications", false); }

    /**
     * Whether Instagram's popups (its own toasts: "Couldn't refresh feed",
     * "Impossible d'actualiser le fil", ...) are dropped. On by default: blocking
     * a surface makes Instagram see a failed request and raise one every time, so
     * they are noise the mod creates itself. All of them go, not just the errors —
     * see {@link Toasts}. Deliberately not a {@code block_*} key: it is not a
     * content surface, so the permanent lock must not freeze it.
     */
    public static boolean arePopupsHidden() { return getBlocked("hide_toasts", true); }

    /**
     * Bottom-navigation icon visibility, stored per tab as {@code nav_show_<tab>}
     * (true = shown). Independent of the content blocks, so hiding the Reels
     * *icon* is decoupled from blocking Reels *content*. Reels defaults to hidden
     * to preserve the previous behaviour (blocking reels used to also hide the tab).
     */
    public static boolean isReelsTabShown() { return getBlocked("nav_show_reels", false); }

    /**
     * Whether the Home icon is on the bar (issue #127). Long-pressing a tab is the
     * only way into the settings: with Home hidden the gesture moves to the first
     * icon left on the bar, so Home only goes once another icon is there to take
     * it over — hiding every icon keeps Home, or the settings would be unreachable.
     */
    public static boolean isHomeTabShown() {
        boolean quoicouview = isQuoicouviewEnabled();
        if (quoicouview) return isHomeTabShown(true, false, false);
        boolean home = getBlocked("nav_show_home", true);
        boolean anotherTabShown = false;
        if (!home) {
            for (String key : NAV_KEYS) {
                if (!"nav_show_home".equals(key) && getBlocked(key, navDefault(key))) {
                    anotherTabShown = true;
                    break;
                }
            }
        }
        return isHomeTabShown(false, home, anotherTabShown);
    }

    static boolean isHomeTabShown(boolean quoicouview, boolean home, boolean anotherTabShown) {
        if (quoicouview) return false;
        return home || !anotherTabShown;
    }

    /**
     * Quoicouview mode (experimental, off by default): Messages is the main page,
     * with the stories tray on top of it, and Home is off. While it is on, Home
     * counts as hidden, Messages as shown and the landing page is Messages,
     * whatever their own settings say; those are kept and apply again once the
     * mode is turned off. Not a block_* surface, so the permanent lock does not
     * freeze it, but it cannot bring back a Messages icon the lock keeps hidden:
     * the mode simply does not apply then. See {@link Quoicouview}.
     */
    public static boolean isQuoicouviewEnabled() {
        return getBlocked("quoicouview_mode", false) && isQuoicouviewAvailable();
    }

    /** False while the permanent lock keeps the Messages icon hidden. */
    public static boolean isQuoicouviewAvailable() {
        return !(isHardcoreMode() && !getBlocked("nav_show_direct", true));
    }

    /** Whether the card presenting Quoicouview mode has been shown on this installation. */
    public static boolean isQuoicouviewIntroShown() { return getBlocked("quoicouview_intro_shown", false); }

    public static void setQuoicouviewIntroShown() { setBlocked("quoicouview_intro_shown", true); }

    /**
     * Whether the first-run coach mark (long-press Home to open settings) has
     * already been shown on this installation. Set once, never cleared: the guide
     * is a one-time thing, not a setting.
     *
     * Kept off by default for a practical app experience: the tutorial is not a
     * core feature and should not keep reappearing in a daily driver.
     */
    public static boolean isOnboardingDone() { return getBlocked("onboarding_done", true); }

    public static void setOnboardingDone() { setBlocked("onboarding_done", true); }

    /** Preferred app language: "fr" or "en". Falls back to the device locale. */
    public static String getUiLanguage() {
        SharedPreferences prefs = prefs();
        String stored = prefs == null ? memoryValue("ui_language", (String) null)
                : prefs.getString("ui_language", null);
        String normalized = normalizeUiLanguage(stored);
        if (normalized != null) return normalized;
        String device = Locale.getDefault().getLanguage();
        return "fr".equalsIgnoreCase(device) ? "fr" : "en";
    }

    public static String getAppVersionName() {
        return "1.0.9";
    }

    public static String getChangelogText() {
        return "test mise à jour v1.0.9\n\n"
                + "Feurstagram 1.0.9\n\n"
                + "- Cloudflare OTA updates verified and published from the public manifest\n"
                + "- fixed update flow for signed in-place APK upgrades\n"
                + "- validated remote manifest parsing and version comparison\n"
                + "- cleaner update notes and release metadata for the app settings screen\n"
                + "- compatibility with the public R2 update feed and APK download URL";
    }

    public static boolean matchesSettingsSearch(String label, String query) {
        if (query == null || query.trim().isEmpty()) return true;
        if (label == null) return false;
        String normalized = label.toLowerCase(Locale.ROOT);
        String[] terms = query.trim().toLowerCase(Locale.ROOT).split("\\s+");
        for (String term : terms) {
            if (term.isEmpty()) continue;
            if (!normalized.contains(term)) return false;
        }
        return true;
    }

    public static void setUiLanguage(String language) {
        String normalized = normalizeUiLanguage(language);
        if (normalized == null) return;
        SharedPreferences prefs = prefs();
        if (prefs != null) {
            prefs.edit().putString("ui_language", normalized).apply();
        } else {
            MEMORY_PREFS.put("ui_language", normalized);
        }
        setNeedsRestart();
    }

    private static String normalizeUiLanguage(String value) {
        if (value == null) return null;
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        if ("fr".equals(trimmed) || "en".equals(trimmed)) return trimmed;
        return null;
    }

    /** Whether promotional / subscription overlays are stripped from the app UI. */
    public static boolean areSubscriptionPromosHidden() {
        return getBlocked("hide_subscription_promos", true);
    }

    /** Snapshot the full Feurstagram preference set, excluding Android internals. */
    public static Map<String, Object> snapshotSettings() {
        SharedPreferences prefs = prefs();
        Map<String, Object> snapshot = new HashMap<>();
        if (prefs != null) {
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                snapshot.put(entry.getKey(), entry.getValue());
            }
            return snapshot;
        }
        snapshot.putAll(MEMORY_PREFS);
        return snapshot;
    }

    /** Restore a previously saved preference snapshot produced by {@link #snapshotSettings()}. */
    public static Map<String, Object> restoreSettings(Map<String, Object> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) return new HashMap<>();
        SharedPreferences prefs = prefs();
        if (prefs != null) {
            SharedPreferences.Editor editor = prefs.edit();
            for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof Boolean) {
                    editor.putBoolean(key, (Boolean) value);
                } else if (value instanceof String) {
                    editor.putString(key, (String) value);
                } else if (value instanceof Integer) {
                    editor.putInt(key, (Integer) value);
                } else if (value instanceof Long) {
                    editor.putLong(key, (Long) value);
                } else if (value instanceof Float) {
                    editor.putFloat(key, (Float) value);
                } else if (value instanceof Set<?>) {
                    editor.putStringSet(key, (Set<String>) value);
                } else if (value == null) {
                    editor.remove(key);
                } else {
                    editor.putString(key, String.valueOf(value));
                }
            }
            editor.apply();
        } else {
            MEMORY_PREFS.clear();
            MEMORY_PREFS.putAll(snapshot);
        }
        setNeedsRestart();
        return new HashMap<>(snapshot);
    }

    /** Navigation-bar icons, with the default the getters use (true = shown). */
    private static final String[] NAV_KEYS = {
            "nav_show_home", "nav_show_search", "nav_show_reels", "nav_show_create",
            "nav_show_direct", "nav_show_profile",
    };

    /** Only Reels defaults to hidden; see {@link #isReelsTabShown()}. */
    public static boolean navDefault(String key) {
        return !"nav_show_reels".equals(key);
    }

    /** Whether the bottom-bar icon for a {@code nav_show_*} key is currently shown. */
    public static boolean isNavTabShown(String navKey) {
        if ("nav_show_home".equals(navKey)) return isHomeTabShown();
        if ("nav_show_direct".equals(navKey) && isQuoicouviewEnabled()) return true;
        return getBlocked(navKey, navDefault(navKey));
    }

    /** The {@code nav_show_*} key behind a bottom-bar tab's resource name, or null. */
    public static String navKeyForTab(String tab) {
        if (tab == null) return null;
        switch (tab) {
            case "feed_tab": return "nav_show_home";
            case "search_tab": return "nav_show_search";
            case "clips_tab": return "nav_show_reels";
            case "creation_tab": return "nav_show_create";
            case "direct_tab": return "nav_show_direct";
            case "profile_tab": return "nav_show_profile";
            default: return null;
        }
    }

    /**
     * The {@code nav_show_*} key whose icon a landing page rides on, or null for
     * anything unrecognised.
     */
    public static String navKeyForLanding(String landing) {
        if ("home".equals(landing)) return "nav_show_home";
        if ("search".equals(landing)) return "nav_show_search";
        if ("direct".equals(landing)) return "nav_show_direct";
        if ("profile".equals(landing)) return "nav_show_profile";
        return null;
    }

    /**
     * Whether a surface may be used as the landing page. Landing on a tab that was
     * hidden from the bottom bar would hand back the very surface the user removed —
     * and under the permanent lock it was a way around a frozen toggle, since the
     * landing picker was never locked (issue #116). So a hidden tab is not a
     * candidate, lock or no lock.
     */
    public static boolean isLandingAvailable(String landing) {
        boolean quoicouview = isQuoicouviewEnabled();
        if (quoicouview) {
            return isLandingAvailable(landing, true, false);
        }
        String navKey = navKeyForLanding(landing);
        boolean navShown = navKey == null || isNavTabShown(navKey);
        return isLandingAvailable(landing, false, navShown);
    }

    static boolean isLandingAvailable(String landing, boolean quoicouview, boolean navShown) {
        if (quoicouview) return "direct".equals(landing);
        return navKeyForLanding(landing) == null || navShown;
    }

    static String landingPageOrHome(String landing, boolean quoicouview, boolean navShown) {
        return isLandingAvailable(landing, quoicouview, navShown)
                ? landing : "home";
    }

    /** Snapshot the lock baseline and persisted settings at the start of a page session. */
    public static void beginSettingsSession() {
        HashMap<String, Boolean> baseline = new HashMap<>();
        baseline.put("block_feed", isFeedBlocked());
        baseline.put("block_explore", isExploreBlocked());
        baseline.put("block_reels", isReelsBlocked());
        baseline.put("block_reels_feed", isFeedReelsBlocked());
        baseline.put("block_friends_lane", isFriendsLaneBlocked());
        baseline.put("block_stories", isStoriesBlocked());
        baseline.put("block_story_opening", areStoryOpeningsBlocked());
        baseline.put("block_instants", isInstantsBlocked());
        baseline.put("block_notes", isNotesBlocked());
        baseline.put("block_suggested", isSuggestedBlocked());
        baseline.put("block_ads", isAdsBlocked());
        for (String key : NAV_KEYS) {
            baseline.put(key, getBlocked(key, navDefault(key)));
        }
        sBaseline = baseline;

        SharedPreferences prefs = prefs();
        sSettingsPreferences = prefs == null ? null : new HashMap<>(prefs.getAll());
        sNeedsRestart = false;
    }

    /**
     * True if the given key's surface was already hidden at the start of the
     * current settings session. Falls back to the live persisted value when no
     * baseline was captured (calls outside the settings page).
     */
    public static boolean wasHiddenAtBaseline(String key) {
        Boolean captured = sBaseline == null ? null : sBaseline.get(key);
        boolean value = captured != null ? captured : getBlocked(key, lockableDefault(key));
        return hidesSurface(key, value);
    }

    /**
     * Default for a lockable key, used only when no baseline was captured (a write
     * from outside the settings page). block_* falls back to false — "not blocked
     * at baseline" — which errs on the permissive side rather than freezing a
     * surface the user never locked.
     */
    private static boolean lockableDefault(String key) {
        if (key == null) return false;
        if (key.startsWith("nav_show_")) return navDefault(key);
        return false;
    }

    /**
     * The surface the app should jump to on cold start: one of "home" (default),
     * "search", "direct", "profile".
     */
    public static String getLandingPage() {
        boolean quoicouview = isQuoicouviewEnabled();
        if (quoicouview) {
            return landingPageOrHome("direct", true, false);
        }
        SharedPreferences prefs = prefs();
        String stored = prefs == null ? "home" : prefs.getString("landing_page", "home");
        // The stored choice is kept as-is so re-showing the tab restores it, but a
        // landing page whose tab is hidden right now falls back to "home": the home
        // feed, or with the Home icon hidden too, the first icon on the bar.
        String navKey = navKeyForLanding(stored);
        boolean navShown = navKey == null || isNavTabShown(navKey);
        return landingPageOrHome(stored, false, navShown);
    }

    public static void setLandingPage(String value) {
        if (!isLandingAvailable(value)) return;
        SharedPreferences prefs = prefs();
        if (prefs == null) return;
        prefs.edit().putString("landing_page", value).apply();
    }

    public static void setNeedsRestart() { sNeedsRestart = true; }

    public static boolean isRestartPending() {
        if (!sNeedsRestart || sSettingsPreferences == null) return sNeedsRestart;
        SharedPreferences prefs = prefs();
        return prefs == null || havePreferencesChanged(sSettingsPreferences, prefs.getAll());
    }

    public static void endSettingsSession() {
        sSettingsPreferences = null;
        sBaseline = null;
        sNeedsRestart = false;
    }

    static boolean havePreferencesChanged(Map<String, ?> initial, Map<String, ?> current) {
        return initial != null && current != null && !initial.equals(current);
    }
}
