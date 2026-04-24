package com.sdk.esc;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Giống mlite C# {@code CdnHelper.RewriteToCdn}:
 * – URL đã là CDN (host {@code mphoto.mphotovn.online}) thì <b>trả về nguyên</b> — API thường trả link CDN sẵn.
 * – Chỉ rewrite khi path đúng dạng Firebase {@code /v0/b/.../o/...} → cùng host CDN mlite, giữ query (token) nếu có.
 * – Các link HTTPS CDN khác (không phải Firebase) → trả về nguyên.
 */
public final class CdnHelper {
    private CdnHelper() {
    }

    public static String rewriteToCdn(String url) {
        if (url == null || url.trim().isEmpty()) {
            return url;
        }
        try {
            URI parsed = new URI(url);
            String host = parsed.getHost();
            if (host != null && "mphoto.mphotovn.online".equalsIgnoreCase(host)) {
                return url;
            }
            String path = parsed.getPath();
            if (path == null || path.isEmpty()) {
                return url;
            }
            // Giống C# AbsolutePath.Split('/', StringSplitOptions.RemoveEmptyEntries)
            List<String> segments = new ArrayList<>();
            for (String s : path.split("/")) {
                if (!s.isEmpty()) {
                    segments.add(s);
                }
            }
            int bIdx = indexOfSegment(segments, "b");
            int oIdx = indexOfSegment(segments, "o");
            if (bIdx >= 0 && oIdx >= 0 && oIdx + 1 < segments.size()) {
                String bucket = segments.get(bIdx + 1);
                StringBuilder objectPath = new StringBuilder();
                for (int i = oIdx + 1; i < segments.size(); i++) {
                    if (objectPath.length() > 0) objectPath.append("/");
                    objectPath.append(segments.get(i));
                }
                return new URI(
                        parsed.getScheme() != null ? parsed.getScheme() : "https",
                        null,
                        "mphoto.mphotovn.online",
                        -1,
                        "/" + bucket + "/" + objectPath,
                        parsed.getQuery(),
                        parsed.getFragment()
                ).toString();
            }
        } catch (Exception ignored) {
        }
        return url;
    }

    private static int indexOfSegment(List<String> segments, String token) {
        for (int i = 0; i < segments.size(); i++) {
            if (token.equals(segments.get(i))) {
                return i;
            }
        }
        return -1;
    }
}
