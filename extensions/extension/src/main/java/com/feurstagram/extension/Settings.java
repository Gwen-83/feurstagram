package com.feurstagram.extension;

import android.app.Activity;
import android.app.Dialog;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;

import java.io.File;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Feurstagram settings, shown as a full-screen page when the Home tab (or, with
 * Home hidden, the first tab on the bar) is long-pressed. Built entirely in code
 * (no bundled resources) and persisted through {@link Config}.
 *
 * <h3>The look: Material 3 Expressive, monochrome</h3>
 * White is the only accent — no hue anywhere, so the panel reads as part of a
 * dark OS rather than as a themed app. The Expressive part is in the shapes and
 * the type: large corner radii, options grouped into "connected" lists where the
 * group's outer corners are round and the joints between rows are nearly square,
 * pill-shaped buttons, sentence-case section labels, and a tight-tracked headline.
 * This class also owns the palette, type and shape helpers the other Feurstagram
 * surfaces ({@link Onboarding}) draw with, so the design
 * only lives in one place.
 *
 * <h3>Fitting any screen</h3>
 * Instagram targets a recent SDK, so its windows are edge-to-edge: system bars
 * and display cutouts overlap the dialog and nothing is inset for us. Every
 * Feurstagram window therefore goes through {@link #applyWindowInsets} and pads
 * itself — otherwise the action buttons sit under a three-button navigation bar
 * and the scroll area runs off the bottom of the screen. Content that can grow
 * (the settings list, a release-notes block) lives in a scroller bounded by what
 * is actually on screen, so a short screen or landscape shrinks it instead of
 * pushing the buttons away.
 */
public final class Settings {

    // --- Colour: a neutral dark scheme with white as the single accent --------

    /** Page background. */
    public static final int SURFACE = 0xFF0E0E0E;
    /** Cards, list rows. */
    public static final int SURFACE_CONTAINER = 0xFF1B1B1B;
    /** One step up: tonal buttons, the raised confirm card. */
    public static final int SURFACE_CONTAINER_HIGH = 0xFF262626;
    public static final int ON_SURFACE = 0xFFF4F4F4;
    public static final int ON_SURFACE_VARIANT = 0xFFABABAB;
    public static final int OUTLINE = 0xFF757575;
    public static final int OUTLINE_VARIANT = 0xFF3A3A3A;
    /** The accent. Monochrome by design: white. */
    public static final int PRIMARY = 0xFFFFFFFF;
    public static final int ON_PRIMARY = 0xFF101010;
    /**
     * Destructive actions. A monochrome scheme has no red to lean on, so they
     * carry the same accent and are distinguished by their wording instead.
     */
    public static final int ERROR = 0xFFFFFFFF;
    public static final int ON_ERROR = 0xFF101010;
    public static final int DIVIDER = 0xFF2A2A2A;
    public static final int RIPPLE = 0x26FFFFFF;

    // --- Shape: the M3 Expressive corner scale --------------------------------

    /** Dialog / card corners. */
    private static final float CORNER_XL = 28f;
    /** The outer corners of a connected list group. */
    private static final float CORNER_L = 24f;
    /** The joints inside a connected list group. */
    private static final float CORNER_JOINT = 4f;
    /** Larger than half a view's height, so it renders as a pill. */
    private static final float CORNER_FULL = 200f;
    /** Gap between the rows of a connected group. */
    private static final int ROW_GAP_DP = 3;

    private Settings() {}

    /**
     * Install the Feurstagram entry points off the bottom tab bar: the long-press
     * settings gesture and UI hiders. The tab bar is
     * just a stable handle into the window's view tree.
     */
    public static void installHomeTabWatcher(ViewGroup tabBar) {
        if (tabBar == null) return;
        tabBar.getViewTreeObserver().addOnGlobalLayoutListener(new HomeTabWatcher(tabBar));
        Hiders.installAll(tabBar);
        Context activity = getActivityContext(tabBar);
        QuoicouviewIntro.maybeShow(activity, () -> {});
    }

    /** Unwrap a view's context down to the hosting Activity when possible. */
    public static Context getActivityContext(View view) {
        if (view == null) return null;
        Context context = view.getContext();
        Context cursor = context;
        while (cursor != null && !(cursor instanceof Activity) && cursor instanceof ContextWrapper) {
            cursor = ((ContextWrapper) cursor).getBaseContext();
        }
        return cursor != null ? cursor : context;
    }

    /** How far the settings page rises while it opens. */
    private static final int OPEN_RISE_DP = 32;
    /** Length of the settings page's opening animation. */
    private static final long OPEN_DURATION_MS = 280L;

    public static void show(Context context) {
        if (context == null) return;
        try {
            SettingsDialog dialog = new SettingsDialog(context);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            View content = buildContent(context, dialog);
            dialog.page = content;
            dialog.setContentView(content);

            // Opening animation: the page rises and fades in over Instagram. The window
            // itself stays transparent (the page paints its own background), so what is
            // behind shows through until the page has settled.
            content.setAlpha(0f);
            content.setTranslationY(dp(context, OPEN_RISE_DP));
            dialog.setOnShowListener(d -> content.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(OPEN_DURATION_MS)
                    .setInterpolator(new DecelerateInterpolator(2f))
                    .start());

            // Intercept Back (incl. predictive-back gesture) to restart on change.
            dialog.setOnCancelListener(d -> {
                if (Config.isRestartPending()) {
                    CacheCleaner.clearAndRestart(context);
                }
            });
            dialog.setOnDismissListener(d -> Config.endSettingsSession());

            styleWindow(dialog, Color.TRANSPARENT, 0f);
            dialog.show();
        } catch (Throwable t) {
            Toast.makeText(context,
                    UiStrings.text("settings.unavailable", "Feurstagram settings unavailable here",
                            "Les réglages Feurstagram ne sont pas disponibles ici"),
                    Toast.LENGTH_LONG).show();
        }
    }

    // --- Window plumbing ------------------------------------------------------

    /**
     * Make a dialog window fill the screen edge to edge with light system-bar
     * icons, so the content underneath can pad itself against the bars and the
     * cutout. Every Feurstagram dialog goes through here.
     *
     * @param background window background colour, or 0 for a transparent scrim
     * @param dim        how much to darken what is behind (0 for the full-screen page)
     */
    public static void styleWindow(Dialog dialog, int background, float dim) {
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(background));
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        window.setDimAmount(dim);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);

        // Draw behind the bars and let the content pad itself. Without this the
        // window may not receive insets at all, and the padding below would be
        // computed from zeros.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        }
        window.getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        // Deprecated (and ignored) from API 35, but still what tints the bars on
        // everything older.
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        // The surface underneath is dark, so the bar icons must be light — the
        // host activity may have asked for the opposite.
        View decor = window.getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else {
            decor.setSystemUiVisibility(decor.getSystemUiVisibility()
                    & ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                    & ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    /** Receives the space taken by system bars and display cutouts, in pixels. */
    public interface InsetTarget {
        void onInsets(int left, int top, int right, int bottom);
    }

    /**
     * Report the window's system-bar + cutout insets to {@code target}, now and on
     * every change (rotation, switching to three-button navigation, a foldable
     * unfolding).
     */
    public static void applyWindowInsets(View view, InsetTarget target) {
        view.setOnApplyWindowInsetsListener((v, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = bars.left;
                top = bars.top;
                right = bars.right;
                bottom = bars.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            target.onInsets(left, top, right, bottom);
            return insets;
        });
        view.requestApplyInsets();
    }

    /**
     * Widest a column of text and controls is allowed to get. Past this a tablet,
     * an unfolded foldable or a landscape phone gives rows so wide the label and
     * its switch end up at opposite edges of the screen.
     */
    private static final int CONTENT_MAX_WIDTH_DP = 620;

    /**
     * Pad a full-screen root against the system bars and the cutout, and keep its
     * content centred within {@code maxWidthDp}.
     *
     * The width part has to run on every layout pass rather than only when the
     * insets change: {@code requestApplyInsets()} is asynchronous, so the first
     * layout happens with no insets at all, and a root that only listened for
     * insets would keep whatever it measured then.
     */
    public static void applyPageInsets(final View root, final int sideDp, final int verticalDp,
                                       final int maxWidthDp) {
        final Context context = root.getContext();
        final int[] insets = new int[4];
        Runnable apply = () -> {
            int usable = root.getWidth() - insets[0] - insets[2];
            int extra = Math.max(0, usable - dp(context, maxWidthDp)) / 2;
            int left = dp(context, sideDp) + insets[0] + extra;
            int top = dp(context, verticalDp) + insets[1];
            int right = dp(context, sideDp) + insets[2] + extra;
            int bottom = dp(context, verticalDp) + insets[3];
            if (root.getPaddingLeft() == left && root.getPaddingTop() == top
                    && root.getPaddingRight() == right && root.getPaddingBottom() == bottom) {
                return;
            }
            root.setPadding(left, top, right, bottom);
        };
        applyWindowInsets(root, (l, t, r, b) -> {
            insets[0] = l;
            insets[1] = t;
            insets[2] = r;
            insets[3] = b;
            apply.run();
        });
        root.getViewTreeObserver().addOnGlobalLayoutListener(apply::run);
    }

    // --- The full-screen page scaffold ----------------------------------------

    /**
     * The shape every Feurstagram full-screen surface takes: a headline block, a
     * scrolling middle that absorbs whatever space is left, and an action row
     * pinned above the navigation bar. Settings and both update screens are built
     * on it, so they inset, centre and shrink identically.
     */
    public static final class Page {
        public final LinearLayout root;
        public final LinearLayout header;
        public final ScrollView scroll;
        public final LinearLayout content;
        public final LinearLayout actions;

        Page(LinearLayout root, LinearLayout header, ScrollView scroll,
             LinearLayout content, LinearLayout actions) {
            this.root = root;
            this.header = header;
            this.scroll = scroll;
            this.content = content;
            this.actions = actions;
        }

        /** Add a pill that shares the action row equally with its siblings. */
        public Button addAction(Button button) {
            Context context = actions.getContext();
            if (actions.getChildCount() > 0) {
                View spacer = new View(context);
                actions.addView(spacer, new LinearLayout.LayoutParams(dp(context, 8), 1));
            }
            actions.addView(button, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            return button;
        }
    }

    public static Page newPage(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(SURFACE);
        applyPageInsets(root, 20, 12, CONTENT_MAX_WIDTH_DP);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(context, 4), dp(context, 12), dp(context, 4), dp(context, 4));
        root.addView(header);

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        // The fading edge is drawn inside the scroller's padding, so bottom
        // padding here would leave a band of unfaded rows under the fade. The
        // breathing room goes on the content instead, keeping the fade flush
        // with the scroller's edge, right above the action row.
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(context, 28));
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(context, 12));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.setMargins(0, dp(context, 12), 0, 0);
        root.addView(actions, actionsLp);

        return new Page(root, header, scroll, content, actions);
    }

    /** Headline plus supporting line, in a page's header block. */
    public static void addPageTitle(Page page, String titleText, String subtitleText) {
        Context context = page.root.getContext();
        TextView title = new TextView(context);
        title.setText(titleText);
        headline(title);
        page.header.addView(title);

        if (subtitleText != null) {
            TextView subtitle = new TextView(context);
            subtitle.setText(subtitleText);
            body(subtitle);
            subtitle.setPadding(0, dp(context, 6), 0, 0);
            page.header.addView(subtitle);
        }
    }

    /** A full-screen page dialog, styled and edge-to-edge. */
    public static Dialog newPageDialog(Context context, View content) {
        Dialog dialog = new Dialog(context, android.R.style.Theme_Material_NoActionBar);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(content);
        styleWindow(dialog, SURFACE, 0f);
        return dialog;
    }

    // --- The settings page ----------------------------------------------------

    /**
     * The settings window. Its pages share one dialog: Back on a section page
     * returns to the overview instead of closing settings. The platform routes
     * both the Back key and the predictive-back gesture through onBackPressed.
     */
    private static final class SettingsDialog extends Dialog {
        /** Set while a section page is open. */
        Runnable onBack;
        View page;
        private boolean closing;

        SettingsDialog(Context context) {
            super(context, android.R.style.Theme_Material_NoActionBar);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onBackPressed() {
            if (onBack != null) {
                onBack.run();
                return;
            }
            cancel();
        }

        @Override
        public void cancel() {
            if (closing) return;
            if (page == null || !isShowing() || !ValueAnimator.areAnimatorsEnabled()) {
                super.cancel();
                return;
            }
            closing = true;
            page.animate().cancel();
            page.animate().alpha(0f).translationY(dp(getContext(), OPEN_RISE_DP))
                    .setDuration(200L)
                    .setInterpolator(new AccelerateInterpolator(1.5f))
                    .withEndAction(this::finishCancel).start();
        }

        private void finishCancel() {
            super.cancel();
        }
    }

    /** Fills a section page's column. */
    private interface SectionBuilder {
        void build(Context context, LinearLayout column);
    }

    /** How far a page slides while it is swapped in. */
    private static final int PAGE_SLIDE_DP = 24;
    /** Length of the page swap. */
    private static final long PAGE_SWAP_MS = 180L;

    /**
     * The settings pages: an overview of sections, each opening its own page in
     * the same window. Header, scroller and the pinned action row are shared;
     * only the title, the back arrow and the column's rows change.
     */
    private static final class Pages {
        final Context context;
        final SettingsDialog dialog;
        final boolean hardcore;
        final View back;
        final TextView title;
        final TextView subtitle;
        final ScrollView scroll;
        final LinearLayout column;

        Pages(Context context, SettingsDialog dialog, boolean hardcore, View back, TextView title,
              TextView subtitle, ScrollView scroll, LinearLayout column) {
            this.context = context;
            this.dialog = dialog;
            this.hardcore = hardcore;
            this.back = back;
            this.title = title;
            this.subtitle = subtitle;
            this.scroll = scroll;
            this.column = column;
        }

        void showOverview(boolean animate) {
            dialog.onBack = null;
            back.setVisibility(View.GONE);
            title.setText("Feurstagram");
            subtitle.setText(hardcore
                    ? "Permanent lock is on. You can tighten blocks but not loosen them. "
                            + "Reinstall to fully unlock."
                    : "Choose what to hide. Tap Done to clear the cache and restart.");
            swap(animate, -1, () -> buildOverview(context, column, this));
        }

        void open(String name, String support, SectionBuilder builder) {
            dialog.onBack = () -> showOverview(true);
            back.setVisibility(View.VISIBLE);
            title.setText(name);
            subtitle.setText(support);
            swap(true, 1, () -> builder.build(context, column));
        }

        /** Rebuild the column, sliding in from the side the user is heading to. */
        private void swap(boolean animate, int direction, Runnable fill) {
            column.removeAllViews();
            fill.run();
            scroll.scrollTo(0, 0);
            if (!animate) return;
            column.setAlpha(0f);
            column.setTranslationX(direction * dp(context, PAGE_SLIDE_DP));
            column.animate().alpha(1f).translationX(0f).setDuration(PAGE_SWAP_MS)
                    .setInterpolator(new DecelerateInterpolator(2f)).start();
        }
    }

    private static View buildContent(Context context, SettingsDialog dialog) {
        // Snapshot the permanent-lock baseline and preference state for restart checks.
        Config.beginSettingsSession();
        boolean hardcore = Config.isHardcoreMode();

        final LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(SURFACE);
        applyPageInsets(root, 20, 12, CONTENT_MAX_WIDTH_DP);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(context, 4), dp(context, 12), dp(context, 4), dp(context, 4));
        root.addView(header);

        // Back arrow above the title, aligned with it: the 48dp touch target
        // reaches 12dp past the column's edge, as an M3 top app bar's does.
        ImageView back = new ImageView(context);
        back.setImageDrawable(igIcon(context, ON_SURFACE, "instagram_arrow_left_pano_outline_24",
                "instagram_arrow_left_outline_24"));
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setContentDescription("Back");
        back.setBackground(ripple(RIPPLE, oval(Color.TRANSPARENT)));
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(dp(context, 48), dp(context, 48));
        backLp.setMargins(-dp(context, 12), 0, 0, dp(context, 4));
        header.addView(back, backLp);

        TextView title = new TextView(context);
        headline(title);
        header.addView(title);

        TextView subtitle = new TextView(context);
        body(subtitle);
        subtitle.setPadding(0, dp(context, 6), 0, 0);
        header.addView(subtitle);

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        // The fading edge is drawn inside the scroller's padding, so bottom
        // padding here would leave a band of unfaded rows under the fade. The
        // breathing room goes on the content instead, keeping the fade flush
        // with the scroller's edge, right above the action row.
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(context, 28));
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(0, 0, 0, dp(context, 12));
        scroll.addView(column);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Pages pages = new Pages(context, dialog, hardcore, back, title, subtitle, scroll, column);
        back.setOnClickListener(v -> pages.showOverview(true));
        pages.showOverview(false);

        // Pinned action bar, on every page, so Done is always one tap away. Two
        // equal pills rather than end-aligned buttons: it cannot overflow, however
        // narrow the screen or long the label.
        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.setMargins(0, dp(context, 12), 0, 0);
        root.addView(actions, actionsLp);

        Button lock = makeButton(context, "Permanent lock", SURFACE_CONTAINER_HIGH, ON_SURFACE, true);
        if (hardcore) {
            lock.setText("Lock enabled");
            lock.setContentDescription("Permanent lock enabled; it cannot be disabled in the app");
        }
        lock.setOnClickListener(v -> {
            dialog.dismiss();
            confirmHardcore(context);
        });
        if (hardcore) {
            lock.setEnabled(false);
            lock.setAlpha(0.38f);
        }
        actions.addView(lock, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        View spacer = new View(context);
        actions.addView(spacer, new LinearLayout.LayoutParams(dp(context, 8), 1));

        Button done = makeButton(context, "Done", PRIMARY, ON_PRIMARY, true);
        done.setOnClickListener(v -> {
            boolean restartPending = Config.isRestartPending();
            dialog.dismiss();
            onDone(context, restartPending);
        });
        actions.addView(done, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        return root;
    }

    // --- The overview and its sections -----------------------------------------

    /** Keys behind the "Blocked surfaces" page, in display order. */
    private static final String[] SURFACE_KEYS = {
            "block_feed", "block_explore", "block_reels", "block_reels_feed",
            "block_friends_lane", "block_stories", "block_story_opening", "block_instants",
            "block_notes", "block_suggested", "block_ads", "block_notifications",
    };

    private static void buildOverview(Context context, LinearLayout column, Pages pages) {
        EditText search = new EditText(context);
        search.setHint(UiStrings.text("settings.search_hint", "Search settings", "Rechercher dans les réglages"));
        search.setSingleLine(true);
        search.setTextColor(ON_SURFACE);
        search.setHintTextColor(ON_SURFACE_VARIANT);
        search.setBackground(ripple(RIPPLE, roundedRect(SURFACE_CONTAINER, context, CORNER_L, CORNER_L, CORNER_L, CORNER_L)));
        search.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        search.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        search.setContentDescription(UiStrings.text("settings.search_hint", "Search settings", "Rechercher dans les réglages"));
        column.addView(search, stackedButtonParams(context, 12));

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String query = s == null ? "" : s.toString();
                applyOverviewSearch(column, query);
            }
        });

        LinearLayout experimental = addSection(context, column,
                UiStrings.text("settings.experimental_feature", "Experimental feature", "Fonctionnalité expérimentale"));
        addRow(context, experimental, UiStrings.text("settings.quoicouview_mode", "Quoicouview mode", "Mode Quoicouview"),
                "quoicouview_mode", Config.isQuoicouviewEnabled());
        if (!Config.isQuoicouviewAvailable()) greyOut(experimental.getChildAt(experimental.getChildCount() - 1));
        sealGroup(context, experimental);

        LinearLayout content = addSection(context, column,
                UiStrings.text("settings.content", "Content", "Contenu"));
        addSectionRow(context, content, "sparkles",
                UiStrings.text("settings.profiles", "Profiles", "Profils"),
                UiStrings.text("settings.profiles_hint", "3 ready-to-apply profiles", "3 profils prêts à appliquer"),
                () -> pages.open(UiStrings.text("settings.profiles_page", "Profiles", "Profils"),
                        UiStrings.text("settings.profiles_page_subtitle", "Preview a profile before applying it.",
                                "Prévisualisez un profil avant de l’appliquer."),
                        (pageContext, pageColumn) -> buildProfiles(pageContext, pageColumn, pages)));
        int hidden = 0;
        for (String key : SURFACE_KEYS) {
            if (isSurfaceHidden(key)) hidden++;
        }
        addSectionRow(context, content, "eye_off",
                UiStrings.text("settings.blocked_surfaces", "Blocked surfaces", "Surfaces bloquées"),
                hidden + " " + UiStrings.text("settings.of_hidden", "of", "sur") + " " + SURFACE_KEYS.length + " " +
                        UiStrings.text("settings.hidden", "hidden", "masquées"),
                () -> pages.open(UiStrings.text("settings.blocked_surfaces_page", "Blocked surfaces", "Surfaces bloquées"),
                        UiStrings.text("settings.blocked_surfaces_page_subtitle", "Hide parts of Instagram.",
                                "Masquez des parties d’Instagram."), Settings::buildSurfaces));
        addSectionRow(context, content, "layout",
                UiStrings.text("settings.navigation_bar", "Navigation bar", "Barre de navigation"), navSummary(context),
                () -> pages.open(UiStrings.text("settings.navigation_bar_page", "Navigation bar", "Barre de navigation"),
                        UiStrings.text("settings.navigation_bar_page_subtitle", "Choose the icons on the bottom bar.",
                                "Choisissez les icônes en bas de l’écran."), Settings::buildNav));
        addSectionRow(context, content, "home",
                UiStrings.text("settings.feed", "Feed", "Fil d’accueil"),
                Config.isFollowingFeedOnly() ? UiStrings.text("settings.feed_following", "Following only", "Suivi uniquement")
                        : UiStrings.text("settings.feed_recommended", "Recommended", "Recommandé"),
                () -> pages.open(UiStrings.text("settings.feed_page", "Feed", "Fil d’accueil"),
                        UiStrings.text("settings.feed_page_subtitle", "How the home feed is put together.",
                                "Comment le fil d’accueil est construit."), Settings::buildFeed));
        addSectionRow(context, content, "flag",
                UiStrings.text("settings.landing_page", "Landing page", "Page d’accueil"), landingLabel(Config.getLandingPage()),
                () -> pages.open(UiStrings.text("settings.landing_page_page", "Landing page", "Page d’accueil"),
                        UiStrings.text("settings.landing_page_page_subtitle", "Where Instagram opens on a cold start.",
                                "Où Instagram s’ouvre au démarrage."),
                        Settings::buildLanding));
        sealGroup(context, content);

        LinearLayout app = addSection(context, column, UiStrings.text("settings.section_app", "App", "Application"));
        addSectionRow(context, app, "moon",
                UiStrings.text("settings.display_and_popups", "Display and popups", "Affichage et popups"),
                displaySummary(),
                () -> pages.open("Display and popups", "How Instagram looks and talks to you.",
                        Settings::buildDisplay));
        addSectionRow(context, app, "flag",
                UiStrings.text("settings.language", "Language", "Langue"),
                "English".equalsIgnoreCase(Config.getUiLanguage()) ? "English" : "Français",
                () -> pages.open("Language", "Choose the interface language for Feurstagram.",
                        Settings::buildLanguage));
        addSectionRow(context, app, "download",
                UiStrings.text("settings.backup", "Settings backup", "Sauvegarde des réglages"),
                UiStrings.text("settings.backup_hint", "Save or restore your preferences",
                        "Enregistrer ou restaurer vos préférences"),
                () -> pages.open("Settings backup", "Keep or restore your Feurstagram preferences.",
                        Settings::buildBackup));
        addSectionRow(context, app, "info",
                UiStrings.text("settings.version", "Version & changelog", "Version et journal"),
                UiStrings.text("settings.version_value", "Version ", "Version ") + Config.getAppVersionName(),
                () -> pages.open("Version & changelog", "View release notes and current app version.",
                        Settings::buildVersionPage));
        addSectionRow(context, app, "download",
                UiStrings.text("settings.updates", "Updates", "Mises à jour"),
                UiStrings.text("settings.updates_hint", "Manual only; no downloads",
                        "Manuel ; aucun téléchargement"),
                () -> pages.open("Updates", "Updates are prepared locally.", Settings::buildUpdates));
        sealGroup(context, app);

        applyOverviewSearch(column, "");
    }

    private static void applyOverviewSearch(LinearLayout root, String query) {
        String normalized = query == null ? "" : query.trim();
        for (int i = 0; i < root.getChildCount(); i++) {
            View maybeLabel = root.getChildAt(i);
            if (!(maybeLabel instanceof TextView)) continue;
            int nextIndex = i + 1;
            if (nextIndex >= root.getChildCount() || !(root.getChildAt(nextIndex) instanceof LinearLayout)) {
                continue;
            }
            LinearLayout section = (LinearLayout) root.getChildAt(nextIndex);
            boolean sectionVisible = false;
            for (int j = 0; j < section.getChildCount(); j++) {
                View row = section.getChildAt(j);
                if (!(row instanceof LinearLayout)) continue;
                Object tag = row.getTag();
                boolean matches = !(tag instanceof String)
                        || Config.matchesSettingsSearch((String) tag, normalized);
                row.setVisibility(matches ? View.VISIBLE : View.GONE);
                if (matches) sectionVisible = true;
            }
            maybeLabel.setVisibility(normalized.isEmpty() || sectionVisible ? View.VISIBLE : View.GONE);
            section.setVisibility(normalized.isEmpty() || sectionVisible ? View.VISIBLE : View.GONE);
        }
    }

    /** Whether a surface is hidden, with that key's own default. */
    private static boolean isSurfaceHidden(String key) {
        switch (key) {
            case "block_feed": return Config.isFeedBlocked();
            case "block_explore": return Config.isExploreBlocked();
            case "block_reels": return Config.isReelsBlocked();
            case "block_reels_feed": return Config.isFeedReelsBlocked();
            case "block_friends_lane": return Config.isFriendsLaneBlocked();
            case "block_stories": return Config.isStoriesBlocked();
            case "block_story_opening": return Config.areStoryOpeningsBlocked();
            case "block_instants": return Config.isInstantsBlocked();
            case "block_notes": return Config.isNotesBlocked();
            case "block_suggested": return Config.isSuggestedBlocked();
            case "block_ads": return Config.isAdsBlocked();
            case "block_notifications": return Config.isNotificationsButtonBlocked();
            default: return Config.getBlocked(key, false);
        }
    }

    private static String surfaceLabel(String key) {
        switch (key) {
            case "block_feed": return UiStrings.text("surface.home_feed", "Home feed", "Fil d’accueil");
            case "block_explore": return UiStrings.text("surface.explore", "Explore", "Explorer");
            case "block_reels": return UiStrings.text("surface.reels_tab_content", "Reels tab content", "Contenu du onglet Reels");
            case "block_reels_feed": return UiStrings.text("surface.reels_feed", "Recommended Reels in feed", "Reels recommandés dans le fil");
            case "block_friends_lane": return UiStrings.text("surface.friends_in_reels", "Friends in Reels", "Amis dans Reels");
            case "block_stories": return UiStrings.text("surface.stories_tray", "Stories tray", "Barre des Stories");
            case "block_story_opening": return UiStrings.text("surface.opening_stories", "Opening Stories", "Ouverture des Stories");
            case "block_instants": return UiStrings.text("surface.instants", "Instants", "Instants");
            case "block_notes": return UiStrings.text("surface.notes", "Notes", "Notes");
            case "block_suggested": return UiStrings.text("surface.suggested_accounts", "Suggested accounts", "Comptes suggérés");
            case "block_ads": return UiStrings.text("surface.ads", "Ads", "Publicités");
            case "block_notifications": return UiStrings.text("surface.notifications_button", "Notifications button", "Bouton de notifications");
            case "nav_show_home": return UiStrings.text("surface.home_tab", "Home tab", "Onglet Accueil");
            case "nav_show_search": return UiStrings.text("surface.search_tab", "Search tab", "Onglet Recherche");
            case "nav_show_reels": return UiStrings.text("surface.reels_tab_icon", "Reels tab icon", "Icône Reels");
            case "nav_show_create": return UiStrings.text("surface.create_tab", "Create tab", "Onglet Créer");
            case "nav_show_direct": return UiStrings.text("surface.messages_tab", "Messages tab", "Onglet Messages");
            case "nav_show_profile": return UiStrings.text("surface.profile_tab", "Profile tab", "Onglet Profil");
            default: return key;
        }
    }

    private static void buildSurfaces(Context context, LinearLayout column) {
        LinearLayout surfaces = addGroup(context, column);
        for (String key : SURFACE_KEYS) {
            addRow(context, surfaces, surfaceLabel(key), key, isSurfaceHidden(key));
        }
        sealGroup(context, surfaces);
    }

    private static void buildProfiles(Context context, LinearLayout column, Pages pages) {
        LinearLayout profiles = addGroup(context, column);
        for (Config.SettingsProfile profile : Config.settingsProfiles()) {
            LinearLayout row = makeRow(context, profiles, profile.title(), profile.description());
            row.setOnClickListener(v -> {
                if (!Config.canApplyProfile(profile.id())) {
                    Toast.makeText(context,
                            UiStrings.text("profile.locked", "The permanent lock prevents this profile from restoring hidden surfaces.",
                                    "Le verrou permanent empêche ce profil de rétablir les surfaces masquées."),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                pages.open(profile.title(),
                        UiStrings.text("profile.review", "Review every setting this profile will replace.",
                                "Vérifiez chaque réglage que ce profil remplacera."),
                        (pageContext, pageColumn) -> buildProfilePreview(pageContext, pageColumn, profile));
            });
        }
        sealGroup(context, profiles);
    }

    private static void buildProfilePreview(Context context, LinearLayout column,
                                            Config.SettingsProfile profile) {
        TextView explanation = new TextView(context);
        explanation.setText(profile.description()
                + "\n\n" + UiStrings.text("profile.preview_info",
                        "Only the settings listed below will be replaced. Other preferences, including the landing page and display options, stay unchanged.",
                        "Seuls les réglages listés ci-dessous seront remplacés. Les autres préférences, y compris la page d’accueil et les options d’affichage, restent inchangées."));
        body(explanation);
        column.addView(explanation, stackedButtonParams(context, 4));

        addSectionLabel(context, column, UiStrings.text("profile.to_replace", "Settings to replace", "Réglages à remplacer"));
        LinearLayout changes = addGroup(context, column);
        StringBuilder confirmation = new StringBuilder(
                UiStrings.text("profile.confirmation_intro",
                        "Only the settings below will be replaced. Other preferences, the landing page, display options and Instagram sign-in data will not change.\n\n",
                        "Seuls les réglages ci-dessous seront remplacés. Les autres préférences, la page d’accueil, les options d’affichage et les données de connexion Instagram ne changeront pas.\n\n"));
        for (int i = 0; i < profile.size(); i++) {
            String key = profile.keyAt(i);
            boolean current = currentProfileValue(key);
            boolean selected = profile.valueAt(i);
            String label = surfaceLabel(key);
            String target = profileValueLabel(key, selected);
            String currentLabel = profileValueLabel(key, current);
            makeRow(context, changes, label, currentLabel + "  →  " + target);
            confirmation.append("• ").append(label).append(": ")
                    .append(currentLabel).append(" → ").append(target).append('\n');
        }
        sealGroup(context, changes);

        boolean available = Config.canApplyProfile(profile.id());
        if (!available) {
            TextView lockInfo = new TextView(context);
            lockInfo.setText(UiStrings.text("profile.lock_info",
                    "The permanent lock prevents this profile from restoring one or more surfaces hidden when the lock was enabled.",
                    "Le verrou permanent empêche ce profil de rétablir une ou plusieurs surfaces masquées quand le verrou a été activé."));
            body(lockInfo);
            lockInfo.setTextColor(ERROR);
            column.addView(lockInfo, stackedButtonParams(context, 12));
        }

        Button apply = makeButton(context,
                UiStrings.text("profile.review_apply", "Review and apply", "Vérifier et appliquer"),
                PRIMARY, ON_PRIMARY, true);
        apply.setEnabled(available);
        if (!available) apply.setAlpha(0.38f);
        apply.setOnClickListener(v -> showConfirm(context,
                UiStrings.text("profile.apply_title", "Apply ", "Appliquer ") + profile.title() + "?",
                confirmation.toString(),
                UiStrings.text("profile.apply_button", "Apply profile", "Appliquer le profil"), PRIMARY, ON_PRIMARY,
                () -> {
                    if (!Config.applyProfile(profile.id())) {
                        Toast.makeText(context,
                                UiStrings.text("profile.apply_failed", "Profile was not applied; the permanent lock blocks one or more changes.",
                                        "Le profil n’a pas été appliqué ; le verrou permanent bloque un ou plusieurs changements."),
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    Toast.makeText(context,
                            UiStrings.text("profile.apply_success", "Profile applied. Tap Done to restart Instagram.",
                                    "Profil appliqué. Appuyez sur Terminé pour redémarrer Instagram."),
                            Toast.LENGTH_LONG).show();
                }));
        column.addView(apply, stackedButtonParams(context, 16));
    }

    private static boolean currentProfileValue(String key) {
        switch (key) {
            case "block_feed": return Config.isFeedBlocked();
            case "limit_following_feed": return Config.isFollowingFeedOnly();
            case "block_explore": return Config.isExploreBlocked();
            case "block_reels": return Config.isReelsBlocked();
            case "block_reels_feed": return Config.isFeedReelsBlocked();
            case "block_friends_lane": return Config.isFriendsLaneBlocked();
            case "block_stories": return Config.isStoriesBlocked();
            case "block_story_opening": return Config.areStoryOpeningsBlocked();
            case "block_instants": return Config.isInstantsBlocked();
            case "block_notes": return Config.isNotesBlocked();
            case "block_suggested": return Config.isSuggestedBlocked();
            case "block_ads": return Config.isAdsBlocked();
            case "block_notifications": return Config.isNotificationsButtonBlocked();
            default:
                return key.startsWith("nav_show_")
                        && Config.getBlocked(key, Config.navDefault(key));
        }
    }

    private static String profileValueLabel(String key, boolean value) {
        if (key.startsWith("nav_show_")) return value ? UiStrings.text("value.shown", "Shown", "Visible") : UiStrings.text("value.hidden", "Hidden", "Masqué");
        if ("limit_following_feed".equals(key)) return value ? UiStrings.text("value.on", "On", "Activé") : UiStrings.text("value.off", "Off", "Désactivé");
        return value ? UiStrings.text("value.blocked", "Blocked", "Bloqué") : UiStrings.text("value.allowed", "Allowed", "Autorisé");
    }

    private static void buildNav(Context context, LinearLayout column) {
        addSectionLabel(context, column, UiStrings.text("nav.preview", "Preview", "Aperçu"));
        NavPreview preview = new NavPreview(context);
        column.addView(preview.view);

        LinearLayout nav = addSection(context, column, UiStrings.text("nav.icons", "Icons", "Icônes"));
        Runnable refresh = preview::refresh;
        addRow(context, nav, UiStrings.text("nav.home", "Home", "Accueil"), "nav_show_home", Config.getBlocked("nav_show_home", true), refresh);
        addRow(context, nav, UiStrings.text("nav.search", "Search", "Recherche"), "nav_show_search", Config.getBlocked("nav_show_search", true), refresh);
        addRow(context, nav, UiStrings.text("nav.reels", "Reels", "Reels"), "nav_show_reels", Config.getBlocked("nav_show_reels", false), refresh);
        addRow(context, nav, UiStrings.text("nav.create", "Create", "Créer"), "nav_show_create", Config.getBlocked("nav_show_create", true), refresh);
        addRow(context, nav, UiStrings.text("nav.messages", "Messages", "Messages"), "nav_show_direct", Config.getBlocked("nav_show_direct", true), refresh);
        addRow(context, nav, UiStrings.text("nav.profile", "Profile", "Profil"), "nav_show_profile", Config.getBlocked("nav_show_profile", true), refresh);
        sealGroup(context, nav);

        // Instagram drops tabs between versions (451 has no Create tab). A toggle
        // for a tab the live bar does not have would do nothing, so it is greyed
        // out; outside the app the order is a guess and every row stays live.
        String[] tabs = {"feed_tab", "search_tab", "clips_tab", "creation_tab", "direct_tab", "profile_tab"};
        if (NavPreview.isLive(context)) {
            List<String> present = NavPreview.barOrder(context);
            for (int i = 0; i < tabs.length && i < nav.getChildCount(); i++) {
                if (!present.contains(tabs[i])) greyOut(nav.getChildAt(i));
            }
        }
        // Quoicouview mode decides these two itself: Home off, Messages on.
        if (Config.isQuoicouviewEnabled()) {
            for (int i = 0; i < tabs.length && i < nav.getChildCount(); i++) {
                if (tabs[i].equals("feed_tab") || tabs[i].equals("direct_tab")) greyOut(nav.getChildAt(i));
            }
        }
    }

    /** Disable a settings row and everything in it, dimmed like a disabled control. */
    private static void greyOut(View row) {
        if (row instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) row;
            for (int j = 0; j < group.getChildCount(); j++) group.getChildAt(j).setEnabled(false);
        }
        row.setEnabled(false);
        row.setClickable(false);
        row.setAlpha(0.38f);
    }

    private static void buildFeed(Context context, LinearLayout column) {
        LinearLayout feed = addGroup(context, column);
        addRow(context, feed,
                UiStrings.text("feed.following_only", "Following feed only", "Fil uniquement suivi"),
                "limit_following_feed", Config.isFollowingFeedOnly());
        sealGroup(context, feed);
    }

    private static void buildLanding(Context context, LinearLayout column) {
        LinearLayout landing = addGroup(context, column);
        buildLandingOptions(context, landing, new Runnable[1]);
        sealGroup(context, landing);
    }

    private static void buildDisplay(Context context, LinearLayout column) {
        LinearLayout display = addGroup(context, column);
        addRow(context, display, UiStrings.text("display.force_dark",
                        "Force dark (disable HDR)", "Mode sombre (désactiver HDR)"),
                "force_sdr", Config.isForceSdr());
        addRow(context, display, UiStrings.text("display.popups", "Instagram popups", "Popups Instagram"),
                "hide_toasts", Config.arePopupsHidden());
        addRow(context, display,
                UiStrings.text("display.hide_promos", "Hide subscription promos", "Masquer les promos d'abonnement"),
                "hide_subscription_promos", Config.areSubscriptionPromosHidden());
        sealGroup(context, display);

        TextView popupInfo = new TextView(context);
        popupInfo.setText(UiStrings.text("display.popup_info",
                "This controls every Instagram popup, not only messages caused by blocked surfaces. "
                        + "Turn it off if you need to see network errors or other important status messages.",
                "Cela contrôle tous les popups Instagram, pas seulement les messages causés par les surfaces bloquées. "
                        + "Désactivez-le si vous avez besoin de voir les erreurs réseau ou autres messages d'état importants."));
        body(popupInfo);
        column.addView(popupInfo, stackedButtonParams(context, 12));
    }

    private static void buildVersionPage(Context context, LinearLayout column) {
        TextView versionHeading = new TextView(context);
        versionHeading.setText("Feurstagram " + Config.getAppVersionName());
        headline(versionHeading);
        column.addView(versionHeading, stackedButtonParams(context, 8));

        TextView changelog = new TextView(context);
        changelog.setText(Config.getChangelogText());
        body(changelog);
        column.addView(changelog, stackedButtonParams(context, 12));
    }

    private static void buildLanguage(Context context, LinearLayout column) {
        LinearLayout language = addGroup(context, column);
        final String current = Config.getUiLanguage();

        addLanguageOption(context, language, "English", "en", "en".equals(current));
        addLanguageOption(context, language, "Français", "fr", "fr".equals(current));
        sealGroup(context, language);
    }

    private static void addLanguageOption(Context context, LinearLayout parent, String label,
                                         String language, boolean selected) {
        LinearLayout row = makeRow(context, parent, label, null);
        RadioButton mark = new RadioButton(context);
        mark.setClickable(false);
        mark.setFocusable(false);
        mark.setChecked(selected);
        mark.setButtonTintList(buildStateList(PRIMARY, OUTLINE));
        row.addView(mark);

        row.setOnClickListener(v -> {
            Config.setUiLanguage(language);
            String toastText = "fr".equalsIgnoreCase(language)
                    ? "Langue changée en Français. Appuyez sur Terminé pour redémarrer Instagram."
                    : "Language changed to English. Tap Done to restart Instagram.";
            Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show();
        });
    }

    private static void buildBackup(Context context, LinearLayout column) {
        TextView intro = new TextView(context);
        intro.setText(UiStrings.text("backup.intro",
                "Save a snapshot of your Feurstagram preferences and restore it later.",
                "Enregistrez une copie de vos préférences Feurstagram et restaurez-la plus tard."));
        body(intro);
        column.addView(intro, stackedButtonParams(context, 12));

        Button save = makeButton(context,
                UiStrings.text("backup.save", "Save current settings", "Enregistrer les réglages"),
                PRIMARY, ON_PRIMARY, true);
        save.setOnClickListener(v -> {
            Map<String, Object> snapshot = Config.snapshotSettings();
            Toast.makeText(context,
                    UiStrings.text("backup.saved",
                            "Saved " + snapshot.size() + " preference entries.",
                            snapshot.size() + " réglages enregistrés."),
                    Toast.LENGTH_SHORT).show();
        });
        column.addView(save, stackedButtonParams(context, 16));

        Button restore = makeButton(context,
                UiStrings.text("backup.restore", "Restore last saved settings", "Restaurer les réglages enregistrés"),
                SURFACE_CONTAINER_HIGH, ON_SURFACE, true);
        restore.setOnClickListener(v -> {
            Map<String, Object> snapshot = Config.snapshotSettings();
            if (snapshot.isEmpty()) {
                Toast.makeText(context,
                        UiStrings.text("backup.empty", "No backup is available yet.", "Aucune sauvegarde n'est disponible pour le moment."),
                        Toast.LENGTH_LONG).show();
                return;
            }
            Config.restoreSettings(snapshot);
            Toast.makeText(context,
                    UiStrings.text("backup.restored", "Preferences restored. Tap Done to restart Instagram.",
                            "Réglages restaurés. Appuyez sur Terminé pour redémarrer Instagram."),
                    Toast.LENGTH_LONG).show();
        });
        column.addView(restore, stackedButtonParams(context, 16));
    }

    private static void buildUpdates(Context context, LinearLayout column) {
        TextView info = new TextView(context);
        info.setText(UiStrings.text("updates.info",
                "Feurstagram can be updated in place when a signed APK is hosted on a reachable URL. Use the same signing key as the installed build so Android treats it as an update, not a fresh install.",
                "Feurstagram peut être mis à jour in situ lorsqu'un APK signé est hébergé sur une URL accessible. Utilisez la même clé de signature que le build installé pour que Android le traite comme une mise à jour et non comme une nouvelle installation."));
        body(info);
        column.addView(info, stackedButtonParams(context, 12));

        TextView urlLabel = new TextView(context);
        urlLabel.setText(UiStrings.text("updates.url_label", "Update manifest URL", "URL du manifest de mise à jour"));
        body(urlLabel);
        column.addView(urlLabel, stackedButtonParams(context, 8));

        TextView urlValue = new TextView(context);
        urlValue.setText(UpdateManager.getUpdateUrl());
        urlValue.setTextColor(ON_SURFACE_VARIANT);
        urlValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        column.addView(urlValue, stackedButtonParams(context, 12));

        Button setUrl = makeButton(context,
                UiStrings.text("updates.set_url", "Set update URL", "Définir l'URL de mise à jour"),
                SURFACE_CONTAINER_HIGH, ON_SURFACE, true);
        setUrl.setOnClickListener(v -> {
            String current = UpdateManager.getUpdateUrl();
            Toast.makeText(context,
                    UiStrings.text("updates.url_current", "Current update URL: " + current,
                            "URL de mise à jour actuelle : " + current),
                    Toast.LENGTH_LONG).show();
        });
        column.addView(setUrl, stackedButtonParams(context, 16));

        Button check = makeButton(context,
                UiStrings.text("updates.check", "Check for updates", "Vérifier les mises à jour"),
                PRIMARY, ON_PRIMARY, true);
        check.setOnClickListener(v -> {
            String url = UpdateManager.getUpdateUrl();
            if (url == null || url.isEmpty()) {
                Toast.makeText(context,
                        UiStrings.text("updates.url_missing", "No update URL configured yet.",
                                "Aucune URL de mise à jour n'a encore été configurée."),
                        Toast.LENGTH_LONG).show();
                return;
            }
            try {
                UpdateManager.UpdateCheckResult result = UpdateManager.checkForUpdate(context, Config.getAppVersionName(), url);
                Toast.makeText(context,
                        result.message,
                        Toast.LENGTH_LONG).show();
            } catch (Throwable t) {
                Toast.makeText(context,
                        UiStrings.text("updates.feed_error", "Unable to open the update feed",
                                "Impossible d'ouvrir le flux de mise à jour"),
                        Toast.LENGTH_LONG).show();
            }
        });
        column.addView(check, stackedButtonParams(context, 16));

        Button download = makeButton(context,
                UiStrings.text("updates.download", "Download update APK", "Télécharger le APK de mise à jour"),
                SURFACE_CONTAINER_HIGH, ON_SURFACE, true);
        download.setOnClickListener(v -> {
            try {
                UpdateManager.Release release = UpdateManager.fetchRelease(UpdateManager.getUpdateUrl());
                File file = UpdateManager.downloadApk(context, release.apkUrl);
                if (file == null) {
                    Toast.makeText(context,
                            UiStrings.text("updates.download_failed", "The APK could not be downloaded.",
                                    "Le APK n'a pas pu être téléchargé."),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(context,
                        UiStrings.text("updates.downloaded",
                                "Downloaded update to cache: " + file.getAbsolutePath(),
                                "Mise à jour téléchargée dans le cache : " + file.getAbsolutePath()),
                        Toast.LENGTH_LONG).show();
            } catch (Throwable t) {
                Toast.makeText(context,
                        UiStrings.text("updates.download_error", "Unable to download the update",
                                "Impossible de télécharger la mise à jour"),
                        Toast.LENGTH_LONG).show();
            }
        });
        column.addView(download, stackedButtonParams(context, 16));

        Button install = makeButton(context,
                UiStrings.text("updates.install", "Install downloaded APK", "Installer le APK téléchargé"),
                SURFACE_CONTAINER_HIGH, ON_SURFACE, true);
        install.setOnClickListener(v -> {
            if (!UpdateManager.canInstallPackage(context)) {
                Toast.makeText(context,
                        UiStrings.text("updates.install_blocked", "Allow installation from unknown sources to install an update in place.",
                                "Autorisez l'installation depuis des sources inconnues pour mettre à jour l'application in situ."),
                        Toast.LENGTH_LONG).show();
                return;
            }
            File apkFile = new File(context.getCacheDir(), "feurstagram-update.apk");
            Intent installIntent = UpdateManager.installIntent(context, apkFile);
            if (installIntent == null) {
                Toast.makeText(context,
                        UiStrings.text("updates.install_missing", "No update has been downloaded yet.",
                                "Aucune mise à jour n'a encore été téléchargée."),
                        Toast.LENGTH_LONG).show();
                return;
            }
            context.startActivity(installIntent);
        });
        column.addView(install, stackedButtonParams(context, 16));
    }

    private static void buildSupport(Context context, LinearLayout column) {
        Button sponsors = makeButton(context, UiStrings.text("support.github", "Donate on GitHub Sponsors",
                "Faire un don sur GitHub Sponsors"), 0, ON_SURFACE, false);
        sponsors.setBackground(outlined(context, OUTLINE_VARIANT));
        sponsors.setOnClickListener(v -> openUrl(context, "https://github.com/sponsors/jean-voila"));
        column.addView(sponsors, stackedButtonParams(context, 16));

        Button coffee = makeButton(context, UiStrings.text("support.coffee", "☕  Buy me a coffee",
                "☕  Offrez-moi un café"), SURFACE_CONTAINER_HIGH, ON_SURFACE, true);
        coffee.setOnClickListener(v -> openUrl(context, "https://buymeacoffee.com/jean_voila"));
        column.addView(coffee, stackedButtonParams(context, 10));
    }

    private static String displaySummary() {
        String dark = Config.isForceSdr() ? UiStrings.text("display.summary.dark_on", "Force dark on", "Mode sombre activé")
                : UiStrings.text("display.summary.dark_off", "Force dark off", "Mode sombre désactivé");
        String popups = Config.arePopupsHidden() ? UiStrings.text("display.summary.popups_hidden", "popups hidden", "popups masqués")
                : UiStrings.text("display.summary.popups_shown", "popups shown", "popups visibles");
        return dark + ", " + popups;
    }

    private static String landingLabel(String value) {
        // "home" with the Home icon hidden: the landing redirect opens the first icon.
        if (!Config.isLandingAvailable(value)) return "First icon on the bar";
        for (int i = 0; i < LANDING_VALUES.length; i++) {
            if (LANDING_VALUES[i].equals(value)) return LANDING_LABELS[i];
        }
        return LANDING_LABELS[0];
    }

    /** The shown icons, in bar order, e.g. "Home, Messages, Search, Profile". */
    private static String navSummary(Context context) {
        StringBuilder out = new StringBuilder();
        for (String tab : NavPreview.barOrder(context)) {
            if (!NavPreview.isShown(tab)) continue;
            if (out.length() > 0) out.append(", ");
            out.append(NavPreview.label(tab));
        }
        return out.toString();
    }

    /**
     * A row that opens a section page: Instagram's own outline icon, the name, a
     * one-line summary of the current state, and a chevron.
     */
    private static void addSectionRow(Context context, LinearLayout parent, String icon, String label,
                                      String summary, Runnable onClick) {
        LinearLayout row = makeRow(context, parent, label, summary);
        Drawable glyph = igIcon(context, ON_SURFACE, "instagram_" + icon + "_pano_outline_24",
                "instagram_" + icon + "_outline_24");
        if (glyph != null) {
            ImageView lead = new ImageView(context);
            lead.setImageDrawable(glyph);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(context, 24), dp(context, 24));
            lp.setMargins(0, 0, dp(context, 16), 0);
            row.addView(lead, 0, lp);
        }
        Drawable chevron = igIcon(context, ON_SURFACE_VARIANT, "instagram_chevron_right_pano_outline_24",
                "instagram_chevron_right_outline_24");
        if (chevron != null) {
            ImageView trail = new ImageView(context);
            trail.setImageDrawable(chevron);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(context, 20), dp(context, 20));
            lp.setMargins(dp(context, 12), 0, 0, 0);
            row.addView(trail, lp);
        }
        row.setOnClickListener(v -> onClick.run());
    }

    /**
     * A live picture of the bottom bar: the icons that will show, in the order
     * Instagram lays them out, redrawn as the toggles below change.
     */
    private static final class NavPreview {
        private static final String[] DEFAULT_ORDER = {
                "feed_tab", "clips_tab", "direct_tab", "search_tab", "creation_tab", "profile_tab",
        };

        final LinearLayout view;
        private final Context context;
        private final List<String> order;

        NavPreview(Context context) {
            this.context = context;
            this.order = barOrder(context);
            view = new LinearLayout(context);
            view.setOrientation(LinearLayout.HORIZONTAL);
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setMinimumHeight(dp(context, 64));
            view.setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8));
            view.setBackground(roundedRect(SURFACE_CONTAINER, CORNER_L, context));
            view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            refresh();
        }

        void refresh() {
            view.removeAllViews();
            for (String tab : order) {
                if (!isShown(tab)) continue;
                Drawable glyph = glyph(context, tab);
                if (glyph == null) continue;
                ImageView icon = new ImageView(context);
                icon.setImageDrawable(glyph);
                icon.setScaleType(ImageView.ScaleType.CENTER);
                view.addView(icon, new LinearLayout.LayoutParams(0, dp(context, 48), 1f));
            }
        }

        /** Whether the live tab bar can be read, so {@link #barOrder} is Instagram's own. */
        static boolean isLive(Context context) {
            if (!(context instanceof Activity)) return false;
            int barId = Hiders.resolveId(context, "tab_bar");
            return barId != 0 && ((Activity) context).findViewById(barId) instanceof ViewGroup;
        }

        /** The tab ids in the live bar's order, or a fixed order outside the app. */
        static List<String> barOrder(Context context) {
            List<String> out = new ArrayList<>();
            if (context instanceof Activity) {
                int barId = Hiders.resolveId(context, "tab_bar");
                View bar = barId == 0 ? null : ((Activity) context).findViewById(barId);
                if (bar instanceof ViewGroup) {
                    ViewGroup group = (ViewGroup) bar;
                    for (int i = 0; i < group.getChildCount(); i++) {
                        int id = group.getChildAt(i).getId();
                        if (id == View.NO_ID) continue;
                        try {
                            out.add(context.getResources().getResourceEntryName(id));
                        } catch (Throwable ignored) {
                            // unnamed child: not a tab
                        }
                    }
                }
            }
            if (out.isEmpty()) {
                for (String tab : DEFAULT_ORDER) out.add(tab);
            }
            return out;
        }

        static boolean isShown(String tab) {
            String key = Config.navKeyForTab(tab);
            return key != null && Config.isNavTabShown(key);
        }

        static String label(String tab) {
            switch (tab) {
                case "feed_tab": return "Home";
                case "clips_tab": return "Reels";
                case "direct_tab": return "Messages";
                case "search_tab": return "Search";
                case "creation_tab": return "Create";
                case "profile_tab": return "Profile";
                default: return tab;
            }
        }

        private static Drawable glyph(Context context, String tab) {
            switch (tab) {
                case "feed_tab": return igIcon(context, ON_SURFACE, "instagram_home_pano_filled_24",
                        "instagram_home_pano_outline_24");
                case "clips_tab": return igIcon(context, ON_SURFACE, "instagram_reels_pano_outline_24");
                case "direct_tab": return igIcon(context, ON_SURFACE, "instagram_direct_pano_outline_24",
                        "instagram_direct_outline_24");
                case "search_tab": return igIcon(context, ON_SURFACE, "instagram_search_pano_outline_24");
                case "creation_tab": return igIcon(context, ON_SURFACE, "instagram_add_pano_outline_24",
                        "instagram_add_outline_24");
                case "profile_tab": return igIcon(context, ON_SURFACE, "instagram_user_pano_outline_24");
                default: return null;
            }
        }
    }

    /**
     * One of Instagram's own icons, by resource name, tinted for our surface: the
     * first of {@code names} that exists, or null. Nothing is bundled: the icon
     * set is the host app's, so it looks like the rest of Instagram, and a name
     * that disappears in a later build only drops the icon.
     */
    static Drawable igIcon(Context context, int tint, String... names) {
        android.content.res.Resources res = context.getResources();
        for (String name : names) {
            int id = res.getIdentifier(name, "drawable", context.getPackageName());
            if (id == 0) id = res.getIdentifier(name, "drawable", "com.instagram.android");
            if (id == 0) continue;
            try {
                Drawable drawable = context.getDrawable(id);
                if (drawable == null) continue;
                drawable = drawable.mutate();
                drawable.setTint(tint);
                return drawable;
            } catch (Throwable ignored) {
                // not loadable in this build; try the next name
            }
        }
        return null;
    }

    private static GradientDrawable oval(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    /** An unlabelled group, for a section page whose title already names it. */
    private static LinearLayout addGroup(Context context, LinearLayout parent) {
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(context, 16), 0, 0);
        parent.addView(group, lp);
        return group;
    }

    private static LinearLayout.LayoutParams stackedButtonParams(Context context, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(context, topDp), 0, 0);
        return lp;
    }

    private static void onDone(Context context, boolean restartPending) {
        if (context != null && restartPending) CacheCleaner.clearAndRestart(context);
    }

    private static void confirmHardcore(Context context) {
        if (context == null) return;
        if (Config.isHardcoreMode()) {
            Toast.makeText(context,
                    UiStrings.text("lock.already_enabled", "Permanent lock is already enabled",
                            "Le verrou permanent est déjà activé"),
                    Toast.LENGTH_LONG).show();
            return;
        }
        showConfirm(context,
                UiStrings.text("lock.enable_title", "Enable permanent lock?", "Activer le verrou permanent ?"),
                UiStrings.text("lock.enable_body",
                        "This cannot be undone in Feurstagram. It freezes content blocks and navigation icons that are already hidden when you open settings; you can still hide more later. Unlocking requires uninstalling and reinstalling the app, which may erase local app data and require setup again.\n\nType LOCK below to confirm.",
                        "Cela ne peut pas être annulé dans Feurstagram. Il fixe les blocages de contenu et les icônes de navigation déjà masquées lorsque vous ouvrez les réglages ; vous pouvez encore en masquer davantage plus tard. Le déverrouillage nécessite la désinstallation puis la réinstallation de l’application, ce qui peut effacer les données locales et exiger une nouvelle configuration.\n\nSaisissez LOCK ci-dessous pour confirmer."),
                UiStrings.text("lock.enable_button", "Enable lock", "Activer le verrou"), ERROR, ON_ERROR,
                () -> {
                    Config.enableHardcoreMode();
                    Toast.makeText(context,
                            UiStrings.text("lock.enabled", "Permanent lock enabled. Reinstall the app to unlock content.",
                                    "Verrou permanent activé. Réinstallez l’application pour débloquer le contenu."),
                            Toast.LENGTH_LONG).show();
                }, "LOCK");
    }

    // --- Confirmation cards ---------------------------------------------------

    /**
     * A centred card with a title, a body and two actions. Shared by the restart
     * and permanent-lock prompts.
     */
    public static void showConfirm(Context context, String titleText, String bodyText,
                                   String confirmText, int confirmBg, int confirmFg,
                                   Runnable onConfirm) {
        showConfirm(context, titleText, bodyText, confirmText, confirmBg, confirmFg,
                onConfirm, null);
    }

    private static void showConfirm(Context context, String titleText, String bodyText,
                                    String confirmText, int confirmBg, int confirmFg,
                                    Runnable onConfirm, String requiredPhrase) {
        try {
            Dialog dialog = new Dialog(context);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            FrameLayout frame = cardFrame(context);
            LinearLayout card = addCard(context, frame);

            TextView title = new TextView(context);
            title.setText(titleText);
            titleLarge(title);
            card.addView(title);

            // Scrollable, and the one thing allowed to shrink: a long warning must
            // not push the buttons off a short screen or a landscape phone.
            ScrollView bodyScroll = new ScrollView(context);
            bodyScroll.setVerticalScrollBarEnabled(false);
            LinearLayout bodyContent = new LinearLayout(context);
            bodyContent.setOrientation(LinearLayout.VERTICAL);
            TextView body = new TextView(context);
            body.setText(bodyText);
            body(body);
            body.setPadding(0, dp(context, 10), 0, 0);
            bodyContent.addView(body);
            EditText confirmationInput = null;
            if (requiredPhrase != null) {
                confirmationInput = new EditText(context);
                confirmationInput.setSingleLine(true);
                confirmationInput.setHint(UiStrings.text("dialog.type_required",
                        "Type " + requiredPhrase,
                        "Saisissez " + requiredPhrase));
                confirmationInput.setInputType(InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                        | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                confirmationInput.setTextColor(ON_SURFACE);
                confirmationInput.setHintTextColor(ON_SURFACE_VARIANT);
                confirmationInput.setContentDescription(
                        UiStrings.text("dialog.type_required_accessibility",
                                "Type " + requiredPhrase + " to confirm this permanent action",
                                "Saisissez " + requiredPhrase + " pour confirmer cette action permanente"));
                confirmationInput.setBackground(outlined(context, OUTLINE_VARIANT));
                int inputPadding = dp(context, 12);
                confirmationInput.setPadding(inputPadding, inputPadding,
                        inputPadding, inputPadding);
                bodyContent.addView(confirmationInput, stackedButtonParams(context, 12));
            }
            final EditText requiredInput = confirmationInput;
            bodyScroll.addView(bodyContent);
            card.addView(bodyScroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            boundToFrame(frame, card, bodyScroll);

            LinearLayout buttons = new LinearLayout(context);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams buttonsLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            buttonsLp.setMargins(0, dp(context, 24), 0, 0);
            card.addView(buttons, buttonsLp);

            Button cancel = makeButton(context,
                    UiStrings.text("dialog.cancel", "Cancel", "Annuler"), 0, ON_SURFACE, false);
            cancel.setBackground(outlined(context, OUTLINE_VARIANT));
            cancel.setOnClickListener(v -> dialog.dismiss());
            buttons.addView(cancel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            View spacer = new View(context);
            buttons.addView(spacer, new LinearLayout.LayoutParams(dp(context, 8), 1));

            Button confirm = makeButton(context, confirmText, confirmBg, confirmFg, true);
            if (requiredInput != null) {
                confirm.setEnabled(false);
                confirm.setAlpha(0.38f);
                requiredInput.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start,
                                                            int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start,
                                                        int before, int count) {
                        boolean matches = requiredPhrase.equalsIgnoreCase(s.toString().trim());
                        confirm.setEnabled(matches);
                        confirm.setAlpha(matches ? 1f : 0.38f);
                    }
                    @Override public void afterTextChanged(Editable s) {}
                });
            }
            confirm.setOnClickListener(v -> {
                if (requiredPhrase != null
                        && (requiredInput == null || !requiredPhrase.equalsIgnoreCase(
                        requiredInput.getText().toString().trim()))) {
                    return;
                }
                dialog.dismiss();
                onConfirm.run();
            });
            buttons.addView(confirm, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            dialog.setContentView(frame);
            dialog.setCanceledOnTouchOutside(true);
            styleWindow(dialog, 0, 0.6f);
            dialog.show();
        } catch (Throwable t) {
            Toast.makeText(context,
                    UiStrings.text("dialog.confirmation_error", "Unable to open confirmation",
                            "Impossible d’ouvrir la confirmation"),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * The full-window frame a centred card sits in: padded for the system bars and
     * the cutout, so a card never slips under them however the device is held.
     */
    public static FrameLayout cardFrame(Context context) {
        final FrameLayout frame = new FrameLayout(context);
        applyPageInsets(frame, 20, 20, CARD_MAX_WIDTH_DP);
        return frame;
    }

    /** M3 keeps dialogs narrow; past this they stop reading as a dialog. */
    private static final int CARD_MAX_WIDTH_DP = 520;

    /** Add the card itself, centred, to a {@link #cardFrame}. */
    public static LinearLayout addCard(Context context, FrameLayout frame) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundedRect(SURFACE_CONTAINER, CORNER_XL, context));
        int pad = dp(context, 24);
        card.setPadding(pad, pad, pad, pad);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        frame.addView(card, lp);
        return card;
    }

    /**
     * Keep {@code card} inside {@code frame} by shrinking {@code growable} — the one
     * child allowed to give up height — when the card would otherwise be taller than
     * the space actually available. This is what lets a long release-notes block
     * survive landscape, a short screen or a three-button navigation bar instead of
     * pushing the buttons off the bottom.
     */
    public static void boundToFrame(final FrameLayout frame, final View card, final View growable) {
        // A global-layout listener, not frame.addOnLayoutChangeListener: the frame
        // fills the window and so never changes bounds, while the things that
        // matter — insets arriving a frame late, the card growing once the notes
        // are measured, a rotation — all show up as ordinary layout passes.
        frame.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            int available = frame.getHeight() - frame.getPaddingTop() - frame.getPaddingBottom();
            if (available <= 0 || card.getWidth() == 0) return;
            ViewGroup.LayoutParams lp = growable.getLayoutParams();
            // The card's laid-out height can't tell: it is capped at the space
            // available, its last children (the buttons) squeezed to fit. Its
            // natural height is what it would take with no limit.
            card.measure(View.MeasureSpec.makeMeasureSpec(card.getWidth(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int overflow = card.getMeasuredHeight() - available;
            if (overflow > 0) {
                int height = Math.max(dp(frame.getContext(), 72), growable.getMeasuredHeight() - overflow);
                if (lp.height != height) {
                    lp.height = height;
                    growable.setLayoutParams(lp);
                }
            } else if (lp.height != ViewGroup.LayoutParams.WRAP_CONTENT
                    && overflow < -dp(frame.getContext(), 8)) {
                // Room came back (rotation, bars hidden): let it grow and re-measure.
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                growable.setLayoutParams(lp);
            }
        });
    }

    private static void openUrl(Context context, String url) {
        if (context == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable t) {
            Toast.makeText(context,
                    UiStrings.text("browser.none", "No browser available",
                            "Aucun navigateur disponible"),
                    Toast.LENGTH_LONG).show();
        }
    }

    // --- Grouped lists --------------------------------------------------------

    /** A sentence-case section label, M3 Expressive style (not all-caps). */
    private static void addSectionLabel(Context context, LinearLayout parent, String text) {
        TextView label = new TextView(context);
        label.setText(text);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        label.setTextColor(ON_SURFACE_VARIANT);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setLetterSpacing(0.01f);
        label.setPadding(dp(context, 16), dp(context, 24), dp(context, 16), dp(context, 8));
        parent.addView(label);
    }

    /** Section label plus the container its rows go into. */
    private static LinearLayout addSection(Context context, LinearLayout parent, String text) {
        addSectionLabel(context, parent, text);
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        parent.addView(group);
        return group;
    }

    /**
     * Give a finished group its connected shape: round on the outside, nearly
     * square where rows meet, with a hairline gap between them.
     */
    private static void sealGroup(Context context, LinearLayout group) {
        int count = group.getChildCount();
        for (int i = 0; i < count; i++) {
            View row = group.getChildAt(i);
            float top = i == 0 ? CORNER_L : CORNER_JOINT;
            float bottom = i == count - 1 ? CORNER_L : CORNER_JOINT;
            row.setBackground(ripple(RIPPLE,
                    roundedRect(SURFACE_CONTAINER, context, top, top, bottom, bottom)));
            if (i < count - 1) {
                ViewGroup.LayoutParams lp = row.getLayoutParams();
                if (lp instanceof LinearLayout.LayoutParams) {
                    ((LinearLayout.LayoutParams) lp).bottomMargin = dp(context, ROW_GAP_DP);
                    row.setLayoutParams(lp);
                }
            }
        }
    }

    /** The shared skeleton of a list row: label, supporting text, trailing control. */
    private static LinearLayout makeRow(Context context, LinearLayout parent, String label, String support) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 20), dp(context, 16), dp(context, 20), dp(context, 16));
        row.setMinimumHeight(dp(context, 64));
        row.setFocusable(true);
        row.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        row.setTag((label == null ? "" : label + " ") + (support == null ? "" : support));
        row.setContentDescription((label == null ? "" : label)
                + (support != null && !support.trim().isEmpty() ? ": " + support : ""));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        labelView.setTextColor(ON_SURFACE);
        texts.addView(labelView);

        if (support != null) {
            TextView supportView = new TextView(context);
            supportView.setText(support);
            supportView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            supportView.setTextColor(ON_SURFACE_VARIANT);
            supportView.setLineSpacing(0f, 1.1f);
            supportView.setPadding(0, dp(context, 2), 0, 0);
            texts.addView(supportView);
        }

        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        parent.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private static void addRow(Context context, LinearLayout parent, String label, String key, boolean value) {
        addRow(context, parent, label, key, value, null);
    }

    /**
     * @param onChanged run after the preference is written, for rows another
     *                  section depends on (the nav icons drive the landing picker)
     */
    private static void addRow(Context context, LinearLayout parent, String label, String key,
                               boolean value, Runnable onChanged) {
        LinearLayout row = makeRow(context, parent, label, supportText(key));

        ExpressiveSwitch toggle = new ExpressiveSwitch(context);
        toggle.setChecked(value);
        toggle.setContentDescription(label);
        toggle.setOnCheckedChangeListener((btn, isChecked) -> {
            // Hardcore: a frozen surface cannot be revealed; snap it back to hidden.
            if (Config.isHardcoreMode() && Config.isLockable(key)
                    && !Config.hidesSurface(key, isChecked) && Config.wasHiddenAtBaseline(key)) {
                btn.setChecked(!isChecked);
                return;
            }
            Config.setBlocked(key, isChecked);
            Config.setNeedsRestart();
            if (onChanged != null) onChanged.run();
        });
        row.addView(toggle);
        row.setOnClickListener(v -> {
            if (toggle.isEnabled()) toggle.toggle();
        });

        // Freeze rows whose surface is already hidden under the permanent lock.
        if (Config.isLockable(key) && Config.isHardcoreMode() && Config.hidesSurface(key, value)) {
            toggle.setEnabled(false);
            row.setEnabled(false);
            row.setClickable(false);
            row.setAlpha(0.38f);
        }
    }

    private static String supportText(String key) {
        if (key.equals("block_ads")) return UiStrings.text("support.block_ads", "Block sponsored ads across Instagram.", "Bloquez les publicités sponsorisées dans Instagram.");
        if (key.equals("limit_following_feed")) return UiStrings.text("support.limit_following_feed", "Show only accounts you follow (needs the feed unblocked).", "N’affichez que les comptes que vous suivez (le fil doit rester débloqué).");
        if (key.equals("hide_toasts")) return UiStrings.text("support.hide_toasts", "Hide every Instagram popup, including network errors and status messages.", "Masquez tous les popups Instagram, y compris les erreurs réseau et les messages d’état.");
        if (key.equals("force_sdr")) return UiStrings.text("support.force_sdr", "Keep blacks deep by stopping Instagram forcing HDR on the UI.", "Gardez les noirs profonds en empêchant Instagram de forcer le HDR sur l’interface.");
        if (key.equals("block_friends_lane")) return UiStrings.text("support.block_friends_lane", "Hide the Friends tab and its avatars in the Reels header.", "Masquez l’onglet Amis et ses avatars dans l’en-tête Reels.");
        if (key.equals("block_notifications")) return UiStrings.text("support.block_notifications", "Hide the notifications (heart) button in the feed header.", "Masquez le bouton de notifications (cœur) dans l’en-tête du fil.");
        if (key.equals("block_reels")) return UiStrings.text("support.block_reels", "Block the Reels tab's feed, discovery and Blend requests; the tab icon is controlled separately.", "Bloquez le fil, la découverte et les demandes Blend du onglet Reels ; l’icône de l’onglet est contrôlée séparément.");
        if (key.equals("block_reels_feed")) return UiStrings.text("support.block_reels_feed", "Hide recommended Reels mixed into the home feed. Reels shared in messages remain playable.", "Masquez les Reels recommandés mélangés au fil d’accueil. Les Reels partagés dans les messages restent lisibles.");
        if (key.equals("block_stories")) return UiStrings.text("support.block_stories", "Hide the Stories tray; opening a Story is controlled separately.", "Masquez la barre des Stories ; l’ouverture d’une Story est contrôlée séparément.");
        if (key.equals("block_story_opening")) return UiStrings.text("support.block_story_opening", "Block Story viewer requests. The Stories tray visibility is controlled separately.", "Bloquez les demandes d’ouverture des Stories. La visibilité de la barre des Stories est contrôlée séparément.");
        if (key.equals("quoicouview_mode")) {
            return UiStrings.text("support.quoicouview_mode", "Messages become the main page, with stories on top. Home is turned off.", "Les messages deviennent la page principale, avec les stories en haut. L’accueil est désactivé.");
        }
        if (key.equals("nav_show_home")) {
            return UiStrings.text("support.nav_show_home", "Show this icon. If hidden, long-press the first visible tab; Profile also has a shortcut.", "Affichez cette icône. Si elle est masquée, appuyez longuement sur le premier onglet visible ; le Profil a aussi un raccourci.");
        }
        if (key.startsWith("nav_show_")) return UiStrings.text("support.nav_show_any", "Show this icon in the navigation bar.", "Affichez cette icône dans la barre de navigation.");
        return UiStrings.text("support.hide_surface", "Hide this surface in Instagram.", "Masquez cette surface dans Instagram.");
    }

    // --- Landing page picker --------------------------------------------------

    private static final String[] LANDING_VALUES = {"home", "search", "direct", "profile"};
    private static final String[] LANDING_LABELS = {
            UiStrings.text("landing.home_feed", "Home feed", "Fil d’accueil"),
            UiStrings.text("landing.search", "Search", "Recherche"),
            UiStrings.text("landing.direct_messages", "Direct messages", "Messages directs"),
            UiStrings.text("landing.profile", "Profile", "Profil")
    };

    /**
     * @param landingSync out-parameter: receives a runnable that re-checks which
     *                    options are still selectable, for the nav rows to call
     *                    when they hide or show a tab
     */
    private static void buildLandingOptions(Context context, LinearLayout group, Runnable[] landingSync) {
        final List<View> rows = new ArrayList<>();
        final List<RadioButton> marks = new ArrayList<>();

        for (int i = 0; i < LANDING_VALUES.length; i++) {
            final String value = LANDING_VALUES[i];
            LinearLayout row = makeRow(context, group, LANDING_LABELS[i], null);

            RadioButton mark = new RadioButton(context);
            mark.setClickable(false);
            mark.setFocusable(false);
            mark.setButtonTintList(buildStateList(PRIMARY, OUTLINE));
            row.addView(mark);

            rows.add(row);
            marks.add(mark);

            row.setOnClickListener(v -> {
                if (!Config.isLandingAvailable(value)) return;
                Config.setLandingPage(value);
                Config.setNeedsRestart();
                if (landingSync[0] != null) landingSync[0].run();
            });
        }

        // A surface whose nav icon is hidden is not a landing page you can choose:
        // it would hand back exactly what was hidden, and under the permanent lock
        // the picker was the one way left to reach a frozen surface (issue #116).
        landingSync[0] = () -> {
            String current = Config.getLandingPage();
            for (int i = 0; i < LANDING_VALUES.length; i++) {
                boolean available = Config.isLandingAvailable(LANDING_VALUES[i]);
                View row = rows.get(i);
                row.setEnabled(available);
                row.setAlpha(available ? 1f : 0.38f);
                marks.get(i).setChecked(available && LANDING_VALUES[i].equals(current));
            }
        };
        landingSync[0].run();
    }

    // --- Type, shape and control helpers --------------------------------------

    /** The page headline: large, tight-tracked, medium weight. */
    public static void headline(TextView view) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f);
        view.setTextColor(ON_SURFACE);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setLetterSpacing(-0.02f);
    }

    /** A card or dialog title. */
    public static void titleLarge(TextView view) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f);
        view.setTextColor(ON_SURFACE);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setLetterSpacing(-0.01f);
    }

    /** Supporting text. */
    public static void body(TextView view) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        view.setTextColor(ON_SURFACE_VARIANT);
        view.setLineSpacing(0f, 1.15f);
    }

    public static int statusBarHeight(Context context) {
        int id = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id == 0 ? 0 : context.getResources().getDimensionPixelSize(id);
    }

    public static int dp(Context context, float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static GradientDrawable roundedRect(int color, float radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    /** Per-corner variant, for the connected list groups. */
    public static GradientDrawable roundedRect(int color, Context context,
                                               float topLeftDp, float topRightDp,
                                               float bottomRightDp, float bottomLeftDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        float tl = dp(context, topLeftDp);
        float tr = dp(context, topRightDp);
        float br = dp(context, bottomRightDp);
        float bl = dp(context, bottomLeftDp);
        drawable.setCornerRadii(new float[]{tl, tl, tr, tr, br, br, bl, bl});
        return drawable;
    }

    /** A transparent pill with a hairline border, for low-emphasis buttons. */
    public static Drawable outlined(Context context, int strokeColor) {
        GradientDrawable shape = roundedRect(Color.TRANSPARENT, CORNER_FULL, context);
        shape.setStroke(dp(context, 1), strokeColor);
        return ripple(RIPPLE, shape);
    }

    public static Drawable ripple(int color, Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(color), content, null);
    }

    public static ColorStateList buildStateList(int checkedColor, int uncheckedColor) {
        int[][] states = {
                new int[]{android.R.attr.state_checked},
                new int[]{},
        };
        int[] colors = {checkedColor, uncheckedColor};
        return new ColorStateList(states, colors);
    }

    /** A pill button. Expressive metrics: 52dp tall, generous horizontal padding. */
    public static Button makeButton(Context context, String text, int bgColor, int textColor, boolean filled) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(textColor);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setLetterSpacing(0f);
        button.setMinHeight(dp(context, 52));
        button.setMinimumHeight(dp(context, 52));
        button.setPadding(dp(context, 20), 0, dp(context, 20), 0);
        button.setStateListAnimator(null);
        button.setBackground(filled
                ? ripple(RIPPLE, roundedRect(bgColor, CORNER_FULL, context))
                : ripple(RIPPLE, roundedRect(Color.TRANSPARENT, CORNER_FULL, context)));
        return button;
    }

    public static View makeDivider(Context context, int color) {
        View divider = new View(context);
        divider.setBackgroundColor(color);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1)));
        return divider;
    }
}
