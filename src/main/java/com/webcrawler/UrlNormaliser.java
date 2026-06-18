package com.webcrawler;

import java.net.URI;
import java.net.URISyntaxException;

public class UrlNormaliser {

    public static String normalise(String url) {
        try {
            URI uri = new URI(url).normalize();
            // Strip fragment — #section anchors are not separate pages
            uri = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), uri.getQuery(), null);
            String result = uri.toString();
            // Remove trailing slash on non-root paths for consistent deduplication
            String path = uri.getPath();
            if (path != null && path.length() > 1 && result.endsWith("/")) {
                result = result.substring(0, result.length() - 1);
            }
            return result;
        } catch (URISyntaxException e) {
            return url;
        }
    }

    public static boolean isSameHost(String url, String seedHost) {
        try {
            String host = new URI(url).getHost();
            return seedHost.equalsIgnoreCase(host);
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
