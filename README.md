# VocaColle Translation Patches

Unofficial localization and metadata translation patches for the Android app
VocaColle (`jp.nicovideo.nicobox`). The patch bundle is compatible with the
Morphe patcher but is not authored, maintained, or endorsed by the Morphe
project or DWANGO.

The current development target is VocaColle 7.40.0 (`versionCode 177`).

## Patches

<!-- PATCHES_START EXPANDED -->
> **[v1.0.0-dev.1](https://github.com/ilikeadofai/morphe-patches-template/releases/tag/v1.0.0-dev.1)**&nbsp;&nbsp;•&nbsp;&nbsp;`dev`&nbsp;&nbsp;•&nbsp;&nbsp;4 patches total
<details open>
<summary>📦 VocaColle&nbsp;&nbsp;•&nbsp;&nbsp;4 patches</summary>
<br>

**🎯 Supported versions:**

| 7.40.0 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Korean hardcoded UI](#korean-hardcoded-ui) | Translates production Compose and third-party UI strings embedded directly in VocaColle bytecode. |  |
| [Korean native server UI](#korean-native-server-ui) | Translates whitelisted server-provided labels only at native UI display boundaries. |  |
| [Korean static UI](#korean-static-ui) | Adds reviewed Korean resources for all app and library static UI strings, plurals, and arrays. |  |
| [VocaColle compatibility probe](#vocacolle-compatibility-probe) | Verifies that VocaColle 7.40.0 can be decoded, rebuilt, and signed without changing app behavior. |  |

</details>

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
