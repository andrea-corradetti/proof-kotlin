import com.ontotext.trree.AbstractInferencer
import com.ontotext.trree.AbstractRepositoryConnection
import com.ontotext.trree.sdk.Request
import com.ontotext.trree.sdk.RequestContext
import kotlin.properties.Delegates

class ProofContext: RequestContext {
    private var request: Request? = null

    var repositoryConnection by Delegates.notNull<AbstractRepositoryConnection>()
    var inferencer by Delegates.notNull<AbstractInferencer>()

    public val attributes = HashMap<String, Any>()
    override fun getRequest(): Request? = request
    override fun setRequest(value: Request?) {
        request = value
    }
}

enum class NamedInferenceContextKey(val key: String) {
    GRAPH_NAMES("graphNames")
}