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
object ReplicatorMode {
    /**
     * The message simply holds the repliactors's position and the mode to be set.
     */
    data class Message(var pos: BlockPos = BlockPos.ORIGIN,
                       var mode: Int = 0) : IMessage {
        override fun toBytes(buf: ByteBuf) {
            buf.writeLong(pos.toLong())
            buf.writeInt(mode)
        }

        override fun fromBytes(buf: ByteBuf) {
            pos = BlockPos.fromLong(buf.readLong())
            mode = buf.readInt()
        }
    }

    object Handler : IMessageHandler<Message, IMessage> {
        override fun onMessage(msg: Message, ctx: MessageContext): IMessage? {
            val player = ctx.serverHandler.playerEntity
            // We interact with the world, hence schedule our action
            player.serverWorld.addScheduledTask {
                val tile = player.world.getTileEntity(msg.pos)
                if (tile is Replicator) {
                    tile.mode = msg.mode
                }
            }
            return null
        }
    }
}