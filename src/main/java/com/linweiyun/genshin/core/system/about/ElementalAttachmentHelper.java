package com.linweiyun.genshin.core.system.about;

import com.linweiyun.genshin.core.attachment.StatusContainer;
import com.linweiyun.genshin.core.character.PGCharacter;
import com.linweiyun.genshin.core.element.GenshinElement;
import com.linweiyun.genshin.core.status.StatusInstance;
import com.linweiyun.genshin.core.system.about.host.ElementalHost;
import com.linweiyun.genshin.core.system.about.host.EntityHost;
import com.linweiyun.genshin.core.system.reaction.ElementalReactionManager;
import com.linweiyun.genshin.core.system.reaction.ReactionContext;
import com.linweiyun.genshin.util.log.LogGroup;
import com.linweiyun.genshin.util.log.ModLog;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;

/**
 * <b>元素附着的唯一入口</b> —— 「附着 → 附着内反应 → 反应引发效果」这一整条链都在这里。
 *
 * <p>调用方只需要说「谁、什么元素、什么来源、多少量」；要不要收（宿主筛查）、怎么写（覆盖规则、
 * 损耗、常驻补量）、挂上以后会不会反应（第二段筛查 + 反应系统）、反应完还剩什么（后手残留），
 * 全部由本入口负责。
 *
 * <p>这也是「环境附着与攻击附着必须是同一个体系」的落点：以前反应是由<b>调用方</b>触发的
 * （攻击管线记得调、方块入口记得调、环境路径忘了调），于是「挂冰的怪走进水里」不会冻结。
 * 现在只要经过本入口，反应一定会被尝试。
 *
 * <pre>
 * attach(host, element, source, profile[, ctx])           ← 会触发反应（默认）
 * attachInternal(host, element, source, profile[, ctx])   ← 反应内部的二次写入用，不再触发反应（防递归）
 * </pre>
 */
public class ElementalAttachmentHelper {

    public static final Logger LOGGER = ModLog.getLogger(LogGroup.ELEMENT);

    // ========== 附着入口 —— 唯一入口是「宿主」 ==========

    /** 不带上下文的附着（环境、自身、反应内）。 */
    public static AttachResult attach(ElementalHost host, GenshinElement element,
                                      AttachmentSource source, AttachmentProfile profile) {
        return attach(host, element, source, profile, AttachContext.ENVIRONMENT);
    }

    /** 带上下文的附着（攻击型附着用 {@link AttachContext#attack}）。 */
    public static AttachResult attach(ElementalHost host, GenshinElement element,
                                      AttachmentSource source, AttachmentProfile profile,
                                      AttachContext context) {
        return doAttach(host, element, source, profile, context, true);
    }

    /** 便捷重载：带角色与附着时刻（用于反应贡献者追踪）。 */
    public static AttachResult attach(ElementalHost host, GenshinElement element,
                                      AttachmentSource source, AttachmentProfile profile,
                                      PGCharacter character, long gameTime) {
        return attach(host, element, source, profile,
                new AttachContext(character, gameTime, null, null, null));
    }

    /**
     * <b>反应内部的二次写入</b> —— 例如冻结反应生成冻元素、扩散把元素带到旁边的人身上。
     * 只负责把附着写进去，不再触发反应（否则会形成「反应生附着、附着再反应」的递归）。
     */
    public static AttachResult attachInternal(ElementalHost host, GenshinElement element,
                                              AttachmentSource source, AttachmentProfile profile) {
        return doAttach(host, element, source, profile, AttachContext.ENVIRONMENT, false);
    }

    public static AttachResult attachInternal(ElementalHost host, GenshinElement element,
                                              AttachmentSource source, AttachmentProfile profile,
                                              AttachContext context) {
        return doAttach(host, element, source, profile, context, false);
    }

    /**
     * 反应内部/自附着的写入，用<b>调用方已经拿到的容器</b>，不再回头问宿主 ——
     * 避免「宿主 container() → 补自附着 → 又要 container()」的递归。
     *
     * @param container 已解析好的容器（通常是 {@code host.container()} 的结果）
     */
    public static AttachResult attachInternalTo(StatusContainer container, ElementalHost host,
                                                GenshinElement element, AttachmentSource source,
                                                AttachmentProfile profile) {
        return doAttach(container, host, element, source, profile, AttachContext.ENVIRONMENT, false);
    }

    /**
     * 生物附着便捷重载 —— 生物宿主 + 实体自带容器。
     *
     * <p>若容器不在这个实体上（例如挂在「出战角色」身上），请改用 {@code CharacterHost}，
     * 不要用这个把「目标」和「容器」拆开。
     */
    public static AttachResult attach(LivingEntity target, StatusContainer container,
                                      GenshinElement element,
                                      AttachmentSource source,
                                      AttachmentProfile profile) {
        return doAttach(container, EntityHost.of(target), element, source, profile,
                AttachContext.ENVIRONMENT, true);
    }

    /** 带角色信息的生物附着重载。 */
    public static AttachResult attach(LivingEntity target, StatusContainer container,
                                      GenshinElement element,
                                      AttachmentSource source,
                                      AttachmentProfile profile,
                                      PGCharacter character, long gameTime) {
        return doAttach(container, EntityHost.of(target), element, source, profile,
                new AttachContext(character, gameTime, null, null, null), true);
    }

    // ========== 消耗（元素反应调用）==========

    /** 从容器里消耗指定元素的附着量，返回实际消耗量。 */
    public static float consume(StatusContainer container, GenshinElement element, float amount) {
        if (container == null) return 0f;
        return consumeInternal(container, element, amount);
    }

