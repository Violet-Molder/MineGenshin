// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.character.configui;

import com.linweiyun.genshin.core.character.PGCharacter;
import net.minecraft.network.chat.Component;

/**
 * <b>最简</b>配置屏幕 —— 给「暂时没什么要单独配」的角色用：只有基类的属性页。
 *
 * <p>分工按设计走：面板骨架、属性页、外观区骨架都在基类 {@link CharacterConfigScreen}；
 * 需要<b>自己的装扮项</b>或<b>技能倍率表</b>的角色，各写一个子类（如 {@code ShenheConfigUI}、
 * {@code LinweiyunConfigUI}），只重写那两处差异。
 */
public class CharacterConfigUI extends CharacterConfigScreen {
   public static final CharacterConfigUI INSTANCE = new CharacterConfigUI();

   @Override
   public Component title() {
      return Component.translatable("gui.minegenshin.character_config.title_generic");
   }

   @Override
   protected Component titlebarText(PGCharacter character) {
      return Component.translatable("gui.minegenshin.character_config.title_format", new Object[]{character.getName()});
   }

}
