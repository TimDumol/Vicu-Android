package com.rendyhd.vicu.data.repository

import com.rendyhd.vicu.auth.authTestJsonHeaders
import com.rendyhd.vicu.util.DateUtils
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * A tiny in-memory Vikunja for the tasks the routine code touches: list and search, get, create,
 * merge-patch update and delete. Every request is recorded, and [failure] can turn chosen
 * requests into error responses.
 */
class FakeTaskServer {

    class Row(
        val id: Long,
        var title: String,
        var description: String,
        var done: Boolean,
        var projectId: Long,
        /** When the task was made; empty for seeded rows unless a test sets it. */
        var created: String = "",
        /** The user who created it (`created_by`); 0 leaves the field out. */
        var createdBy: Long = 0,
    )

    data class Recorded(val method: String, val path: String, val query: Map<String, String>, val body: String?) {
        val bodyJson: JsonObject? get() = body?.let { Json.parseToJsonElement(it).jsonObject }
        override fun toString() = "$method $path"
    }

    val rows = LinkedHashMap<Long, Row>()
    val requests = mutableListOf<Recorded>()
    private var nextId = 1000L

    /** A status to answer a request with instead of handling it; null lets it through. */
    var failure: (Recorded) -> HttpStatusCode? = { null }

    /** Runs before a request is handled, to model something else happening on the server. */
    var beforeRequest: (Recorded) -> Unit = {}

    /** Runs after a create or update changed [Row], before the answer is sent (a misbehaving server). */
    var afterWrite: (Row) -> Unit = {}

    fun seed(
        id: Long,
        title: String,
        description: String,
        done: Boolean = true,
        projectId: Long = 5,
        created: String = "",
        createdBy: Long = 0,
    ): Row = Row(id, title, description, done, projectId, created, createdBy).also { rows[id] = it }

    /** The user new tasks are created by; 0 leaves `created_by` out. */
    var currentUserId: Long = 0

    fun row(id: Long): Row = checkNotNull(rows[id]) { "No task $id on the fake server" }

    /** The tasks whose description holds a routine archive marker. */
    fun archiveRows(): List<Row> = rows.values.filter { it.description.contains("<!-- vicu-routine:archive:") }

    /** The paths of the JSON Patch replace operations the fake keeps no field for (due date, ...). */
    val jsonPatchReplaced = mutableListOf<String>()

    private fun isJsonPatch(request: HttpRequestData): Boolean =
        request.body.contentType?.withoutParameters()?.toString() == "application/json-patch+json"

    fun requestsLike(method: String, pathPrefix: String): List<Recorded> =
        requests.filter { it.method == method && it.path.startsWith(pathPrefix) }

    private fun Row.toJson(): String = buildJsonObject {
        put("id", id)
        put("title", title)
        put("description", description)
        put("done", done)
        put("project_id", projectId)
        if (created.isNotEmpty()) put("created", created)
        if (createdBy > 0L) put("created_by", buildJsonObject { put("id", createdBy) })
    }.toString()

