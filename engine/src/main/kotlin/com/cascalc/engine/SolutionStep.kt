package com.cascalc.engine

/**
 * One line of a worked solution.
 *
 * @param explanation what is being done, in words
 * @param expression the state of the problem after doing it, already formatted
 *                   for display; null for steps that are purely commentary
 */
data class SolutionStep(val explanation: String, val expression: String? = null)
