package com.elsure.hyperglow

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Control-center tile: cycles flashlight brightness levels.
 * Each tap advances: off -> 1 -> 2 -> 3 -> 4 -> 5 -> off.
 * Long-press opens the HyperGlow app.
 */
class TorchTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val sp = getSharedPreferences("hyperglow", MODE_PRIVATE)
        val current = sp.getInt("torchLevel", 0)
        val next = if (current >= 5) 0 else current + 1
        sp.edit().putInt("torchLevel", next).apply()

        if (next == 0) {
            LightController.setTorch(false)
        } else {
            LightController.setTorch(true, next * 20)
        }
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val level = getSharedPreferences("hyperglow", MODE_PRIVATE).getInt("torchLevel", 0)
        tile.state = if (level > 0) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (level > 0) "闪光灯  档" else "闪光灯"
        tile.updateTile()
    }
}