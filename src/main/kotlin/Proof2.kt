import com.ontotext.trree.AbstractRepositoryConnection
import com.ontotext.trree.StatementIdIterator
import com.ontotext.trree.sdk.*
import org.eclipse.rdf4j.model.Triple
import org.eclipse.rdf4j.model.util.Values.*
import org.slf4j.Logger
import java.io.File
import kotlin.properties.Delegates

const val ITER = "iter"
const val UNBOUND = 0L
const val DEFAULT_GRAPH = 0L

const val contextMask = //TODO name is copied from original proof plugin
    (StatementIdIterator.DELETED_STATEMENT_STATUS or StatementIdIterator.SKIP_ON_BROWSE_STATEMENT_STATUS or StatementIdIterator.INFERRED_STATEMENT_STATUS) //Copied this from the old proof plugin. No documentation on what each flag does


class Proof2 : Plugin, Preprocessor, PatternInterpreter {
    private val namespace = "http://example.com/proof/"
    private val explainIri = iri(namespace + "explain")
    private val fromRuleIri = iri(namespace + "fromRule")
    private val hasSubjectIri = iri(namespace + "hasSubject")
    private val hasPredicateIri = iri(namespace + "hasPredicate")
    private val hasObjectIri = iri(namespace + "hasObject")
    private val hasContextIri = iri(namespace + "hasContext")

    private var explainId by Delegates.notNull<Long>()
    private var fromRuleId by Delegates.notNull<Long>()
    private var hasSubjectId by Delegates.notNull<Long>()
    private var hasPredicateId by Delegates.notNull<Long>()
    private var hasObjectId by Delegates.notNull<Long>()
    private var hasContextId by Delegates.notNull<Long>()

    private var logger: Logger? = null

    override fun initialize(initReason: InitReason, pluginConnection: PluginConnection) {
        explainId = pluginConnection.entities.put(explainIri, Entities.Scope.SYSTEM)
        fromRuleId = pluginConnection.entities.put(fromRuleIri, Entities.Scope.SYSTEM)
        hasSubjectId = pluginConnection.entities.put(hasSubjectIri, Entities.Scope.SYSTEM)
        hasPredicateId = pluginConnection.entities.put(hasPredicateIri, Entities.Scope.SYSTEM)
        hasObjectId = pluginConnection.entities.put(hasObjectIri, Entities.Scope.SYSTEM)
        hasContextId = pluginConnection.entities.put(hasContextIri, Entities.Scope.SYSTEM)
    }

    override fun preprocess(request: Request?): RequestContext {
        return ProofContext(request)
    }


    override fun estimate(
        subjectId: Long,
        predicateId: Long,
        objectId: Long,
        contextId: Long,
        pluginConnection: PluginConnection,
        requestContext: RequestContext?
    ): Double {
        //TODO finish this
        return 0.0
    }

    override fun interpret(
        subjectId: Long,
        predicateId: Long,
        objectId: Long,
        contextId: Long,
        pluginConnection: PluginConnection,
        requestContext: RequestContext?
    ): StatementIterator {
        if (requestContext !is ProofContext) {
            logger?.warn("Request context is not ProofContext")
            return StatementIterator.EMPTY
        }

        if (!requestContext.inferencer.inferStatementsFlag) {
            logger?.warn("inferStatementsFlag is ${requestContext.inferencer.inferStatementsFlag}")
            return StatementIterator.EMPTY
        }

        logger?.info("Subject$subjectId, predicate $predicateId, object $objectId, context $contextId")

        if (predicateId == explainId) {
            val explainIterator = getExplainIteratorForStatement(
                quotedStatementId = objectId, contextId, requestContext, pluginConnection
            ) ?: return StatementIterator.EMPTY
            requestContext.iterators["$ITER-${explainIterator.reificationId}"] = explainIterator
            logger?.info("Proof2 has handled a statement")
            return explainIterator
        }

        return getExplainIteratorForPredicate(subjectId, predicateId, objectId, requestContext, pluginConnection).also { logger?.info("Proof2 has handled a predicate") }
    }

