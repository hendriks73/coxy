# Coxy - a simple caching proxy for Discogs

Coxy lets you pipe your image requests through a proxy server which
issues OAuth authorized requests to the actual Discogs server, if
the requested file is not already in its cache.

## Plain Installation

You can find pre-built binaries in the beaTunes [Maven](http://maven.apache.org) repository at
[https://www.beatunes.com/repo/maven2/com/tagtraum/coxy/](https://www.beatunes.com/repo/maven2/com/tagtraum/coxy/)

Install the `war` file in a servlet container of your choice.
Then make sure to set the following system properties (the ones you specify
as `-Dkey=value` for the `java` command):

* `cache.base` - this is the folder where the cached files are store.
Obviously is must be writable by the servlet container process.
* `resolver` - determines how files are stored in the filesystem. Valid values are either `straight` or `discogs`.
* `http.agent` - the user agent to send to Discogs, e.g. `coolapp/2.0`
* `target.base` - the base part of the target server URL, e.g. `http://api.discogs.com` (no trailing slash!)

Make sure that the directory `cache.base` exists and is readable and writable
by your servlet container.

If you are planning on caching more than a couple of thousand files,
you *will* want to store those files not in one directory, but a hierarchy of
multiple nested ones. Otherwise filesystem performance will suffer.
For Discogs images you should therefore use the `discogs` resolver, in order
to translate a filename like `[signature]/discogs-images/R-1074891-1267544771.jpeg.jpg`
to something like `/images/R/10/74/R-1074891/R-1074891-1267544771.jpeg`.

*Hint: If used in conjunction with NGINX, it makes
sense to use a `cache.base`-path that ends with `coxy`, the name of this coxy's
servlet context. E.g.: `/var/www/coxy`.
This makes it easier to configure NGINX in a way that allows it to easily
serve the static images.*

You should now be able to send requests to your server, which will be forwarded
to your target server.

E.g. if configured correctly for Discogs, a request like
`http://localhost:8080/coxy/[signature]/discogs-images/R-944131-1175701834.jpeg.jpg`
should be forwarded to `http://api-img.discogs.com/[signature]/discogs-images/R-944131-1175701834.jpeg.jpg`,
the response then stored in the file system under the folder specified by
`cache.base` and ultimately returned to the user. The next request for the
same entity should be served directly from your file system.

## Installation with NGINX

Install the `war` just like you did above.
Then install NGINX and configure it to serve files directly from your `cache.base`.
This ensures that all files you have already fetched from your target server
are served efficiently without executing any fancy logic.
Only when a file is not in the cache yet, we need to forward the request to
the servlet engine.

Here's an example for an appropriate NGINX configuration:

    server {

        listen       80;
        server_name  localhost;
        root   /whatever/your/root/is;

        location /coxy {
            # enable this rewrite rule, if you are using the discogs resolver
            # see com.tagtraum.coxy.DiscogsImageCacheResolver
            rewrite "^/coxy/.*/discogs-(images/)((.)-([^-]{0,2})([^-]{0,2})[^-]*)(.*)(\.[^\.]*)$" /coxy/$1/$3/$4/$5/$2/$2$6 break;

            # if cache.base == /your/cache/base/coxy, root must be:
            root   /your/cache/base;
            # i.e., you must then omit "coxy", as it is appended by
            # default as part of the location

            # redirect 404 File Not Found errors to servlet container
            error_page  404              = @servlet_container;
            # make sure we tell browsers to cache images for a year
            expires 365d;
        }

        # redirect server error pages to the static page /50x.html
        error_page  404              /404.html;
        error_page  500 502 503 504  /50x.html;
        location = /50x.html {
            root   /usr/share/nginx/html;
        }


        location @servlet_container {
            # append $request_uri, so that we send the original URI, that hasn't been rewritten
            proxy_pass http://127.0.0.1:8080$request_uri;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header Host $http_host;
        }

    }

Note that for this to work, the servlet container must listen for HTTP request
on the configured port (in the example, that's port 8080 on localhost).

Of course we could also simply use NGINX built-in proxy cache. However, then the files
wouldn't be stored in the directory structure controlled by us, leading to possible
duplication.

## Disclaimer

Coxy comes with absolutely no warranty.

## Convert flat image directory to DiscogsImageCacheResolver

Run these commands from the directory that contains the images (note, these are not perfect...):
 
    ls --color=none *.jpeg | sed -e 's@\(\(.\)-\([^-]\{2\}\)\([^-]\{2\}\)[^-]*\)\(.*\)@mkdir -p \2/\3/\4/\1@' | sh
    ls --color=none *.jpeg | sed -e 's@\(\(.\)-\([^-]\{2\}\)\([^-]\{2\}\)[^-]*\)\(.*\)@mv & \2/\3/\4/\1/\1\5@' | sh

You might want to check what is actually being done with the `| sh` first (dry-run).

## Create list with flat image names
 
    ls -LR /var/www/coxy/images | grep -e "\.jpeg" -e "\.png" -e "\.gif" -e "\.bmp" -e "\.jpg"
