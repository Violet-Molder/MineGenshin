package com.linweiyun.genshin.core.system.compat;

import com.linweiyun.genshin.Minegenshin;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;

import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * 玩家 ↔ 角色之间的属性桥。
 *
 * <h2>两个方向</h2>
 * <ul>
 *   <li><b>原神模式</b>：角色基础攻击力里挂一条 {@code minecraft} 来源的「玩家原版攻击力 × 15」。
 *       只是让角色吃得上玩家手里那把剑／那身装备，玩家的本体属性一概不动。</li>
 *   <li><b>非原神模式</b>：当前角色的生命上限 / 攻击力 × 0.7 直接加在玩家身上，
 *       并且把「玩家血量 ↔ 角色血量」按同一倍率折算成一条连续的池子。</li>
 * </ul>
 *
 * <h2>血量折算</h2>
 * <pre>
 *   出原神模式：玩家上限 = 自己(原版)上限 + 角色上限 × 0.7
 *               玩家当前 = 自己(原版)上限 + 角色当前 × 0.7
 *   回原神模式：角色当前 = clamp((玩家当前 − 自己(原版)上限) ÷ 0.7, 1, 角色上限)
 *               玩家上限 = 自己(原版)上限（摘掉修饰符即可，别的 MOD 加的上限原样留着）
 *               玩家当前 = 不动，只在超过上限时夹回去
 * </pre>
 *
 * <p>最后那条 clamp 下界是 1：玩家被打到「自己原版上限」以下时，角色身上剩的就被打光了，
 * 但保底给 1 点，不是无敌 —— 回原神模式后这个角色随时可能被打倒。
 *
 * <p>玩家身上那两条加成（生命上限 / 攻击力）的<b>加与减都走属性修饰符</b>
 * （{@code ADD_VALUE}），从不写基础值 —— 所以别的 MOD 给玩家改过生命上限也不会被我们重置回原值。
 *
 * <p>折算只在「原神模式 → 非原神模式」这一个切换点上建立（记在
 * {@link AttachmentRegistration#COMPAT_LINK_ATTACHMENT} 里），所以从没开过原神模式的玩家
 * 一进世界不会莫名其妙变成 720 血。
 */
public final class PlayerStatBridge {

    /** 角色基础攻击力里「玩家原版攻击力 × 15」那一条的来源名 */
    public static final String MINECRAFT_ATTACK_SOURCE = "minecraft";

    private static final Identifier CHARACTER_ATTACK_MODIFIER =
            Identifier.fromNamespaceAndPath(Minegenshin.MOD_ID, "compat_character_attack");
    private static final Identifier CHARACTER_HEALTH_MODIFIER =
            Identifier.fromNamespaceAndPath(Minegenshin.MOD_ID, "compat_character_health");

    private PlayerStatBridge() {
    }

    // ==================== 每 tick 维护 ====================

    /**
     * 服务端每 tick 维护一次：模式没变时这里只做「和上一刻是否一致」的比较，不会反复写属性。
     */
    public static void tick(Player player) {
        if (player.level().isClientSide() || !CompatConfig.enabled()) {
            return;
        }

        PersistentState state = stateOf(player);
        if (state.genshinMode) {
            // 原神模式下玩家本体不该带角色加成；顺手把可能残留的链接清掉
            if (state.linked) {
                player.setData(AttachmentRegistration.COMPAT_LINK_ATTACHMENT, false);
            }
            removePlayerCharacterModifiers(player);
            if (state.current != null) {
                applyPlayerAttackContribution(player, state.characters, state.current);
            } else {
                clearPlayerAttackContribution(state.characters);
            }
            return;
        }

        if (!state.linked || state.current == null) {
            return;
        }
        applyCharacterStatsToPlayer(player, state.current);
    }

    // ==================== 模式切换（唯一的折算入口） ====================

    /**
     * 原神模式开关变化时调用。
     *
     * @param genshinMode 切换后的状态：true = 进原神模式，false = 退回非原神模式
     */
    public static void onGenshinModeChanged(Player player, boolean genshinMode) {
        if (player.level().isClientSide() || !CompatConfig.enabled()) {
            return;
        }

        PersistentState state = stateOf(player);

        // 这两项必须在动属性之前量好：
        // 摘掉生命上限加成的瞬间，原版会把当前血量夹到新上限（370 → 20），量晚了就全被夹没了。
        double playerHealth = player.getHealth();
        double baseMaxHealth = playerBaseMaxHealth(player);

        removePlayerCharacterModifiers(player);

        if (genshinMode) {
            if (state.linked) {
                if (state.current != null) {
                    applyPlayerHealthToCharacter(player, state.current, baseMaxHealth, playerHealth);
                }
                player.setData(AttachmentRegistration.COMPAT_LINK_ATTACHMENT, false);
            }
            if (state.current != null) {
                applyPlayerAttackContribution(player, state.characters, state.current);
            } else {
                clearPlayerAttackContribution(state.characters);
            }
            return;
        }

        if (state.current == null) {
            return;
        }
        if (state.linked) {
            // 已经链接过了（模式其实没变的重入）：只刷新属性，玩家当前血量原样放回去，别再折一次
            applyCharacterStatsToPlayer(player, state.current);
            restoreHealth(player, playerHealth);
            return;
        }
        player.setData(AttachmentRegistration.COMPAT_LINK_ATTACHMENT, true);
        // 「玩家原版攻击力 × 15」只在原神模式生效，退出时先从角色身上摘掉
        clearPlayerAttackContribution(state.characters);
        applyCharacterStatsToPlayer(player, state.current);
        applyCharacterHealthToPlayer(player, state.current, baseMaxHealth);
    }

    // ==================== 原神模式：玩家攻击力进角色 ====================

    /**
     * 把「玩家原版攻击力 × 15」写成<b>当前出战角色</b>基础攻击力里的 minecraft 来源。
     *
     * <p>顺手把队伍里其他角色身上这条残留摘掉 —— 换人之后前一个角色不该继续白拿这份加成。
     */
    public static void applyPlayerAttackContribution(Player player, PlayerCharactersAttachment characters,
                                                     PGCharacter current) {
        double contribution = player.getAttributeValue(Attributes.ATTACK_DAMAGE)
                * CompatConfig.playerAttackScale();
        for (int i = 0; i < 4; i++) {
            PGCharacter character = characters.getPartyCharacter(i);
            if (character == null) {
                continue;
            }
            if (character == current) {
                character.getData().setAttributeBaseValue(
                        ModAttributes.ATK.value(), MINECRAFT_ATTACK_SOURCE, contribution);
            } else {
                character.getData().removeAttributeBaseValue(
                        ModAttributes.ATK.value(), MINECRAFT_ATTACK_SOURCE);
            }
        }
    }

    /** 摘掉所有角色身上「玩家原版攻击力 × 15」这条加成。 */
    public static void clearPlayerAttackContribution(PlayerCharactersAttachment characters) {
        for (PGCharacter character : characters.getOwnedCharacters()) {
            if (character != null) {
                character.getData().removeAttributeBaseValue(
                        ModAttributes.ATK.value(), MINECRAFT_ATTACK_SOURCE);
            }
        }
    }

    // ==================== 非原神模式：角色属性进玩家 ====================

    /** 角色生命上限 / 攻击力 × 0.7 加到玩家身上（攻击力之外的属性、防御力都不动）。 */
    public static void applyCharacterStatsToPlayer(Player player, PGCharacter character) {
        double scale = CompatConfig.characterStatScale();
        setModifier(player, Attributes.ATTACK_DAMAGE, CHARACTER_ATTACK_MODIFIER,
                character.getData().getAttributeTotalValue(ModAttributes.ATK.value()) * scale);
        setModifier(player, Attributes.MAX_HEALTH, CHARACTER_HEALTH_MODIFIER,
                characterMaxHealth(character) * scale);
    }

    /** 摘掉玩家身上由角色提供的加成（攻击力 + 生命上限两条）。 */
    public static void removePlayerCharacterModifiers(Player player) {
        removeModifier(player, Attributes.ATTACK_DAMAGE, CHARACTER_ATTACK_MODIFIER);
        removeModifier(player, Attributes.MAX_HEALTH, CHARACTER_HEALTH_MODIFIER);
    }

    /**
     * 回原神模式：玩家当前血量折回角色当前血量，玩家本体血量恢复成「自己的满血」。
     *
     * <p>折算不出正数（玩家被打到原版上限以下）时保底 1 点 —— 角色没被打死，但也没剩多少。
     */
    private static void applyPlayerHealthToCharacter(Player player, PGCharacter character,
                                                     double baseMaxHealth, double playerHealth) {
        double scale = CompatConfig.characterStatScale();
        double characterMaxHealth = characterMaxHealth(character);
        double characterHealth = 1.0D;
        if (scale > 0.0D && characterMaxHealth > 0.0D) {
            characterHealth = Math.min(
                    Math.max((playerHealth - baseMaxHealth) / scale, 1.0D),
                    characterMaxHealth);
        }
        character.getData().setCurrentHP(characterHealth);
        character.syncRealtimeState();
        // 玩家本体的血量不动：上限随着修饰符被摘掉已经回到「玩家原本的上限」，
        // 多出来的那截原版自己会夹回去。这里只在万一还高于上限时夹一次 ——
        // 不回血也不补血：本来就只剩 19 点的话，切回原神模式还是 19 点。
        clampPlayerHealth(player);
    }

    /**
     * 出原神模式：玩家血量上限 = 自己的上限 + 角色上限 × 0.7，
     * 当前血量按同一倍率从角色当前血量折算（角色满血 → 玩家满血）。
     */
    private static void applyCharacterHealthToPlayer(Player player, PGCharacter character,
                                                     double baseMaxHealth) {
        double scale = CompatConfig.characterStatScale();
        double playerMaxHealth = Math.max(baseMaxHealth + characterMaxHealth(character) * scale, 1.0D);
        double playerHealth = baseMaxHealth + character.getData().getCurrentHP() * scale;
        player.setHealth((float) Math.min(Math.max(playerHealth, 1.0D), playerMaxHealth));
    }

    // ==================== 查询 / 工具 ====================

    /** 玩家身上「不含角色加成」的那份生命上限。 */
    private static double playerBaseMaxHealth(Player player) {
        AttributeInstance instance = player.getAttribute(Attributes.MAX_HEALTH);
        if (instance == null) {
            return player.getAttributeValue(Attributes.MAX_HEALTH);
        }
        AttributeModifier ours = instance.getModifier(CHARACTER_HEALTH_MODIFIER);
        return ours != null ? instance.getValue() - ours.amount() : instance.getValue();
    }

    /** 把血量放回一个已知值，并夹进当前上限（属性刚被改写时用）。 */
    private static void restoreHealth(Player player, double health) {
        if (health <= 0.0D) {
            return;
        }
        player.setHealth((float) Math.min(health, Math.max(player.getAttributeValue(Attributes.MAX_HEALTH), 1.0D)));
    }

    /** 只把超过上限的血量夹回去，绝不往上补血。 */
    private static void clampPlayerHealth(Player player) {
        double max = player.getAttributeValue(Attributes.MAX_HEALTH);
        if (max >= 1.0D && player.getHealth() > max) {
            player.setHealth((float) max);
        }
    }

    public static boolean isGenshinMode(Player player) {
        return Boolean.TRUE.equals(player.getData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT));
    }

    /** 玩家是不是「从原神模式退出来过」—— 只有这类玩家身上才挂着角色属性。 */
    public static boolean isLinked(Player player) {
        return Boolean.TRUE.equals(player.getData(AttachmentRegistration.COMPAT_LINK_ATTACHMENT));
    }

    /**
     * 玩家身上当前是不是挂着「角色生命上限 × 0.7」那份加成。
     *
     * <p>这条只看<b>属性修饰符</b>，不看服务端那个链接记账 —— 属性修饰符会随原版属性同步发到
     * 客户端，链接记账（{@link AttachmentRegistration#COMPAT_LINK_ATTACHMENT}）是纯服务端字段，
     * 客户端读到的永远是 false。客户端 HUD 要判断「玩家这条血里有没有角色那一截」，只能走这里。
     *
     * <p>也正是这个条件决定「非原神模式下原版血心会不会堆成一长条」—— 血上限被抬到几百点时，
     * 原版会把血心一层层往上摞，所以客户端要换成我们自己的短血条。
     */
    public static boolean hasCharacterHealth(Player player) {
        AttributeInstance instance = player.getAttribute(Attributes.MAX_HEALTH);
        return instance != null && instance.getModifier(CHARACTER_HEALTH_MODIFIER) != null;
    }

    /**
     * 非原神模式下「角色那一截血」当前占它自己的比例（0~1）。
     *
     * <p>直接拿客户端同步过来的属性反推：{@code (当前血量 − 玩家自己那份上限) ÷ 角色那份上限}。
     * <b>不能</b>去读角色数据 —— 非原神模式下角色的 {@code currentHP} 是冻结的，
     * 只有切换模式那一下才会重新折算，拿它画血条会一直停在退出时的样子。
     */
    public static float characterHealthRatio(Player player) {
        AttributeInstance instance = player.getAttribute(Attributes.MAX_HEALTH);
        if (instance == null) {
            return 1.0F;
        }
        AttributeModifier ours = instance.getModifier(CHARACTER_HEALTH_MODIFIER);
        if (ours == null || ours.amount() <= 0.0D) {
            return 1.0F;
        }
        double base = instance.getValue() - ours.amount();
        return (float) Mth.clamp((player.getHealth() - base) / ours.amount(), 0.0D, 1.0D);
    }

    public static double characterMaxHealth(PGCharacter character) {
        return character.getData().getAttributeTotalValue(ModAttributes.MAX_HP.value());
    }

    private static PersistentState stateOf(Player player) {
        PlayerCharactersAttachment characters =
                player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        return new PersistentState(
                isGenshinMode(player),
                isLinked(player),
                characters,
                characters.getCurrentCharacter());
    }

    private static void setModifier(LivingEntity entity, Holder<Attribute> attribute,
                                    Identifier id, double amount) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(id);
        if (existing != null && Math.abs(existing.amount() - amount) < 0.0001D) {
            return;
        }
        instance.addOrUpdateTransientModifier(
                new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    private static void removeModifier(LivingEntity entity, Holder<Attribute> attribute, Identifier id) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }

    /** 一次 tick / 一次切换里要读的状态，凑一起避免反复查附件。 */
    private record PersistentState(boolean genshinMode, boolean linked,
                                   PlayerCharactersAttachment characters, PGCharacter current) {
    }
}
