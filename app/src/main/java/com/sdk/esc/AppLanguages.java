package com.sdk.esc;

/**
 * Tên ngôn ngữ hiển thị trong dialog chọn — luôn dùng tên gốc, không dịch theo locale app.
 */
public final class AppLanguages {

    public static final String[] CODES = {"vi", "en", "ko"};

    private static final String[] NATIVE_LABELS = {
            "Tiếng Việt",
            "English",
            "한국어"
    };

    private AppLanguages() {
    }

    public static String[] nativeDisplayLabels() {
        return NATIVE_LABELS.clone();
    }
}
