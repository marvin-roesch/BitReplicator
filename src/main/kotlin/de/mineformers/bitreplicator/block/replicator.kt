package de.mineformers.bitreplicator.block

import cofh.api.energy.EnergyStorage
import cofh.api.energy.IEnergyReceiver
import com.google.common.base.Objects
import de.mineformers.bitreplicator.BitReplicator
import de.mineformers.bitreplicator.BitReplicator.CREATIVE_TAB
import de.mineformers.bitreplicator.BitReplicator.MODID
import de.mineformers.bitreplicator.CABAddon
import de.mineformers.bitreplicator.util.Inventories
import de.mineformers.bitreplicator.util.readFromNBTWithIntSize
import de.mineformers.bitreplicator.util.sync
import de.mineformers.bitreplicator.util.writeToNBTWithIntSize
import mod.chiselsandbits.api.APIExceptions
import mod.chiselsandbits.api.ItemType
import mod.chiselsandbits.chiseledblock.NBTBlobConverter
import mod.chiselsandbits.chiseledblock.data.VoxelBlob
import mod.chiselsandbits.core.ChiselsAndBits
import mod.chiselsandbits.helpers.ModUtil
import net.minecraft.block.Block
import net.minecraft.block.BlockHorizontal
import net.minecraft.block.SoundType
import net.minecraft.block.material.MapColor
import net.minecraft.block.material.Material
import net.minecraft.block.state.BlockStateContainer
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.InventoryPlayer
import net.minecraft.init.Blocks
import net.minecraft.inventory.Container
import net.minecraft.inventory.Slot
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemBlockSpecial
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraft.network.NetworkManager
import net.minecraft.network.play.server.SPacketUpdateTileEntity
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.*
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.util.Constants
import net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY
import net.minecraftforge.items.IItemHandlerModifiable
import net.minecraftforge.items.ItemHandlerHelper
import net.minecraftforge.items.ItemStackHandler
import net.minecraftforge.items.SlotItemHandler
import net.minecraftforge.items.wrapper.RangedWrapper

const val BLOCK_BIT_VOLUME = 16 * 16 * 16
const val MAX_BIT_STACK_SIZE = 64

/**
 * Can replicate C&B designs from blocks.
 */
class ReplicatorBlock : Block(Material.IRON, MapColor.IRON) {
    companion object {
        val FACING = BlockHorizontal.FACING!!
    }

    init {
        unlocalizedName = "$MODID.replicator"
        registryName = ResourceLocation(MODID, "replicator")
        setCreativeTab(CREATIVE_TAB)
        defaultState = blockState.baseState.withProperty(FACING, EnumFacing.NORTH)
        soundType = SoundType.METAL
        setHardness(5.0F)
        setResistance(10.0F)
        setHarvestLevel("pickaxe", 2)
    }

    override fun breakBlock(world: World, pos: BlockPos, state: IBlockState) {
        val tile = world.getTileEntity(pos)
        if (tile is Replicator) {
            Inventories.spill(world, pos, tile.inventory)
            world.updateComparatorOutputLevel(pos, this)
        }
        super.breakBlock(world, pos, state)
    }

    override fun onBlockAdded(worldIn: World, pos: BlockPos, state: IBlockState) {
        this.setDefaultFacing(worldIn, pos, state)
    }

    private fun setDefaultFacing(worldIn: World, pos: BlockPos, state: IBlockState) {
        if (!worldIn.isRemote) {
            var facing = state.getValue(FACING)
            val north = worldIn.getBlockState(pos.north())
            val south = worldIn.getBlockState(pos.south())
            val west = worldIn.getBlockState(pos.west())
            val east = worldIn.getBlockState(pos.east())

            if (north.getValue(FACING) == EnumFacing.NORTH && north.isFullBlock && !south.isFullBlock) {
                facing = EnumFacing.SOUTH
            } else if (north.getValue(FACING) == EnumFacing.SOUTH && south.isFullBlock && !north.isFullBlock) {
                facing = EnumFacing.NORTH
            } else if (north.getValue(FACING) == EnumFacing.WEST && west.isFullBlock && !east.isFullBlock) {
                facing = EnumFacing.EAST
            } else if (north.getValue(FACING) == EnumFacing.EAST && east.isFullBlock && !west.isFullBlock) {
                facing = EnumFacing.WEST
            }

            worldIn.setBlockState(pos, north.withProperty(FACING, facing), 2)
        }
    }

