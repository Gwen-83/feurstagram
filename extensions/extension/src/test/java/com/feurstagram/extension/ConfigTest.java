package com.feurstagram.extension;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfigTest {

    @Test
    public void permanentLockOnlyPreventsRevealingBaselineHiddenSurfaces() {
        assertFalse(Config.isLockedChangeAllowed("block_feed", false, true, true));
        assertTrue(Config.isLockedChangeAllowed("block_feed", true, true, true));
        assertTrue(Config.isLockedChangeAllowed("block_feed", false, true, false));
        assertTrue(Config.isLockedChangeAllowed("block_feed", false, false, true));

        assertFalse(Config.isLockedChangeAllowed("nav_show_direct", true, true, true));
        assertTrue(Config.isLockedChangeAllowed("nav_show_direct", false, true, true));
        assertTrue(Config.isLockedChangeAllowed("hide_toasts", false, true, true));
    }

    @Test
    public void homeTabStaysAvailableWhenEveryOtherTabIsHidden() {
        assertFalse(Config.isHomeTabShown(false, false, true));
        assertTrue(Config.isHomeTabShown(false, false, false));
        assertTrue(Config.isHomeTabShown(false, true, true));
        assertFalse(Config.isHomeTabShown(true, true, false));
    }

    @Test
    public void recommendedReelsKeepTheLegacySuggestedSettingAsTheirDefault() {
        assertEquals(Config.isSuggestedBlocked(), Config.isFeedReelsBlocked());
    }

    @Test
    public void onboardingIsDisabledByDefaultToKeepTheAppPractical() {
        assertTrue(Config.isOnboardingDone());
    }

    @Test
    public void restartDiffIgnoresUnchangedAndRevertedPreferences() {
        Map<String, Object> initial = new HashMap<>();
        initial.put("block_feed", true);
        initial.put("landing_page", "home");
        initial.put("nav_show_profile", true);

        Map<String, Object> unchanged = new HashMap<>(initial);
        assertFalse(Config.havePreferencesChanged(initial, unchanged));

        Map<String, Object> changed = new HashMap<>(initial);
        changed.put("block_feed", false);
        assertTrue(Config.havePreferencesChanged(initial, changed));

        changed.put("block_feed", true);
        assertFalse(Config.havePreferencesChanged(initial, changed));

        changed.put("landing_page", "profile");
        assertTrue(Config.havePreferencesChanged(initial, changed));
        changed.put("landing_page", "home");
        changed.put("nav_show_profile", false);
        assertTrue(Config.havePreferencesChanged(initial, changed));
        changed.put("nav_show_profile", true);
        assertFalse(Config.havePreferencesChanged(initial, changed));
    }

    @Test
    public void landingChoicesRespectVisibleTabsAndQuoicouview() {
        assertTrue(Config.isLandingAvailable("search", false, true));
        assertFalse(Config.isLandingAvailable("search", false, false));
        assertTrue(Config.isLandingAvailable("direct", true, false));
        assertFalse(Config.isLandingAvailable("home", true, true));

        assertEquals("home", Config.landingPageOrHome("search", false, false));
        assertEquals("direct", Config.landingPageOrHome("direct", true, false));
        assertEquals("custom", Config.landingPageOrHome("custom", false, true));
    }

    @Test
    public void languagePreferenceFallsBackToDeviceLocaleAndCanBeOverridden() {
        assertTrue(Config.getUiLanguage().equals("fr") || Config.getUiLanguage().equals("en"));
        Config.setUiLanguage("fr");
        assertEquals("fr", Config.getUiLanguage());
        Config.setUiLanguage("en");
        assertEquals("en", Config.getUiLanguage());
    }

    @Test
    public void backupAndRestoreSettingsRoundTripWithoutChangingLockedValues() {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("block_feed", true);
        snapshot.put("landing_page", "profile");
        snapshot.put("hide_toasts", false);
        snapshot.put("nav_show_reels", false);

        Map<String, Object> copied = new HashMap<>(snapshot);
        assertEquals(snapshot, copied);
        assertTrue(Config.snapshotSettings().containsKey("block_feed") || Config.snapshotSettings().isEmpty());

        Map<String, Object> restored = Config.restoreSettings(snapshot);
        assertEquals(snapshot, restored);
        assertEquals("profile", restored.get("landing_page"));
        assertEquals(Boolean.FALSE, restored.get("hide_toasts"));
    }

    @Test
    public void appVersionAndSettingsSearchSupportUserFacingDiscovery() {
        assertFalse(Config.getAppVersionName().isEmpty());
        assertTrue(Config.getChangelogText().length() > 0);
        assertTrue(Config.matchesSettingsSearch("Display and popups", "popup"));
        assertTrue(Config.matchesSettingsSearch("Language", "lang"));
        assertFalse(Config.matchesSettingsSearch("Support", "reels"));
    }

    @Test
    public void updateMetadataSupportsVersionChecksAndInstallUrls() {
        UpdateManager.Release release = UpdateManager.parseRelease(
                "{\"version\":\"1.0.1\",\"apkUrl\":\"https://example.com/feurstagram.apk\",\"notes\":\"Fixes\"}");
        assertEquals("1.0.1", release.version);
        assertEquals("https://example.com/feurstagram.apk", release.apkUrl);
        assertTrue(UpdateManager.shouldUpdate("1.0.0", release.version));
        assertFalse(UpdateManager.shouldUpdate(release.version, "1.0.0"));
    }

    @Test
    public void otaManifestUnescapesNotesAndFallsBackToDownloadUrl() {
        UpdateManager.Release release = UpdateManager.parseRelease(
                "{\"version\":\"1.0.5\",\"downloadUrl\":\"https://example.com/feurstagram-release.apk\",\"notes\":\"test mise à jour\\n\\nVersion 1.0.5 - mise à jour Feurstagram.\"}");
        assertEquals("1.0.5", release.version);
        assertEquals("https://example.com/feurstagram-release.apk", release.apkUrl);
        assertTrue(release.notes.contains("test mise à jour"));
        assertTrue(release.notes.contains("Version 1.0.5"));
        assertTrue(release.notes.contains("\n\n"));
    }

    @Test
    public void profilesDefineExactPresetsWithoutTouchingOtherPreferences() {
        Config.SettingsProfile messages = Config.settingsProfile("messages");
        Config.SettingsProfile following = Config.settingsProfile("following");
        Config.SettingsProfile strict = Config.settingsProfile("strict");

        assertEquals(3, Config.settingsProfiles().length);
        assertTrue(profileValue(messages, "block_feed"));
        assertTrue(profileValue(messages, "nav_show_direct"));
        assertFalse(profileValue(messages, "nav_show_search"));

        assertFalse(profileValue(following, "block_feed"));
        assertTrue(profileValue(following, "limit_following_feed"));
        assertFalse(profileValue(following, "block_stories"));

        assertTrue(profileValue(strict, "block_feed"));
        assertTrue(profileValue(strict, "block_story_opening"));
        assertTrue(profileValue(strict, "block_reels_feed"));
        assertTrue(profileValue(strict, "nav_show_home"));
        assertFalse(profileValue(strict, "nav_show_profile"));

        for (int i = 0; i < strict.size(); i++) {
            assertFalse("profiles must not modify credentials or unrelated preferences",
                    strict.keyAt(i).contains("password"));
            assertFalse("profiles must not modify credentials or unrelated preferences",
                    strict.keyAt(i).contains("token"));
            assertFalse("profiles must not change the permanent lock", "hardcore_mode".equals(strict.keyAt(i)));
        }
        assertEquals(null, Config.settingsProfile("unknown"));
    }

    private static boolean profileValue(Config.SettingsProfile profile, String key) {
        for (int i = 0; i < profile.size(); i++) {
            if (key.equals(profile.keyAt(i))) return profile.valueAt(i);
        }
        throw new AssertionError("Profile does not define " + key);
    }
}
