# Release signing

Versions 0.2.0 and 0.2.1 are non-debuggable release builds signed with the same private RSA-3072 key rather than the Android debug certificate. Their public signing certificate SHA-256 fingerprint is:

`F5892B30E3208FCB29F812F6605383CBB44BD068B2739E5FF5EAD14F9BB69CD5`

## Installing and updating

The package remains `com.plotmeasure.app`. Android requires uninstalling the differently signed 0.1.x debug build before installing a release. Version 0.2.1 (code 4) updates 0.2.0 (code 3) directly with the same signing key. Existing private settings/session/cache are removed by uninstall; externally exported photos are separate.

## Private key custody

In the original Codex workspace, the key and its generated password are retained in **`work/signing/` relative to the workspace root**, outside the distributable `outputs/PlotMeasure` source folder:

- `plotmeasure-release.p12`: encrypted private keystore, alias `plotmeasure`.
- `store-password.txt`: generated signing password; this is a secret.

The directory has owner-only access (0700), and both files have owner-only read/write access (0600). Neither file is in the APK, source ZIP or checksum list. Back up the keystore securely and move the password into your password manager or secret store before deleting this workspace. Do not distribute these files with the app. Losing this key prevents normal updates to installations signed with it; do not regenerate a different key for each release.

These are local signing credentials, not a configured Play App Signing or CI secret-management service. For public store distribution, configure a release process and review current store requirements before publishing.

## Rebuilding

Build with the README command. Use the Android SDK's `apksigner` with the retained keystore. Supply the password via a protected file or secret store rather than embedding it in a script, Gradle project or command-line password argument. For example, after setting `PLOT_SIGNING_DIR` to the private signing directory and `PLOT_BUILD_TOOLS` to Android Build Tools 35.0.0:

```sh
"$PLOT_BUILD_TOOLS/apksigner" sign --ks "$PLOT_SIGNING_DIR/plotmeasure-release.p12" --ks-key-alias plotmeasure --ks-pass "file:$PLOT_SIGNING_DIR/store-password.txt" --out PlotMeasure-release.apk app/build/outputs/apk/release/app-release-unsigned.apk
"$PLOT_BUILD_TOOLS/apksigner" verify --verbose --print-certs PlotMeasure-release.apk
"$PLOT_BUILD_TOOLS/zipalign" -c -P 16 4 PlotMeasure-release.apk
```

Verify that the release APK is non-debuggable and the certificate matches the fingerprint above before distributing an update. The source package alone intentionally cannot reproduce the signing identity without the retained private key.