    override fun getStateForPlacement(worldIn: World, pos: BlockPos, facing: EnumFacing, hitX: Float, hitY: Float,
                                      hitZ: Float, meta: Int, placer: EntityLivingBase): IBlockState {
        return this.defaultState.withProperty(FACING, placer.horizontalFacing.opposite)
    }

    override fun onBlockPlacedBy(worldIn: World, pos: BlockPos, state: IBlockState, placer: EntityLivingBase,
                                 stack: ItemStack) {
        worldIn.setBlockState(pos, state.withProperty(FACING, placer.horizontalFacing.opposite), 2)
    }

    override fun onBlockActivated(world: World, pos: BlockPos, state: IBlockState, player: EntityPlayer,
                                  hand: EnumHand, heldItem: ItemStack?, side: EnumFacing?, hitX: Float, hitY: Float,
                                  hitZ: Float): Boolean {
        if (!world.isRemote) {
            player.openGui(BitReplicator, 0, world, pos.x, pos.y, pos.z)
        }
        return true
    }

    override fun isOpaqueCube(state: IBlockState) = false

    override fun isFullCube(state: IBlockState) = false

    override fun isSideSolid(state: IBlockState, world: IBlockAccess, pos: BlockPos, side: EnumFacing?): Boolean {
        val rotatedValue =
            if (state.getValue(FACING).axis == EnumFacing.Axis.Z)
                side == EnumFacing.WEST || side == EnumFacing.EAST
            else
                side == EnumFacing.NORTH || side == EnumFacing.SOUTH
        return rotatedValue || side == EnumFacing.UP || side == EnumFacing.DOWN
    }

    override fun hasTileEntity(state: IBlockState) = true

    override fun createTileEntity(world: World?, state: IBlockState?): TileEntity {
        return Replicator()
    }

    override fun getStateFromMeta(meta: Int): IBlockState {
        var facing = EnumFacing.getFront(meta)

        if (facing.axis == EnumFacing.Axis.Y) {
            facing = EnumFacing.NORTH
        }

        return this.defaultState.withProperty(FACING, facing)
    }

    override fun getMetaFromState(state: IBlockState): Int {
        return (state.getValue(FACING) as EnumFacing).index
    }

    override fun withRotation(state: IBlockState, rot: Rotation): IBlockState {
        return state.withProperty(FACING, rot.rotate(state.getValue(FACING)))
    }

    override fun withMirror(state: IBlockState, mirror: Mirror): IBlockState {
        return state.withRotation(mirror.toRotation(state.getValue(FACING)))
    }

    override fun createBlockState(): BlockStateContainer {
        return BlockStateContainer(this, FACING)
    }
}

