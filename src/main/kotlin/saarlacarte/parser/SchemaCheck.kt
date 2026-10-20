package saarlacarte.parser

import com.github.erosb.jsonsKema.JsonParseException
import com.github.erosb.jsonsKema.SchemaLoader
import com.github.erosb.jsonsKema.Validator

/**
Checks raw JSON text against a schema from the given resources
Always pass a path
 */
object SchemaCheck {

    /** True when JSON is well-formed and matches the schema*/
    fun fits(json: String, schemaPath: String): Boolean {
        val schema = SchemaLoader.forURL(schemaPath).load()
        return try {
            Validator.forSchema(schema).validate(json) == null
        } catch (ignored: JsonParseException) {
            false
        }
    }
}
