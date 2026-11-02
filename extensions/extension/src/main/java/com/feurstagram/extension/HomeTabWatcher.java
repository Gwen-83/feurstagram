package com.feurstagram.extension;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

/**
 * Watches the bottom tab bar until the settings tab is inflated, then installs
 * settings long-presses on Home (or the first visible tab) and Profile.
 *
 * The existing Home/first-visible-tab gesture remains available everywhere.
 * Profile provides a second shortcut without replacing its normal click behavior.
 * Both resource ids are resolved by name (with a clone fallback).
 *
 * When the user hides Home or enables Quoicouview, the primary gesture moves to
 * the first icon still on the bar ({@link Hiders#settingsTab}).
 */
public final class HomeTabWatcher implements ViewTreeObserver.OnGlobalLayoutListener {

    private ViewGroup tabBar;

    public HomeTabWatcher(ViewGroup tabBar) {
        this.tabBar = tabBar;
    }

    @Override
    public void onGlobalLayout() {
        ViewGroup bar = tabBar;
        if (bar == null) return;
        View homeTab = Hiders.settingsTab(bar);
        if (homeTab == null) return; // not inflated yet; keep waiting

        // Note: do NOT call homeTab.setTag(...) to mark this view. Instagram stores
        // its own object in the tab's view tag and casts it back on resume, so an
        // overwritten tag crashes (ClassCastException). Detaching after install is
        // enough to run once; re-setting the listener is harmless if it ever repeats.
        installSettingsShortcut(homeTab);

        View profileTab = findTab(bar, "profile_tab");
        if (profileTab != null && Config.isNavTabShown("nav_show_profile")) {
            installSettingsShortcut(profileTab);
        }

        // First launch with a signed-in account: point at the gesture we just
        // installed. The tab bar only exists once the user is past the login
        // screens, so reaching here *is* "after the Instagram connection". The
        // guide waits for a screen clear of Instagram's own post-login popups
        // before it appears, so it is handed the bar and finds the tab itself.
        Onboarding.maybeShow(bar);

        // Done: detach so we don't keep re-running on every layout pass.
        bar.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        tabBar = null;
    }

    private static View findTab(ViewGroup bar, String name) {
        for (int i = 0; i < bar.getChildCount(); i++) {
            View child = bar.getChildAt(i);
            if (name.equals(Hiders.tabName(child))) return child;
        }
        return null;
    }

    private static void installSettingsShortcut(View tab) {
        tab.setTooltipText("Long-press to open Feurstagram settings");
        CharSequence description = tab.getContentDescription();
        String hint = "Long-press for Feurstagram settings";
        if (description == null || !description.toString().contains(hint)) {
            tab.setContentDescription(description == null
                    ? hint : description + ". " + hint);
        }
        tab.setOnLongClickListener(v -> {
            Context activity = Settings.getActivityContext(v);
            if (activity == null) return false;
            Settings.show(activity);
            return true;
        });
    }
}
