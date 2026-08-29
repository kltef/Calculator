package com.cascalc.engine

/** What the user wants done with the expression they typed. */
enum class Action(val label: String, val group: Group) {

    /** Work out a value (or store an assignment). */
    EVALUATE("Evaluate", Group.CORE),

    // --- V2: symbolic algebra -------------------------------------------
    SIMPLIFY("Simplify", Group.ALGEBRA),
    EXPAND("Expand", Group.ALGEBRA),
    FACTOR("Factor", Group.ALGEBRA),
    SOLVE("Solve", Group.ALGEBRA),

    // --- V4: calculus ----------------------------------------------------
    DIFFERENTIATE("d/dx", Group.CALCULUS),
    INTEGRATE("∫ dx", Group.CALCULUS),
    LIMIT("Limit", Group.CALCULUS),

    // --- V5: linear algebra ---------------------------------------------
    DETERMINANT("det", Group.MATRIX),
    INVERSE("inverse", Group.MATRIX),
    EIGENVALUES("eigenvalues", Group.MATRIX),
    ROW_REDUCE("row reduce", Group.MATRIX),
    TRANSPOSE("transpose", Group.MATRIX),
    RANK("rank", Group.MATRIX);

    enum class Group { CORE, ALGEBRA, CALCULUS, MATRIX }

    /** Actions that need a variable to work with respect to. */
    val needsVariable: Boolean
        get() = this == DIFFERENTIATE || this == INTEGRATE || this == SOLVE || this == LIMIT
}
