# VocaColle Morphe Patches

Unofficial [Morphe](https://morphe.software/) patches for the Android app
VocaColle (`jp.nicovideo.nicobox`). The current release localizes static
resources, hardcoded Compose strings, and reviewed server-provided labels into
Korean.

[![Latest release](https://img.shields.io/github/v/release/ilikeadofai/vocacolle-morphe-patches?sort=semver)](https://github.com/ilikeadofai/vocacolle-morphe-patches/releases/latest)
[![Release workflow](https://github.com/ilikeadofai/vocacolle-morphe-patches/actions/workflows/release.yml/badge.svg?branch=main)](https://github.com/ilikeadofai/vocacolle-morphe-patches/actions/workflows/release.yml)
[![License: GPL-3.0](https://img.shields.io/github/license/ilikeadofai/vocacolle-morphe-patches)](LICENSE)
[![Target: VocaColle 7.40.0](https://img.shields.io/badge/VocaColle-7.40.0-00a4e4)](#compatibility)

> [!NOTE]
> This project is not affiliated with Morphe or DWANGO. It does not distribute
> VocaColle APKs, signing keys, or account credentials.

## Install

1. Open [Add this repository to Morphe](https://morphe.software/add-source?github=ilikeadofai/vocacolle-morphe-patches).
2. If the deep link does not open, add this source manually:

   ```text
   https://github.com/ilikeadofai/vocacolle-morphe-patches
   ```

3. Patch VocaColle 7.40.0 and enable the three Korean localization patches
   together. The compatibility probe is optional and does not change app
   behavior.

## Current patches

<!-- PATCHES_START EXPANDED -->
> **[v1.0.0](https://github.com/ilikeadofai/vocacolle-morphe-patches/releases/tag/v1.0.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;4 patches total
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

### Korean hardcoded UI

Translates strings embedded directly in production Compose and third-party
bytecode. Fingerprints fail closed when the expected code shape changes.

### Korean native server UI

Translates a small whitelist of stable server-provided navigation and push
labels at display boundaries. Video titles, creator names, URLs, request IDs,
and unknown strings remain unchanged. See the
[native server UI report](docs/patches/korean-native-server-ui.md).

### Korean static UI

Adds reviewed Korean Android resources for app and library strings, plurals,
and arrays. The catalog and verification results are documented in the
[full static UI report](docs/patches/korean-full-static-ui.md).

### VocaColle compatibility probe

A no-op patch used to prove that the target APK can be decoded, rebuilt, and
signed without changing runtime behavior. See the
[compatibility probe report](docs/patches/vocacolle-compatibility-probe.md).

## Compatibility

- Target: VocaColle 7.40.0 (`versionCode 177`)
- Package: `jp.nicovideo.nicobox`
- Unknown app versions are rejected instead of receiving a best-effort patch.
- Fresh installs and updates must include the required ABI, language, and
  density splits signed with the same key as the patched base APK.

## Roadmap

These items are planned. They are not part of the current release unless they
also appear in the patch table above.

| Priority | Planned scope |
|----------|---------------|
| **P0: foundation** | Native Morphe settings, Korean and English UI, ad controls, VocaDB metadata, multi-provider BYOK translation, title/detail/notice translation, and sharing a matched YouTube Original URL. |
| **P1: lyrics and tags** | External lyrics through LRCLIB and VocaDB, synchronized lyrics, tag translation that preserves the original Japanese search value, and optional Korean pronunciation and translation under each lyric line. |
| **P2: local library** | Local playlists, favorites, and creator watchlists when NicoNico enforces mylist, follow, or daily-like limits on the server. |
| **P3: experiments** | Guest-login diagnostics, offline-cache improvements, and larger trend lists when ordinary server pagination supports them. |
| **P4: entitlement research** | High-quality audio and premium video only when the server already supplies an authorized playable asset. UI-only or fake-entitlement unlocks are out of scope. |

The [full roadmap](docs/ROADMAP.md) covers release milestones, source selection,
fallback behavior, acceptance criteria, and server-enforced limits.

## Documentation

- [Full feature roadmap](docs/ROADMAP.md)
- [Compatibility probe](docs/patches/vocacolle-compatibility-probe.md)
- [Static UI first pass](docs/patches/korean-static-ui-first-pass.md)
- [Home and Search second pass](docs/patches/korean-static-ui-second-pass.md)
- [Full static UI pass](docs/patches/korean-full-static-ui.md)
- [Native server UI](docs/patches/korean-native-server-ui.md)

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

Development happens on `dev`. Successful changes produce prereleases there;
stable releases are cut from `main` and backmerged into `dev` by the release
workflow.

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
