package com.cascalc.engine

/** V7's reference sheet: formulas grouped by topic, insertable into the editor. */
object FormulaReference {

    data class Formula(val name: String, val expression: String, val note: String = "")
    data class Section(val title: String, val formulas: List<Formula>)

    val SECTIONS: List<Section> = listOf(
        Section(
            "Algebra",
            listOf(
                Formula("Quadratic formula", "x = (-b ± √(b² − 4ac)) / 2a", "for ax² + bx + c = 0"),
                Formula("Difference of squares", "a² − b² = (a − b)(a + b)"),
                Formula("Binomial square", "(a + b)² = a² + 2ab + b²"),
                Formula("Binomial cube", "(a + b)³ = a³ + 3a²b + 3ab² + b³"),
                Formula("Logarithm product", "log(ab) = log a + log b"),
                Formula("Change of base", "log_b(x) = ln x / ln b"),
            ),
        ),
        Section(
            "Trigonometry",
            listOf(
                Formula("Pythagorean identity", "sin²θ + cos²θ = 1"),
                Formula("Double angle (sine)", "sin 2θ = 2 sin θ cos θ"),
                Formula("Double angle (cosine)", "cos 2θ = cos²θ − sin²θ"),
                Formula("Law of cosines", "c² = a² + b² − 2ab cos C"),
                Formula("Law of sines", "a / sin A = b / sin B = c / sin C"),
            ),
        ),
        Section(
            "Calculus",
            listOf(
                Formula("Power rule", "d/dx (xⁿ) = n·xⁿ⁻¹"),
                Formula("Product rule", "d/dx (uv) = u′v + uv′"),
                Formula("Quotient rule", "d/dx (u/v) = (u′v − uv′) / v²"),
                Formula("Chain rule", "d/dx f(g(x)) = f′(g(x))·g′(x)"),
                Formula("Power integral", "∫ xⁿ dx = xⁿ⁺¹/(n+1) + C", "n ≠ −1"),
                Formula("Fundamental theorem", "∫ₐᵇ f′(x) dx = f(b) − f(a)"),
            ),
        ),
        Section(
            "Linear algebra",
            listOf(
                Formula("2×2 determinant", "det [[a b],[c d]] = ad − bc"),
                Formula("2×2 inverse", "[[a b],[c d]]⁻¹ = 1/(ad−bc) · [[d −b],[−c a]]"),
                Formula("Eigenvalue equation", "A·v = λ·v"),
            ),
        ),
        Section(
            "Geometry",
            listOf(
                Formula("Circle area", "A = π r²"),
                Formula("Circle circumference", "C = 2π r"),
                Formula("Sphere volume", "V = 4/3 π r³"),
                Formula("Cone volume", "V = 1/3 π r² h"),
            ),
        ),
        Section(
            "Statistics",
            listOf(
                Formula("Mean", "x̄ = Σxᵢ / n"),
                Formula("Variance", "σ² = Σ(xᵢ − x̄)² / n"),
                Formula("Combinations", "C(n, k) = n! / (k!(n−k)!)"),
                Formula("Permutations", "P(n, k) = n! / (n−k)!"),
            ),
        ),
    )
}
