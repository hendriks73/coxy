/*
 * =================================================
 * Copyright 2014 tagtraum industries incorporated
 * All rights reserved.
 * =================================================
 */
package com.tagtraum.coxy;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>
 * Translates a regular path like {@code /[signature]discogs-images/R-1074891-1267544771.jpeg.jpg}
 * to a path that attempts to limit the number of files per directory.
 * The sample path above is translated to {@code /images/R/10/74/R-1074891/R-1074891-1267544771.jpeg},
 * using "R", "10", "74", and "R-1074891" as hash values.
 * When using this resolver in conjunction with NGINX (or any other static web server),
 * make sure that it also translates paths in the same way, when serving the static file from
 * the {@code cache.base}.
 * </p>
 * <p>
 * However, you still need to pass the <em>original</em> request URI to the servlet container as it
 * still needs it to request resources that are not cached yet from the target server.
 * </p>
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class DiscogsImageCacheResolver implements CacheResolver {

    private static final Pattern IMAGE_PATH_PATTERN = Pattern.compile("/.*(/discogs-images/)((.)-([^-]{0,2})([^-]{0,2})[^-]*)(.*)(\\.[^\\.]*)");
    private File cacheBase;

    @Override
    public void setCacheBase(final File cacheBase) {
        this.cacheBase = cacheBase;
    }

    @Override
    public File getCacheBase() {
        return cacheBase;
    }

    @Override
    public File resolve(final String path) throws IOException {
        final Matcher matcher = IMAGE_PATH_PATTERN.matcher(path);
        final String hashedPath;
        if (matcher.matches()) {
            final String images = "/images/"; // matcher.group(1);
            final String releaseId = matcher.group(2);
            final String type = matcher.group(3);
            final String releaseHash1 = matcher.group(4);
            final String releaseHash2 = matcher.group(5);
            final String imageId = matcher.group(6);
            final String additionalFileExtension = matcher.group(7);

            hashedPath = images + File.separator
                    + type + File.separator
                    + releaseHash1 + File.separator
                    + releaseHash2 + File.separator
                    + releaseId + File.separator
                    + releaseId + imageId
                    + (imageId.contains(".") ? "" : additionalFileExtension);

        } else {
            // fallback
            hashedPath = path;
        }
        return new File(cacheBase, hashedPath).getCanonicalFile();
    }
}
