package de.mineformers.bitreplicator.network

import de.mineformers.bitreplicator.block.Replicator
import de.mineformers.bitreplicator.block.ReplicatorContainer
import de.mineformers.bitreplicator.client.gui.ReplicatorGui
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraftforge.fml.common.network.IGuiHandler
import net.minecraftforge.items.CapabilityItemHandler
import net.minecraftforge.items.IItemHandlerModifiable

/**
 * GUI Handler for Vanilla Immersion, currently only used for JEI integration.
 */
class GuiHandler : IGuiHandler {
    override fun getClientGuiElement(ID: Int, player: EntityPlayer, world: World, x: Int, y: Int, z: Int): Any? {
        val replicator = world.getTileEntity(BlockPos(x, y, z)) as Replicator
        val inventory = replicator.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)
        return ReplicatorGui(player.inventory, inventory as IItemHandlerModifiable, replicator)
    }

    override fun getServerGuiElement(ID: Int, player: EntityPlayer, world: World, x: Int, y: Int, z: Int): Any? {
        val replicator = world.getTileEntity(BlockPos(x, y, z)) as Replicator
        val inventory = replicator.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null)
        return ReplicatorContainer(player.inventory, inventory as IItemHandlerModifiable)
    }
}