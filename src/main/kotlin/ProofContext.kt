import com.ontotext.trree.AbstractInferencer
import com.ontotext.trree.AbstractRepositoryConnection
import com.ontotext.trree.sdk.Request
import com.ontotext.trree.sdk.RequestContext
import com.ontotext.trree.sdk.SystemPluginOptions
import kotlin.properties.Delegates

class ProofContext(request: Request?) : RequestContext {
    private var request: Request? = null

    var repositoryConnection by Delegates.notNull<AbstractRepositoryConnection>()
    var inferencer by Delegates.notNull<AbstractInferencer>()

    val iterators = HashMap<String, ExplainIterator>()
    override fun getRequest(): Request? = request
    override fun setRequest(value: Request?) {
        request = value
    }

    init {
        setRequest(request)
        val options = request?.options
        if (options != null && options is SystemPluginOptions) {
            options.getOption(SystemPluginOptions.Option.ACCESS_INFERENCER).let {
                if (it is AbstractInferencer) {
                    inferencer = it
                }
            }

            options.getOption(SystemPluginOptions.Option.ACCESS_REPOSITORY_CONNECTION).let {
                if (it is AbstractRepositoryConnection) {
                    repositoryConnection = it
                }
            }
        }
    }
}


enum class NamedInferenceContextKey(val key: String) {
    GRAPH_NAMES("graphNames")
}