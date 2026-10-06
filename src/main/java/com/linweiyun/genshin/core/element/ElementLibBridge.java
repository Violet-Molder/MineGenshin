package com.linweiyun.genshin.core.element;

import com.linweiyun.elementlib.api.ElementLibApi;
import com.linweiyun.elementlib.api.ElibAttackAction;
import com.linweiyun.elementlib.api.ElementalDamageContext;
import com.linweiyun.elementlib.api.ElementalDamageHandler;
import com.linweiyun.elementlib.api.ElementalReactionType;
import com.linweiyun.elementlib.api.ReactionFeedbackHandler;
import com.linweiyun.elementlib.api.ReactionVariantGate;
import com.linweiyun.elementlib.config.ElementLibConfig;
import com.linweiyun.elementlib.core.element.GenshinElement;
import com.linweiyun.elementlib.core.module.ElibModuleTargetKind;
import com.linweiyun.elementlib.core.module.ElibModuleTargetKinds;
import com.linweiyun.elementlib.core.module.ElibModuleTypes;
import com.linweiyun.elementlib.core.system.about.host.EntityHost;
import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.util.CharacterKeys;
import com.linweiyun.genshin.core.system.about.host.CharacterHost;
import com.linweiyun.genshin.core.system.about.host.CharacterModuleHost;
import com.linweiyun.genshin.core.system.module.MinegenshinModuleKinds;
import com.linweiyun.genshin.core.system.toughness.ToughnessAttackListener;
import com.linweiyun.genshin.core.system.toughness.ToughnessTypes;
import com.linweiyun.genshin.core.system.combat.damage.DamageIndicatorFactory;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSource;
import com.linweiyun.genshin.core.system.combat.damage.ModDamageSpec;
import com.linweiyun.genshin.core.system.poise.PoiseFreezeBreak;
import com.linweiyun.genshin.core.system.reaction.ReactionPriorityCalculator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * 本模组与 elementlib 的接缝：关掉库的示范内容，把「具体效果」全部换回本模组自己的实现。
 */
public final class ElementLibBridge {

    private ElementLibBridge() {
    }

    public static void install() {
        // 必须在 elementlib 构造之前调用（本模组在 mods.toml 里声明 ordering=BEFORE）
        ElementLibConfig.suppressDemoContent();

        // 附着图标由本模组的血条 HUD 画，关掉库自带的世界渲染
        ElementLibApi.setAuraIconVisible(false);

        ElementLibApi.setFeedbackHandler(new ReactionFeedbackHandler() {
            @Override
            public void reaction(LivingEntity target, ElementalReactionType type, GenshinElement element) {
                DamageIndicatorFactory.reaction(target, type);
            }

            @Override
            public void transformative(LivingEntity target, ElementalReactionType type, GenshinElement element) {
                DamageIndicatorFactory.reaction(target, type);
            }

            @Override
            public void atBlock(ServerLevel level, net.minecraft.core.BlockPos pos, ElementalReactionType type) {
                DamageIndicatorFactory.reactionAtBlock(level, pos, type);
            }
        });

        ElementLibApi.setDamageHandler(new ElementalDamageHandler() {
            @Override
            public float computeDamage(ElementalDamageContext ctx) {
                // 真实数值由本模组的伤害管线算，这里只负责「要不要结算」
                return 1f;
            }

            @Override
            public void dealDamage(ElementalDamageContext ctx, float amount) {
                LivingEntity target = ctx.target();
                ServerLevel level = ctx.level();
                if (target == null || level == null || ctx.reactionType() == null) {
                    return;
                }
                GenshinElement element = ctx.element() != null ? ctx.element() : ModElements.ELECTRO.get();
                Entity attacker = ctx.attacker();
                Entity sourceEntity = attacker != null ? attacker : target;
                ModDamageSpec spec = ModDamageSpec.transformative(ctx.reactionType(), element);
                target.hurtServer(level, ModDamageSource.from(spec, sourceEntity), 0f);
            }
        });

        ElementLibApi.setTickListener((entity, container, frozenWithCold) ->
                PoiseFreezeBreak.onFreezeTick(entity, frozenWithCold));

        // 环境元素（水 / 火）挂在出战角色身上，而不是玩家实体上
        ElementLibApi.setEnvironmentTarget(entity -> {
            if (!(entity instanceof Player player)) {
                return EntityHost.of(entity);
            }
            PGCharacter character = currentCharacter(player);
            return CharacterHost.of(character);
        });

        // 模块宿主：角色
        ElibModuleTargetKind characterKind = MinegenshinModuleKinds.CHARACTER;
        ElibModuleTypes.ELEMENT.support(characterKind);
        ToughnessTypes.TOUGHNESS.support(characterKind);
        ToughnessTypes.init();
        MinegenshinModuleKinds.init();
        ElementLibApi.registerModuleCarrier(PGCharacter.class, CharacterModuleHost::new);

        // 攻击链路：关默认桥与实体附着（实体附着仍由伤害管线负责），注入原神模式门禁与角色元素
        ElementLibApi.setDefaultVanillaAttackBridge(false);
        ElementLibApi.setAttackGate(action -> {
            if (!(action.attacker() instanceof Player player)) {
                return false;
            }
            return Boolean.TRUE.equals(player.getData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT));
        });
        ElementLibApi.setAttackElementResolver(action -> {
            if (!(action.attacker() instanceof Player player)) {
                return null;
            }
            PGCharacter character = currentCharacter(player);
            return character == null ? null : character.getElemental();
        });
        ElementLibApi.addAttackBlockInterest(ToughnessAttackListener::interested);
        ElementLibApi.addAttackListener(ToughnessAttackListener::onAttack);

        ElementLibApi.setVariantGate(new ReactionVariantGate() {
            @Override
            public boolean stellarSwirl(Entity attacker, LivingEntity target) {
                return target != null && target.level() instanceof ServerLevel level
                        && ReactionPriorityCalculator.hasStellarSwirlHousehold(level);
            }

            @Override
            public boolean stellarConduce(Entity attacker, LivingEntity target) {
                return false;
            }

            @Override
            public boolean lunarCharged(Entity attacker, LivingEntity target) {
                return target != null && target.level() instanceof ServerLevel level
                        && ReactionPriorityCalculator.hasColumbinaInParty(level);
            }
        });
    }

    @Nullable
    private static PGCharacter currentCharacter(Player player) {
        PlayerCharactersAttachment chars = player.getData(
                AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        return chars == null ? null : chars.getCurrentCharacter();
    }
}
