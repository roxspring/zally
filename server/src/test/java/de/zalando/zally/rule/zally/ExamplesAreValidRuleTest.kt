package de.zalando.zally.rule.zally

import de.zalando.zally.rule.Context
import de.zalando.zally.rule.ZallyAssertions.Companion.assertThat
import org.junit.Test

class ExamplesAreValidRuleTest {

    val rule = ExamplesAreValidRule()

    @Test
    fun test1() {
        val content = """
            openapi: '3.0.0'
            info:
              title: Things API
              version: 1.0.0
            paths:
              /things:
                post:
                  description: Description of /things
                  parameters:
                  - name: p
                    in: query
                    description: Parameter p
                    example: "ParameterExample!!"
                    required: true
                    schema:
                      type: string
                      format: uuid
                      default: "SchemaDefault!!"
                      example: "SchemaExample!!"
                  responses:
                    200:
                      description: Description of 200 response
            """.trimIndent()

        val context = Context.createOpenApiContext(content)!!
        val violations = rule.validate(context)

        assertThat(violations)
                .descriptionsAllEqualTo("input string \"ParameterExample!!\" is not a valid UUID")
                .pointersEqualTo("/paths/~1things/post/parameters/0/example")
    }

    @Test
    fun test2() {
        val content = """
            openapi: '3.0.0'
            info:
              title: Things API
              version: 1.0.0
            paths:
              /things:
                post:
                  description: Description of /things
                  parameters:
                    - "${'$'}ref": "#/components/parameters/P"
                  responses:
                    200:
                      description: Description of 200 response
            components:
              parameters:
                P:
                  name: p
                  in: query
                  description: Parameter p
                  required: true
                  schema:
                    type: string
                    format: uuid
                    default: "SchemaDefault!!"
                    example: "SchemaExample!!"
                  example: "ParameterExample!!"
            """.trimIndent()
        println(content)

        val context = Context.createOpenApiContext(content)!!
        val violations = rule.validate(context)

        assertThat(violations)
                .descriptionsAllEqualTo("input string \"ParameterExample!!\" is not a valid UUID")
//                .pointersEqualTo("/paths/~1things/post/parameters/0/example")//FIXME inconsistent pointer
    }

    @Test
    fun body() {
        val content = """
            openapi: '3.0.0'
            info:
              title: Things API
              version: 1.0.0
            paths:
              /things:
                post:
                  description: Description of /things
                  requestBody:
                    description: Pet to add to the store
                    required: true
                    content:
                      application/json:
                        schema:
                          "${'$'}ref": '#/components/schemas/NewThing'
                        examples:
                          user:
                            summary: User Example
                            value: "Hello World!"
                  responses:
                    200:
                      description: Description of 200 response
            components:
              schemas:
                NewThing:
                  required:
                    - name
                  properties:
                    name:
                      type: string
                    tag:
                      type: string
            """.trimIndent()
        println(content)

        val context = Context.createOpenApiContext(content)!!
        val violations = rule.validate(context)

        assertThat(violations)
                .descriptionsAllEqualTo("object has missing required properties ([\"name\"])")
                .pointersEqualTo("/paths/~1things/post/requestBody/content/application~1json/examples/user")
    }

    @Test
    fun recurse() {
        val content = """
            openapi: '3.0.0'
            info:
              title: Things API
              version: 1.0.0
            paths:
              /things:
                get:
                  description: Description of /things
                  responses:
                    '200':
                      description: Description of 200 response
                      content:
                        "application/json":
                          schema:
                            ${'$'}ref: "#/components/schemas/ThingNode"
                          examples:
                            Root:
                            - id: 0000
                              title: "The Root Node"
            components:
              schemas:
                ThingNode:
                  type: object
                  properties:
                    id:
                      type: string
                      format: uuid
                      example: "7dafeb6e-49b2-463f-a360-5ce65f348503"
                    title:
                      type: string
                      default: "Untitled"
                      example: "A Parent Node"
                    modified:
                      type: string
                      format: date-time
                      default: now
                      example: "10 minutes ago"
                    children:
                      type: array
                      items:
                        ${'$'}ref: "#/components/schemas/ThingNode"
                      example:
                      - id: "4321"
                        name: "A Child Node"
                      default: []
            """.trimIndent()

        val context = Context.createOpenApiContext(content)!!
        val violations = rule.validate(context)

        assertThat(violations)
                .descriptionsAllEqualTo("JSON Reference \"#/components/schemas/ThingNode\" cannot be resolved")
                .pointersEqualTo(
                        "/paths/~1things/get/responses/200/content/application~1json/properties/children/schema/example",
                        "/components/schemas/ThingNode/properties/children/schema/example")
    }
}
