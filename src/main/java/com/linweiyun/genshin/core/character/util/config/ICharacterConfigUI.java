// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.character.util.config;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.linweiyun.genshin.core.character.PGCharacter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public interface ICharacterConfigUI {
   ModularUI createConfigUI(Player var1, PGCharacter var2);

   Component title();

   /**
    * 只取「外观」那一块（标题 + 这个角色可调的装扮项），不带整屏框架。
    *
    * <p>K 页的配置屏幕用它拼右侧那份；<b>U 键装备页最下面那块也用它</b> ——
    * 装扮项是每个角色自己的（各角色包下的 {@code *ConfigUI} 重写 {@code fillAppearance}），
    * 所以两个界面天然一致，不必各写一份。
    *
    * @param previewMask 长度 1 的可写数组，行里的改动会写回它（U 页用不到也能传）
    */
   default UIElement buildAppearanceSection(Player player, PGCharacter character, int[] previewMask) {
      return new UIElement();
   }
}