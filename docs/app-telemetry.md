# Official app telemetry (third-party SDKs in the APK)

Off-device, read-only review (client dev gen 37, 2026-10-18) of the network traffic that the official
OSRS Android app (241.4) makes by itself. This traffic is separate from the host. NetGuard
(`host/src/net/runelite/host/NetGuard.java`) only guards the host's OkHttp client, so it never covered
the app's own SDKs. Sources: `re/jadx/sources` (jadx of the base APK) and `re/apk/base/AndroidManifest.xml`.
Nothing here has been changed in the repack.

Trigger: gen 37 logcat at app start showed `D Singular: SingularRequestHandler [api] - 200 {"admon_batching":...,"resolve":{...}}`.
This is Singular's config response, so a request to Singular's API completed.

## SDKs present

| SDK | Wired up by the app? | Network at start | Notes |
|---|---|---|---|
| **Singular** (attribution) | yes: `sa.f` wraps it; `JagexMobileSDKWrapper` inits it via `sa.b.a(activity, "jagex_games_8f0ecb09", <secret>)` | **yes** (seen) | see below |
| **OneTrust** (consent / CMP) | yes: `za.p` (`initialiseMobileCMP`, called from native) | yes, `cdn-ukwest.onetrust.com` (consent config) | its callback decides whether Singular starts |
| **Firebase Analytics** (Google App Measurement) | yes: `ra.a.b` = `setAnalyticsCollectionEnabled(true)`, from `initialiseAnalyticsService` (called from native, string at ELF `0x0c0f15`); events through `ra.a.c` (`sendAnalyticsEvent`) | likely, once enabled | manifest ships `firebase_analytics_collection_enabled=false`; the app turns it on at runtime |
| **Firebase Crashlytics** (+ NDK) | registrars in the manifest; no `firebase_crashlytics_collection_enabled` meta-data, so it is on by default | on a crash or a pending report upload | sends crash reports with device info |
| **Firebase Messaging / Installations / Sessions / datatransport** | registrars in the manifest; `firebase_messaging_auto_init_enabled=false` | FCM token once messaging inits; Sessions may report app sessions | push only after the app opts in |
| **Braze** (engagement / push) | API key and EU endpoint in `res/values/strings.xml` (`com_braze_api_key`, `com_braze_custom_endpoint` = `sdk.fra-01.braze.eu`); manifest activities and receivers | unverified (not seen in logcat) | lifecycle hooks are in obfuscated classes; whether it opens sessions at start isn't confirmed |

## Singular in detail

- **Init path**: native → `JagexMobileSDKWrapper.initialiseMobileCMP` → OneTrust (`za.p.c` or `za.p.a`) →
  callback `r(boolean)` → `c(wrapper, consent, ...)`. When `consent` is true, it calls `sa.b.a(...)`, which runs
  `Singular.init(config.withLoggingEnabled().withLogLevel(1)...)` and then `Singular.limitDataSharing(true)`.
  The OneTrust boolean on this device has evidently been true, since Singular initialised.
- **Endpoints** (`com/singular/sdk/internal`): `https://sdk-api-v1.singular.net/api/v1` with `/start`
  (session, `ApiStartSession`), `/event` (`ApiSubmitEvent`), the config call (the logged `"resolve"` reply),
  plus `https://exceptions.singular.net/v2/exceptions/android` (SDK exception reports) and `/shorten_link`.
- **Payload of `/start`** (ApiStartSession): app version, ABI, device brand/build/manufacturer/model/product,
  OS version, locale, install/update/current times, device type, user agent, install source, install
  referrer, Do-Not-Track flag, FCM/GCM registration ids when set, preload campaign fields and the encrypted
  API key. Every request identifies the device as keyspace `k` plus id `u` (`BaseApi`, Constants
  keyspaces `aifa` advertising id, `andi` Android id, `asid` App Set id, `imei`, `oaid`, `sdid` Singular's
  own id). The gen 37 response echoed `"k":"AIFA"`, so this device sends its Google advertising id (the
  manifest declares `com.google.android.gms.permission.AD_ID`). `limitDataSharing(true)` flags
  the data as limited; it does not stop sending.
- **When**: `/start` on every app start (and on resume after a session timeout). Events come from
  `sendAttributionEvent` (native) and the one-time `af_started_first_time` after the first attribution
  callback (guarded by the `singular_boot` prefs flag `install_event_fired`).
  `setAttributionAccountId` → `Singular.setCustomUserId(<account id>)`.
- **Earlier captures**: the repo keeps no logcat archives (only `re/jadx.log` and `re/ghidra_import.log`).
  So "since the first gen" can't be shown from files. It is very likely, though: the init path doesn't depend
  on anything we patch, and every LOCAL_AUTH build reaches the same CMP step.

