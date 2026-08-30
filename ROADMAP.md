# CAS Calculator — Roadmap

Native Android (Kotlin), math engine powered by [Symja](https://github.com/axkr/symja_android_library).

**Status: V1–V6 and V8 shipped. V7 partly shipped.** See the per-version
notes; what is *not* built is stated plainly rather than implied.

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
- [x] Polar `r(θ)` and parametric `(x(t), y(t))` modes
- [x] Derivative plotted alongside the function

`Plotter` samples in graph coordinates and the canvas only maps to pixels, so
zooming re-samples rather than stretching an image. Two details that matter:
curves are **split at poles** — `1/x` is not drawn joined through infinity —
and root finding **excludes** those poles, because a sign change at an
asymptote is not a root. Roots come from sign changes refined by bisection;
intersections are the roots of a difference.

## V4 — Calculus ✅

- [x] Numeric + symbolic derivatives
- [x] Definite/indefinite integrals
- [x] Limits
- [x] Tangent lines and area-under-curve shading on V3's graphs

Derivatives, integrals and limits are actions with the variable inferred, plus
typed `d`/`integrate`/`limit` syntax for higher derivatives, definite bounds and
limits at a stated point. `StepSolver` shows a power-rule derivation for
polynomial derivatives; product/quotient/chain rules report no steps rather
than inventing a method.

Tangent slope comes from the **symbolic** derivative evaluated at the point, not
a finite difference — finite differences are why naive tangent tools drift near
sharp turns; a central difference is only the fallback. Area shading reports the
exact integral where Symja finds a closed form and the numeric (Simpson) area
otherwise. Integral bounds are written as exact literals, because passing `3.0`
turns an exact `9` into `9.0` and defeats the point of an exact engine.

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

- [x] Smooth animations throughout — cross-fade between screens, results that
      rise into place, panels that expand rather than appear, and curves that
      sweep in when the plotted set changes (but not while panning, where
      re-animating would read as stutter)
- [x] Widgets — home-screen widget showing the last result
- [x] Custom themes (system/light/dark, dynamic colour), adjustable UI density
- [x] Export: step-by-step solutions as PDF, results as text
- [x] Sharing: equation + result via the system share sheet
- [x] Education tools: practice problem generator + grading, formula reference
- [x] Performance: engine built off the main thread and warmed at startup;
      plotting compiles once and samples numerically instead of exactly

Not built, with reasons:

- [x] **Camera/OCR input** — shipped as part of V8's AR mode (ML Kit
      Play-services text recognition, interval-throttled).
- [ ] **Cloud sync/backup** — needs a backend, an account system and a privacy
      decision. There is nothing to write until those exist.
- [ ] **Collaboration** (shared workspaces, public links) — same; a backend
      product, not a client feature.
- [ ] **Plugin system / scripting** — loading third-party code into the app is a
      security design question first. Note that `FUZZY_PARSER` currently
      *disables* Symja's `Compile`, `JavaNew` and filesystem builtins on purpose.
- [ ] **Graph export as image** — the share plumbing exists (`Sharing.shareImage`)
      but nothing captures the canvas to a bitmap yet.
- [x] **"Explain this"** — `Explainer` describes what an expression is, not only
      what it equals. Per-*step* commentary is still open.
- [ ] **Monetization structure** — a business decision, not an implementation.

## V8 — AR Overlay ✅ (screen-space, not world-anchored)

- [x] Live camera feed with **interval-based** OCR (400 ms, not per-frame)
- [x] Equation detection + tracking, with overlays locked to the writing
- [x] Solution overlay rendered beside the written equation
- [x] Tap-to-expand step-by-step as page annotations
- [x] **Hand tracking — point at an equation to open it**
- [x] Symja remains the sole math engine — AR adds input and rendering only
- [ ] Graphs anchored in space at real scale — not built
- [ ] ARCore world anchors — **deliberately not used**, see below

**Tracking, the risk this roadmap names.** `EquationTracker` is plain Kotlin in
the engine module with no Android types, so the hard part is unit-tested rather
than eyeballed through a viewfinder. Three things keep an overlay steady:
matching detections by **position** (text only breaks ties), **easing** the drawn
box toward each new reading instead of snapping, and **grace frames** so a
momentary OCR failure from shadow or blur does not make the answer flicker away.

One bug the tests caught: the text-match bonus initially exceeded the overlap
threshold on its own, so identical writing anywhere on the page could capture a
track and the overlay would jump across the page. Position now gates matching
outright.

**Why screen-space rather than ARCore anchors.** ARCore must own the camera and
render the feed through OpenGL, which rules out a CameraX preview and ML Kit
analysis on the same stream. Choosing ARCore would mean rewriting recognition
and rendering around a GL pipeline that cannot be exercised here at all. The
screen-space overlay is what shipping AR translation apps do, and it is the
honest deliverable: it works on any camera phone, and its stability logic is
tested. World anchoring — and graphs pinned in space at real scale — remains the
upgrade path, and needs a device in hand.

**Pointing.** Hand landmarks come from MediaPipe; the decisions made from them
live in `HandPointer` in the engine module, tested like everything else.
Pointing is index-out-with-the-rest-curled; the thumb is ignored because it sits
at an angle that makes "extended" ambiguous and people point comfortably either
way. The cursor sits slightly *beyond* the fingertip, along the finger, because
pointing at something means your finger covers it.

Selection is by **dwell**, not by a pinch or an air-tap: those are unreliable to
detect and easy to trigger by accident, and there is nothing to tap in mid-air.
A ring fills while the finger is held still, so nothing ever opens without
warning. Hands are read every frame while text stays on its interval — a cursor
that lags the finger feels broken, but handwriting on a page does not move.

Hand tracking is an enhancement, never a requirement: if the model cannot be
fetched or the device cannot run it, AR mode says so and tapping still works.

**Explaining.** Pointing at something gives more than a number. `Explainer`
names what the thing is — linear, quadratic, cubic, a fraction, a prime — and
lists supporting facts, each one computed rather than asserted.

OCR is ML Kit's Play-services text recognition, which fetches its model on
demand rather than bundling ~16 MB. The hand model (7.5 MB) is fetched the same
way, on first use of AR mode. Both then run entirely on-device: no frame,
landmark or equation leaves the phone. Lines that do not look like maths are
rejected outright: an overlay that confidently answers a shopping list is worse
than one that stays quiet.

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
