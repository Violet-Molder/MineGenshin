package com.linweiyun.genshin.core.system.reaction;

import com.linweiyun.genshin.content.effect.character.CharacterEffectContainer;
import com.linweiyun.genshin.content.effect.character.impl.RadianceStellarConduceEffect;
import com.linweiyun.genshin.content.effect.character.impl.RadianceStellarSwirlEffect;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.util.capability.IStellarHousehold;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 辉映·星烁的查询入口 —— 「这个角色现在处于哪个分支」「星烁加成了多少」。
 *
 * <pre>
 * 辉映·星烁（统称）
 *   ├─ 辉映·星超导（优先级高）
 *   └─ 辉映·星扩散（优先级低）
 * </pre>
 *
 * <p>同一角色身上两者互斥（互斥规则写在两个 effect 类里，见
 * {@link RadianceStellarConduceEffect} / {@link RadianceStellarSwirlEffect}），
 * 这里的 {@link #branchOf(PGCharacter)} 只是按优先级读出「现在是哪一个」。
 */
public final class StellarGlimmer {

    private StellarGlimmer() {
    }

    /** 当前身上的星烁分支；两个都没有就是 {@code null}。星超导优先。 */
    @Nullable
    public static StellarGlimmerBranch branchOf(@Nullable PGCharacter character) {
        if (character == null) {
            return null;
        }
        CharacterEffectContainer container = character.getData().getEffectContainer();
        if (container.hasEffectOfType(RadianceStellarConduceEffect.class)) {
            return StellarGlimmerBranch.CONDUCE;
        }
        if (container.hasEffectOfType(RadianceStellarSwirlEffect.class)) {
            return StellarGlimmerBranch.SWIRL;
        }
        return null;
    }

    /** 身上是不是这个分支。 */
    public static boolean has(@Nullable PGCharacter character, StellarGlimmerBranch branch) {
        return branchOf(character) == branch;
    }

    /** 快捷：是否处于辉映·星扩散（薇斯娜的天赋判定用它 —— 她的加成只认星扩散）。 */
    public static boolean hasSwirl(@Nullable PGCharacter character) {
        return has(character, StellarGlimmerBranch.SWIRL);
    }

    /** 快捷：是否处于辉映·星超导。 */
    public static boolean hasConduce(@Nullable PGCharacter character) {
        return has(character, StellarGlimmerBranch.CONDUCE);
    }

    /**
     * 星烁反应的伤害加成总和（反应加成区里那一项，和元素精通加算）。
     */
    public static float bonusOf(@Nullable PGCharacter character, StellarGlimmerBranch branch) {
        if (character == null || branch == null) {
            return 0f;
        }
        float total = character.getData().getEffectContainer().getTotalStellarGlimmerBonus(branch);
        total += character.getStellarGlimmerBonus(branch);
        return total;
    }

    /**
     * 全队「星扩散户口」—— 检测队伍里是否有角色带星扩散户口。
     *
     * @return 队伍里第一个找到的星扩散户口（仅用于 {@code != null} 检测）；没有就返回 null
     */
    @Nullable
    public static IStellarHousehold.StellarHousehold swirlHousehold(@Nullable Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        for (Player player : serverLevel.players()) {
            PlayerCharactersAttachment attachment = player.getData(
                    AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
            for (int i = 0; i < 4; i++) {
                PGCharacter member = attachment.getPartyCharacter(i);
                if (member instanceof IStellarHousehold provider) {
                    IStellarHousehold.StellarHousehold household = provider.stellarHousehold();
                    if (household != null && household.branch() == StellarGlimmerBranch.SWIRL) {
                        return household;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 全队「星扩散反应基础伤害提升」—— 全队所有星扩散户口的 {@code baseBonusMult} 累加。
     *
     * <p>加在星扩散的基础区上（{@code 基础区 × (1 + 基础倍率提升)}）。
     * 有多角色带星扩散户口时累加，没有就是 0。
     */
    public static float swirlBaseBonusMult(@Nullable Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return 0f;
        }
        float total = 0f;
        for (Player player : serverLevel.players()) {
            PlayerCharactersAttachment attachment = player.getData(
                    AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
            for (int i = 0; i < 4; i++) {
                PGCharacter member = attachment.getPartyCharacter(i);
                if (member instanceof IStellarHousehold provider) {
                    IStellarHousehold.StellarHousehold household = provider.stellarHousehold();
                    if (household != null && household.branch() == StellarGlimmerBranch.SWIRL) {
                        total += household.baseBonusMult();
                    }
                }
            }
        }
        return total;
    }

    /** 队伍里第一个星超导户口；没有就返回 null。 */
    @Nullable
    public static IStellarHousehold.StellarHousehold conduceHousehold(@Nullable Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        for (Player player : serverLevel.players()) {
            PlayerCharactersAttachment attachment = player.getData(
                    AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
            for (int i = 0; i < 4; i++) {
                PGCharacter member = attachment.getPartyCharacter(i);
                if (member instanceof IStellarHousehold provider) {
                    IStellarHousehold.StellarHousehold household = provider.stellarHousehold();
                    if (household != null && household.branch() == StellarGlimmerBranch.CONDUCE) {
                        return household;
                    }
                }
            }
        }
        return null;
    }

    /** 全队星超导反应基础伤害提升，取队伍里最高的一份。 */
    public static float conduceBaseBonusMult(@Nullable Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return 0f;
        }
        float best = 0f;
        for (Player player : serverLevel.players()) {
            PlayerCharactersAttachment attachment = player.getData(
                    AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
            for (int i = 0; i < 4; i++) {
                PGCharacter member = attachment.getPartyCharacter(i);
                if (member instanceof IStellarHousehold provider) {
                    IStellarHousehold.StellarHousehold household = provider.stellarHousehold();
                    if (household != null && household.branch() == StellarGlimmerBranch.CONDUCE) {
                        best = Math.max(best, household.baseBonusMult());
                    }
                }
            }
        }
        return best;
    }
}
