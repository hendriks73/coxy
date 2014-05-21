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
        return new File(cacheBase, path).getCanonicalFile();
    }
}
