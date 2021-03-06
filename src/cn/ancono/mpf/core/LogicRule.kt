package cn.ancono.mpf.core

import cn.ancono.mpf.builder.RefFormulaScope
import cn.ancono.mpf.builder.SimpleFormulaScope
import cn.ancono.mpf.builder.buildFormula
import cn.ancono.mpf.matcher.FormulaMatcher
import cn.ancono.mpf.matcher.FormulaMatcherContext
import cn.ancono.mpf.matcher.buildMatcher
import java.util.*
import kotlin.collections.ArrayList


interface LogicRule : Rule {

    /**
     * Applies this rule to the given context and the currently obtained formulas.
     *
     * This method is designed for applying logic rules for more than one step.
     *
     * @param obtained obtained formulas in regular form
     */
    fun applyIncremental(
        context: FormulaContext,
        obtained: SortedSet<Formula>,
        formulas: List<Formula>,
        terms: List<Term>,
        desiredResult: Formula
    ): TowardResult

    override fun applyToward(
        context: FormulaContext,
        formulas: List<Formula>,
        terms: List<Term>,
        desiredResult: Formula
    ): TowardResult {
        return applyIncremental(context, context.regularForms.navigableKeySet(), formulas, terms, desiredResult)
    }

}

open class LogicMatcherRule(
    name: QualifiedName,
    description: String,
    matcher: FormulaMatcher,
    replacer: RefFormulaScope.() -> Formula
) : MatcherRule(name, description, matcher, replacer), LogicRule {
    override fun applyIncremental(
        context: FormulaContext,
        obtained: SortedSet<Formula>,
        formulas: List<Formula>,
        terms: List<Term>,
        desiredResult: Formula
    ): TowardResult {
        val results = arrayListOf<Deduction>()
        for (f in obtained) {
            val replaced = applyOne(f)
            val ctx = listOf(f)
            for (g in replaced) {
                val re = Deduction(this, g, ctx)
                if (g.isIdenticalTo(desiredResult)) {
                    return Reached(re)
                }
                results.add(re)
            }
        }
        return NotReached(results)
    }

    override fun applyToward(
        context: FormulaContext,
        formulas: List<Formula>,
        terms: List<Term>,
        desiredResult: Formula
    ): TowardResult {
        return applyIncremental(context, context.regularForms.navigableKeySet(), formulas, terms, desiredResult)
    }
}

open class LogicEquivRule(
    name: QualifiedName,
    description: String,
    m1: FormulaMatcher,
    r1: RefFormulaScope.() -> Formula,
    m2: FormulaMatcher,
    r2: RefFormulaScope.() -> Formula
) : MatcherEquivRule(name, description, m1, r1, m2, r2), LogicRule {
    override fun applyIncremental(
        context: FormulaContext,
        obtained: SortedSet<Formula>,
        formulas: List<Formula>,
        terms: List<Term>,
        desiredResult: Formula
    ): TowardResult {
        val results = arrayListOf<Deduction>()
        for (f in obtained) {
            val replaced = applyOne(f)
            val ctx = listOf(f)
            for (g in replaced) {
                val re = Deduction(this, g, ctx)
                if (g.isIdenticalTo(desiredResult)) {
                    return Reached(re)
                }
                results.add(re)
            }
        }
        return NotReached(results)
    }

    override fun applyToward(
        context: FormulaContext,
        formulas: List<Formula>,
        terms: List<Term>,
        desiredResult: Formula
    ): TowardResult {
        return applyIncremental(context, context.regularForms.navigableKeySet(), formulas, terms, desiredResult)
    }
}

/**
 * Contains all the rules of first order logic.
 * Created by liyicheng at 2020-05-04 13:40
 */
object LogicRules {
    val LogicNamespace = "logic"

    private fun nameOf(n: String): QualifiedName {
        return QualifiedName.of(n, LogicNamespace)
    }

    object RuleFlatten : LogicRule {
        override val name: QualifiedName = nameOf("Flatten")

        override val description: String
            get() = "(A∧B)∧C ⇒ A∧B∧C, (A∨B)∨C ⇒ A∨B∨C"

