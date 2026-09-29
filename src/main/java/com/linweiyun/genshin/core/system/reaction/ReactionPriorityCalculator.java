package com.linweiyun.genshin.core.system.reaction;

import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.attachment.StatusContainer;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.character.catalyst.columbina.Columbina;
import com.linweiyun.genshin.core.character.util.capability.IStellarStateHolder;
import com.linweiyun.genshin.core.element.GenshinElement;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.status.StatusInstance;
import com.linweiyun.genshin.core.system.about.ElementalAttachmentInstance;
import com.linweiyun.genshin.core.system.performance.TickSnapshot;
import net.minecraft.server.level.ServerLevel;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class ReactionPriorityCalculator {

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.ELEMENT);

    /** 未登记在默认顺序表里的主元素统一排到最后（比表内任何下标都大）。 */
    public static final int UNKNOWN_PRIORITY = 50;

    /**
     * 默认反应优先级顺序 —— <b>唯一真相</b>。
     *
     * <p>后手元素与各先手元素反应时，按先手元素（主元素）在本表里的下标从小到大排序；
     * 索引越靠前的先手元素越先被反应消耗。表内没有的元素排到 {@link #UNKNOWN_PRIORITY}。
     */
    private static final GenshinElement[] DEFAULT_ORDER = {
            ModElements.ANEMO.get(), ModElements.CYRO.get(), ModElements.ELECTRO.get(),
            ModElements.HYDRO.get(), ModElements.FROZEN.get(), ModElements.PYRO.get(),
            ModElements.DENDRO.get(), ModElements.AGGRAVATE.get(), ModElements.GEO.get()
    };

    /**
     * 算一个反应的具体优先级 —— 只看先手（目标身上已有）元素的主元素排在顺序表里的位置。
     *
     * <p>注册时手填了非负 {@code basePriority} 的反应不走这里；只有填 -1（表示「用默认顺序」）
     * 的反应才会调用本方法，目前是月感电。
     *
     * @param defenderElement 先手附着的元素（目标身上已有的那一个）
     */
    public static int computeFor(GenshinElement defenderElement) {
        return priorityOf(defenderElement.getMainElement());
    }

    /** 主元素在默认顺序表里的下标；不在表内返回 {@link #UNKNOWN_PRIORITY}。 */
    public static int priorityOf(GenshinElement mainElement) {
        for (int i = 0; i < DEFAULT_ORDER.length; i++) {
            if (DEFAULT_ORDER[i] == mainElement) return i;
        }
        return UNKNOWN_PRIORITY;
    }

    public static boolean hasFrozen(StatusContainer container) {
        for (StatusInstance inst : container.getAll()) {
            if (inst.isFinished()) continue;
            if (inst instanceof ElementalAttachmentInstance ea
                    && ea.getElement() == ModElements.FROZEN.get()) return true;
        }
        return false;
    }

    public static boolean hasAggravate(StatusContainer container) {
        for (StatusInstance inst : container.getAll()) {
            if (inst.isFinished()) continue;
            if (inst instanceof ElementalAttachmentInstance ea
                    && ea.getElement() == ModElements.AGGRAVATE.get()) return true;
        }
        return false;
    }

    public static boolean hasColumbinaInParty(ReactionContext context) {
        Entity attacker = context.attackerEntity();
        if (!(attacker instanceof Player player)) return false;
        if (!(player.level() instanceof ServerLevel level)) return false;
        return hasColumbinaInParty(level);
    }

    /** 队伍扫描的每刻快照（见 {@link TickSnapshot}） */
    private static final TickSnapshot COLUMBINA_SNAPSHOT = new TickSnapshot();

    /**
     * 队伍里有没有哥伦比娅。
     *
     * <p>这个方法挂在「每实体每刻」的感电自激活判定里，而它要遍历全维度玩家 × 队伍四人；
     * 于是按「维度 + 游戏刻」缓存一格 —— 队伍构成一刻之内不会变，
     * 同一刻第二次问就只是一次查表。换人最迟下一 tick 生效。</p>
     */
    public static boolean hasColumbinaInParty(ServerLevel level) {
        long tick = level.getGameTime();
        Boolean cached = COLUMBINA_SNAPSHOT.getOrNull(level, tick);
        if (cached != null) {
            return cached;
        }
        boolean value = scanColumbinaInParty(level);
        COLUMBINA_SNAPSHOT.put(level, tick, value);
        return value;
    }

    /** 服务器停机时清掉快照（会持有 ServerLevel 引用）。 */
    public static void clearSnapshots() {
        COLUMBINA_SNAPSHOT.clear();
    }

    private static boolean scanColumbinaInParty(ServerLevel level) {
        for (Player p : level.players()) {
            PlayerCharactersAttachment att = p.getData(
                    AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
            for (int i = 0; i < 4; i++) {
                PGCharacter character = att.getPartyCharacter(i);
                if (character instanceof Columbina) return true;
            }
        }
        return false;
    }

    /**
     * 队伍里有没有<b>星扩散户口</b> —— 有的话冰扩散要转成星扩散。
     *
     * <p>注意查的是户口（转化 + 体系加成，写在同一个天赋里），
     * 不是「能进入星扩散状态」的角色（那个是 {@code IStellarStateHolder}，两回事）。
     */
    public static boolean hasStellarSwirlHousehold(ServerLevel level) {
        return StellarGlimmer.swirlHousehold(level) != null;
    }

    public static boolean hasStellarSwirlParticipant(ServerLevel level) {
        return hasStellarSwirlHousehold(level);
    }

    /** 队伍里能进入星烁状态的角色（和户口无关）。 */
    public static List<PGCharacter> getStellarStateHolders(ServerLevel level) {
        List<PGCharacter> result = new ArrayList<>();
        for (Player p : level.players()) {
            PlayerCharactersAttachment att = p.getData(
                    AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
            for (int i = 0; i < 4; i++) {
                PGCharacter character = att.getPartyCharacter(i);
                if (character instanceof IStellarStateHolder holder
                        && holder.canHoldStellarState()) {
                    result.add(character);
                }
            }
        }
        return result;
    }
}
