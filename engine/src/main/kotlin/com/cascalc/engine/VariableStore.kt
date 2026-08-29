package com.cascalc.engine

/**
 * The user's variable bindings.
 *
 * Deliberately *not* delegated to Symja's own global assignments. If `x` is
 * bound inside Symja then `Solve(x^2-4==0, x)` sees the value rather than the
 * unknown and returns nonsense like `{{3->-2}}`; `Block` does not help, because
 * the argument is substituted before `Block` runs.
 *
 * So bindings live here and are applied as an explicit `ReplaceRepeated` rule
 * list at evaluation time. Symja's symbols stay free, and solving can exclude
 * exactly the variable being solved for.
 */
class VariableStore {

    private val definitions = linkedMapOf<String, String>()

    /** Variable name -> its definition, in Symja syntax. */
    fun asMap(): Map<String, String> = definitions.toMap()

    fun names(): Set<String> = definitions.keys.toSet()

    operator fun get(name: String): String? = definitions[name]

    fun define(name: String, symjaDefinition: String) {
        definitions[name] = symjaDefinition
    }

    fun remove(name: String) {
        definitions.remove(name)
    }

    fun clear() {
        definitions.clear()
    }

    /**
     * The bindings as a Symja rule list, e.g. `{a -> 2, b -> a + 1}`.
     *
     * [excluding] drops bindings that must stay symbolic — the unknown being
     * solved for. Returns `{}` when there is nothing to substitute.
     */
    fun rulesText(excluding: Set<String> = emptySet()): String {
        val rules = definitions
            .filterKeys { it !in excluding }
            .map { (name, value) -> "$name -> ($value)" }
        return rules.joinToString(prefix = "{", separator = ", ", postfix = "}")
    }

    fun isEmpty(excluding: Set<String> = emptySet()): Boolean =
        definitions.keys.none { it !in excluding }
}
