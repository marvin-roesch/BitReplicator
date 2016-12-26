package de.mineformers.bitreplicator.client.gui

import de.mineformers.bitreplicator.BitReplicator
import de.mineformers.bitreplicator.BitReplicator.MODID
import de.mineformers.bitreplicator.block.Replicator
import de.mineformers.bitreplicator.block.ReplicatorContainer
import de.mineformers.bitreplicator.network.ReplicatorMode
import de.mineformers.bitreplicator.network.TrashBits
import de.mineformers.bitreplicator.client.getLocale
import mod.chiselsandbits.bitbag.GuiIconButton
import mod.chiselsandbits.core.ClientSide
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.FontRenderer
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.inventory.GuiContainer
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.I18n
import net.minecraft.entity.player.InventoryPlayer
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.relauncher.ReflectionHelper
import net.minecraftforge.items.IItemHandlerModifiable
import java.text.NumberFormat

/**
 * ${JDOC}
 */
class ReplicatorGui(private val playerInventory: InventoryPlayer, private val inventory: IItemHandlerModifiable,
                    private val replicator: Replicator) :
    GuiContainer(ReplicatorContainer(playerInventory, inventory)) {
    private inner class ModeButton : GuiIconButton(2, guiLeft + 33, guiTop + 15, "mode",
                                                   BitReplicator.ClientProxy.iconModeAll) {
        private var lastMode = 0

        fun drawLabel(x: Int, y: Int) {
            val modeText = if (replicator.mode == 0) "all" else "single"
            drawHoveringText(listOf(I18n.format("label.$MODID.mode.$modeText")), x, y)
        }

        fun toggle() {
            BitReplicator.NETWORK.sendToServer(ReplicatorMode.Message(replicator.pos, (replicator.mode + 1) % 2))
        }

        override fun drawButton(mc: Minecraft?, mouseX: Int, mouseY: Int) {
            if (lastMode != replicator.mode) {
                setIcon(if (replicator.mode == 0) BitReplicator.ClientProxy.iconModeAll
                        else BitReplicator.ClientProxy.iconModeSingle)
                lastMode = replicator.mode
            }
            super.drawButton(mc, mouseX, mouseY)
        }

        private fun setIcon(icon: TextureAtlasSprite) {
            ReflectionHelper.setPrivateValue(GuiIconButton::class.java, this, icon, "icon")
        }
    }

    private lateinit var trashBtn: GuiButton
    private lateinit var modeBtn: ModeButton

    init {
        xSize = 225
        ySize = 204
    }

    override fun initGui() {
        super.initGui()

        trashBtn = GuiIconButton(1, guiLeft + 33, guiTop + 53, "help.trash", ClientSide.trashIcon)
        modeBtn = ModeButton()
        buttonList.add(trashBtn)
        buttonList.add(modeBtn)
        fontRendererObj = BitReplicator.ClientProxy.slotFontRenderer
    }

    override fun actionPerformed(button: GuiButton) {
        when (button.id) {
            1 -> BitReplicator.NETWORK.sendToServer(TrashBits.Message(replicator.pos))
            2 -> modeBtn.toggle()
        }
    }

    override fun drawGuiContainerBackgroundLayer(partialTicks: Float, mouseX: Int, mouseY: Int) {
        this.mc.textureManager.bindTexture(ResourceLocation(MODID, "textures/gui/replicator.png"))
        val x = (this.width - this.xSize) / 2
        val y = (this.height - this.ySize) / 2
        this.drawTexturedModalRect(x, y, 0, 0, this.xSize, this.ySize)

        val maxEnergy = replicator.getMaxEnergyStored(null)
        val energy = replicator.getEnergyStored(null)
        val height = (energy.toFloat() / maxEnergy * 100).toInt()
        val offset = 100 - height
        this.drawTexturedModalRect(x + 6, y + 8 + offset, 225, 1 + offset, 22, height)
    }

    override fun drawGuiContainerForegroundLayer(mouseX: Int, mouseY: Int) {
        BitReplicator.ClientProxy.slotFontRenderer.bypass = true
        this.fontRendererObj.drawString(I18n.format("tile.$MODID.replicator.name"), 57, 5, 0x404040)
        this.fontRendererObj.drawString(playerInventory.displayName.unformattedText, 57, this.ySize - 93, 0x404040)
        val localX = mouseX - (this.width - this.xSize) / 2
        val localY = mouseY - (this.height - this.ySize) / 2
        if (localX in 7..27 && localY in 8..108) {
            val numberFormat = NumberFormat.getIntegerInstance(Minecraft.getMinecraft().getLocale())
            drawHoveringText(listOf(I18n.format("label.$MODID.energy.stored"),
                                    I18n.format("label.$MODID.energy.unit",
                                                numberFormat.format(replicator.getEnergyStored(null)))),
                             localX, localY)
        }
        if (modeBtn.isMouseOver) {
            modeBtn.drawLabel(localX, localY)
        }
        if (trashBtn.isMouseOver) {
            drawHoveringText(listOf(I18n.format("label.$MODID.trash")),
                             localX, localY)
        }
        BitReplicator.ClientProxy.slotFontRenderer.bypass = false
    }

    override fun drawHoveringText(textLines: MutableList<String>?, x: Int, y: Int, font: FontRenderer?) {
        BitReplicator.ClientProxy.slotFontRenderer.bypass = true
        super.drawHoveringText(textLines, x, y, font)
        BitReplicator.ClientProxy.slotFontRenderer.bypass = false
    }
}