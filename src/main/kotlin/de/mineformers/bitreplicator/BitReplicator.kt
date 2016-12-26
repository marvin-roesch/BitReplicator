package de.mineformers.bitreplicator

import de.mineformers.bitreplicator.block.Replicator
import de.mineformers.bitreplicator.block.ReplicatorBlock
import de.mineformers.bitreplicator.client.gui.SlotFontRenderer
import de.mineformers.bitreplicator.network.GuiHandler
import de.mineformers.bitreplicator.network.ReplicatorMode
import de.mineformers.bitreplicator.network.TrashBits
import mod.chiselsandbits.core.ChiselsAndBits
import net.minecraft.block.Block
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.block.model.ModelResourceLocation
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.IReloadableResourceManager
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.item.Item
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.client.event.TextureStitchEvent
import net.minecraftforge.client.model.ModelLoader
import net.minecraftforge.client.model.obj.OBJLoader
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.Mod.EventHandler
import net.minecraftforge.fml.common.SidedProxy
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.network.NetworkRegistry
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper
import net.minecraftforge.fml.common.registry.GameRegistry
import net.minecraftforge.fml.relauncher.Side
import org.apache.logging.log4j.LogManager
import net.minecraft.init.Blocks as VBlocks
import net.minecraft.init.Items as VItems

/**
 * Main entry point for Vanilla Immersion
 */
@Mod(modid = BitReplicator.MODID,
     name = BitReplicator.MOD_NAME,
     version = BitReplicator.VERSION,
     acceptedMinecraftVersions = "*",
     dependencies = "required-after:Forge;required-after:chiselsandbits",
     updateJSON = "@UPDATE_URL@",
     modLanguageAdapter = "de.mineformers.bitreplicator.KotlinAdapter")
object BitReplicator {
    const val MOD_NAME = "Bit Replicator"
    const val MODID = "bitreplicator"
    const val VERSION = "@VERSION@"

    /**
     * Proxy for client- or server-specific code
     */
    @SidedProxy
    lateinit var PROXY: Proxy
    /**
     * SimpleImpl network instance for client-server communication
     */
    val NETWORK by lazy {
        SimpleNetworkWrapper(MODID)
    }
    /**
     * Logger for the mod to inform people about things.
     */
    val LOG by lazy {
        LogManager.getLogger(MODID)
    }
    /**
     * Temporary creative tab for the mod
     * To be removed once substitutions are fixed
     */
    val CREATIVE_TAB = object : CreativeTabs(MODID) {
        override fun getTabIconItem() = Item.getItemFromBlock(Blocks.REPLICATOR)
    }

    /**
     * Runs during the pre-initialization phase of mod loading, registers blocks, items etc.
     */
    @EventHandler
    fun preInit(event: FMLPreInitializationEvent) {
        Blocks.init()
        Items.init()

        NETWORK.registerMessage(ReplicatorMode.Handler, ReplicatorMode.Message::class.java,
                                0, Side.SERVER)
        NETWORK.registerMessage(TrashBits.Handler, TrashBits.Message::class.java,
                                1, Side.SERVER)
        NetworkRegistry.INSTANCE.registerGuiHandler(this, GuiHandler())

        PROXY.preInit(event)
    }

    /**
     * Runs during the initialization phase of mod loading, registers recipes etc.
     */
    @EventHandler
    fun init(event: FMLInitializationEvent) {
        LOG.info("Adding recipes...")
        GameRegistry.addShapedRecipe(ItemStack(Blocks.REPLICATOR),
                                     "ici",
                                     "ibi",
                                     "iii",
                                     'i', ItemStack(VItems.IRON_INGOT),
                                     'c', ItemStack(ChiselsAndBits.getItems().itemChiselDiamond),
                                     'b', ItemStack(ChiselsAndBits.getItems().itemBitBag))
        PROXY.init(event)
    }

    /**
     * Holder object for all blocks introduced by this mod.
     * Due to be reworked once MinecraftForge's substitutions are fixed.
     * Blocks utilize lazy initialization to guarantee they're not created before first access in [Blocks.init]
     */
    object Blocks {
        val REPLICATOR by lazy {
            ReplicatorBlock()
        }

        /**
         * Initializes and registers blocks and related data
         */
        fun init() {
            register(REPLICATOR)

            GameRegistry.registerTileEntity(Replicator::class.java, "$MODID:replicator")
        }

        /**
         * Helper method for registering blocks.
         *
         * @param block       the block to register
         * @param itemFactory a function reference serving as factory for the block's item representation,
         *                     may be null if no item is necessary
         */
        private fun register(block: Block, itemFactory: ((Block) -> Item)? = ::ItemBlock) {
            GameRegistry.register(block)
            val item = itemFactory?.invoke(block)
            if (item != null) {
                item.registryName = block.registryName
                GameRegistry.register(item)
            }
        }
    }

    object Items {

        /**
         * Initializes and registers items and related data
         */
        fun init() {
        }
    }

    /**
     * Interface for client & server proxies
     */
    interface Proxy {
        /**
         * Performs pre-initialization tasks for the proxy's side.
         */
        fun preInit(event: FMLPreInitializationEvent)

        fun init(event: FMLInitializationEvent)
    }

    /**
     * The client proxy serves as client-specific interface for the mod.
     * Code that may only be accessed on the client should be put here.
     */
    class ClientProxy : Proxy {
        companion object {
            val slotFontRenderer by lazy {
                SlotFontRenderer()
            }
            lateinit var iconModeSingle: TextureAtlasSprite
            lateinit var iconModeAll: TextureAtlasSprite
        }

        override fun preInit(event: FMLPreInitializationEvent) {
            OBJLoader.INSTANCE.addDomain(MODID) // We don't have OBJ models yet, but maybe in the future?

            setItemModel(Blocks.REPLICATOR, 0, "$MODID:replicator")
            MinecraftForge.EVENT_BUS.register(this)
        }

        override fun init(event: FMLInitializationEvent) {
            val resourceManager = (Minecraft.getMinecraft().resourceManager as IReloadableResourceManager)
            resourceManager.registerReloadListener(ClientProxy.slotFontRenderer)
        }

        @SubscribeEvent
        fun onTextureStitch(event: TextureStitchEvent.Pre) {
            ClientProxy.iconModeSingle = event.map.registerSprite(ResourceLocation(MODID, "icons/mode_single"))
            ClientProxy.iconModeAll = event.map.registerSprite(ResourceLocation(MODID, "icons/mode_all"))
        }

        /**
         * Sets the model associated with the item representation of a block.
         * Assumes the default variant is to be used ("inventory").
         */
        private fun setItemModel(block: Block, meta: Int, resource: String) {
            setItemModel(Item.getItemFromBlock(block)!!, meta, resource)
        }

        /**
         * Sets the model associated with an item.
         * Assumes the default variant is to be used ("inventory").
         */
        private fun setItemModel(item: Item, meta: Int, resource: String) {
            ModelLoader.setCustomModelResourceLocation(item,
                                                       meta,
                                                       ModelResourceLocation(resource, "inventory"))
        }
    }

    /**
     * The server proxy serves as server-specific interface for the mod.
     * Code that may only be accessed on the sver should be put here.
     */
    class ServerProxy : Proxy {
        override fun preInit(event: FMLPreInitializationEvent) {
        }

        override fun init(event: FMLInitializationEvent) {
        }
    }
}
