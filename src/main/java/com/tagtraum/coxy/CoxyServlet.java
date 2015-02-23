/*
 * =================================================
 * Copyright 2014 tagtraum industries incorporated
 * All rights reserved.
 * =================================================
 */
package com.tagtraum.coxy;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static javax.servlet.http.HttpServletResponse.*;

/**
 * CoxyServlet.
 *
 * @author <a href="mailto:hs@tagtraum.com">Hendrik Schreiber</a>
 */
public class CoxyServlet extends HttpServlet {

    private static final ResourceBundle MIME_TYPES = ResourceBundle.getBundle("/mime");
    private static final String DEFAULT_MIME_TYPE = "application/octet-stream";
    private static final String CACHE_BASE = System.getProperty("cache.base");
    private static final String USER_AGENT = System.getProperty("http.agent");
    private static final String TARGET_BASE = stripTrailingSlash(System.getProperty("target.base"));
    private static final String RESOLVER = System.getProperty("resolver", "straight");
    private static final long ONE_YEAR = 1000L * 60L * 60L * 24L * 365L;
    private File cacheBase;
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
        switch (RESOLVER) {
            case "discogs": cacheResolver = new DiscogsImageCacheResolver(); break;
            case "straight":
            default: cacheResolver = new StraightCacheResolver();
        }
        log("Using " + cacheResolver.getClass().getSimpleName() + " as cache resolver. To change this, set the System property -Dresolver=(straight|discogs).");

        try {
            this.cacheBase = new File(CACHE_BASE).getCanonicalFile();
            this.cacheResolver.setCacheBase(cacheBase);
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
                final HttpURLConnection response = getAuthProtectedResource(path);
                copyRateLimitHeaders(response, resp);
                final int limit = getLimit(response);
                final int reset = getReset(response);
                final int remaining = getRemaining(response);
                if (remaining == 0 && reset > 0) {
                    rateLimitResetTimeMillis = System.currentTimeMillis() + reset * 1000L;
                    log("Rate limit of " + limit + " hit. Next request possible at " + new Date(rateLimitResetTimeMillis) + ".");
                }
                final int statusCode = response.getResponseCode();
                if (statusCode == SC_OK) {
                    // make sure directory exists
                    Files.createDirectories(file.getParentFile().toPath());
                    log("Copying resource " + path + " to " + file);
                    Files.copy(response.getInputStream(), file.toPath(), REPLACE_EXISTING);
                    sendFile(resp, file);
                } else {
                    log("Failed to fetch resource " + path + " from target " + TARGET_BASE
                            + ": " + statusCode + " " + response.getResponseMessage()
                            + ", limit=" + limit + ", reset=" + reset + ", remaining=" + remaining);
                    resp.setStatus(statusCode);
                    final PrintWriter writer = resp.getWriter();
                    writer.print("<html><head><title>"
                            + statusCode + " " + response.getResponseMessage() + "</title></head><body><h1>"
                            + statusCode + " " + response.getResponseMessage() + "</h1></body></html>");
                    writer.close();
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
    private void copyRateLimitHeaders(final HttpURLConnection targetResponse, final HttpServletResponse servletResponse) {
        for (final Map.Entry<String, List<String>> e : targetResponse.getHeaderFields().entrySet()) {
            if (e != null && e.getKey() != null && e.getKey().toLowerCase().startsWith("x-ratelimit-")) {
                for (final String value : e.getValue()) {
                    servletResponse.addHeader(e.getKey(), value);
                }
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
    private int getLimit(final HttpURLConnection response) {
        return response.getHeaderFieldInt("X-RateLimit-Limit", -1);
    }

    /**
     * An integer representing the number of requests you have remaining.
     *
     * @param response response
     *
     * @return limit
     */
    private int getRemaining(final HttpURLConnection response) {
        return response.getHeaderFieldInt("X-RateLimit-Remaining", -1);
    }

    /**
     * An integer representing the number of seconds left until your "remaining" meter resets.
     *
     * @param response response
     *
     * @return limit
     */
    private int getReset(final HttpURLConnection response) {
        return response.getHeaderFieldInt("X-RateLimit-Reset", -1);
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
     * @param path path
     * @return response
     */
    private HttpURLConnection getAuthProtectedResource(final String path) throws IOException {
        final HttpURLConnection connection = (HttpURLConnection) new URL(TARGET_BASE + path).openConnection();
        // sensible default?
        connection.setReadTimeout(3000);
        connection.setConnectTimeout(3000);
        // add headers?
        connection.connect();
        return connection;
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

}
