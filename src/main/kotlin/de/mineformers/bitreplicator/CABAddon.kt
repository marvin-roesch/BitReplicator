package de.mineformers.bitreplicator

import mod.chiselsandbits.api.ChiselsAndBitsAddon
import mod.chiselsandbits.api.IChiselAndBitsAPI
import mod.chiselsandbits.api.IChiselsAndBitsAddon

/**
 * ${JDOC}
 */
@ChiselsAndBitsAddon
class CABAddon : IChiselsAndBitsAddon {
    companion object {
        lateinit var api: IChiselAndBitsAPI
    }

    override fun onReadyChiselsAndBits(api: IChiselAndBitsAPI) {
        CABAddon.api = api
    }
}