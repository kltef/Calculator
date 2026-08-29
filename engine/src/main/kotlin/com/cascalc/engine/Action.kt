package com.cascalc.engine

/** What the user wants done with the expression they typed. */
enum class Action {
    /** Work out a value (or store an assignment). */
    EVALUATE,

    /** `Simplify` — reduce to the smallest equivalent form. */
    SIMPLIFY,

    /** `Expand` — multiply everything out: `(x+1)^2` becomes `1+2*x+x^2`. */
    EXPAND,

    /** `Factor` — the inverse of expand. */
    FACTOR,

    /** Solve an equation for its unknown. */
    SOLVE,
}
