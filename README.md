# CAS Calculator

A computer-algebra calculator for Android. Kotlin, Jetpack Compose, and
[Symja](https://github.com/axkr/symja_android_library) for the math.

Currently at **V1** of the [roadmap](ROADMAP.md): exact arithmetic, fractions
and history.

## Why exact arithmetic matters

An ordinary calculator answers `1/3 + 1/6` with `0.5`, and `0.1 + 0.2` with
`0.30000000000000004`. This one keeps values exact until you ask for a decimal:

| Input        | Exact result | Approximation  |
|--------------|--------------|----------------|
| `1/3 + 1/6`  | `1/2`        | —              |
| `1/3`        | `1/3`        | `0.333333333333` |
| `sqrt(8)`    | `2·√(2)`     | `2.82842712475` |
| `2^100`      | `1267650600228229401496703205376` | — |
| `sin(pi/6)`  | `1/2`        | —              |

The approximation is only shown when it adds something the exact form doesn't.

## Modules

```
engine/   Pure JVM math core — Symja wrapper, input normalizer, history model.
          No Android dependencies; builds and tests with just a JDK.
app/      Jetpack Compose UI: expression editor, keypad, live result, history.
```

Keeping the math in a plain JVM module means the interesting logic is covered by
fast unit tests that run anywhere, and the later roadmap versions (graphing,
calculus, AR) can all be built as new front-ends over the same evaluator.

## Building

```bash
# The math engine and its tests — no Android SDK required.
./gradlew :engine:test

# Everything, including the app — needs an Android SDK.
./gradlew build
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease   # R8-shrunk
```

`settings.gradle.kts` only includes `:app` when an Android SDK is actually
present (`ANDROID_HOME`, `ANDROID_SDK_ROOT`, or `sdk.dir` in
`local.properties`). Without one, Gradle configures and tests `:engine` alone
instead of failing outright.

Minimum SDK 26, target/compile SDK 35. The debug APK is ~34 MB and the
R8-shrunk release ~8 MB; Symja's function catalogue is most of that.

### Symja on Android

Two things Symja needs that a plain Android build doesn't give it:

- **Core library desugaring** (`isCoreLibraryDesugaringEnabled`), because Symja
  uses `java.time` and other APIs newer than minSdk 26.
- **Keep rules.** Symja resolves much of its function catalogue reflectively, so
  `proguard-rules.pro` keeps `org.matheclipse.**` and `org.hipparchus.**`
  wholesale. It also silences references its dependencies make to desktop-only
  APIs (`java.lang.management` from apfloat, `javax.lang.model` from Guava,
  `org.osgi` from jgrapht) which are unreachable on Android.

`matheclipse-external` shades jgrapht, so a few resources (`*.xsd`) arrive twice
and are resolved with `pickFirsts`.

## Input notation

The keypad and text field accept what people actually type, and
`InputNormalizer` translates it to Symja's syntax:

| You type            | Meaning                                     |
|---------------------|---------------------------------------------|
| `2(3+4)`, `2x`      | implicit multiplication                     |
| `×  ÷  −  √  π  ²`  | keypad symbols, mapped to ASCII/Symja       |
| `20%`               | postfix percent → `20/100`                  |
| `sin`, `ln`, `sqrt` | lowercase function names                    |
| `log(x)`            | base 10 — use `ln(x)` for natural log        |
| `√9`, `sqrt 9`      | square root without parentheses             |

**Angle mode** (the RAD/DEG chip) rewrites trigonometry rather than
post-processing it: in degree mode `sin(10+20)` becomes `Sin((10+20)*Degree)`,
so the whole argument is converted, not just its last term. Inverse functions
convert the other way — `asin(1)` gives `90`.

## Error handling

Errors are classified rather than dumped raw:

- `SYNTAX` — unparseable input (`3 + *`, unbalanced parentheses)
- `UNDEFINED` — division by zero, indeterminate forms
- `TIMEOUT` — evaluation exceeded the engine's time budget
- `INTERNAL` — anything else

While you are typing, errors are not shown: a half-finished expression isn't a
mistake yet. The live result simply stays on the last thing that evaluated, and
`=` is disabled until the expression is valid. Pressing `=` on a bad expression
is what surfaces the message.

## Threading

Symja's `EvalEngine` holds mutable state and is not thread-safe.
`CalculatorSession` owns a single dedicated thread and funnels every evaluation
through it, exposing `suspend` functions. The ViewModel debounces the live
preview so fast typing doesn't queue an evaluation per keystroke, and discards
results that arrive after the input has changed again.

## Tests

```bash
./gradlew :engine:test
```

42 tests covering input normalization (including degree-mode rewriting and
implicit multiplication), exact evaluation, error classification, the history
model and the session wrapper.
