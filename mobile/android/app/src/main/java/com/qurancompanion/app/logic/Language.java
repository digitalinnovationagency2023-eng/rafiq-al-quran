package com.qurancompanion.app.logic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Interface languages and the mandatory notices. */
public final class Language {
    public final String code;
    public final String englishName;
    public final String nativeName;
    public final boolean rtl;

    public Language(String code, String englishName, String nativeName, boolean rtl) {
        this.code = code;
        this.englishName = englishName;
        this.nativeName = nativeName;
        this.rtl = rtl;
    }

    public static final List<Language> ALL = Collections.unmodifiableList(Arrays.asList(
            new Language("ar", "Arabic", "العربية", true),
            new Language("en", "English", "English", false),
            new Language("ur", "Urdu", "اردو", true),
            new Language("id", "Indonesian", "Bahasa Indonesia", false),
            new Language("bn", "Bengali", "বাংলা", false),
            new Language("tr", "Turkish", "Türkçe", false),
            new Language("fa", "Persian", "فارسی", true),
            new Language("ms", "Malay", "Bahasa Melayu", false),
            new Language("sw", "Swahili", "Kiswahili", false),
            new Language("fr", "French", "Français", false)
    ));

    public static Language byCode(String code) {
        for (Language language : ALL) if (language.code.equals(code)) return language;
        return ALL.get(1);
    }

    public static List<String> codes() {
        List<String> codes = new ArrayList<>();
        for (Language language : ALL) codes.add(language.code);
        return codes;
    }
}
