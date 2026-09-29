package com.linweiyun.genshin.core.character.allweapon.linweiyun;

import com.linweiyun.genshin.config.character.LinweiyunTalentConfig;
import com.linweiyun.genshin.config.character.TalentConfigSource;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.allweapon.AllWeaponAppearanceData;
import com.linweiyun.genshin.core.character.util.config.CharacterConfigScreen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 林薇云的配置页 —— 和 {@code ShenheConfigUI} 同一个套路：<b>自己的那一份</b>，
 * 不是指向申鹤、也不是直接拿最简的通用页。
 *
 * <h2>分工（基类管大部分内容，子类只管差异）</h2>
 * 基类 {@link CharacterConfigScreen} 负责：整块面板与页签框架、角色预览、标题栏、
 * 「属性」页（基础 / 进阶 / 元素）、外观区的骨架、以及「技能倍率」页签的排版
 * （分组 + 可编辑数字框 + 改完同步服务端）。
 *
 * <p>这个子类只声明<b>属于她自己的两件事</b>：
 * <ol>
 *   <li><b>装扮</b>：她的外观数据是 {@link AllWeaponAppearanceData} ——
 *       两项，<b>武器形态</b>（拳 / 剑 / 长柄 / 大剑 / 法器 / 弓）与<b>显示武器</b>。
 *       项本身由这份数据提供，基类的外观区按它自动列出来（手写项才会在子类里自己搭行）；</li>
 *   <li><b>技能倍率</b>：她自己的那张表 {@link LinweiyunTalentConfig}
 *       （key 带 {@code lwy-} 前缀，数值照抄申鹤那套，以后改 TOML 即可）。</li>
 * </ol>
 *
 * <p>另外她这套动画里没有 {@code extra48}（那是申鹤 K 页预览用的），预览动画改用 {@code idle}。
 */
public class LinweiyunConfigUI extends CharacterConfigScreen {
   public static final LinweiyunConfigUI INSTANCE = new LinweiyunConfigUI();

   @Override
   public Component title() {
      return Component.translatable("gui.minegenshin.character_config.title_generic");
   }

   @Override
   protected Component titlebarText(PGCharacter character) {
      return Component.translatable("gui.minegenshin.character_config.title_format", new Object[]{character.getName()});
   }

   /** 她这套动画没有 {@code extra48}，预览用 idle。 */
   @Override
   protected String previewAnimation() {
      return "idle";
   }

   /** 她的技能倍率表（申鹤那份是 {@code ShenheTalentConfig}，各用各的）。 */
   @Nullable
   @Override
   protected TalentConfigSource talentConfig() {
      return LinweiyunTalentConfig.SOURCE;
   }
}