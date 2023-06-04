import com.ontotext.trree.AbstractRepositoryConnection
import com.ontotext.trree.ReportSupportedSolution
import com.ontotext.trree.SystemGraphs
import com.ontotext.trree.query.QueryResultIterator
import com.ontotext.trree.query.StatementSource
import com.ontotext.trree.sdk.StatementIterator

const val EXPLICIT = "explicit"

class ExplainIterator(
    public val reificationId: Long,
    statement: Statement,
    private val statementProperties: StatementProperties,
    private val requestContext: ProofContext
) : StatementIterator(statement.subj, statement.pred, statement.obj, statement.ctx),
    ReportSupportedSolution {

    private val solutions = mutableListOf<Solution>()
    private lateinit var solutionsIterator: Iterator<Solution>

    public var currentSolution: Solution? = null


    init {
        if (statementProperties.isExplicit) {
            solutions.add(Solution(rule = EXPLICIT, premises = mutableSetOf(statement)))
            solutionsIterator = solutions.iterator()
        } else {
            requestContext.inferencer.isSupported(statement.subj, statement.pred, statement.obj, statement.ctx, 0, this)

            //TODO finish this
        }

    }

    override fun next(): Boolean {
        return if (solutionsIterator.hasNext()) {
            currentSolution = solutionsIterator.next()
            true
        } else {
            currentSolution = null
            false
        }
    }

    override fun close() {
        solutions.clear()
        currentSolution = null
    }

    override fun report(ruleName: String, queryResult: QueryResultIterator): Boolean {
        while (queryResult.hasNext()) {
            if (queryResult is StatementSource) {
                val resultIterator = queryResult.solution()
                val antecedents = mutableSetOf<Statement>()

                while (resultIterator.hasNext()) {
                    val currentResult = resultIterator.next()
                    connection.getStatements(
                        currentResult.subj,
                        currentResult.pred,
                        currentResult.obj,
                        true,
                        0,
                        explicitStatus
                    ).use { iter ->
                        while (iter.hasNext()) {
                            if (iter.context != SystemGraphs.EXPLICIT_GRAPH.id.toLong()) {
                                currentResult.context = iter.context
                                currentResult.status = iter.status
                                break
                            }
                            iter.next()
                        }
                    }

                    val isSelfReferential =
                        this.subject == currentResult.subj && this.predicate == currentResult.pred && this.`object` == currentResult.obj //TODO consider adding context, refactor to different constructor
                    antecedents.add(currentResult.run { Statement(subj, pred, obj, context, status) })

                    val solution = Solution(ruleName, antecedents)
                    if (isSelfReferential) {
                        println("solution not added - statement is self referential")
                    } else {
                        if (!solutions.contains(solution)) {
                            println("added")
                            solutions.add(solution)
                        } else {
                            println("already added")
                        }

                    }
                }


            }

        }
        return false
    }


    override fun isExplicit() = statementProperties.isExplicit
    override fun isImplicit() = !statementProperties.isExplicit

    override fun getConnection(): AbstractRepositoryConnection = requestContext.repositoryConnection


}

class Statement(values: LongArray) {
    private var values = values.sliceArray(IntRange(0, 4))

    constructor(subj: Long, pred: Long, obj: Long, ctx: Long, status: Int = 0) : this(
        longArrayOf(
            subj, pred, obj, ctx,
            status.toLong()
        )
    )

    operator fun get(index: Int) = values.getOrNull(index)

    var subj
        get() = values[0]
        set(value) {
            values[0] = value
        }

    var pred
        get() = values[1]
        set(value) {
            values[1] = value
        }

    var obj
        get() = values[2]
        set(value) {
            values[2] = value
        }

    var ctx
        get() = values[3]
        set(value) {
            values[3] = value
        }

    var status
        get() = values[4]
        set(value) {
            values[4] = value
        }


    override fun equals(other: Any?): Boolean {
        if (other !is Statement)
            return false

        return (subj == other.subj) && (pred == other.pred) && (obj == other.obj) && (status == other.status)
    }

}

class Solution(val rule: String, val premises: MutableSet<Statement>) {
    override fun toString(): String {
        var string = "rule:$rule\n"
        premises.forEachIndexed { index, p ->
            string += "{$index}:${p.subj}, ${p.pred}, ${p.obj}, ${p.ctx}\n"
        }
        return string
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Solution)
            return false

        return this.rule == other.rule && this.premises == other.premises
    }

}