package com.feurstagram.extension;

import java.util.Locale;

/**
 * Small locale helper for the few user-facing Feurstagram prompts that live in code.
 * French is used when the device language is French; English is the fallback.
 */
public final class UiStrings {
    private UiStrings() {}

    public static String text(String key, String english, String french) {
        return "fr".equalsIgnoreCase(Config.getUiLanguage()) ? french : english;
    }
}