    private fun getExplainIteratorForStatement(
        quotedStatementId: Long, contextId: Long, requestContext: ProofContext, pluginConnection: PluginConnection
    ): ExplainIterator? {
        val entities = pluginConnection.entities
        val objectValue = entities.get(quotedStatementId) as? Triple ?: run {logger?.info("Object is ${entities[quotedStatementId]}"); return null}
        val quotedSubjectId = entities.put(objectValue.subject, Entities.Scope.SYSTEM)
        val quotedPredicateId = entities.put(objectValue.predicate, Entities.Scope.SYSTEM)
        val quotedObjectId = entities.put(objectValue.predicate, Entities.Scope.SYSTEM)

        val reificationId = entities.put(bnode(), Entities.Scope.REQUEST)
        val statement = Statement(quotedSubjectId, quotedPredicateId, quotedObjectId, contextId)
        val statementProperties = requestContext.repositoryConnection.getStatementProperties(statement)

        return ExplainIterator(reificationId, statement, statementProperties, requestContext)
    }


    private fun getExplainIteratorForPredicate(
        subjectId: Long,
        predicateId: Long,
        objectId: Long,
        requestContext: ProofContext,
        pluginConnection: PluginConnection
    ): StatementIterator {
        val iter = requestContext.iterators["$ITER-$subjectId"]
        val currentSolution = iter?.currentSolution ?: return StatementIterator.EMPTY


        when (predicateId) {
            fromRuleId -> {
                val ruleId = pluginConnection.entities.put(literal(currentSolution.rule), Entities.Scope.REQUEST)
                return StatementIterator.create(
                    iter.reificationId,
                    fromRuleId,
                    ruleId,
                    DEFAULT_GRAPH
                ) //TODO check if default graph is the correct context
            }

            hasSubjectId -> {
                if (objectId.isBound() && objectId != iter.subject) {
                    return StatementIterator.EMPTY
                }
                return StatementIterator.create(iter.reificationId, hasSubjectId, iter.subject, DEFAULT_GRAPH)
            }

            hasPredicateId -> {
                if (objectId.isBound() && objectId != iter.predicate) {
                    return StatementIterator.EMPTY
                }
                return StatementIterator.create(iter.reificationId, hasPredicateId, iter.predicate, DEFAULT_GRAPH)
            }

            hasObjectId -> {
                if (objectId.isBound() && objectId != iter.`object`) {
                    return StatementIterator.EMPTY
                }
                return StatementIterator.create(iter.reificationId, hasObjectId, iter.`object`, DEFAULT_GRAPH)
            }

            hasContextId -> {
                if (objectId.isBound() && objectId != iter.context) {
                    return StatementIterator.EMPTY
                }
                return StatementIterator.create(iter.reificationId, hasContextId, iter.context, DEFAULT_GRAPH)
            }

            else -> return StatementIterator.EMPTY
        }
    }


    private fun Long.isUnbound(): Boolean {
        return this == UNBOUND
    }

    private fun Long.isBound(): Boolean {
        return this != UNBOUND
    }


    private fun AbstractRepositoryConnection.getStatementProperties(
        subjectId: Long, predicateId: Long, objectId: Long, contextId: Long
    ): StatementProperties {
        this.getStatements(subjectId, predicateId, objectId, contextId, contextMask).use { iter ->
            val isExplicit = iter.hasNext()
            return StatementProperties(
                isExplicit,
                context = if (isExplicit) iter.context else DEFAULT_GRAPH,
                isDerivedFromSameAs = (iter.status and StatementIdIterator.SKIP_ON_REINFER_STATEMENT_STATUS) != 0 //taken from the old proof plugin. No idea why it works
            )
        }
    }

    private fun AbstractRepositoryConnection.getStatementProperties(
        statement: Statement
    ): StatementProperties {
        return this.getStatementProperties(statement.subj, statement.pred, statement.obj, statement.ctx)
    }

    override fun getName(): String {
        return "proof2"
    }

    override fun setDataDir(p0: File?) {
//        TODO("Not yet implemented")
    }

    override fun setLogger(logger: Logger?) {
        this.logger = logger
    }


    override fun setFingerprint(p0: Long) {
//        TODO("Not yet implemented")
    }

    override fun getFingerprint(): Long {
        return 1L //TODO choose an actual not made up fingerprint
    }

    override fun shutdown(p0: ShutdownReason?) {
//        TODO("Not yet implemented")
    }


}


data class StatementProperties(val isExplicit: Boolean, val context: Long, val isDerivedFromSameAs: Boolean)




