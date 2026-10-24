# Sesame-TK (beta)

Local modernization of the Sesame-TK Alipay Xposed module for the Magisk317 sibling toolchain.

## Scope

- Target package: `com.eg.android.AlipayGphone`
- Module package: `io.github.magisk317.sesame`
- Branch: `beta` (local project; no main remote/publish unless explicitly requested)
- Runtime: **LibXposed API 102 only** via GitLab submodule `magisk-xposed-kit`

## Toolchain

| Item | Value |
|------|-------|
| Gradle Wrapper | 9.6.1 |
| AGP | 9.3.1 |
| Kotlin | 2.4.10 |
| Compose BOM | `compose-bom-alpha:2026.07.01` |
| JDK / bytecode | 26 / 25 |
| minSdk / compileSdk / targetSdk | 32 / 37 / 37 |
| LibXposed API & service | 102.0.0 |

## Module layout

```
app/                         Android application + Xposed module
magisk-xposed-kit/           GitLab submodule (API 102 helpers / BaseLibXposedEntry)
gradle/libs.versions.toml    Version catalog aligned with sibling projects
```

Entry point:

- Class: `io.github.magisk317.sesame.xp.LibXposedEntry`
- Declared in: `app/src/main/resources/META-INF/xposed/java_init.list`
- Module props: `minApiVersion=100`, `targetApiVersion=102`, `staticScope=true`
- Scope: `com.eg.android.AlipayGphone`

## Submodule

```gitmodules
[submodule "magisk-xposed-kit"]
  path = magisk-xposed-kit
  url = https://gitlab.com/magisk3171/magisk-xposed-kit.git
  branch = main
```

LibXposed artifacts are resolved from Maven Central (`io.github.libxposed:api/service:102.0.0`).  
Do **not** compile or edit local `wqk/libxposed` sources from this project.

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :magisk-xposed-kit:testDebugUnitTest
```

Debug APKs land in:

```text
app/build/outputs/apk/debug/Sesame-TK-*-1.0.0-debug.apk
```

Use the ABI that matches the device, or the `universal` build.

## Runtime boundaries

- Hooks install through `magisk-xposed-kit` (`HookHelpers`, `MethodHook`, `XposedRuntime`, `BaseLibXposedEntry`).
- Legacy API 82/100 AAR packages, Normal/Compatible flavors, and `assets/xposed_init` are removed.
- Application hook core remains in `io.github.magisk317.sesame.hook.ApplicationHook`.
- Thin app-side wrappers live in `io.github.magisk317.sesame.hook.SesameHooks` for findAndHook* call sites.

## Agent notes

See [AGENTS.md](./AGENTS.md) for local agent guidance (toolchain pins, submodule policy, verification commands).

## License

See [LICENSE](./LICENSE).
