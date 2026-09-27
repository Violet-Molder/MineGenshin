// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.character;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public interface ICharacterConfigUI {
   ModularUI createConfigUI(Player var1, PGCharacter var2);

   Component title();
}
