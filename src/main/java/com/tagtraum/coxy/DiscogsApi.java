/*
 * =================================================
 * Copyright 2014 tagtraum industries incorporated
 * All rights reserved.
 * =================================================
 */
package com.tagtraum.coxy;

import org.scribe.builder.api.DefaultApi10a;
import org.scribe.extractors.BaseStringExtractor;
import org.scribe.model.Token;

/**
 * DiscogsApi.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class DiscogsApi extends DefaultApi10a {

    public static final String REQUEST_TOKEN_URL = "http://api.discogs.com/oauth/request_token";
    public static final String ACCESS_TOKEN_URL = "http://api.discogs.com/oauth/access_token";
    public static final String AUTHORIZATION_URL = "http://www.discogs.com/oauth/authorize?oauth_token=%s";

    @Override
    public String getRequestTokenEndpoint() {
        return REQUEST_TOKEN_URL;
    }

    @Override
    public String getAccessTokenEndpoint() {
        return ACCESS_TOKEN_URL;
    }

    @Override
    public String getAuthorizationUrl(final Token requestToken) {
        return String.format(AUTHORIZATION_URL, requestToken.getToken());
    }

    @Override
    public BaseStringExtractor getBaseStringExtractor() {
        return super.getBaseStringExtractor();
    }
}
