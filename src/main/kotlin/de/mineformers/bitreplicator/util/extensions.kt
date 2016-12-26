@file:JvmName("VanillaExtensions")

package de.mineformers.bitreplicator.util

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.Language
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.ResourceLocation
import net.minecraft.world.WorldServer
import net.minecraftforge.fml.relauncher.ReflectionHelper
import java.util.*

/**
 * Synchronizes a tile entity to all clients interested, using its update packet.
 */
fun TileEntity.sync() {
    // This only makes sense for the server side
    if (world !is WorldServer)
        return
    val packet = updatePacket
    val manager = (world as WorldServer).playerChunkMap
    // Send the packet to all player's watching the TE's chunk
    for (player in world.playerEntities)
        if (manager.isPlayerWatchingChunk(player as EntityPlayerMP, pos.x shr 4, pos.z shr 4))
            player.connection.sendPacket(packet)
}

private val regionField = ReflectionHelper.findField(Language::class.java, "field_135037_b", "region")

fun Minecraft.getLocale(): Locale {
    val language = this.languageManager.currentLanguage
    regionField.isAccessible = true
    return Locale(language.languageCode, regionField.get(language) as String)
}

fun ItemStack.writeToNBTWithIntSize(nbt: NBTTagCompound): NBTTagCompound {
    this.writeToNBT(nbt)
    nbt.setInteger("Count", stackSize)
    return nbt
}

fun ItemStack.readFromNBTWithIntSize(nbt: NBTTagCompound) {
    this.readFromNBT(nbt)
    this.stackSize = nbt.getInteger("Count")
}