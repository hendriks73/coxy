/*
 * =================================================
 * Copyright 2014 tagtraum industries incorporated
 * All rights reserved.
 * =================================================
 */
package com.tagtraum.coxy;

import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * DiscogsImageCacheResolver.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class DiscogsImageCacheResolverTest {

    @Test
    public void testNormalPattern() throws IOException {
        final DiscogsImageCacheResolver resolver = new DiscogsImageCacheResolver();
        resolver.setCacheBase(new File("/"));
        final File resolvedFile = resolver.resolve("/Mkq8l7_aFIXDsTdRT07pQoubjB4=/600x600/smart/filters:strip_icc():format(jpeg):mode_rgb():quality(96)/discogs-images/R-1507297-1370212405-8548.jpeg.jpg");
        assertEquals(new File("/images/R/15/07/R-1507297/R-1507297-1370212405-8548.jpeg"), resolvedFile);
    }

    @Test
    public void testShortReleaseID() throws IOException {
        final DiscogsImageCacheResolver resolver = new DiscogsImageCacheResolver();
        resolver.setCacheBase(new File("/"));
        final File resolvedFile = resolver.resolve("/Mkq8l7_aFIXDsTdRT07pQoubjB4=/600x600/smart/filters:strip_icc():format(jpeg):mode_rgb():quality(96)/discogs-images/R-150-1370212405-8548.jpeg");
        assertEquals(new File("/images/R/15/0/R-150/R-150-1370212405-8548.jpeg"), resolvedFile);
    }


}
