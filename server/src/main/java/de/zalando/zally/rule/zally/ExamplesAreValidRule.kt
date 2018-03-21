package de.zalando.zally.rule.zally

import com.fasterxml.jackson.core.JsonPointer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ContainerNode
import com.fasterxml.jackson.databind.node.MissingNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.fge.jsonschema.cfg.ValidationConfiguration
import com.github.fge.jsonschema.core.report.ProcessingMessage
import com.github.fge.jsonschema.format.draftv3.DateAttribute
import com.github.fge.jsonschema.library.DraftV4Library
import com.github.fge.jsonschema.main.JsonSchemaFactory
import de.zalando.zally.rule.Context
import de.zalando.zally.rule.api.Violation
import de.zalando.zally.util.ast.JsonPointers
import io.swagger.models.Model
import io.swagger.util.Json

class ExamplesAreValidRule {

    private val validator =
            JsonSchemaFactory
                    .newBuilder()
                    .setValidationConfiguration(
                            ValidationConfiguration
                                    .newBuilder()
                                    .setDefaultLibrary(
                                            "http://some.site/myschema#",
                                            DraftV4Library
                                                    .get()
                                                    .thaw()
                                                    .addFormatAttribute(
                                                            "date",
                                                            DateAttribute.getInstance())
                                                    .freeze())
                                    .freeze())
                    .freeze()
                    .validator!!

    private val mapper = Json.mapper()!!

    fun validate(context: Context): List<Violation> {
        val root = mapper.nodeFactory.objectNode()
        root.set("components", json(context.api.components))

        val history = mutableListOf<JsonPointer>()

        return context.api.paths.orEmpty().flatMap {
            it.value.readOperationsMap().orEmpty().flatMap {
                it.value.parameters.orEmpty().flatMap {
                    validateAnyWithSchemaAndExamples(history, root, json(it), context.pointerForValue(it)!!)
                } +
                it.value.requestBody?.content.orEmpty().flatMap {
                    validateAnyWithSchemaAndExamples(history, root, json(it.value), context.pointerForValue(it.value)!!)
                } +
                it.value.responses.orEmpty().flatMap {
                    it.value.content.orEmpty().flatMap {
                        validateAnyWithSchemaAndExamples(history, root, json(it.value), context.pointerForValue(it.value)!!)
                    }
                }
            }
        }
    }

    private fun validateAnyWithSchemaAndExamples(history: MutableList<JsonPointer>, root: JsonNode, json: JsonNode, pointer: JsonPointer): List<Violation> {
        val schema = json.get("schema") as ObjectNode
        return validateSchema(history, root, schema, pointer) +
                validateSchemaValues(root, schema, json.get("examples"), pointer + "examples") +
                validateSchemaValue(root, schema, json.get("example"), pointer + "example")
    }

    private fun validateSchema(history: MutableList<JsonPointer>, root: JsonNode, schema: ObjectNode, pointer: JsonPointer): List<Violation> {
        val (resolvedSchema, resolvedPointer) = resolve(root, schema, pointer)

        if (history.contains(resolvedPointer)) {
            return emptyList()
        }
        history.add(resolvedPointer)

        return validateSchemaValue(root, resolvedSchema, resolvedSchema.remove("example"), resolvedPointer + "schema" + "example") +
            validateSchemaValue(root, resolvedSchema, resolvedSchema.get("default"), resolvedPointer + "schema" + "default") +
            when (resolvedSchema.path("type").asText()) {
                "array" -> validateArraySchema(history, root, resolvedSchema, resolvedPointer)
                "object" -> validateObjectSchema(history, root, resolvedSchema, resolvedPointer)
                else -> emptyList()
            }
    }

    private fun resolve(root: JsonNode, schema: ObjectNode, pointer: JsonPointer): Pair<ObjectNode, JsonPointer> {
        val ref= schema.get("\$ref")?.asText()
        return when (ref) {
            null -> Pair(schema, pointer)
            else -> {
                val p = JsonPointer.compile(ref.substring(1))
                val s = root.at(p) as ObjectNode
                Pair(s, p)
            }
        }
    }

    private fun validateArraySchema(history: MutableList<JsonPointer>, root: JsonNode, schema: ObjectNode, pointer: JsonPointer?): List<Violation> {
        return validateSchema(history, root, schema.path("items") as ObjectNode, pointer + "items")
    }

    private fun validateObjectSchema(history: MutableList<JsonPointer>, root: JsonNode, schema: ObjectNode, pointer: JsonPointer?): List<Violation> {
        val properties = schema.path("properties")
        return properties.fields().asSequence().asIterable().flatMap {
            validateSchema(history, root, it.value as ObjectNode, pointer + "properties" + it.key)
        }
    }

    private fun validateSchemaValues(root: JsonNode, schema: JsonNode, values: JsonNode?, pointer: JsonPointer): List<Violation> {
        return values
                ?.fields()
                ?.asSequence()
                ?.asIterable()
                ?.flatMap {
                    validateSchemaValue(root, schema, it.value, pointer + it.key)
                }.orEmpty()
    }

    private fun validateSchemaValue(root: JsonNode, schema: JsonNode, value: JsonNode?, pointer: JsonPointer): List<Violation> = value
            ?.let { validator.validateUnchecked(schema, value) }
            ?.map { toValidationMessage(it, schema, pointer) }
            .orEmpty()

    // duplicated from JsonSchemaValidator
    private fun toValidationMessage(processingMessage: ProcessingMessage, schema: JsonNode, pointer: JsonPointer): Violation {
        val node = processingMessage.asJson()
        val keyword = node.path("keyword").textValue()
        val message = node.path("message").textValue()
        val pointer = node.at("/instance/pointer").textValue()
                .let { pointer.append(JsonPointer.compile(it)) }

        return when (keyword) {
            "oneOf", "anyOf" -> createValidationMessageWithSchemaRefs(node, schema, message, pointer, keyword)
            "additionalProperties" -> createValidationMessageWithSchemaPath(node, message, pointer)
            else -> Violation(message, emptyList(), pointer)
        }
    }

    // duplicated from JsonSchemaValidator
    private fun createValidationMessageWithSchemaRefs(node: JsonNode, schema: JsonNode, message: String, pointer: JsonPointer, keyword: String): Violation {
        val schemaPath = node.at("/schema/pointer").textValue()
        return if (!schemaPath.isNullOrBlank()) {
            val schemaRefNodes = schema.at("$schemaPath/$keyword")
            val schemaRefs = schemaRefNodes
                    .map { it.path("\$ref") }
                    .filterNot(JsonNode::isMissingNode)
                    .joinToString("; ", transform = JsonNode::textValue)
            Violation(message + schemaRefs, pointer)
        } else {
            Violation(message, pointer)
        }
    }

    // duplicated from JsonSchemaValidator
    private fun createValidationMessageWithSchemaPath(node: JsonNode, message: String, pointer: JsonPointer): Violation {
        val schemaPath = node.at("/schema/pointer").textValue()
        return Violation(message + schemaPath, pointer)
    }

    private fun json(any: Any): JsonNode = mapper.convertValue(any, JsonNode::class.java)

    private operator fun JsonPointer?.plus(unescaped: String): JsonPointer {
        return when (this) {
            null -> JsonPointers.escape(unescaped)
            else -> this.append(JsonPointers.escape(unescaped))
        }
    }
}
