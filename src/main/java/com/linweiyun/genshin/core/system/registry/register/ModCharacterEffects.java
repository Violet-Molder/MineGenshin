package com.linweiyun.genshin.core.system.registry.register;

import com.linweiyun.genshin.content.effect.character.CharacterEffectInstance;
import com.linweiyun.genshin.content.effect.character.ICharacterEffect;
import com.linweiyun.genshin.content.effect.character.artifact.CrimsonWitch2;
import com.linweiyun.genshin.content.effect.character.artifact.CrimsonWitch4;
import com.linweiyun.genshin.content.effect.character.artifact.ScarletProof2;
import com.linweiyun.genshin.content.effect.character.artifact.ScarletProof4;
import com.linweiyun.genshin.content.effect.character.artifact.ScarletProofBuffEffect;
import com.linweiyun.genshin.content.effect.character.artifact.TenacityOfTheMillelith2;
import com.linweiyun.genshin.content.effect.character.artifact.TenacityOfTheMillelith4;
import com.linweiyun.genshin.content.effect.character.artifact.TenacityOfTheMillelithBuff;
import com.linweiyun.genshin.content.effect.character.impl.DamageBonusEffect;
import com.linweiyun.genshin.content.effect.character.impl.DiebianForsakenWindEffect;
import com.linweiyun.genshin.content.effect.character.impl.DiebianLoyalWindEffect;
import com.linweiyun.genshin.content.effect.character.impl.RadianceStellarConduceEffect;
import com.linweiyun.genshin.content.effect.character.impl.StellarConduceFieldEffect;
import com.linweiyun.genshin.content.effect.character.impl.RadianceStellarSwirlEffect;
import com.linweiyun.genshin.content.effect.character.shenhe.IcyQuillEffect;
import com.linweiyun.genshin.core.system.registry.ModRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCharacterEffects {
    public static final DeferredRegister<ICharacterEffect> CHARACTER_EFFECTS = ModRegistries.CHARACTER_EFFECTS;

    /**
     * 占位效果 —— <b>必须注册</b>，id 就是 {@code ModRegistries} 里配的默认键
     * {@code minegenshin:empty}。
     *
     * <p>效果注册表是 {@code DefaultedMappedRegistry}（`ModRegistries.java` 的
     * {@code .defaultKey(Minegenshin.id("empty"))`），它的语义是「查到不存在的键 → 返回默认值」。
     * 如果默认键没有任何注册项，它内部的默认 Holder 就是 null，
     * 于是<b>任何</b>一次「查一个不存在的效果 id」都会在 {@code getValue} 里空指针崩溃
     * （2026-09-21 的 Ticking player 崩溃就是这么来的：
     * 存档里存着参数化 buff 的<b>实例 id</b>（`shenhe_ascend2_tap`），读档时拿它当注册名查表）。
     */
    public static final DeferredHolder<ICharacterEffect, CharacterEffectInstance.DummyEffect> EMPTY_EFFECT =
            CHARACTER_EFFECTS.register(
                    "empty",
                    () -> CharacterEffectInstance.DummyEffect.INSTANCE
            );

    public static final DeferredHolder<ICharacterEffect, IcyQuillEffect> ICY_QUILL_EFFECT = CHARACTER_EFFECTS.register(
            "icy_quill",
            IcyQuillEffect::new
    );

    // ======== 通用效果 ========
    public static final DeferredHolder<ICharacterEffect, DamageBonusEffect> DAMAGE_BONUS_EFFECT = CHARACTER_EFFECTS.register(
            "damage_bonus",
            () -> new DamageBonusEffect()
    );

    // ======== 星烁反应效果（辉映·星烁的两个分支，互斥且星超导优先）========
    public static final DeferredHolder<ICharacterEffect, RadianceStellarSwirlEffect> RADIANCE_STELLAR_SWIRL_EFFECT = CHARACTER_EFFECTS.register(
            "radiance_stellar_swirl",
            RadianceStellarSwirlEffect::new
    );

    /** 辉映·星超导 —— 暂时还没有会上这个效果的角色，接口和 effect 先备好。 */
    public static final DeferredHolder<ICharacterEffect, RadianceStellarConduceEffect> RADIANCE_STELLAR_CONDUCE_EFFECT = CHARACTER_EFFECTS.register(
            "radiance_stellar_conduce",
            RadianceStellarConduceEffect::new
    );

    public static final DeferredHolder<ICharacterEffect, StellarConduceFieldEffect> STELLAR_CONDUCE_FIELD_EFFECT = CHARACTER_EFFECTS.register(
            "stellar_conduce_field",
            StellarConduceFieldEffect::new
    );

    // ======= 圣遗物套装效果 =======
    public static final DeferredHolder<ICharacterEffect, CrimsonWitch2> CRIMSON_WITCH2_EFFECT = CHARACTER_EFFECTS.register(
            "crimson_witch2",
            CrimsonWitch2::new
    );
    public static final DeferredHolder<ICharacterEffect, CrimsonWitch4> CRIMSON_WITCH4_EFFECT = CHARACTER_EFFECTS.register(
            "crimson_witch4",
            CrimsonWitch4::new
    );

    // ---- 血红之证（Scarlet Proof）----
    // 2件套：攻击力 +18%
    public static final DeferredHolder<ICharacterEffect, ScarletProof2> SCARLET_PROOF2_EFFECT = CHARACTER_EFFECTS.register(
            "scarlet_proof2",
            ScarletProof2::new
    );
    // 4件套：只是「穿着四件套」的标记，真正的加成在触发buff里
    public static final DeferredHolder<ICharacterEffect, ScarletProof4> SCARLET_PROOF4_EFFECT = CHARACTER_EFFECTS.register(
            "scarlet_proof4",
            ScarletProof4::new
    );
    /** 四件套触发buff：触发星扩散后 10 秒内 暴击率+16% / 星扩散伤害+40%。 */
    public static final DeferredHolder<ICharacterEffect, ScarletProofBuffEffect> SCARLET_PROOF_BUFF_EFFECT = CHARACTER_EFFECTS.register(
            "scarlet_proof_buff",
            ScarletProofBuffEffect::new
    );

    // ---- 千岩牢固（Tenacity of the Millelith）----
    // 2件套：生命值上限 +20%
    public static final DeferredHolder<ICharacterEffect, TenacityOfTheMillelith2> TENACITY_OF_THE_MILLELITH2_EFFECT =
            CHARACTER_EFFECTS.register(
                    "tenacity_of_the_millelith2",
                    TenacityOfTheMillelith2::new
            );
    // 4件套：只是「穿着四件套」的标记，真正的加成在触发buff里
    public static final DeferredHolder<ICharacterEffect, TenacityOfTheMillelith4> TENACITY_OF_THE_MILLELITH4_EFFECT =
            CHARACTER_EFFECTS.register(
                    "tenacity_of_the_millelith4",
                    TenacityOfTheMillelith4::new
            );
    /** 四件套触发buff：元素战技命中敌人后 3 秒内 攻击力+20%。 */
    public static final DeferredHolder<ICharacterEffect, TenacityOfTheMillelithBuff> TENACITY_OF_THE_MILLELITH_BUFF_EFFECT =
            CHARACTER_EFFECTS.register(
                    "tenacity_of_the_millelith_buff",
                    TenacityOfTheMillelithBuff::new
            );

    /** 蝶变 · 忠忱之风：暴击伤害 +56%（10 秒）。 */
    public static final DeferredHolder<ICharacterEffect, DiebianLoyalWindEffect> DIEBIAN_LOYAL_WIND_EFFECT =
            CHARACTER_EFFECTS.register(
                    "diebian_loyal_wind",
                    DiebianLoyalWindEffect::new
            );

    /** 蝶变 · 叛弃之风：星扩散反应伤害 +36%（10 秒）。 */
    public static final DeferredHolder<ICharacterEffect, DiebianForsakenWindEffect> DIEBIAN_FORSAKEN_WIND_EFFECT =
            CHARACTER_EFFECTS.register(
                    "diebian_forsaken_wind",
                    DiebianForsakenWindEffect::new
            );

    /** 沃雅妮莎突破天赋 2：领唱（挂当前场上角色，25 层）。 */
    public static final DeferredHolder<ICharacterEffect,
            com.linweiyun.genshin.content.effect.character.vodyanitsa.VodyanitsaSongEffects.Antiphon>
            VODYANITSA_ANTIPHON_EFFECT = CHARACTER_EFFECTS.register(
                    "vodyanitsa_antiphon",
                    com.linweiyun.genshin.content.effect.character.vodyanitsa.VodyanitsaSongEffects.Antiphon::new
            );

    /** 沃雅妮莎突破天赋 2：重唱（挂后台/附近其他角色，10 层）。 */
    public static final DeferredHolder<ICharacterEffect,
            com.linweiyun.genshin.content.effect.character.vodyanitsa.VodyanitsaSongEffects.Refrain>
            VODYANITSA_REFRAIN_EFFECT = CHARACTER_EFFECTS.register(
                    "vodyanitsa_refrain",
                    com.linweiyun.genshin.content.effect.character.vodyanitsa.VodyanitsaSongEffects.Refrain::new
            );

    /** 沃雅妮莎 1 命：聚光灯下的水华（队伍附近攻击力 +1% 她的生命上限，5 秒）。 */
    public static final DeferredHolder<ICharacterEffect,
            com.linweiyun.genshin.content.effect.character.vodyanitsa.VodyanitsaBuffs.Spotlight>
            VODYANITSA_SPOTLIGHT_EFFECT = CHARACTER_EFFECTS.register(
                    "vodyanitsa_spotlight",
                    com.linweiyun.genshin.content.effect.character.vodyanitsa.VodyanitsaBuffs.Spotlight::new);

    /** 沃雅妮莎 2/6 命：黑与白的双音（水/冰暴伤 +50%）。 */
    public static final DeferredHolder<ICharacterEffect,
            com.linweiyun.genshin.content.effect.character.vodyanitsa.VodyanitsaBuffs.DuetElement>
            VODYANITSA_DUET_ELEMENT_EFFECT = CHARACTER_EFFECTS.register(
                    "vodyanitsa_duet_element",
                    com.linweiyun.genshin.content.effect.character.vodyanitsa.VodyanitsaBuffs.DuetElement::new);

    /** 沃雅妮莎 2/6 命：黑与白的双音（流荡风旋版，星扩散反应伤害暴伤 +60%）。 */
    public static final DeferredHolder<ICharacterEffect,
            com.linweiyun.genshin.content.effect.character.vodyanitsa.VodyanitsaBuffs.DuetStellar>
            VODYANITSA_DUET_STELLAR_EFFECT = CHARACTER_EFFECTS.register(
                    "vodyanitsa_duet_stellar",
                    com.linweiyun.genshin.content.effect.character.vodyanitsa.VodyanitsaBuffs.DuetStellar::new);

    /** 沃雅妮莎 6 命：遥久之歌期间队伍附近星扩散擢升 30% / 水冰伤害 +60%。 */
    public static final DeferredHolder<ICharacterEffect,
            com.linweiyun.genshin.content.effect.character.vodyanitsa.VodyanitsaBuffs.Glimmer>
            VODYANITSA_GLIMMER_EFFECT = CHARACTER_EFFECTS.register(
                    "vodyanitsa_glimmer",
                    com.linweiyun.genshin.content.effect.character.vodyanitsa.VodyanitsaBuffs.Glimmer::new);

    /** 漩流颂歌 · 告真的蜜酿（装备者侧）：每层生命值上限 +4%，最多 3 层 / 10 秒。 */
    public static final DeferredHolder<ICharacterEffect,
            com.linweiyun.genshin.content.effect.character.impl.WhirlflowHymnEffects.Mead>
            WHIRLFLOW_MEAD_EFFECT = CHARACTER_EFFECTS.register(
                    "whirlflow_hymn_mead",
                    com.linweiyun.genshin.content.effect.character.impl.WhirlflowHymnEffects.Mead::new);

    /** 漩流颂歌 · 告真的蜜酿（当前场上角色侧）：按装备者生命值上限给攻击力% 。 */
    public static final DeferredHolder<ICharacterEffect,
            com.linweiyun.genshin.content.effect.character.impl.WhirlflowHymnEffects.MeadAtk>
            WHIRLFLOW_MEAD_ATK_EFFECT = CHARACTER_EFFECTS.register(
                    "whirlflow_hymn_mead_atk",
                    com.linweiyun.genshin.content.effect.character.impl.WhirlflowHymnEffects.MeadAtk::new);

    public static void register(IEventBus eventBus) {
        CHARACTER_EFFECTS.register(eventBus);
    }
}
