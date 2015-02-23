/*
 * =================================================
 * Copyright 2014 tagtraum industries incorporated
 * All rights reserved.
 * =================================================
 */
package com.tagtraum.coxy;

import java.io.File;
import java.io.IOException;

/**
 * Straight Cache Resolver, which simply appends the path to the cache base.
 * This can lead to too many files in a single directory, reducing performance.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class StraightCacheResolver implements CacheResolver {

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
        // strip out everything before "images" out of something that looks like
        // /Mkq8l7_aFIXDsTdRT07pQoubjB4=/600x600/smart/filters:strip_icc():format(jpeg):mode_rgb():quality(96)/discogs-images/A-45-1408907021-4444.jpeg.jpg
        final int i = path.indexOf("images/");
        if (i<0) throw new IOException("Failed to find image path: " + path);
        String noSignaturePath = path.substring(i);
        if (noSignaturePath.split("\\.").length == 3) {
            // we have two file extensions,
            // let's remove the last one
            noSignaturePath = noSignaturePath.substring(0, noSignaturePath.lastIndexOf('.'));
        }
        return new File(cacheBase, noSignaturePath).getCanonicalFile();
    }
}
