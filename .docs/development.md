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
[release.yml](../.github/workflows/release.yml) to publish the jars to Maven Central and writes the release page,
a pre-release while it is 0.x. **Central is immutable**: a version, once published, can never be replaced or
deleted, and the next fix is the next tag. A `-rc` tag (`v0.3.0-rc1`) tries the pipeline without taking a real
version number.

Up to 0.2.0 releases went to GitHub Packages, and stay there; from 0.3.0 on only Central has them.

### Maven Central, set up once

None of this is in code, so it is written down here. The namespace and the key also serve the other `cz.insuit`
libraries.

1. **Namespace.** On the [Central Portal](https://central.sonatype.com/), signed in, *Namespaces → Add namespace*
   `cz.insuit`, then add the TXT record it shows to `insuit.cz` and press *Verify*. The record lives in the
   Terraform of `insuit.cz` ([homelab#633](https://github.com/landsman/homelab/pull/633)).
2. **User token.** *Account → Generate User Token*. Its two halves go into the repository secrets
   `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD`; keep a copy in 1Password, the Portal shows it once.
3. **Signing key**, kept in 1Password, never in a repo:

   ```sh
   gpg --quick-gen-key "Michal Landsman (release signing)" rsa4096 sign 3y
   gpg --keyserver keyserver.ubuntu.com --send-keys <key id>
   gpg --armor --export-secret-keys <key id> | gh secret set SIGNING_KEY -R landsman/fio-api-java
   gh secret set SIGNING_KEY_PASSWORD -R landsman/fio-api-java   # its passphrase, typed
   ```

   RSA and a primary key that signs, no subkey: Central's
   [GPG page](https://central.sonatype.org/publish/requirements/gpg/) shows RSA and verifies against the primary
   key only. The token secrets go in the same way: `gh secret set MAVEN_CENTRAL_USERNAME -R landsman/fio-api-java`.

Without the signing key the build still publishes to `~/.m2` (`make publish-local`), unsigned; Central refuses
an unsigned upload, so a release without the secrets fails at the Portal's validation, before anything is public.
