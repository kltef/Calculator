# CAS Calculator — Roadmap

Native Android (Kotlin), math engine powered by [Symja](https://github.com/axkr/symja_android_library).

**Status: V1 shipped.** V2–V8 below are planned and unimplemented.

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

## V2 — Symbolic Algebra

- Variable support (assign/store values, use in expressions)
- Simplify expressions (e.g. expand `(x+1)^2`)
- Solve equations (linear, then quadratic)
- Step-by-step solution display

*Groundwork already in place:* the engine leaves unknown symbols symbolic
rather than failing (`x + 1` evaluates to `1+x`), and `CasEngine.reset()`
exists to clear variable bindings. Symja provides `Expand`, `Simplify` and
`Solve` directly; the work is the input syntax for assignment, a variable
store surfaced in the UI, and the step-by-step renderer — which Symja does
*not* provide and will need to be built on top of rule application.

## V3 — Graphing

- Plot `f(x)` over a range, with pan/zoom
- Multiple functions on one graph
- Trace mode (tap curve for coordinates)
- Visual root/intersection finding, feeding back into V2's solver

## V4 — Calculus

- Numeric + symbolic derivatives
- Definite/indefinite integrals
- Limits
- Tangent lines and area-under-curve shading on V3's graphs

## V5 — Matrices & Linear Algebra

- Matrix input/editing UI
- Determinants, inverses, eigenvalues, row reduction
- Solve linear systems using V2's solver core

## V6 — Natural Language Input

- Voice input (Android `SpeechRecognizer`)
- Natural language → expression parsing ("what's 20% of 150 plus tax")
- **Decision point:** local rule-based parser (offline, limited) vs. LLM-backed
  (flexible, needs network/API)
- Catch-all math extras: unit conversion, constants library, number theory,
  statistics, base conversion

## V7 — Polish, Ecosystem & Everything Else

- Camera/OCR input — photograph handwritten or textbook equations and parse them
- Cloud sync/backup of history and saved equations
- Widgets (home-screen calculator, lock-screen shortcut)
- Custom themes, adjustable UI density
- Export: graphs as images, step-by-step solutions as PDF/notes
- Sharing: send equation + result as link or image
- Education tools: practice problem generator + grading, "explain this step,"
  formula reference sheet
- Collaboration: shared live workspaces, public solved-problem links
- Programmability: scripting/macros, plugin system for niche domains
  (chemistry, physics, finance)
- Performance pass: cold-start time, Symja call latency on complex ops
- Monetization structure decisions (offline vs. cloud tiers, ads vs. free)

## V8 — AR Overlay

- Live camera feed with continuous (interval-based, not per-frame) OCR
- Equation detection + tracking via ARCore anchors, locked to the physical page
  as the camera moves
- Solution overlay rendered in space next to/over the written equation
- Graphs anchored in space at real scale relative to the writing surface
- Tap-to-expand step-by-step mode rendered as page annotations
- Symja remains the sole math engine — AR is a new rendering/input layer, not a
  new solver
- **Biggest risk:** tracking stability across lighting/hand movement — prototype
  standalone before full integration

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
- **Input normalization is a separate, tested stage.** `InputNormalizer` turns
  keypad notation into Symja syntax. V6's natural-language and V7's OCR input
  should target the *normalizer's* input format, not Symja's, so they inherit
  implicit multiplication, degree handling and percent for free.
- **Exact-first, approximate-second.** Every result carries an exact form and an
  optional decimal. V4's integrals and V5's eigenvalues must preserve this;
  collapsing to `double` early is how a CAS stops being a CAS.
