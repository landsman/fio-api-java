# Development

`make build` checks formatting, compiles with Error Prone and NullAway, runs the tests twice (on the Jackson in
the build and on Jackson 3.0.4, Spring Boot 4.0.0's, so an API newer than that fails here rather than in a
caller), checks the declared dependencies and packages the jar. `make` lists the targets. A JDK 21 or newer is
enough; `mise.toml` pins the one used here.

The tests run against fixtures in [src/test/resources/fio/](../modules/fio-api/src/test/resources/fio/README.md)
and the JDK's own HTTP server, never a live account.

After a dependency changes, `make lock` rewrites `modules/fio-api/gradle.lockfile`, which the vulnerability scan
reads; the build fails while it is stale.

## Releasing

Only a tag releases: merging to `main` publishes nothing. `make release` asks for the tag, suggesting the next
patch, refuses one that exists or a checkout that is not a clean `origin/main`, tags, waits for
[release.yml](../.github/workflows/release.yml) to publish the jar to GitHub Packages and writes the release page,
a pre-release while it is 0.x. A version, once published, cannot be published again; the next fix is the next tag.