class Replicator : TileEntity(), IEnergyReceiver, ITickable {
    internal inner class ItemHandler : ItemStackHandler(28) {
        override fun insertItem(slot: Int, stack: ItemStack?, simulate: Boolean): ItemStack? {
            if (slot > 9)
                return stack
            if (slot == 0 && !isValidDesign(stack))
                return stack
            return super.insertItem(slot, stack, simulate)
        }

        fun insertInternal(slot: Int, stack: ItemStack?, simulate: Boolean): ItemStack? {
            return super.insertItem(slot, stack, simulate)
        }

        fun extractInternal(slot: Int, amount: Int, simulate: Boolean): ItemStack? {
            if (amount == 0)
                return null

            validateSlotIndex(slot)

            val existing = this.stacks[slot] ?: return null

            val toExtract = Math.min(amount, getStackLimit(slot, existing))

            if (existing.stackSize <= toExtract) {
                if (!simulate) {
                    this.stacks[slot] = null
                    onContentsChanged(slot)
                }
                return existing
            } else {
                if (!simulate) {
                    this.stacks[slot] = ItemHandlerHelper.copyStackWithSize(existing, existing.stackSize - toExtract)
                    onContentsChanged(slot)
                }

                return ItemHandlerHelper.copyStackWithSize(existing, toExtract)
            }
        }

        override fun getStackLimit(slot: Int, stack: ItemStack?): Int {
            return if (slot in 10..18) BLOCK_BIT_VOLUME * MAX_BIT_STACK_SIZE else super.getStackLimit(slot, stack)
        }

        override fun serializeNBT(): NBTTagCompound {
            val nbtTagList = NBTTagList()
            for (i in stacks.indices) {
                if (stacks[i] != null) {
                    val itemTag = NBTTagCompound()
                    itemTag.setInteger("Slot", i)
                    stacks[i].writeToNBTWithIntSize(itemTag)
                    nbtTagList.appendTag(itemTag)
                }
            }
            val nbt = NBTTagCompound()
            nbt.setTag("Items", nbtTagList)
            nbt.setInteger("Size", stacks.size)
            return nbt
        }

        override fun deserializeNBT(nbt: NBTTagCompound) {
            setSize(if (nbt.hasKey("Size", Constants.NBT.TAG_INT)) nbt.getInteger("Size") else stacks.size)
            val tagList = nbt.getTagList("Items", Constants.NBT.TAG_COMPOUND)
            for (i in 0..tagList.tagCount() - 1) {
                val itemTags = tagList.getCompoundTagAt(i)
                val slot = itemTags.getInteger("Slot")

                if (slot >= 0 && slot < stacks.size) {
                    val stack = ItemStack.loadItemStackFromNBT(itemTags)
                    stack?.readFromNBTWithIntSize(itemTags)
                    stacks[slot] = stack
                }
            }
            onLoad()
        }

        override fun onLoad() {
            val stack = getStackInSlot(0) ?: return
            updateRequirements(stack)
        }

        override fun onContentsChanged(slot: Int) {
            super.onContentsChanged(slot)
            val stack = getStackInSlot(slot)
            if (slot == 0 && stack != null) {
                updateRequirements(stack)
            }
            markDirty()
        }
    }

    private val storage = EnergyStorage(32000)
    internal val inventory = ItemHandler()
    private val inputSlots = RangedWrapper(inventory, 1, 10)
    private val outputSlots = RangedWrapper(inventory, 19, 28)
    private val requirements = mutableListOf<VoxelBlob.TypeRef>()
    private var counter = 0
    var mode = 0
        get() = field
        set(value) {
            field = Math.max(0, Math.min(value, 1))
            markDirty()
            sync()
        }

    override fun update() {
        counter++
        if (counter == 10) {
            counter = 0
            replicate()
        }
        for (i in 1..9) {
            val stack = inventory.getStackInSlot(i) ?: continue
            if (stack.stackSize == 1 && mode == 1)
                continue
            when (CABAddon.api.getItemType(stack)) {
                ItemType.CHISLED_BIT -> consumeChiseledBit(i)
                ItemType.CHISLED_BLOCK -> consumeChiseledBlock(i, stack)
                else -> {
                    val item = stack.item
                    when (item) {
                        is ItemBlock -> consumeItemBlock(i, item.getBlock(), stack)
                        is ItemBlockSpecial -> consumeItemBlock(i, item.block, stack)
                    }
                }
            }
        }
    }

    fun consumeChiseledBit(slot: Int) {
        val requiredEnergy = 8
        if (storage.extractEnergy(requiredEnergy, true) < requiredEnergy)
            return
        val extracted = inventory.extractItem(slot, 1, true)!!
        val remaining = moveToBuffer(extracted, true)
        if (remaining == null) {
            moveToBuffer(extracted, false)
            inventory.extractItem(slot, 1, false)
            storage.extractEnergy(requiredEnergy, false)
        }
    }

    fun consumeChiseledBlock(slot: Int, stack: ItemStack) {
        val bits = ModUtil.getBlobFromStack(stack, null)
        val counts = bits.blockCounts
        val requiredEnergy = counts.size * 8
        if (storage.extractEnergy(requiredEnergy, true) < requiredEnergy)
            return
        if (counts.all { consumeBlockState(Block.getStateById(it.stateId), it.quantity, true) }) {
            counts.forEach { consumeBlockState(Block.getStateById(it.stateId), it.quantity, false) }
            inventory.extractItem(slot, 1, false)
            storage.extractEnergy(requiredEnergy, false)
        }
    }