        override fun applyToward(
            context: FormulaContext,
            formulas: List<Formula>,
            terms: List<Term>,
            desiredResult: Formula
        ): TowardResult {
            val fs = context.regularForms.keys
            val flattened = desiredResult.flatten().regularForm
            val results = ArrayList<Deduction>(fs.size)
            for ((fr, f) in context.regularForms) {
                val f1 = fr.flatten()
                if (f1.isIdenticalTo(flattened)) {
                    return Reached(this, desiredResult, listOf(f))
                }
                results += Deduction(this, f1, listOf(f))
            }

            return NotReached(results)
        }

        override fun apply(context: FormulaContext, formulas: List<Formula>, terms: List<Term>): List<Deduction> {
            return context.formulas.map { Deduction(this, it.flatten(), listOf(it)) }
        }

        override fun applyIncremental(
            context: FormulaContext,
            obtained: SortedSet<Formula>,
            formulas: List<Formula>,
            terms: List<Term>,
            desiredResult: Formula
        ): TowardResult {
            val results = ArrayList<Deduction>(obtained.size)
            val flattened = desiredResult.flatten().regularForm
            for (f in obtained) {
                val f1 = f.flatten().regularForm
                if (f1.isIdenticalTo(flattened)) {
                    return Reached(this, desiredResult, listOf(f))
                }
                results += Deduction(this, f1, listOf(f))
            }

            return NotReached(results)
        }
    }

    private fun of(
        matcher: FormulaMatcherContext.() -> FormulaMatcher, replacer: RefFormulaScope.() -> Formula,
        name: String, description: String = "None"
    ): LogicRule {
        return LogicMatcherRule(nameOf(name), description, buildMatcher(matcher), replacer)
    }

    private fun def(
        p: SimpleFormulaScope.() -> Formula,
        q: SimpleFormulaScope.() -> Formula,
        name: String, description: String = "None"
    ): LogicRule {
        val f1 = buildFormula(p)
        val f2 = buildFormula(q)
        val m1 = FormulaMatcher.fromFormula(f1, false)
        val m2 = FormulaMatcher.fromFormula(f2, false)

        fun renameAndReplace(f: Formula): (RefFormulaScope.() -> Formula) = {
            val renamed = f.regularizeQualifiedVar(unusedVars().iterator())
            renamed.replaceVar { v ->
                termContext.context[v.name]?.term!!
            }.replaceNamed { nf ->
                formulas[nf.name.fullName]!!.build(nf.parameters)
            }
        }

        val r1 = renameAndReplace(f2)
        val r2 = renameAndReplace(f1)
        return LogicEquivRule(nameOf(name), description, m1, r1, m2, r2)
    }


    val RuleDoubleNegate = of({ !!P }, { P }, "DoubleNegate", "!!P => P")

    val RuleIdentityAnd = of({ andF(Q, P, P) }, { Q and P }, "IdentityAnd", "P&P => P")
    val RuleIdentityOr = of({ orF(Q, P, P) }, { Q or P }, "IdentityOr", "P|P => P")

    val RuleAbsorptionAnd = of({ andF(R, P, P or Q) }, { R and P }, "AbsorptionAnd", "P&(P|Q) => P")
    val RuleAbsorptionOr = of({ orF(R, P, P and Q) }, { R or P }, "AbsorptionOr", "P|(P&Q) => P")

    object RuleAndConstruct : LogicRule {
        override val name: QualifiedName = nameOf("ConstructAnd")
        override val description: String
            get() = "P,Q => P&Q"

        override fun applyToward(
            context: FormulaContext,
            formulas: List<Formula>,
            terms: List<Term>,
            desiredResult: Formula
        ): TowardResult {
            if (desiredResult !is AndFormula) {
                return NotReached(emptyList())
            }
            val children = desiredResult.children

            val rf = context.regularForms
            if (children.all { c -> c.regularForm in rf }) {
                val ctx = children.map { c ->
                    context.regularForms[c.regularForm]!!
                }
                return Reached(this, desiredResult, ctx)
            }
            return NotReached(emptyList())
        }

        override fun apply(context: FormulaContext, formulas: List<Formula>, terms: List<Term>): List<Deduction> {
            return emptyList()
        }

