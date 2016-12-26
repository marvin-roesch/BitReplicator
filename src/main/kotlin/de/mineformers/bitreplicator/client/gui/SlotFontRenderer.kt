package de.mineformers.bitreplicator.client.gui

import de.mineformers.bitreplicator.client.getLocale
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.FontRenderer
import net.minecraft.util.ResourceLocation
import java.text.NumberFormat

class SlotFontRenderer : FontRenderer(
    Minecraft.getMinecraft().gameSettings,
    ResourceLocation("textures/font/ascii.png"),
    Minecraft.getMinecraft().textureManager, false) {
    private val alternative = Minecraft.getMinecraft().fontRendererObj
    var bypass = false

    override fun getStringWidth(text: String): Int {
        val displayedText = convertText(text)
        if (displayedText != text) {
            val oldFlag = alternative.unicodeFlag
            alternative.unicodeFlag = true
            val result = alternative.getStringWidth(displayedText)
            alternative.unicodeFlag = oldFlag
            return result
        }
        return super.getStringWidth(displayedText)
    }

    override fun drawString(text: String, x: Float, y: Float, color: Int, dropShadow: Boolean): Int {
        val displayedText = convertText(text)
        if (displayedText != text) {
            val oldFlag = alternative.unicodeFlag
            alternative.unicodeFlag = true
            val result = alternative.drawString(displayedText, x, y, color, dropShadow)
            alternative.unicodeFlag = oldFlag
            return result
        }
        return super.drawString(displayedText, x, y, color, dropShadow)
    }

    private fun convertText(text: String): String {
        if (bypass)
            return text
        if (!text.matches(Regex("\\d+")))
            return text
        val number = text.toInt()
        if (number < 100)
            return text
        if (number < 1000)
            return " " + text
        val numberFormat = NumberFormat.getInstance(Minecraft.getMinecraft().getLocale())
        numberFormat.maximumFractionDigits = 0
        if (number < 100000) {
            numberFormat.maximumFractionDigits = 1
        }
        if (number < 10000) {
            numberFormat.maximumFractionDigits = 2
        }
        return numberFormat.format(number / 1000.0) + "k"
    }
}