    fun consumeItemBlock(slot: Int, block: Block, stack: ItemStack) {
        val requiredEnergy = 32
        if (storage.extractEnergy(requiredEnergy, true) < requiredEnergy)
            return
        val metadata = stack.metadata
        val state = block.getStateFromMeta(metadata)
        if (consumeBlockState(state, BLOCK_BIT_VOLUME, false)) {
            inventory.extractItem(slot, 1, false)
            storage.extractEnergy(requiredEnergy, false)
        }
    }

    fun consumeBlockState(state: IBlockState, amount: Int, simulate: Boolean): Boolean {
        if (state.block == Blocks.AIR)
            return true
        try {
            val bit = CABAddon.api.getBitItem(state)
            bit.stackSize = amount
            val remaining = moveToBuffer(bit, true)
            if (remaining == null && !simulate) {
                moveToBuffer(bit, false)
            }
            return remaining == null
        } catch (ex: APIExceptions.InvalidBitItem) {
        }
        return false
    }

    fun trashBits() {
        for (i in 10..18) {
            inventory.setStackInSlot(i, null)
        }
    }

    private fun moveToBuffer(stack: ItemStack, simulate: Boolean): ItemStack? {
        var remaining: ItemStack? = stack
        for (i in 10..18) {
            remaining = inventory.insertInternal(i, remaining, simulate) ?: return null
        }
        return remaining
    }

    fun getBuffer() = (10..18).map { Pair(it, inventory.getStackInSlot(it)) }.filter { it.second != null }

    private fun getResult(): ItemStack? {
        val stack = inventory.getStackInSlot(0) ?: return null
        val tag = stack.tagCompound

        // Detect and provide full blocks if pattern solid full and solid.
        val conv = NBTBlobConverter()
        conv.readChisleData(tag)

        if (ChiselsAndBits.getConfig().fullBlockCrafting) {
            val stats = conv.blob.voxelStats
            if (stats.isFullBlock) {
                val state = Block.getStateById(stats.mostCommonState)
                val `is` = ModUtil.getItemFromBlock(state)

                if (`is` != null) {
                    return `is`
                }
            }
        }

        val state = conv.primaryBlockState
        val itemstack = ItemStack(ChiselsAndBits.getBlocks().getConversionWithDefault(state), 1)

        itemstack.setTagInfo(ModUtil.NBT_BLOCKENTITYTAG, tag!!)
        return itemstack
    }

    fun replicate(): Boolean {
        val requiredEnergy = requirements.size * 8
        if (storage.extractEnergy(requiredEnergy, true) < requiredEnergy)
            return false
        val brushes = getBuffer()
            .map { Pair(it.first, CABAddon.api.createBrush(it.second)) }
            .groupBy { it.second.stateID }
            .mapValues { it.value.map { it.first } }
        val available = brushes.mapValues { it.value.map { inventory.getStackInSlot(it)!!.stackSize }.sum() }
        if (!requirements.all {
            it.stateId == 0 ||
            (available.containsKey(it.stateId) && available[it.stateId]!! >= it.quantity)
        }) return false

        val result = getResult() ?: return false

        fun insertResult(simulate: Boolean): Boolean {
            for (i in 19..27) {
                if (inventory.insertInternal(i, result, simulate) == null)
                    return true
            }
            return false
        }

        if (!insertResult(true))
            return false

        storage.extractEnergy(requiredEnergy, false)
        insertResult(false)
        for (req in requirements) {
            if (req.stateId == 0)
                continue
            val slots = brushes[req.stateId]!!
            var remaining = req.quantity
            for (slot in slots) {
                val extracted = inventory.extractInternal(slot, remaining, false) ?: continue
                remaining -= extracted.stackSize
            }
        }
        return true
    }

    private fun updateRequirements(stack: ItemStack) {
        val blob = ModUtil.getBlobFromStack(stack, null)
        requirements.clear()
        requirements.addAll(blob.blockCounts)
    }

    private fun isValidDesign(stack: ItemStack?): Boolean {
        if (CABAddon.api.getItemType(stack) == ItemType.POSITIVE_DESIGN && stack!!.hasTagCompound()) {
            val blob = ModUtil.getBlobFromStack(stack, null)
            return blob.blockCounts.any { it.stateId != 0 }
        }
        return false
    }

    override fun canConnectEnergy(from: EnumFacing?) = true

