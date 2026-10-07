package com.linweiyun.genshin.content.items.weapon.catalyst;

import com.linweiyun.genshin.content.effect.character.impl.WhirlflowHymnEffects;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 漩流颂歌（五星法器）。
 *
 * <p>主词条：攻击力 <b>44.34</b>；副词条：生命值 <b>14.4%</b>。
 *
 * <h2>为什么是 tier 1 + 0.34 的修正</h2>
 * {@code WeaponStatData#getBaseAtk(5, tier)} 的档位是
 * tier1 → 44 / tier2 → 46 / tier3 → 48 / tier4 → 49（见 {@code WeaponMainStatConfig}），
 * 所以 <b>tier 1</b> 就是 44 那一档；再用 {@link #MAIN_STAT_DELTA} 补 0.34 → <b>44.34</b>
 * （和蝶变用 -0.46 补 47.54 是同一个做法）。
 * 副词条按 tier1 → {@code WeaponSubStatConfig.SUB_5_TIER44_HP_PERCENT} = <b>0.144 = 14.4%</b>
 * （tier1 走的是 {@code WeaponStatData.sub5_44}，44 指的就是这一档的 44 基础攻击力）。
 *
 * <h2>武器效果：告真的蜜酿</h2>
 * <pre>
 * ① 装备者治疗加成 +4%。
 * ② 装备者完成治疗（含后台治疗）→ 获得一层「告真的蜜酿」：
 *      装备者自身：生命值上限每层 +4%（最多 3 层）；
 *      同时当前场上角色获得攻击力 = 装备者生命值上限超出 40000 的部分每 1000 点 → +0.4%，至多 8%；
 *      持续 10 秒，重新触发刷新时长并加层（按受益人各自计层）。
 * ③ 附近的队伍成员触发冻结 / 星扩散后的 5 秒内，上面两份加成各提升 75%（×1.75）。
 * ④ 后台照常生效（效果挂在角色数据上，不依赖是否出战）。
 * </pre>
 *
 * <p>状态按仓库惯例放<b>角色</b>上：蜜酿是效果实例，反应窗口是
 * {@code PGCharacterData.whirlflowReactionWindowEnd}。
 */
public class HymnTheMaelstrom extends Catalyst {

    public static final String NAME = "hymn_of_the_maelstrom";

    /**
     * 主词条修正：五星 tier1 的整数基础攻击力是 44，补 +0.34 → 44.34。
     * （与蝶变 {@code MAIN_STAT_DELTA = -0.46} 同一机制。）
     */
    public static final double MAIN_STAT_DELTA = 0.34;

    /** 武器被动 ①：装备者的治疗加成 +4%。 */
    public static final float HEALING_BONUS = 0.04f;

    /** 「冻结 / 星扩散」触发算「附近」的半径（格）。 */
    public static final double TRIGGER_RADIUS = 20.0;

    public HymnTheMaelstrom(Item.Properties properties) {
        super(properties);
        this.star = 5;
        // tier1 = 基础攻击力 44 / 副词条生命值 14.4%（就是这一档的 0.144）
        this.tier = 1;
        this.subStatAttribute = ModAttributes.MAX_HP;
        this.mainStatDelta = MAIN_STAT_DELTA;
    }

    // ==================== 武器被动 ①：治疗加成 ====================

    /** 装备者自身提供 +4% 治疗加成（由 {@code PGCharacter#getHealingBonus} 聚合）。 */
    @Override
    public float getHealingBonus() {
        return HEALING_BONUS;
    }

    // ==================== 武器被动 ②：治疗 → 告真的蜜酿 ====================

    /**
     * 装备者完成了一次治疗（服务端）。
     *
     * <p>治疗者就是装备者；攻击力那一边发给<b>当前场上角色</b>（可能不是装备者）。
     * 后台照常生效 —— 这里只依赖玩家与角色数据，不依赖出战状态。
     */
    @Override
    public void onHeal(Player healer, PGCharacter character, LivingEntity target, float amount) {
        if (healer.level().isClientSide()) return;

        PlayerCharactersAttachment attachment = healer.getData(
                AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        PGCharacter onField = attachment == null ? null : attachment.getCurrentCharacter();

        WhirlflowHymnEffects.onEquipperHeal(healer, character, onField);
    }

    // ==================== 武器被动 ③：冻结 / 星扩散的 5 秒强化 ====================

    /**
     * 「附近的队伍成员触发了冻结 / 星扩散」—— 由反应执行处调用
     * （{@code FreezeReaction#execute} / {@code SwirlReaction#handleStellarSwirl}）。
     *
     * <p><b>触发者必须是玩家</b>（其他玩家打出来的也算，怪物打出来的不算）；
     * 然后按「谁在附近」来开窗：冻结点 {@value #TRIGGER_RADIUS} 格内的<b>每个玩家</b>，
     * 各自把窗口开给自己队伍里装备了本武器的角色 —— 所以 A 玩家打出的冻结
     * 也能给站在旁边的 B 玩家的漩流颂歌开窗。
     */
    public static void markReactionTriggers(ServerLevel level, Entity triggerEntity,
                                           double x, double y, double z) {
        if (level == null || !(triggerEntity instanceof Player)) return;

        long gameTime = level.getGameTime();
        double radiusSqr = TRIGGER_RADIUS * TRIGGER_RADIUS;
        for (Player nearby : level.players()) {
            if (nearby.distanceToSqr(x, y, z) > radiusSqr) continue;

            PlayerCharactersAttachment attachment = nearby.getData(
                    AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
            if (attachment == null) continue;

            for (int i = 0; i < 4; i++) {
                PGCharacter member = attachment.getPartyCharacter(i);
                if (member == null || !holdsWhirlflowHymn(member)) continue;
                WhirlflowHymnEffects.markReactionWindow(member, gameTime);
                WhirlflowHymnEffects.refreshMeadEffects(nearby, member);
            }
        }
    }

    /** 这个角色装备的是不是本武器。 */
    public static boolean holdsWhirlflowHymn(PGCharacter character) {
        if (character == null) return false;
        ItemStack weapon = character.getData().getWeapon();
        return !weapon.isEmpty() && weapon.getItem() instanceof HymnTheMaelstrom;
    }

    // ==================== 物品信息 ====================

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, builder, flag);
        builder.add(Component.empty());
        builder.add(Component.translatable("item.minegenshin.hymn_of_the_maelstrom.passive").withStyle(ChatFormatting.YELLOW));
        builder.add(Component.empty());
        builder.add(Component.literal("蓝玉髓所铸的精致灯盏，仿若自童话中诞\n生的宝物，据闻尘封着一曲为所有人忘却\n的颂歌。"));
    }
}
