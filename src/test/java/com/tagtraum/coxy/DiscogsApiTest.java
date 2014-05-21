/*
 * =================================================
 * Copyright 2014 tagtraum industries incorporated
 * All rights reserved.
 * =================================================
 */
package com.tagtraum.coxy;

import org.junit.Ignore;
import org.junit.Test;
import org.scribe.builder.ServiceBuilder;
import org.scribe.model.*;
import org.scribe.oauth.OAuthService;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.Assert.assertNotNull;

/**
 * DiscogsApiTest.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class DiscogsApiTest {

    @Test
    @Ignore("Not suitable as automatic unit test. Requires manual action.")
    public void authorize() throws URISyntaxException, IOException {
        final String key = System.getProperty("key");
        final String secret = System.getProperty("secret");
        assertNotNull("You must use a discogs key via System property -Dkey=", key);
        assertNotNull("You must use a discogs secret via System property -Dsecret=", secret);
        assertNotNull("You must use a custom user agent via System property -Dhttp.agent=", System.getProperty("http.agent"));
        final OAuthService service = new ServiceBuilder()
                .provider(DiscogsApi.class)
                .apiKey(key)
                .apiSecret(secret)
                .debug()
                .build();
        final Token requestToken = service.getRequestToken();
        System.out.println("Token: " + requestToken);
        final String authorizationUrl = service.getAuthorizationUrl(requestToken);
        System.out.println("AuthorizationUrl: " + authorizationUrl);

        Desktop.getDesktop().browse(new URI(authorizationUrl));
        System.out.println("Please enter token: ");
        final String token = System.console().readLine();
        System.out.println("Got " + token);
        final Verifier verifier = new Verifier(token);

        // Trade the Request Token and Verifier for the Access Token
        final Token accessToken = service.getAccessToken(requestToken, verifier);

        final OAuthRequest request = new OAuthRequest(Verb.GET, "http://api.discogs.com/image/R-944131-1175701834.jpeg");
        service.signRequest(accessToken, request);
        final Response response = request.send();
        System.out.println(response.getCode() + " " + response.getMessage());
    }
}