    override fun getEnergyStored(from: EnumFacing?) = storage.energyStored

    override fun getMaxEnergyStored(from: EnumFacing?) = storage.maxEnergyStored

    override fun receiveEnergy(from: EnumFacing?, maxReceive: Int, simulate: Boolean): Int {
        val result = storage.receiveEnergy(maxReceive, simulate)
        this.sync()
        return result
    }

    override fun writeToNBT(compound: NBTTagCompound): NBTTagCompound {
        super.writeToNBT(compound)
        storage.writeToNBT(compound)
        compound.setTag("Inventory", inventory.serializeNBT())
        compound.setByte("Mode", mode.toByte())
        return compound
    }

    override fun readFromNBT(compound: NBTTagCompound) {
        super.readFromNBT(compound)
        storage.readFromNBT(compound)
        inventory.deserializeNBT(compound.getCompoundTag("Inventory"))
        mode = compound.getByte("Mode").toInt()
    }

    override fun getUpdateTag(): NBTTagCompound {
        val compound = super.getUpdateTag()
        storage.writeToNBT(compound)
        compound.setByte("Mode", mode.toByte())
        return compound
    }

    override fun getUpdatePacket() = SPacketUpdateTileEntity(pos, 0, updateTag)

    override fun onDataPacket(net: NetworkManager, pkt: SPacketUpdateTileEntity) {
        storage.readFromNBT(pkt.nbtCompound)
        mode = pkt.nbtCompound.getByte("Mode").toInt()
    }

    override fun hasCapability(capability: Capability<*>?, facing: EnumFacing?): Boolean {
        if (capability == ITEM_HANDLER_CAPABILITY)
            return facing == null || facing.axis == EnumFacing.Axis.Y
        return super.hasCapability(capability, facing)
    }

    override fun <T : Any?> getCapability(capability: Capability<T>?, facing: EnumFacing?): T {
        if (capability == ITEM_HANDLER_CAPABILITY)
            return when (facing) {
                null -> ITEM_HANDLER_CAPABILITY.cast(inventory)
                EnumFacing.UP -> ITEM_HANDLER_CAPABILITY.cast(inputSlots)
                EnumFacing.DOWN -> ITEM_HANDLER_CAPABILITY.cast(outputSlots)
                else -> super.getCapability(capability, facing)
            }
        return super.getCapability(capability, facing)
    }
}

class ReplicatorContainer(playerInventory: InventoryPlayer, inventory: IItemHandlerModifiable) : Container() {
    init {
        class DesignSlot : SlotItemHandler(inventory, 0, 34, 92)

        class BufferSlot(private val x: Int) : SlotItemHandler(inventory, 10 + x, 57 + x * 18, 54) {
            override fun getSlotStackLimit(): Int {
                return BLOCK_BIT_VOLUME * MAX_BIT_STACK_SIZE
            }

            override fun getItemStackLimit(stack: ItemStack): Int {
                val index = 10 + x
                val maxAdd = stack.copy()
                val maxInput = BLOCK_BIT_VOLUME * MAX_BIT_STACK_SIZE
                maxAdd.stackSize = maxInput

                val handler = this.itemHandler
                val currentStack = handler.getStackInSlot(index)
                if (handler is IItemHandlerModifiable) {

                    handler.setStackInSlot(index, null)

                    val remainder = handler.insertItem(index, maxAdd, true)

                    handler.setStackInSlot(index, currentStack)

                    return maxInput - (remainder?.stackSize ?: 0)
                } else {
                    val remainder = handler.insertItem(index, maxAdd, true)

                    val current = currentStack?.stackSize ?: 0
                    val added = maxInput - (remainder?.stackSize ?: 0)
                    return current + added
                }
            }
        }

        class OutputSlot(x: Int) : SlotItemHandler(inventory, 19 + x, 57 + x * 18, 92)

        this.addSlotToContainer(DesignSlot())

        for (x in 0..8) {
            this.addSlotToContainer(SlotItemHandler(inventory, 1 + x, 57 + x * 18, 16))
            this.addSlotToContainer(BufferSlot(x))
            this.addSlotToContainer(OutputSlot(x))
        }

        for (y in 0..2) {
            for (x in 0..8) {
                this.addSlotToContainer(Slot(playerInventory, x + y * 9 + 9, 57 + x * 18, 122 + y * 18))
            }
        }

        for (x in 0..8) {
            this.addSlotToContainer(Slot(playerInventory, x, 57 + x * 18, 180))
        }
    }