    private static float consumeInternal(StatusContainer container, GenshinElement element, float amount) {
        float remaining = amount;
        for (StatusInstance inst : container.getAll()) {
            if (inst.isFinished()) continue;
            if (!(inst instanceof ElementalAttachmentInstance ea)) continue;
            if (ea.getElement() != element) continue;
            float consumed = ea.consume(remaining);
            remaining -= consumed;
            if (remaining <= 0) break;
        }
        return amount - remaining;
    }

    // ========== 核心附着逻辑 ==========

    /**
     * 写附着 + （可选）触发反应。
     *
     * <p>顺序固定：宿主筛查 → 写进容器 → 触发反应。宿主拒收时什么都不写、也不反应
     * ——「没挂上去就没有反应」是附着与反应之间唯一的顺序约束。
     */
    private static AttachResult doAttach(ElementalHost host, GenshinElement element,
                                         AttachmentSource source, AttachmentProfile profile,
                                         AttachContext context, boolean react) {
        if (host == null) {
            return AttachResult.REJECTED;
        }
        AttachContext ctx = context == null ? AttachContext.ENVIRONMENT : context;
        return doAttach(host.container(), host, element, source, profile, ctx, react);
    }

    private static AttachResult doAttach(StatusContainer container, ElementalHost host,
                                         GenshinElement element,
                                         AttachmentSource source,
                                         AttachmentProfile profile,
                                         AttachContext ctx, boolean react) {
        if (container == null) {
            return AttachResult.REJECTED;
        }

        // 0. 先问宿主收不收这次附着
        if (host != null && !host.acceptsElement(element, source, profile)) {
            return AttachResult.REJECTED;
        }

        PGCharacter character = ctx.character();
        long gameTime = ctx.gameTime();

        // 1. 实际附着量 = baseQuantity × lossMultiplier
        float actualQuantity = profile.actualQuantity();

        // 2. 查找容器里"同元素 + 同 source"的已有实例
        ElementalAttachmentInstance existing = findMatching(container, element, source, character);

        if (existing == null) {
            // 无匹配实例 → 新建
            ElementalAttachmentInstance newInst =
                    new ElementalAttachmentInstance(element, source, profile, actualQuantity);
            if (character != null) {
                newInst.setSourceCharacter(character, gameTime);
            } else if (gameTime > 0L) {
                newInst.setAttachTick(gameTime);
            }
            if (host != null) {
                host.onElementAttached(element);
            }
            newInst.setHost(host);
            container.add(newInst);
            return finish(host, container, element, source, profile, ctx, react, actualQuantity);
        }

        // 3. 同角色重复附着：新到期时间更长则延长
        if (character != null && existing.hasSourceCharacter()) {
            long currentTick = gameTime;
            long existingEnd = existing.getDecayEndTick();
            float newDurationTicks = profile.getDurationSeconds() * 20f;
            long newEnd = currentTick + (long) newDurationTicks;
            if (newEnd > existingEnd) {
                existing.refreshQuantity(actualQuantity);
                existing.overrideDecayRate(profile.getDecayPerSecond());
                existing.setAttachTick(currentTick);
            } else {
                // 支线未超过主线：仅刷新角色信息，不改变到期时间
                existing.setSourceCharacter(character, gameTime);
            }
            return finish(host, container, element, source, profile, ctx, react, actualQuantity);
        }

        if (actualQuantity <= existing.getUnit()) {
            // 后手段量 ≤ 先手段量 → 不覆盖，但仍记录角色信息
            if (character != null) {
                existing.setSourceCharacter(character, gameTime);
            }
            return finish(host, container, element, source, profile, ctx, react, actualQuantity);
        }

        // 4. 发生覆盖
        existing.refreshQuantity(actualQuantity);
        if (character != null) {
            existing.setSourceCharacter(character, gameTime);
        }
        if (element.canOverrideDecay()) {
            // 火/激/燃：直接替换为新的衰减速率；其他元素继承原先的（什么都不做）
            existing.overrideDecayRate(profile.getDecayPerSecond());
        }
        return finish(host, container, element, source, profile, ctx, react, actualQuantity);
    }

    /**
     * 附着已写进去之后收尾：触发反应（如果这次附着允许）并返回结果。
     *
     * <p>注意「触发反应」与「覆盖没覆盖」无关：宿主收下了这次附着，就该问一次反应
     * （先手元素可能就是目标身上别的东西），这与既有行为一致。
     */
    private static AttachResult finish(ElementalHost host, StatusContainer container,
                                       GenshinElement element, AttachmentSource source,
                                       AttachmentProfile profile, AttachContext ctx,
                                       boolean react, float actualQuantity) {
        if (!react) {
            return AttachResult.ATTACHED_NO_REACTION;
        }

        float reactionUnit = ctx.reactionUnit() != null ? ctx.reactionUnit() : actualQuantity;
        ReactionContext reactionContext = new ReactionContext(
                element, reactionUnit, source, profile,
                ctx.damageSpec(), ctx.attackerEntity(),
                container, host == null ? null : host.entity(), host);
        return new AttachResult(true, ElementalReactionManager.tryReactFor(host, reactionContext));
    }

    // ========== 查找工具 ==========

    private static ElementalAttachmentInstance findMatching(
            StatusContainer container, GenshinElement element, AttachmentSource source,
            PGCharacter character) {
        return (ElementalAttachmentInstance) container.find(inst -> {
            if (inst.isFinished()) return false;
            if (!(inst instanceof ElementalAttachmentInstance ea)) return false;
            if (ea.getElement() != element || ea.getSource() != source) return false;
            if (character != null && ea.hasSourceCharacter()
                    && ea.getSourceCharacter() != character) {
                return false;
            }
            return true;
        });
    }
}
