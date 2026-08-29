# CAS Calculator — Roadmap

Native Android (Kotlin), math engine powered by [Symja](https://github.com/axkr/symja_android_library).

**Status: V1–V6 shipped. V7 partly shipped. V8 not started.** See the
per-version notes; what is *not* built is stated plainly rather than implied.

---

## V1 — Core Arithmetic & Fractions ✅

- [x] Single-screen calculator UI with expression input + live result
- [x] Exact fraction arithmetic (no premature decimal conversion)
- [x] Calculation history

Delivered as two modules:

- `:engine` — a plain JVM library holding all the math. No Android
  dependencies, so it builds and tests on any machine with a JDK.
- `:app` — Jetpack Compose UI on top of it.

See [README.md](README.md) for what the engine understands and how to build.

## V2 — Symbolic Algebra ✅

- [x] Variable support (assign/store values, use in expressions)
- [x] Simplify expressions (e.g. expand `(x+1)^2`)
- [x] Solve equations (linear, then quadratic)
- [x] Step-by-step solution display

Two decisions shaped this version.

**Variables are not delegated to Symja.** The obvious implementation — evaluate
`x = 5` and let Symja hold the binding — breaks solving. Once `x` is bound,
`Solve(x^2-4==0, x)` sees the value instead of the unknown and returns
`{{3->-2},{3->2}}`. `Block({x}, ...)` does not help either, because the argument
is substituted before `Block` runs. So bindings live in `VariableStore` and are
applied as an explicit `ReplaceRepeated` rule list at evaluation time. Symja's
symbols stay free, and `Solve` excludes exactly the variable being solved for.

A consequence worth knowing: definitions are resolved when written, not when
read. `b = a + 1` with `a = 5` stores `6`, and later changing `a` leaves `b`
alone. That is the spreadsheet-versus-algebra choice, and it is the one that
matches how a calculator's memory keys behave.

**Steps are generated, not extracted.** Symja solves far more than it can
explain — `Solve` returns roots with no derivation. `StepSolver` drives the
school method for linear and quadratic equations in one unknown, asking Symja
to evaluate each intermediate quantity exactly. Anything outside that range is
still solved, but reports no steps rather than inventing a derivation.

Extending step coverage (systems, cubics, factoring by grouping) means adding
methods to `StepSolver`, not changing the solver.

## V3 — Graphing ✅

- [x] Plot `f(x)` over a range, with pan/zoom
- [x] Multiple functions on one graph (up to four)
- [x] Trace mode (tap curve for coordinates)
- [x] Visual root/intersection finding, feeding back into V2's solver

`Plotter` samples in graph coordinates and the canvas only maps to pixels, so
zooming re-samples rather than stretching an image. Two details that matter:
curves are **split at poles** — `1/x` is not drawn joined through infinity —
and root finding **excludes** those poles, because a sign change at an
asymptote is not a root. Roots come from sign changes refined by bisection;
intersections are the roots of a difference.

## V4 — Calculus ✅ (mostly)

- [x] Numeric + symbolic derivatives
- [x] Definite/indefinite integrals
- [x] Limits
- [ ] Tangent lines and area-under-curve shading on V3's graphs — **not built**

Derivatives, integrals and limits are actions with the variable inferred, plus
typed `d`/`integrate`/`limit` syntax for higher derivatives, definite bounds and
limits at a stated point. `StepSolver` shows a power-rule derivation for
polynomial derivatives; product/quotient/chain rules report no steps rather
than inventing a method.

The graph overlays are the gap: they need the graph screen to accept an
expression *and* a marked point from the calculus screen, which is a
cross-screen state change rather than new maths.

## V5 — Matrices & Linear Algebra ✅

- [x] Matrix input/editing UI (resizable grid editor)
- [x] Determinants, inverses, eigenvalues, row reduction, transpose, rank
- [x] Solve linear systems (`linearsolve`) through the shared engine

Matrix results are formatted by `ResultFormatter`, not Symja: Symja wraps on its
own width heuristic, so the same matrix came back on one line or several
depending on how wide the numbers were.

## V6 — Natural Language Input ✅

- [x] Voice input (system recogniser intent)
- [x] Natural language → expression parsing
- [x] **Decision point resolved: local rule-based parser.**
- [x] Catch-all math extras: unit conversion, constants library, number theory,
      statistics, base conversion

**Why local, not LLM-backed.** A network round trip for "twenty percent of 150"
fails on a train, costs money per tap, adds latency to something that must feel
instant, and sends the user's working to a third party. The price is a fixed
vocabulary, so the parser reports `NotUnderstood` rather than guessing — including
for this roadmap's own example, `"what's 20% of 150 plus tax"`: **"tax" names no
rate**, so any number would be invented. The `20% of 150` part parses fine.

Voice uses the recogniser *intent* rather than a bound `SpeechRecognizer`: it
needs no `RECORD_AUDIO` permission of ours and works without on-device
recognition.

Unit conversion is a local table because Symja's `UnitConvert` is unavailable in
this build. Temperature is affine (offset, not ratio) — 0 °C is 32 °F, not 0 °F.

## V7 — Polish, Ecosystem & Everything Else — partly shipped

Built:

- [x] Widgets — home-screen widget showing the last result
- [x] Custom themes (system/light/dark, dynamic colour), adjustable UI density
- [x] Export: step-by-step solutions as PDF, results as text
- [x] Sharing: equation + result via the system share sheet
- [x] Education tools: practice problem generator + grading, formula reference
- [x] Performance: engine built off the main thread and warmed at startup;
      plotting compiles once and samples numerically instead of exactly

Not built, with reasons:

- [ ] **Camera/OCR input** — needs ML Kit or an equivalent, which is a large
      dependency that cannot be exercised in this environment (no camera, no
      emulator). Shipping it untested into a working app is how the startup
      crash happened.
- [ ] **Cloud sync/backup** — needs a backend, an account system and a privacy
      decision. There is nothing to write until those exist.
- [ ] **Collaboration** (shared workspaces, public links) — same; a backend
      product, not a client feature.
- [ ] **Plugin system / scripting** — loading third-party code into the app is a
      security design question first. Note that `FUZZY_PARSER` currently
      *disables* Symja's `Compile`, `JavaNew` and filesystem builtins on purpose.
- [ ] **Graph export as image** — the share plumbing exists (`Sharing.shareImage`)
      but nothing captures the canvas to a bitmap yet.
- [ ] **"Explain this step"** — needs per-step commentary beyond what
      `StepSolver` generates.
- [ ] **Monetization structure** — a business decision, not an implementation.

## V8 — AR Overlay — not started

- [ ] Live camera feed with continuous (interval-based, not per-frame) OCR
- [ ] Equation detection + tracking via ARCore anchors
- [ ] Solution overlay rendered in space
- [ ] Graphs anchored in space at real scale
- [ ] Tap-to-expand step-by-step as page annotations

**Why nothing was written.** This roadmap already answers it: *"prototype
standalone before full integration"*, with tracking stability named as the
biggest risk. That prototype needs an ARCore-capable phone in hand, under real
lighting, with real handwriting. None of that can be approximated here — there
is no camera, no emulator and no ARCore. Writing an AR module blind would
produce code that compiles and cannot be trusted, which is exactly the failure
mode that produced the startup crash earlier in this project.

The prerequisite is V7's OCR: AR is a rendering layer over the same recognition
and the same solver. Build and validate camera OCR on a device first, then
anchor it.

---

## Architectural notes carried forward

The split that V1 establishes is what the later versions depend on:

- **All math lives in `:engine`, none in the UI.** V3's grapher, V5's matrix
  screen and V8's AR layer are all additional *renderers* over the same
  evaluator. Nothing above V1 should call Symja directly.
- **The engine is single-threaded by construction.** Symja's `EvalEngine` holds
  mutable state and is not thread-safe. `CalculatorSession` confines it to one
  dedicated thread and exposes `suspend` functions. V3's plotting will evaluate
  `f(x)` hundreds of times per frame-batch and must go through that same queue
  (or take its own engine instance — never share one).
- **Intent is explicit, never inferred.** `x^2 - 4` can be expanded, factored or
  solved; guessing would be wrong two times out of three. The action chips
  (Simplify / Expand / Factor / Solve) name the operation, and `=` evaluates.
  V4's derivatives and integrals should extend that same `Action` enum.
- **Input normalization is a separate, tested stage.** `InputNormalizer` turns
  keypad notation into Symja syntax. V6's natural-language and V7's OCR input
  should target the *normalizer's* input format, not Symja's, so they inherit
  implicit multiplication, degree handling and percent for free.
- **Exact-first, approximate-second.** Every result carries an exact form and an
  optional decimal. V4's integrals and V5's eigenvalues must preserve this;
  collapsing to `double` early is how a CAS stops being a CAS.
