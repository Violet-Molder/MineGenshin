package com.linweiyun.genshin.core.character.catalyst.vodyanitsa;

import com.linweiyun.genshin.config.character.VodyanitsaAttributeConfig;
import com.linweiyun.genshin.core.character.catalyst.CatalystCharacter;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.linweiyun.genshin.core.character.util.type.CharacterAscendAttribute;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 沃雅妮莎（Vodyanitsa）—— 水系五星法器，突破属性<b>生命值</b>。
 *
 * <p>她只做<b>中转/调度</b>：持有 {@link VodyanitsaSkill}（招式）/
 * {@link VodyanitsaTalent}（突破天赋）/ {@link VodyanitsaConstellation}（命座）三个协作者，
 * 每刻按固定顺序把它们串起来，自己不写任何伤害 / 效果实现。
 *
 * <p>数值表见 {@link VodyanitsaAttributeConfig}；技能见 {@link VodyanitsaSkill}；
 * 突破天赋见 {@link VodyanitsaTalent}；命座见 {@link VodyanitsaConstellation}。
 *
 * <h2>为什么「遥久之歌」的计时字段留在这里</h2>
 * {@code songTicks} 是 {@code @DescSynced @Persisted(key = "song_ticks")} ——
 * 键属于存档格式，搬家会让老存档读不出来。所以状态本体留在这里（读写各一个访问器），
 * <b>怎么用</b>（每 3 秒打一次、每 1.5 秒回一次血）在 {@link VodyanitsaSkill} 里。
 */
public class Vodyanitsa extends CatalystCharacter {

    public static final String ID = "vodyanitsa";
    public static final int UID = 145002;

    /** 战技冷却 16 秒。 */
    public static final int SKILL_COOLDOWN_TICKS = 16 * 20;
    /** 大招冷却 15 秒 / 能量 60。 */
    public static final int BURST_COOLDOWN_TICKS = 15 * 20;
    public static final float BURST_ENERGY_COST = 60f;

    // ==================== 「遥久之歌」的状态本体 ====================

    /** 剩余刻数（0 = 没在唱）。存档键为 {@code song_ticks}。 */
    @DescSynced
    @Persisted(key = "song_ticks")
    protected int songTicks;

    public Vodyanitsa() {
        super(UID, 5, Component.translatable("character.name.vodyanitsa"),
                "minegenshin:hydro", CharacterAscendAttribute.HP,
                SKILL_COOLDOWN_TICKS, BURST_COOLDOWN_TICKS, BURST_ENERGY_COST, ID,
                Map.of(
                        ModAttributes.MAX_HP.getId(), VodyanitsaAttributeConfig::getAllHp,
                        ModAttributes.ATK.getId(), VodyanitsaAttributeConfig::getAllAtk,
                        ModAttributes.DEF.getId(), VodyanitsaAttributeConfig::getAllDef
                ));
        // 三个协作者都在无参构造器里建（客户端反序列化走 newInstance()，会跑到这里）。
        this.skill = new VodyanitsaSkill();
        this.talent = new VodyanitsaTalent();
        this.constellation = new VodyanitsaConstellation();
    }

    @Override
    public com.linweiyun.genshin.core.system.combat.action.data.CharacterActionData getActionData() {
        return VodyanitsaResources.ACTION_DATA;
    }

    @Override
    public Map<Identifier, Supplier<List<? extends Integer>>> getStatGrowthMap() {
        return Map.of(
                ModAttributes.MAX_HP.getId(), VodyanitsaAttributeConfig::getAllHp,
                ModAttributes.ATK.getId(), VodyanitsaAttributeConfig::getAllAtk,
                ModAttributes.DEF.getId(), VodyanitsaAttributeConfig::getAllDef
        );
    }

    // ==================== 「遥久之歌」的读写 ====================

    /** 写入「遥久之歌」的时长（由 {@link VodyanitsaSkill#startSong} 算好后传进来）。 */
    public void startSong(int durationTicks) {
        this.songTicks = durationTicks;
        syncRealtimeState();
    }

    public boolean isSongActive() {
        return songTicks > 0;
    }

    /** 遥久之歌还剩多少刻（6 命用这个时长发给队友）。 */
    public int songTicksRemaining() {
        return Math.max(0, songTicks);
    }

    // ==================== tick（只做调度） ====================

    @Override
    protected void characterTick(Player player) {
        if (player.level().isClientSide()) return;

        // 4 命的层数即使在遥久之歌结束后也要继续独立倒计时
        getConstellationObj().tick(player, this);

        if (songTicks <= 0) return;

        songTicks--;
        if (songTicks <= 0) {
            syncRealtimeState();
            return;
        }

        // 定时攻击 / 回血 / 减抗（战技的持续部分，实现在 VodyanitsaSkill）
        getSkill().tick(player, this);

        // 流荡风旋的风抗降低到期 → 一并清理（突破天赋 1，实现在 VodyanitsaTalent）
        if (player.level() instanceof ServerLevel serverLevel
                && getTalent() instanceof VodyanitsaTalent passive) {
            passive.clearExpiredWindShred(serverLevel);
        }
    }
}
