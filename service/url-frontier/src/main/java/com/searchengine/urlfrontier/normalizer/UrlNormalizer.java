package com.searchengine.urlfrontier.normalizer;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** Performs local, deterministic URL normalization without network access. */
@Component
public class UrlNormalizer {

    public String normalize(String url) {
        try {
            URI uri = new URI(url.strip());
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalArgumentException("URL must include a scheme and host");
            }
            String normalizedPath = "/".equals(uri.getPath()) ? null : uri.getPath();

            return new URI(
                    uri.getScheme().toLowerCase(Locale.ROOT),
                    uri.getUserInfo(),
                    uri.getHost().toLowerCase(Locale.ROOT),
                    uri.getPort(),
                    normalizedPath,
                    uri.getQuery(),
                    uri.getFragment()
            ).toString();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("URL cannot be normalized", exception);
        }
    }
}