        override fun applyIncremental(
            context: FormulaContext,
            obtained: SortedSet<Formula>,
            formulas: List<Formula>,
            terms: List<Term>,
            desiredResult: Formula
        ): TowardResult {
            if (desiredResult !is AndFormula) {
                return NotReached(emptyList())
            }
            val children = desiredResult.children
            if (children.all { context.regularForms.contains(it) || obtained.contains(it) }) {
                val allContext = context.formulas.asSequence() + obtained.asSequence()
                val ctx = children.map { c ->
                    allContext.first { f -> c.isIdenticalTo(f) }
                }
                return Reached(this, desiredResult, ctx)
            }
            return NotReached(emptyList())
        }
    }

    val RuleAndProject = of({ andF(Q, P) }, { P }, "AndProject", "P&Q => P")

    //    val Rule
    val RuleImplyCompose = of({ (P implies Q) and (Q implies R) }, { P implies R }, "ImplyCompose",
        "P->Q and Q->R => P->R"
    )


    val RuleDefImply = def({ P implies Q }, { !P or Q },
//        { !P or Q }, { P implies Q },
        "DefImply", "P->Q <=> !P | Q"
    )


    object RuleImply : LogicRule {

        val matcher = buildMatcher { P implies Q }

        /**
         * @param rf regular form map
         */
        private fun buildResults(
            context: Collection<Formula>,
            rf: NavigableMap<Formula, Formula>
        ): List<Pair<Formula, Formula>> {
            return context.flatMap { f ->
                matcher.replaceOneWith(f) {
                    val pr = P.regularForm
                    if (rf.contains(pr)) {
                        Q to rf[pr]!!
                    } else {
                        null
                    }
                }
            }
        }

        override fun applyIncremental(
            context: FormulaContext,
            obtained: SortedSet<Formula>,
            formulas: List<Formula>,
            terms: List<Term>,
            desiredResult: Formula
        ): TowardResult {
            val dr = desiredResult.regularForm
            val rf = context.regularForms
            val results = buildResults(obtained, rf)
            for (re in results) {
                if (re.first.regularForm.isIdenticalTo(dr)) {
                    return Reached(this, re.first, listOf(re.second))
                }
            }
            return NotReached(results.map {
                Deduction(this, it.first, listOf(it.second))
            })
        }

        override val name: QualifiedName = nameOf("Imply")
        override val description: String
            get() = "P,P->Q => Q"


        override fun apply(context: FormulaContext, formulas: List<Formula>, terms: List<Term>): List<Deduction> {
            val rf = context.regularForms
            return buildResults(context.formulas, rf).map {
                Deduction(this, it.first, listOf(it.second))
            }
        }
    }


    val RuleDefEquivTo = def({ (P implies Q) and (Q implies P) }, { P equivTo Q },
//        { P equivTo Q }, { (P implies Q) and (Q implies P) },
        "DefEquivTo", "(P->Q & Q->P) <=> P<->Q"
    )

    val RuleEqualReplace = of({ (x equalTo y) and phi(x) }, { phi(y) }, "EqualReplace",
        "x=y & phi(x) => phi(y)"
    )

    object RuleExcludeMiddle : LogicRule {

        override val name: QualifiedName = nameOf("ExcludeMiddle")
        override val description: String
            get() = "=> P or !P"

        val matcher = buildMatcher { P or !P }

        override fun applyIncremental(
            context: FormulaContext,
            obtained: SortedSet<Formula>,
            formulas: List<Formula>,
            terms: List<Term>,
            desiredResult: Formula
        ): TowardResult {
            val matchResults = matcher.match(desiredResult)
            if (matchResults.isNotEmpty()) {
                return Reached(this, desiredResult, emptyList())
            }
            return NotReached(emptyList())
        }


        override fun apply(context: FormulaContext, formulas: List<Formula>, terms: List<Term>): List<Deduction> {
            if (formulas.isEmpty()) {
                return emptyList()
            }
            return formulas.map { f ->
                val nf = buildFormula { f or !f }
                Deduction(this, nf, emptyList())
            }
        }
    }

    object RuleExistConstant : LogicRule {

        val KEY_CONSTANT = "constant"

