package de.mineformers.bitreplicator.client

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.Language
import net.minecraftforge.fml.relauncher.ReflectionHelper
import java.util.*

private val regionField = ReflectionHelper.findField(Language::class.java, "field_135037_b", "region")

fun Minecraft.getLocale(): Locale {
    val language = this.languageManager.currentLanguage
    regionField.isAccessible = true
    return Locale(language.languageCode, regionField.get(language) as String)
}