## Options to stop it in our repack (none applied)

| Option | What | Risk to the game |
|---|---|---|
| A. smali: make `sa.b.a` return at once | Singular never initialises. Every other `sa.b` entry already null-checks the instance (`b`, `c`, `d`, `e`), so events, consent and user id become no-ops | **low**: Singular is attribution only; the consent callback still runs the game's continuation (`c(...)` sets the flags and runs the runnable regardless) |
| B. smali: make `ra.a.b` / `ra.a.c` no-ops | Firebase Analytics stays at the manifest default (collection off) | **low** |
| C. manifest meta-data `firebase_analytics_collection_deactivated=true` | Google's permanent kill switch; it overrides `setAnalyticsCollectionEnabled(true)` | **very low**: documented flag, no code change |
| D. manifest meta-data `firebase_crashlytics_collection_enabled=false` | Crashlytics stops automatic collection and uploads | **very low** |
| E. Singular kill switch | none found: Singular has no manifest meta-data switch; `Singular.stopAllTracking()` is only reached through `sa.f.c` when the CMP says no consent | n/a |
| F. smali: force OneTrust's callback to `false` (skip the CMP fetch) | also stops `cdn-ukwest.onetrust.com` | **medium**: the CMP flow gates `setSSOTokenState` and login continuations (`syncConsentFederatedLoginAfterCMP`); getting it wrong can block sign-in |
| G. block hosts outside the app (Windows hosts file / firewall for `*.singular.net`, `*.braze.eu`, `app-measurement.com`, `firebase*.googleapis.com`, `cdn-ukwest.onetrust.com`) | no APK change | **low for the game**, but it changes the user's machine-wide network and affects every app on the PC/emulator; the user's call |
| H. native: skip the JNI calls to `initialiseAnalyticsService` / `initialiseMobileCMP` | stops both at the source | **medium**: needs RE of the caller; the CMP path may also gate login |

Suggested minimal set if the user wants it off: **A + C + D**. All three are in our existing repack step
(`tools/repack.sh` / `tools/patch_server.py`, which the server session owns): one smali method body and two
manifest meta-data lines. Each can be verified on the device by the absence of the `Singular:` /
`FA` / `FirebaseCrashlytics` log lines at start.

## Applied and verified (gen 37, 2026-10-18)

The user chose **A + C + D**. `tools/patch_telemetry.py` runs as step 1b of `tools/repack.sh` for every
build, normal and LOCAL_AUTH; normal builds now decode resources too, so that the manifest is text. A
normal build rebuilt fine but was not installed. The LOCAL_AUTH build was verified on BlueStacks from a
clean app start (force-stop, cleared logcat, `debug.osrsrl.netlog 1`), then sign-in, Play Now, LOGGED_IN
and a few minutes of play (walking, tabs, `::nproj`), then a second restart:

- **Singular: gone.** No `Singular` line at all (no "Singular Initialized", no `SingularRequestHandler`), so
  no `/start`, `/event`, config or exception requests. Sign-in and play are unaffected.
- **Firebase Analytics: off.** `FA: App measurement deactivated via the manifest`. Play services' own
  `PackageMeasurementTaskService` upload jobs still get scheduled in the gms process; they belong to
  Google Play services, not the app.
- **Crashlytics: collection off.** It still initialises (`Initializing Firebase Crashlytics 20.0.2`, native
  handler installed), and `FirebaseSessions: Sessions SDK disabled through data collection. Events will
  not be sent.` No upload lines.
- **Still active (not part of A/C/D):**
  - **OneTrust**: `GET https://mobile-data.onetrust.io/cfw/cmp/v1/banner` returns 200 (consent banner
    config; it logs a geolocation country/region), with parameters for `cdn-ukwest.onetrust.com`. This
    is the read-only fetch we expected.
  - **Braze**: active. It registers the FCM push token, "Requesting immediate data flush to Braze", sets
    a user, and resolves `sdk.fra-01.braze.eu`. It is configured from resources (`com_braze_api_key`),
    not from the code paths patched here. To stop it, an option is to blank `com_braze_api_key` /
    `com_braze_custom_endpoint` in `res/values/strings.xml`, or to disable Braze's lifecycle hook (risk
    not assessed yet).
  - DNS lookups netd logged during the run: `api.runelite.net` and `static.runelite.net` (the host's
    read-only GETs: prices and NPC data), `sdk.fra-01.braze.eu` (Braze), and **`account.jagex.com`**,
    which the app looked up right after Play Now, next to its billing client starting ("In-app billing
    API version 3 is not supported"). Whether a connection followed is not visible in logcat. netd only
    logs some lookups, so this list is not exhaustive.