        override val name: QualifiedName = nameOf("ExistConstant")
        override val description: String
            get() = "phi(c) => exist[x] phi(x)"

        private fun buildFromConstant(f: Formula, c: Constant, nv: Variable, vt: VarTerm): Pair<Formula, Constant> {
            val sub = f.recurMapTerm { t ->
                if (t is ConstTerm && t.c == c) {
                    vt
                } else {
                    t
                }
            }
            val nf = ExistFormula(sub, nv)
            return nf to c
        }


        override fun applyIncremental(
            context: FormulaContext,
            obtained: SortedSet<Formula>,
            formulas: List<Formula>,
            terms: List<Term>,
            desiredResult: Formula
        ): TowardResult {
            // apply to the given constant term if exists
            val givenConstants = terms.filterIsInstance<ConstTerm>().map { it.c }

            val allResults = arrayListOf<Deduction>()
            for (f in context.formulas + obtained.toList()) {
                val newVariable = Formula.nextVar(f)
                val varTerm = VarTerm(newVariable)
                val constants = if (givenConstants.isEmpty()) {
                    f.allConstants()
                } else {
                    givenConstants
                }
                for (c in constants) {
                    val (rf, constant) = buildFromConstant(f, c, newVariable, varTerm)
                    val regular = rf.regularForm
                    if (regular.isIdenticalTo(desiredResult.regularForm)) {
                        return Reached(this, desiredResult, listOf(f), mapOf(KEY_CONSTANT to constant))
                    }
                    if (regular !in obtained) {
                        val result = Deduction(this, rf, listOf(f), mapOf(KEY_CONSTANT to constant))
                        allResults.add(result)
                    }
                }

            }
            return NotReached(allResults)
        }


        override fun apply(context: FormulaContext, formulas: List<Formula>, terms: List<Term>): List<Deduction> {
            val givenConstants = terms.filterIsInstance<ConstTerm>().map { it.c }

            val allResults = arrayListOf<Deduction>()
            for (f in context.formulas) {
                val newVariable = Formula.nextVar(f)
                val varTerm = VarTerm(newVariable)
                val constants = if (givenConstants.isEmpty()) {
                    f.allConstants()
                } else {
                    givenConstants
                }
                for (c in constants) {
                    val (rf, constant) = buildFromConstant(f, c, newVariable, varTerm)
                    val result = Deduction(this, rf, listOf(f), mapOf(KEY_CONSTANT to constant))
                    allResults.add(result)
                }

            }
            return allResults
        }
    }

    object RuleForAnyVariable : LogicRule {
        override val name: QualifiedName = nameOf("ForAnyVariable")
        val KEY_VARIABLE = "variable"

        override val description: String
            get() = "phi(x) => any[x] phi(x)"

        private fun buildFromVar(f: Formula, v: Variable): Formula {
            return ExistFormula(f, v)
        }

        override fun applyIncremental(
            context: FormulaContext,
            obtained: SortedSet<Formula>,
            formulas: List<Formula>,
            terms: List<Term>,
            desiredResult: Formula
        ): TowardResult {
            // apply to the given variable if exists
            val givenVariables = terms.filterIsInstance<VarTerm>().map { it.v }

            val allResults = arrayListOf<Deduction>()
            for (f in context.formulas + obtained.toList()) {
                var variables = f.variables
                if (givenVariables.isNotEmpty()) {
                    variables = variables.intersect(givenVariables)
                }
                for (v in variables) {
                    val rf = buildFromVar(f, v)
                    val regular = rf.regularForm
                    if (regular.isIdenticalTo(desiredResult.regularForm)) {
                        return Reached(this, desiredResult, listOf(f), mapOf(KEY_VARIABLE to v))
                    }
                    if (regular !in obtained) {
                        val result = Deduction(this, rf, listOf(f), mapOf(KEY_VARIABLE to v))
                        allResults.add(result)
                    }
                }

            }
            return NotReached(allResults)
        }


