// restored by decompilation (2026-09-27): this file had been rolled back to an older snapshot;
// the newest version only existed as a compiled class in the Gradle build cache (08:55 build).
package com.linweiyun.genshin.core.character.configui;

import com.linweiyun.genshin.config.character.TalentConfigSource;
import com.linweiyun.genshin.config.character.TalentConfigs;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * <b>通用</b>配置屏幕 —— 没有专属页面的角色都用它（例如林薇云 {@code Linweiyun}）。
 *
 * <p>两页：
 * <ul>
 *   <li><b>属性</b>：基础属性 + 进阶 + 元素各条（见 {@code CharacterConfigPage#buildStatsScroller}），
 *       这一页一直都有（父类的默认实现就是它）；</li>
 *   <li><b>技能倍率</b>：按角色 id 去 {@code TalentConfigs} 取倍率表（申鹤 / 林薇云各自登记过），
 *       列成可编辑的输入框。没登记倍率表的角色就不出这一页。</li>
 * </ul>
 * 页签只在有作弊权限（OP）时出现，和申鹤那边一致 —— 普通玩家只看得到属性页。
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

   @Override
   protected List<CharacterConfigScreen.InfoPage> infoPages(Player player, PGCharacter character) {
      UIElement stats = CharacterConfigPage.buildStatsScroller(character);
      TalentConfigSource talents = TalentConfigs.of(character.getTextureId());

      if (talents == null || !hasCheatPermission(player)) {
         return List.of(new CharacterConfigScreen.InfoPage(null, stats));
      }

      return List.of(
              new CharacterConfigScreen.InfoPage("gui.minegenshin.character_config.tab.stats", stats),
              new CharacterConfigScreen.InfoPage("gui.minegenshin.character_config.tab.talent", this.buildTalentScroller(talents))
      );
   }
}
