package io.github.monun.psychics.config

import io.github.Shai0717.kolor.KolorSchema
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.ItemStack
import java.lang.reflect.Field
import java.lang.reflect.ParameterizedType
import java.util.Locale

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class PsychicSetting(
    val path: String = "",
    val required: Boolean = true,
    val minimum: Double = -Double.MAX_VALUE,
    val maximum: Double = Double.MAX_VALUE
)

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class PsychicGroup(val path: String)

object PsychicSettings {
    fun apply(target: Any, root: ConfigurationSection): Boolean {
        var changed = false
        val hierarchy = generateSequence(target.javaClass) { it.superclass }
            .takeWhile { it != Any::class.java }
            .toList()

        for (type in hierarchy.asReversed()) {
            val fields = type.declaredFields.filter { it.isAnnotationPresent(PsychicSetting::class.java) }
            if (fields.isEmpty()) continue

            val group = type.getAnnotation(PsychicGroup::class.java)
            val section = if (group == null) {
                root
            } else {
                root.getConfigurationSection(group.path)
                    ?: root.createSection(group.path).also { changed = true }
            }
            val schema = KolorSchema(section)

            for (field in fields) {
                field.isAccessible = true
                val setting = field.getAnnotation(PsychicSetting::class.java)
                val path = setting.path.ifBlank { field.name.toKebabCase() }
                val current = field.get(target)

                if (!schema.contains(path)) {
                    if (!setting.required) continue
                    requireNotNull(current) { "Required setting '$path' has no default in ${type.name}" }
                    schema.putDefault(path, current)
                    changed = true
                    continue
                }

                val value = if (field.type == ItemStack::class.java) {
                    requireNotNull(schema.item(path, current as? ItemStack)) {
                        "Setting '$path' cannot be null"
                    }
                } else {
                    val raw = requireNotNull(schema.raw(path)) { "Setting '$path' cannot be null" }
                    parse(field, raw, path)
                }
                if (value is Number) {
                    require(value.toDouble().isFinite() && value.toDouble() in setting.minimum..setting.maximum) {
                        "Setting '$path' must be between ${setting.minimum} and ${setting.maximum}"
                    }
                }
                field.set(target, value)
            }
        }
        return changed
    }

    private fun parse(field: Field, raw: Any, path: String): Any {
        val type = field.type
        return when (type) {
            String::class.java -> raw as? String ?: invalid(path, "text")
            java.lang.Boolean.TYPE, java.lang.Boolean::class.java -> raw as? Boolean ?: invalid(path, "a boolean")
            java.lang.Integer.TYPE, java.lang.Integer::class.java ->
                (raw as? Number)?.toInt()?.takeIf { it.toDouble() == (raw as Number).toDouble() } ?: invalid(path, "an integer")
            java.lang.Long.TYPE, java.lang.Long::class.java ->
                (raw as? Number)?.toLong()?.takeIf { it.toDouble() == (raw as Number).toDouble() } ?: invalid(path, "an integer")
            java.lang.Double.TYPE, java.lang.Double::class.java ->
                (raw as? Number)?.toDouble()?.takeIf(Double::isFinite) ?: invalid(path, "a finite number")
            java.lang.Float.TYPE, java.lang.Float::class.java ->
                (raw as? Number)?.toFloat()?.takeIf(Float::isFinite) ?: invalid(path, "a finite number")
            else -> when {
                type.isEnum -> {
                    val name = raw as? String ?: invalid(path, "an enum name")
                    type.enumConstants.firstOrNull { (it as Enum<*>).name.equals(name, true) }
                        ?: throw IllegalArgumentException("Unknown ${type.simpleName} value '$name' at '$path'")
                }
                List::class.java.isAssignableFrom(type) -> parseList(field, raw, path)
                type.isInstance(raw) -> raw
                else -> invalid(path, type.simpleName)
            }
        }
    }

    private fun parseList(field: Field, raw: Any, path: String): List<*> {
        val values = raw as? List<*> ?: invalid(path, "a list")
        val elementType = (field.genericType as? ParameterizedType)
            ?.actualTypeArguments
            ?.firstOrNull()
            ?.let { it as? Class<*> }
            ?: return values
        return values.mapIndexed { index, value ->
            requireNotNull(value) { "Setting '$path[$index]' cannot be null" }
            when {
                elementType.isInstance(value) -> value
                elementType == String::class.java && value is String -> value
                elementType.isEnum && value is String ->
                    elementType.enumConstants.firstOrNull { (it as Enum<*>).name.equals(value, true) }
                        ?: throw IllegalArgumentException("Unknown ${elementType.simpleName} value '$value' at '$path[$index]'")
                else -> invalid("$path[$index]", elementType.simpleName)
            }
        }
    }

    private fun invalid(path: String, expected: String): Nothing =
        throw IllegalArgumentException("Setting '$path' must be $expected")

    private fun String.toKebabCase(): String =
        replace(Regex("([a-z0-9])([A-Z])"), "$1-$2").lowercase(Locale.ROOT)
}