    override fun canInteractWith(player: EntityPlayer) = true

    override fun transferStackInSlot(player: EntityPlayer, index: Int): ItemStack? {
        return handleShiftClick(this, player, index)
    }

    fun handleShiftClick(container: Container, player: EntityPlayer, slotIndex: Int): ItemStack? {
        val slots = container.inventorySlots
        val sourceSlot = slots[slotIndex]
        val inputStack = sourceSlot.stack ?: return null

        val sourceIsPlayer = sourceSlot.inventory === player.inventory

        val copy = inputStack.copy()

        if (sourceIsPlayer) {
            // transfer to any inventory
            if (!mergeStack(player.inventory, false, sourceSlot, slots, false)) {
                return null
            } else {
                return copy
            }
        } else {
            // transfer to player inventory
            // this is heuristic, but should do fine. if it doesn't the only "issue" is that vanilla behavior is not matched 100%
            val isMachineOutput = !sourceSlot.isItemValid(inputStack)
            if (!mergeStack(player.inventory, true, sourceSlot, slots, !isMachineOutput)) {
                return null
            } else {
                return copy
            }
        }
    }

    private fun mergeStack(playerInv: InventoryPlayer, mergeIntoPlayer: Boolean, sourceSlot: Slot, slots: List<Slot>,
                           reverse: Boolean): Boolean {
        val sourceStack = sourceSlot.stack!!

        val originalSize = sourceStack.stackSize

        val len = slots.size
        var idx: Int

        // first pass, try to merge with existing stacks
        // can skip this if stack is not stackable at all
        if (sourceStack.isStackable) {
            idx = if (reverse) len - 1 else 0

            while (sourceStack.stackSize > 0 && if (reverse) idx >= 0 else idx < len) {
                val targetSlot = slots[idx]
                if (targetSlot.inventory === playerInv == mergeIntoPlayer) {
                    val target = targetSlot.stack
                    if (stacksEqual(sourceStack, target)) { // also checks target != null, because stack is never null
                        val targetMax = Math.min(targetSlot.slotStackLimit, target!!.maxStackSize)
                        val toTransfer = Math.min(sourceStack.stackSize, targetMax - target.stackSize)
                        if (toTransfer > 0) {
                            target.stackSize += toTransfer
                            sourceStack.stackSize -= toTransfer
                            targetSlot.onSlotChanged()
                        }
                    }
                }

                if (reverse) {
                    idx--
                } else {
                    idx++
                }
            }
            if (sourceStack.stackSize == 0) {
                sourceSlot.putStack(null)
                return true
            }
        }

        // 2nd pass: try to put anything remaining into a free slot
        idx = if (reverse) len - 1 else 0
        while (if (reverse) idx >= 0 else idx < len) {
            val targetSlot = slots[idx]
            if (targetSlot.inventory === playerInv == mergeIntoPlayer
                && !targetSlot.hasStack && targetSlot.isItemValid(sourceStack)) {
                if (sourceStack.stackSize > sourceStack.maxStackSize) {
                    val insertedStack = sourceStack.copy()
                    insertedStack.stackSize = sourceStack.maxStackSize
                    targetSlot.putStack(insertedStack)
                    sourceStack.stackSize -= insertedStack.stackSize
                } else {
                    targetSlot.putStack(sourceStack)
                    sourceSlot.putStack(null)
                    return true
                }
            }

            if (reverse) {
                idx--
            } else {
                idx++
            }
        }

        // we had success in merging only a partial stack
        if (sourceStack.stackSize != originalSize) {
            sourceSlot.onSlotChanged()
            return true
        }
        return false
    }

    fun stacksEqual(a: ItemStack?, b: ItemStack?): Boolean {
        return a == b || a != null && b != null && equalsImpl(a, b)
    }

    private fun equalsImpl(a: ItemStack, b: ItemStack): Boolean {
        return a.item === b.item && a.metadata == b.metadata
               && Objects.equal(a.tagCompound, b.tagCompound)
    }
}