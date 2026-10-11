# Security and performance review

Reviewed upstream: `0f1df749340ed2fae59241426985fc4536e31d19`
Repository: https://github.com/rendyhd/Vicu-Android
Branch: `fix/security-performance-review`

This is a targeted source review of Android intent/upload boundaries, OIDC callbacks,
API authentication and pagination, attachment cache writes, backup exclusions, and
image authentication. It is not a device penetration test or measured performance benchmark.

## Changes

| Finding | Impact and trigger | Fix |
| --- | --- | --- |
| Untrusted share URIs can nominate app-private files | A malicious app sends a `file://` URI to the exported MainActivity. If the user creates the shared task, FileUtils opens the path with Vicu's permissions and uploads it. The app's own FileProvider is another route to cached files. | Reject file URIs and the app's own content authority at the upload boundary, including Android user-id prefixes. Only external content providers are accepted. |
| OIDC callback uses a string prefix and accepts subframes | A longer path can be mistaken for the registered callback; a subframe can end the sign-in activity. The shared OidcHandler already checks state, so this is defense in depth, not a demonstrated state-check bypass. | Match scheme, host, effective port and exact raw path; reject userinfo and fragments; intercept only main-frame callbacks. Require the expected state and check returned state in the activity too. |
| Token-free 401s enter account refresh/retry handling | A public endpoint returning 401 may be retried with the existing account token, or trigger refresh/re-auth unnecessarily. | Exclude every auth-exempt endpoint from reactive token handling, preserving the original 401. |
| Pagination can keep extending the finish line | `maxOf` accepts a growing total on every page. A continuously growing or malformed response can monopolize the process-wide sync lock and allocate an ever-growing list. | Fix the snapshot boundary from the first response and reject totals above 10,000 pages with an error, rather than return truncated data. Ordinary multi-page and short-page handling remains intact. |
| API connection/socket stalls have no explicit app policy | A stalled connection or transfer can hold sync or write locks. | Set 30-second connection and 60-second socket inactivity limits. Avoid a total-transfer deadline because attachment uploads may take minutes. |
| Concurrent attachment downloads share a partial file | Two downloads of the same attachment write, rename or delete the same `.part`, causing corruption or failed saves. | Use a unique UUID temporary file per download, retaining cleanup and final rename. |

## Regression coverage added

- OIDC: correct callback, default port, wrong host/port/scheme, longer paths, userinfo,
  fragments and malformed URLs.
- Upload URI policy: private files, self-provider, user-qualified self-provider,
  missing authority, and valid external providers.
- Authentication: public-endpoint 401 is requested once without an account bearer token.
- Pagination: increasing totals terminate at the original boundary; absurd totals fail
  immediately without returning a partial snapshot.

## Validation

- `git diff --check`: passed.
- Required gate attempted: `bash ./gradlew test lint assembleDebug`.
- The gate could not start: Gradle 9.1.0 download failed with
  `java.net.SocketException: Network is unreachable`. No Kotlin compilation, unit
  tests, lint, APK build, emulator tests or device checks completed in this environment.
- The checkout has Java 17, but no provisioned Android SDK or cached Gradle distribution.

Before installing, run the required gate on an Android build host. On a device, check
OIDC success/cancellation and state rejection, ordinary file sharing from Photos/Files,
rejection of private-file shares, concurrent downloads of the same attachment, and
recovery after a network stall. The URI restriction intentionally drops legacy
`file://` sharing and re-uploading through Vicu's own FileProvider.

## Scope and practical limits

Existing encrypted token storage, restricted FileProvider paths, and backup exclusions
were retained. LAN HTTP support remains an explicit upstream feature. The pagination
limit bounds page requests, not individual response-body size. These changes do not
provide transactionally consistent server snapshots during concurrent writes, a cache
quota, or evidence of improved frame rate/battery use. No upstream push, PR or release
was made.