        override fun apply(context: FormulaContext, formulas: List<Formula>, terms: List<Term>): List<Deduction> {
            val givenVariables = terms.filterIsInstance<VarTerm>().map { it.v }

            val allResults = arrayListOf<Deduction>()
            for (f in context.formulas) {
                var variables = f.variables
                if (givenVariables.isNotEmpty()) {
                    variables = variables.intersect(givenVariables)
                }
                for (v in variables) {
                    val rf = buildFromVar(f, v)
                    val result = Deduction(this, rf, listOf(f), mapOf(KEY_VARIABLE to v))
                    allResults.add(result)
                }

            }
            return allResults
        }
    }

    val RuleForAnyAnd = def(
        { forAny(x) { "phi".n(x) } and forAny(y) { "psi".n(y) } },
        { forAny(z) { "phi".n(z) and "psi".n(z) } },
        "ForAnyAnd",
        "any[x](phi(x)) and any[y](psi(y)) <=> any[z](phi(z) and psi(z))"
    )

    /**
     * The list of all the logic rules.
     */
    val Rules = listOf(
        RuleFlatten,
        RuleDoubleNegate,
        RuleIdentityAnd,
        RuleIdentityOr,
        RuleAbsorptionAnd,
        RuleAbsorptionOr,
        RuleAndConstruct,
        RuleAndProject,
        RuleImplyCompose,
        RuleDefImply,
        RuleImply,
        RuleDefEquivTo,
        RuleEqualReplace,
        RuleExcludeMiddle,
        RuleExistConstant,
        RuleForAnyAnd
    )

    /**
     * A rule that tries to apply all the viable logic rules for multiple steps.
     *
     * The result will contain additional information named as 'DeductionTree', which is
     * a `DeductionNode`.
     */
    object AllLogicRule : Rule {

        override val name: QualifiedName
            get() = nameOf("Logic")
        override val description: String
            get() = "Combination of all logic rules."

        var searchDepth = 3


        @Suppress("NAME_SHADOWING")
        override fun applyToward(
            context: FormulaContext,
            formulas: List<Formula>,
            terms: List<Term>,
            desiredResult: Formula
        ): TowardResult {
//            val dr = desiredResult.regularForm
            val context = context.copy()

            val reached = TreeMap<Formula, DeductionNode>(FormulaComparator)
            // the map of an obtained formula and the deduction chain
            for (en in context.regularForms) {
                reached[en.key] = DeductionNode(Deduction(this, en.key, listOf(en.value)), emptyList())
            }

            fun buildNodeFrom(re: Deduction): DeductionNode {

                val subNodes = re.dependencies.map {
                    reached[it.regularForm]!!
                }
                return DeductionNode(re, subNodes)
            }

            var obtained: SortedSet<Formula> = TreeSet(reached.navigableKeySet()) // formulas obtained in each loop
            for (i in 0 until searchDepth) {
                var applied = false
                val newObtained = sortedSetOf(FormulaComparator)
                for (rule in Rules) {
                    val towardResult =
                        rule.applyIncremental(context, obtained, formulas, terms, desiredResult)
                    when (towardResult) {
                        is Reached -> {
                            val re = towardResult.result

                            val tree = buildNodeFrom(re)
                            // recursive dependencies
                            val context = TreeSet<Formula>(FormulaComparator)
                            tree.recurApply {
                                context.addAll(it.deduction.dependencies)
                                false
                            }
                            val moreInfo = mapOf(
                                "DeductionTree" to tree
                            )
                            return Reached(this, desiredResult, context.toList(), moreInfo)
                        }
                        is NotReached -> {
                            for (re in towardResult.results) {
                                val f = re.f
                                val fr = f.regularForm
                                if (fr !in reached) {
                                    applied = true
                                    reached[fr] = buildNodeFrom(re)
                                    newObtained += fr
                                }
                            }
                        }
                    }
                }
                if (!applied) {
                    break
                }
                context.addAll(obtained)
                obtained.clear()
                obtained = newObtained
            }
            return NotReached(emptyList())
        }

        override fun apply(context: FormulaContext, formulas: List<Formula>, terms: List<Term>): List<Deduction> {
            return emptyList()
        }

    }

    fun rulesAsMap(): Map<QualifiedName, Rule> {
//        println(Rules)
        val rules = Rules + AllLogicRule
        val map = mutableMapOf<QualifiedName, Rule>()
        for (r in rules) {
            map[r.name] = r
        }
        return map
    }
}

