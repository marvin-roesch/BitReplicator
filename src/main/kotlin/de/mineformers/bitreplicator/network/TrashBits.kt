package de.mineformers.bitreplicator.network

import de.mineformers.bitreplicator.block.Replicator
import io.netty.buffer.ByteBuf
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

/**
 * Message and handler for notifying the server about a mode change in a replicator.
 */
object TrashBits {
    /**
     * The message simply holds the repliactors's position and the mode to be set.
     */
    data class Message(var pos: BlockPos = BlockPos.ORIGIN) : IMessage {
        override fun toBytes(buf: ByteBuf) {
            buf.writeLong(pos.toLong())
        }

        override fun fromBytes(buf: ByteBuf) {
            pos = BlockPos.fromLong(buf.readLong())
        }
    }

    object Handler : IMessageHandler<Message, IMessage> {
        override fun onMessage(msg: Message, ctx: MessageContext): IMessage? {
            val player = ctx.serverHandler.playerEntity
            // We interact with the world, hence schedule our action
            player.serverWorld.addScheduledTask {
                val tile = player.world.getTileEntity(msg.pos)
                if (tile is Replicator) {
                    tile.trashBits()
                }
            }
            return null
        }
    }
}