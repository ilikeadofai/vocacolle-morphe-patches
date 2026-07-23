# VocaColle Translation Patches

Unofficial localization and metadata translation patches for the Android app
VocaColle (`jp.nicovideo.nicobox`). The patch bundle is compatible with the
Morphe patcher but is not authored, maintained, or endorsed by the Morphe
project or DWANGO.

The current development target is VocaColle 7.40.0 (`versionCode 177`).

## Patches

<!-- PATCHES_START EXPANDED -->

### VocaColle compatibility probe

An opt-in no-op patch that verifies package/version filtering, APK rebuilding,
signing, split installation, and application startup before functional patches
are introduced. It intentionally performs no resource or bytecode changes.

### Korean static UI

Adds an opt-in Korean locale overlay for 1,607 user-visible strings, three
plural resources, and one string array across the app and bundled UI libraries.
The original default resources remain unchanged as the fallback.

### Korean hardcoded UI

Translates eight method-scoped DEX literals used by the first-party Proseka
screen and two bundled advertising UIs. Preview fixtures, server comparison
values, search queries, social hashtags, filenames, and submission payloads are
intentionally excluded.

### Korean native server UI

Translates a narrow whitelist of server-provided labels at native display
boundaries. It currently covers the ranking navigation and stable push-topic
titles while preserving unknown strings, song titles, creator names, URLs, and
request identifiers.

<!-- PATCHES_END -->

See [the compatibility probe report](docs/patches/vocacolle-compatibility-probe.md)
and the Korean UI reports for
[the first pass](docs/patches/korean-static-ui-first-pass.md) and
[the Home/Search second pass](docs/patches/korean-static-ui-second-pass.md), and
[the full static UI pass](docs/patches/korean-full-static-ui.md), plus the
[native server UI report](docs/patches/korean-native-server-ui.md)
for the complete validation records.

## Usage

[Add this repository as a Morphe patch source](https://morphe.software/add-source?github=ilikeadofai/morphe-patches-template),
or manually add:

```text
https://github.com/ilikeadofai/morphe-patches-template
```

The three Korean localization patches are opt-in and are intended to be enabled
together. The compatibility probe is a no-op diagnostic patch and is not needed
for normal use.

## Development

Configure the environment according to the
[Morphe development documentation](https://github.com/MorpheApp/morphe-documentation/blob/main/docs/morphe-development/README.md),
then run:

```shell
./gradlew :extensions:extension:testDebugUnitTest :patches:test buildAndroid
```

The generated bundle is written to:

```text
patches/build/libs/patches-*.mpp
```

Do not manually edit release-generated files such as `patches-list.json`,
`patches-bundle.json`, or `CHANGELOG.md`.

### Split APK warning

The VocaColle 7.40.0 input used for development is a base APK. Both fresh installs
and updates must submit the patched base together with the required ABI, language,
and density splits in one package-manager transaction. All APKs must use the same
signer. A successful Waydroid binder call is not sufficient evidence that Android
accepted a base-only update; require the package manager's explicit `Success` result.

Local APKs, keystores, patching artifacts, work files, and rollback snapshots
are ignored by Git.

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).
See [NOTICE](NOTICE) for the upstream project-name restriction.