    fun handle(scope: MockRequestHandleScope, request: HttpRequestData): HttpResponseData = with(scope) {
        val path = request.url.encodedPath
        val query = request.url.parameters.entries().associate { it.key to it.value.first() }
        val body = (request.body as? TextContent)?.text
        val recorded = Recorded(request.method.value, path, query, body)
        requests += recorded
        beforeRequest(recorded)
        failure(recorded)?.let { return respond("", it) }

        val taskId = Regex("^/tasks/(\\d+)$").matchEntire(path)?.groupValues?.get(1)?.toLong()
        val createIn = Regex("^/projects/(\\d+)/tasks$").matchEntire(path)?.groupValues?.get(1)?.toLong()
        when {
            request.method.value == "GET" && path == "/tasks" -> {
                var found = rows.values.toList()
                if ("done = true" in query["filter"].orEmpty()) found = found.filter { it.done }
                query["q"]?.lowercase()?.let { needle ->
                    found = found.filter { needle in it.title.lowercase() || needle in it.description.lowercase() }
                }
                respond(
                    """{"items":[${found.joinToString(",") { it.toJson() }}],"total":${found.size},"page":1,"per_page":100,"total_pages":1}""",
                    HttpStatusCode.OK,
                    authTestJsonHeaders,
                )
            }
            request.method.value == "GET" && taskId != null ->
                rows[taskId]?.let { respond(it.toJson(), HttpStatusCode.OK, authTestJsonHeaders) }
                    ?: respond("", HttpStatusCode.NotFound)
            request.method.value == "POST" && createIn != null -> {
                val fields = Json.parseToJsonElement(checkNotNull(body)).jsonObject
                val row = Row(
                    id = nextId++,
                    title = fields["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    description = fields["description"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    done = fields["done"]?.jsonPrimitive?.booleanOrNull ?: false,
                    projectId = createIn,
                    created = DateUtils.nowIso(),
                    createdBy = currentUserId,
                )
                rows[row.id] = row
                afterWrite(row)
                respond(row.toJson(), HttpStatusCode.Created, authTestJsonHeaders)
            }
            request.method.value == "PATCH" && taskId != null && isJsonPatch(request) -> {
                val row = rows[taskId] ?: return@with respond("", HttpStatusCode.NotFound)
                // RFC 6902 as Vikunja applies it: all operations or none, 422 when a test fails.
                val operations = Json.parseToJsonElement(checkNotNull(body)).jsonArray.map { it.jsonObject }
                val tests = operations.filter { it["op"]?.jsonPrimitive?.content == "test" }
                for (test in tests) {
                    val field = test["path"]!!.jsonPrimitive.content.removePrefix("/")
                    val expected = test["value"]?.jsonPrimitive?.contentOrNull
                    val actual = when (field) {
                        "description" -> row.description
                        "title" -> row.title
                        else -> error("The fake server tests only description and title, not $field")
                    }
                    if (actual != expected) {
                        return@with respond(
                            """{"title":"Unprocessable Entity","detail":"Unable to apply patch"}""",
                            HttpStatusCode.UnprocessableEntity,
                            authTestJsonHeaders,
                        )
                    }
                }
                val before = row.toJson()
                for (operation in operations - tests.toSet()) {
                    check(operation["op"]?.jsonPrimitive?.content == "replace") { "Only test and replace: $operation" }
                    val value = operation["value"]
                    when (operation["path"]!!.jsonPrimitive.content.removePrefix("/")) {
                        "title" -> row.title = value!!.jsonPrimitive.content
                        "description" -> row.description = value!!.jsonPrimitive.content
                        "done" -> row.done = value!!.jsonPrimitive.booleanOrNull!!
                        "project_id" -> row.projectId = value!!.jsonPrimitive.content.toLong()
                        else -> jsonPatchReplaced += operation["path"]!!.jsonPrimitive.content
                    }
                }
                afterWrite(row)
                if (row.toJson() == before) {
                    respond("", HttpStatusCode.NotModified)
                } else {
                    respond(row.toJson(), HttpStatusCode.OK, authTestJsonHeaders)
                }
            }
            request.method.value == "PATCH" && taskId != null -> {
                val row = rows[taskId] ?: return@with respond("", HttpStatusCode.NotFound)
                val fields = Json.parseToJsonElement(checkNotNull(body)).jsonObject
                (fields["title"] as? JsonPrimitive)?.contentOrNull?.let { row.title = it }
                (fields["description"] as? JsonPrimitive)?.contentOrNull?.let { row.description = it }
                (fields["done"] as? JsonPrimitive)?.booleanOrNull?.let { row.done = it }
                (fields["project_id"] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()?.let { row.projectId = it }
                afterWrite(row)
                respond(row.toJson(), HttpStatusCode.OK, authTestJsonHeaders)
            }
            request.method.value == "DELETE" && taskId != null ->
                if (rows.remove(taskId) != null) respond("", HttpStatusCode.NoContent) else respond("", HttpStatusCode.NotFound)
            else -> respond("", HttpStatusCode.NotFound)
        }
    }
}
