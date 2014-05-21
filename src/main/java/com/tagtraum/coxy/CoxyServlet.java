/*
 * =================================================
 * Copyright 2014 tagtraum industries incorporated
 * All rights reserved.
 * =================================================
 */
package com.tagtraum.coxy;

import org.scribe.builder.ServiceBuilder;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verifier;
import org.scribe.oauth.OAuthService;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.util.Date;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static javax.servlet.http.HttpServletResponse.*;
import static org.scribe.model.Verb.GET;

/**
 * CoxyServlet.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class CoxyServlet extends HttpServlet {

    private static final Preferences PREFERENCES = Preferences.userNodeForPackage(CoxyServlet.class);
    private static final ResourceBundle MIME_TYPES = ResourceBundle.getBundle("/mime");
    private static final String DEFAULT_MIME_TYPE = "application/octet-stream";
    private static final String CACHE_BASE = System.getProperty("cache.base");
    private static final String USER_AGENT = System.getProperty("http.agent");
    private static final String TARGET_BASE = stripTrailingSlash(System.getProperty("target.base"));
    private static final String KEY = System.getProperty("key");
    private static final String SECRET = System.getProperty("secret");
    private static final String RESOLVER = System.getProperty("resolver", "straight");
    private static final String ACCESS_TOKEN_KEY = "access.token";
    private static final long ONE_YEAR = 1000L * 60L * 60L * 24L * 365L;
    private File cacheBase;
    private OAuthService service;
    private Token accessToken;
    private Token requestToken;
    private long rateLimitResetTimeMillis;
    private CacheResolver cacheResolver;

    private static String stripTrailingSlash(String s) {
        if (s == null) return null;
        else while (s.endsWith("/")) { s = s.substring(0, s.length()-1); }
        return s;
    }

    @Override
    public void init() throws ServletException {
        if (USER_AGENT == null) {
            throw new ServletException("Configuration error. System property http.agent=... must be set.");
        }
        if (CACHE_BASE == null) {
            throw new ServletException("Configuration error. System property cache.base must be set.");
        }
        if (TARGET_BASE == null) {
            throw new ServletException("Configuration error. System property target.base must be set.");
        }
        if (KEY == null) {
            throw new ServletException("Configuration error. System property key must be set.");
        }
        if (SECRET == null) {
            throw new ServletException("Configuration error. System property secret must be set.");
        }
        switch (RESOLVER) {
            case "discogs": cacheResolver = new DiscogsImageCacheResolver(); break;
            case "straight":
            default: cacheResolver = new StraightCacheResolver();
        }
        log("Using " + cacheResolver.getClass().getSimpleName() + " as cache resolver. To change this, set the System property -Dresolver=(straight|discogs).");

        try {
            this.cacheBase = new File(CACHE_BASE).getCanonicalFile();
            this.cacheResolver.setCacheBase(cacheBase);
            this.service = new ServiceBuilder()
                    .provider(DiscogsApi.class)
                    .apiKey(KEY)
                    .apiSecret(SECRET)
                    .debug()
                    .build();
            final byte[] buf = PREFERENCES.getByteArray(ACCESS_TOKEN_KEY, null);
            if (buf != null) {
                final ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(buf));
                this.accessToken = (Token)in.readObject();
                log("Read available access token from preferences.");
                in.close();
            } else {
                log("No access token found in preferences. OAuth sign-in required.");
            }
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        final String path = req.getPathInfo();
        if (path == null || path.length()==0 || path.contains("..")) {
            resp.setStatus(SC_BAD_REQUEST);
            return;
        }
        final File file = cacheResolver.resolve(path);
        log("Request " + path + " mapped to file " + file + ", which does " + (file.exists()?"":"NOT ") + "exist.");
        if (!file.toString().startsWith(cacheBase.toString())) {
            resp.setStatus(SC_FORBIDDEN);
            log("Attempt to access resource outside of cache.base " + cacheBase + " by " + req.getRemoteAddr() + ": " + file);
            return;
        }
        if (file.exists() && file.isFile() && !isStale(file)) {
            sendFile(resp, file);
        } else {
            if (isRateLimitHit()) {
                sendServiceUnavailable(resp);
            } else {
                final Token accessToken = getAccessToken(req, resp, path);
                if (accessToken != null) {
                    final Response response = getAuthProtectedResource(accessToken, path);
                    copyRateLimitHeaders(response, resp);
                    final int limit = getLimit(response);
                    final int reset = getReset(response);
                    final int remaining = getRemaining(response);
                    if (remaining == 0 && reset > 0) {
                        rateLimitResetTimeMillis = System.currentTimeMillis() + reset * 1000L;
                        log("Rate limit of " + limit + " hit. Next request possible at " + new Date(rateLimitResetTimeMillis) + ".");
                    }
                    final int statusCode = response.getCode();
                    if (statusCode == SC_OK) {
                        // make sure directory exists
                        Files.createDirectories(file.getParentFile().toPath());
                        log("Copying resource " + path + " to " + file);
                        Files.copy(response.getStream(), file.toPath(), REPLACE_EXISTING);
                        sendFile(resp, file);
                    } else {
                        log("Failed to fetch resource " + path + " from target " + TARGET_BASE
                                + ": " + statusCode + " " + response.getMessage()
                                + ", limit=" + limit + ", reset=" + reset + ", remaining=" + remaining);
                        resp.setStatus(statusCode);
                        final PrintWriter writer = resp.getWriter();
                        writer.print("<html><head><title>"
                                + statusCode + " " + response.getMessage() + "</title></head><body><h1>"
                                + statusCode + " " + response.getMessage() + "</h1></body></html>");
                        writer.close();
                    }
                } else {
                    log("No access token available.");
                }
            }
        }
    }

    /**
     * Indicate to client that the resource is currently unavailable, but will
     * be available again after a certain time has passed.
     *
     * @param response response
     */
    private void sendServiceUnavailable(final HttpServletResponse response) {
        log("Proxy service unavailable. Next request possible at " + new Date(rateLimitResetTimeMillis) + ".");
        final long millisecondsUntilReset = rateLimitResetTimeMillis - System.currentTimeMillis();
        final String secondsUntilReset = Long.toString(millisecondsUntilReset / 1000L);
        response.addHeader("X-RateLimit-Reset", secondsUntilReset);
        response.addHeader("X-RateLimit-Remaining", "0");
        response.addHeader("Retry-After", secondsUntilReset);
        response.setStatus(SC_SERVICE_UNAVAILABLE);
    }

    /**
     * Indicates whether we already hit the rate limit or not.
     *
     * @return true or false
     */
    private boolean isRateLimitHit() {
        return rateLimitResetTimeMillis - System.currentTimeMillis() > 0;
    }

    /**
     * Copy those special rate limit headers, so that the client is informed, should he care.
     *
     * @param targetResponse targetResponse
     * @param servletResponse servletResponse
     */
    private void copyRateLimitHeaders(final Response targetResponse, final HttpServletResponse servletResponse) {
        for (final Map.Entry<String, String> e : targetResponse.getHeaders().entrySet()) {
            if (e != null && e.getKey() != null && e.getKey().toLowerCase().startsWith("x-ratelimit-")) {
                servletResponse.addHeader(e.getKey(), e.getValue());
            }
        }
    }

    /**
     * An integer representing the number of requests it’s possible to make on this type of resource in a 24 hour period.
     *
     * @param response response
     *
     * @return limit
     */
    private int getLimit(final Response response) {
        return getIntHeader(response, "X-RateLimit-Limit");
    }

    /**
     * An integer representing the number of requests you have remaining.
     *
     * @param response response
     *
     * @return limit
     */
    private int getRemaining(final Response response) {
        return getIntHeader(response, "X-RateLimit-Remaining");
    }

    /**
     * An integer representing the number of seconds left until your "remaining" meter resets.
     *
     * @param response response
     *
     * @return limit
     */
    private int getReset(final Response response) {
        return getIntHeader(response, "X-RateLimit-Reset");
    }

    private int getIntHeader(final Response response, final String headerName) {
        try {
            final String header = response.getHeader(headerName);
            return header == null ? -1 : Integer.valueOf(header);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * Indicates whether the cached file is considered stale.
     *
     * @param file file
     * @return true, if stale
     */
    protected boolean isStale(final File file) {
        return System.currentTimeMillis() - file.lastModified() > ONE_YEAR;
    }

    /**
     * Fetches the protected resource using the {@link #TARGET_BASE} and {@link #USER_AGENT}.
     *
     * @param accessToken accessToken
     * @param path path
     * @return response object
     */
    private Response getAuthProtectedResource(final Token accessToken, final String path) {
        final OAuthRequest request = new OAuthRequest(GET, TARGET_BASE  + path);
        service.signRequest(accessToken, request);
        return request.send();
    }

    private Token getAccessToken(final HttpServletRequest req, final HttpServletResponse resp, final String path) throws IOException {
        if (accessToken != null) {
            return accessToken;
        }
        log("No access token available...");
        final String verifierToken = req.getParameter("code");
        if ((path.equals("/index.html") || path.equals("/")) && verifierToken == null) {
            sendAuthorizationCodeInputForm(resp);
        } else if (verifierToken != null) {
            accessToken = verifyAuthorizationCode(verifierToken);
            sendHTMLResource(resp, "/code.html");
            return null;
        } else {
            sendAuthorizationCodeRequest(resp);
        }
        return null;
    }

    private Token verifyAuthorizationCode(final String verifierToken) throws IOException {
        log("Verifying authorization code...");
        final Verifier verifier = new Verifier(verifierToken);
        // Trade the Request Token and Verifier for the Access Token
        final Token accessToken = service.getAccessToken(requestToken, verifier);
        log("Success. Received new access token.");
        final ByteArrayOutputStream bout = new ByteArrayOutputStream();
        final ObjectOutputStream out = new ObjectOutputStream(bout);
        out.writeObject(accessToken);
        out.flush();
        PREFERENCES.putByteArray(ACCESS_TOKEN_KEY, bout.toByteArray());
        return accessToken;
    }

    private void sendAuthorizationCodeRequest(final HttpServletResponse resp) throws IOException {
        log("Sending authorization code request...");
        requestToken = service.getRequestToken();
        final String authorizationUrl = service.getAuthorizationUrl(requestToken);
        log("Received request token. Redirecting user to " + authorizationUrl);
        resp.sendRedirect(authorizationUrl);
    }

    private void sendAuthorizationCodeInputForm(final HttpServletResponse resp) throws IOException {
        log("Sending authorization code input form...");
        sendHTMLResource(resp, "/index.html");
    }

    private void sendFile(final HttpServletResponse response, final File file) throws IOException {
        log("Sending file " + file + ", size=" + file.length() + ", lastModified=" + new Date(file.lastModified()));
        // make sure clients know that they should cache these files..
        response.addDateHeader("Last-Modified", file.lastModified());
        response.addDateHeader("Expires", System.currentTimeMillis() + ONE_YEAR);
        response.addHeader("Cache-Control", "max-age=" + ONE_YEAR / 1000L);
        response.setContentLength((int) file.length());

        final String filePath = file.toString();
        final int i = filePath.lastIndexOf('.');
        if (i>0) {
            final String extension = filePath.substring(i+1).toLowerCase();
            final String mimeType = MIME_TYPES.containsKey(extension) ? MIME_TYPES.getString(extension) : DEFAULT_MIME_TYPE;
            response.setContentType(mimeType);
        }
        final ServletOutputStream out = response.getOutputStream();
        Files.copy(file.toPath(), out);
        out.close();
    }

    /**
     * Our own little file servlet so to say... because we map <em>everything</em> to {@link com.tagtraum.coxy.CoxyServlet}.
     *
     * @param resp response
     * @param path file resource path
     * @throws IOException
     */
    private void sendHTMLResource(final HttpServletResponse resp, final String path) throws IOException {
        resp.setContentType("text/html");
        InputStream in = null;
        OutputStream out = null;
        try {
            in = getServletContext().getResourceAsStream(path);
            out = resp.getOutputStream();
            copy(in, out);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    log(e.toString(), e);
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    log(e.toString(), e);
                }
            }
        }
    }

    /**
     * Simply copy one stream to another.
     *
     * @param in in
     * @param out out
     * @throws IOException
     */
    private static void copy(final InputStream in, final OutputStream out) throws IOException {
        final byte[] buf = new byte[16 * 1024];
        int justRead;
        while ((justRead = in.read(buf)) > 0) {
            out.write(buf, 0, justRead);
        }
    }
}
