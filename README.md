# XYZShow

<p align="center">
  <img src="screenshots/XYZShow_showcase.jpg" alt="XYZShow molecule viewer, source viewer, and details screens" width="900" />
</p>

XYZShow is a small native Android molecular viewer for local XYZ files and Gaussian frequency outputs.

## Features

- Open local `.xyz`, multi-frame `.xyz`, Gaussian `.out`, and Gaussian `.log` files.
- Render molecules with OpenGL ES 2.0.
- Switch between `Space` and `Stick` styles from the top icon bar.
- Toggle black/white backgrounds from the top icon bar.
- Reset the camera to a fit-to-screen view when the molecule is dragged out of view.
- For Gaussian frequency jobs, inspect modes with the bottom playback bar: previous mode, play/pause, next mode, and the mode seek bar.
- View the loaded source file in a read-only line-numbered text page from the top icon bar.
- Use the top info button to open a full details page for XYZ/Gaussian metadata and the selected vibration.

## Gaussian Output Support

The parser reads text Gaussian `.out` / `.log` files and extracts:

- last `Standard orientation` block, falling back to last `Input orientation`
- harmonic `Frequencies --` blocks
- reduced masses, force constants, IR intensities, and normal-mode displacement vectors
- termination state, route section, charge/multiplicity, last SCF energy, ZPE, frequency count, imaginary-mode count, and lowest frequency

This is visualization only. XYZShow does not validate whether a calculation is a real minimum, transition state, or IRC-confirmed result.

## Build

Requirements:

- Android SDK with build tools
- Gradle 8.9 or compatible
- JDK 17 or newer

Build and verify the debug APK:

```bash
./build_debug_apk.sh
```

Build a signed release APK:

```bash
./build_release_apk.sh
```

The release helper creates local-only signing material under `local-signing/` on first use, signs `releases/XYZShow-release-<version>.apk`, and verifies the APK. The helper scripts default to the local toolchains under `/home/iaw/soft` and keep Gradle cache state inside this project. Override `ANDROID_HOME`, `JAVA_HOME`, `GRADLE_BIN`, `GRADLE_USER_HOME`, `KEYSTORE`, `SIGNING_ENV`, or `GRADLE_ARGS` when needed.

The latest verified release APK from this snapshot is included at:

```text
releases/XYZShow-release-0.4.5.apk
```

Release metadata from the local build:

- package: `io.iaw.xyzshow`
- version: `0.4.5` (`versionCode 20`)
- size: `112,320` bytes
- SHA-256: `c139b2048c145bd7e52195f31bec491c7f24152676ba04d9569a15afde7ee6e9`
- signing: release-signed, `apksigner verify` passed

## Scope

Current version: `0.4.5`.

PDB, mmCIF, checkpoint files, formatted checkpoint files, cube/volume data, cloud sync, and Play Store release packaging are not included in this snapshot.
