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
 * Translates path info strings to file objects.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public interface CacheResolver {

    /**
     * Set the cache base.
     *
     * @param cacheBase cache base
     */
    void setCacheBase(File cacheBase);

    /**
     * Get cache base.
     *
     * @return cache base
     */
    File getCacheBase();

    /**
     * Resolves the path info received from the user to a file.
     *
     * @param path path info
     * @return file
     * @throws java.io.IOException if we cannot resolve the path
     */
    File resolve(String path) throws IOException;

}
