package com.linweiyun.genshin.core.character;

import com.linweiyun.genshin.config.character.CharacterXpConfig;
import com.linweiyun.genshin.content.skill_node.ElementalOrbSpawner;
import com.linweiyun.genshin.content.attribute.AttributeType;
import com.linweiyun.genshin.content.effect.character.CharacterEffectContainer;
import com.linweiyun.genshin.content.effect.character.CharacterEffectHelper;
import com.linweiyun.genshin.content.effect.character.CharacterEffectInstance;
import com.linweiyun.genshin.content.effect.character.ICharacterEffect;
import com.linweiyun.genshin.content.effect.character.artifact.ArtifactSetEffect;
import com.linweiyun.genshin.content.items.artifact.ArtifactItem;
import com.linweiyun.genshin.content.items.artifact.ArtifactLevelData;
import com.linweiyun.genshin.content.items.artifact.ArtifactSet;
import com.linweiyun.genshin.content.items.artifact.inventory.ArtifactInventory;
import com.linweiyun.genshin.content.items.artifact.type.ArtifactType;
import com.linweiyun.genshin.content.items.component.ArtifactStatsComponent;
import com.linweiyun.genshin.content.items.component.WeaponStatsComponent;
import com.linweiyun.genshin.content.items.weapon.WeaponItem;
import com.linweiyun.genshin.content.items.weapon.WeaponLevelData;
import com.linweiyun.genshin.content.stat.TeyvatItemStat;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.core.attachment.PlayerCharactersAttachment;
import com.linweiyun.genshin.core.network.NetworkManager;
import com.linweiyun.genshin.core.character.talent.ConstellationBase;
import com.linweiyun.genshin.core.character.talent.SkillBase;
import com.linweiyun.genshin.core.character.talent.TalentBase;
import com.linweiyun.genshin.core.element.GenshinElement;
import com.linweiyun.genshin.core.element.ModElements;
import com.linweiyun.genshin.core.system.combat.action.ActionKind;
import com.linweiyun.genshin.core.system.combat.action.ActionManager;
import com.linweiyun.genshin.core.system.combat.action.ActionSet;
import com.linweiyun.genshin.core.system.registry.ModRegistries;
import com.linweiyun.genshin.core.system.registry.register.ModAttributes;
import com.linweiyun.genshin.core.system.registry.register.ModDataComponents;
import com.linweiyun.genshin.core.character.CharacterAscendAttribute;
import com.linweiyun.genshin.core.sync.ISyncCharacter;
import com.linweiyun.genshin.core.system.poise.WeaponPoiseTable;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import com.lowdragmc.lowdraglib2.syncdata.storage.IManagedStorage;
import com.linweiyun.genshin.core.log.LogGroup;
import com.linweiyun.genshin.core.log.ModLog;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class PGCharacter implements IPersistedSerializable, ISyncCharacter {

    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    @Override public IManagedStorage getSyncStorage() { return syncStorage; }
    @Override public PGCharacter getSelfCharacter() { return this; }
    @Override public void notifyPersistence() { }

    @Getter
    @Setter
    @Persisted(key = "character_uuid")
    protected int characterUUID;
    @Getter
    @Setter
    @Persisted(key = "star_rating")
    protected int starRating;
    @Getter
    @Setter
    @Persisted(key = "name")
    protected Component name;
    @Persisted(key = "elemental")
    protected String elementalId;
    private transient GenshinElement elemental;
    @Getter
    @Setter
    @Persisted(key = "ascend_attribute")
    protected CharacterAscendAttribute ascendAttribute;
    @Getter
    @Setter
    @Persisted(key = "texture_id")
    protected String textureId;
    @Getter
    @Setter
    @Persisted(key = "data")
    protected PGCharacterData data;

    private static final String SOURCE_WEAPON = "weapon";

    /**
     * 三大协作对象：技能（普攻/重击/战技/爆发）、天赋（被动）、命座。
     *
     * <p>三者都是 {@code transient}，实例只在角色<b>无参构造器</b>里创建。
     * 客户端反序列化走 {@code ModSyncAccessors.deserializeFromTag} 里的
     * {@code clazz.getDeclaredConstructor().newInstance()} —— <b>会</b>跑到子类的无参构造器，
     * 所以双端都有这三个对象（和重构前 {@code talent} 的做法一致）。
     *
     * <p>尽管如此，<b>出手前置判断</b>（{@link #canCast}）仍然只读
     * {@link PGCharacterData} 那类同步数据：协作者内部可能带着服务端专属的运行时状态
     * （CD、层数、窗口…），客户端那一份不一定和服务端同步过。
     */
    protected transient SkillBase skill;
    protected transient TalentBase talent;
    protected transient ConstellationBase constellation;

    /**
     * 角色自己的<b>配置页</b>（LDLib2 构建）。
     *
     * <p>和 {@link #skill} / {@link #talent} / {@link #constellation} 同一套路数：
     * 基类只留一个 {@code transient} 槽位 + 一个 getter，具体页面在<b>子类无参构造器</b>里 new 出来
     * 赋给它（客户端反序列化也会跑到子类无参构造器，所以双端都有实例）。
     * 没赋值的角色 {@link #hasConfigUI()} 为 false，按 N 会提示「该角色还没有配置页」，
     * 而不是弹一个空页面。
     *
     * <p>约定成 {@code transient} 而不是 {@code @Persisted}：页面是纯表现层对象，
     * 里面有 Scene / 控件树这些不该入档的东西，它读写的状态全在角色数据里。
     */
    protected transient ICharacterConfigUI configUI;

    /** 配置页对象；没有专属页的角色返回 null。 */
    @Nullable
    public ICharacterConfigUI getConfigUI() {
        return configUI;
    }

    public boolean hasConfigUI() {
        return configUI != null;
    }

    /**
     * 角色自己的<b>外观数据</b>——决定外观掩码（{@code PGCharacterData#getAppearance()}）
     * 里每一位是什么意思，以及配置页的外观区该列哪几项。
     *
     * <p>和 {@link #configUI} 同一套路数：基类给一个默认实例（只有「常态是否显示武器」一项），
     * 有自己外观的角色在子类无参构造器里换成自己的那一款
     * （申鹤 → {@code ShenheAppearanceData}，全武器类角色 → {@code AllWeaponAppearanceData}）。
     *
     * <p>它本身<b>不存状态</b>：掩码仍然是 {@code PGCharacterData} 上那一个 int，
     * 这里只提供"怎么读、怎么改"的方法，所以是 {@code transient} 且可以共用单例。
     */
    protected transient com.linweiyun.genshin.core.character.appearance.CharacterAppearanceData appearanceData =
            com.linweiyun.genshin.core.character.appearance.DefaultAppearanceData.INSTANCE;

    /** 这个角色的外观数据（掩码读法 + 配置页外观项）。 */
    public com.linweiyun.genshin.core.character.appearance.CharacterAppearanceData appearanceData() {
        return appearanceData;
    }

    /** 技能对象（普攻/重击/战技/爆发）。 */
    @Nullable
    public SkillBase getSkill() {
        return skill;
    }

    /**
     * 天赋对象（突破天赋 / 被动）。
     *
     * <p>没给自己天赋的角色拿到的是 {@link TalentBase#DEFAULT}（空实现）——
     * 「缺天赋」因此不会变成 {@code NullPointerException}，技能里那些
     * {@code character.getTalent().xxx()} 也不用处处判空。
     */
    public TalentBase getTalent() {
        return talent == null ? TalentBase.DEFAULT : talent;
    }

    /**
     * 命座对象。
     *
     * <p>注意和 {@link #getConstellation()} 区分：那个返回的是<b>命座等级</b>（int）。
     */
    @Nullable
    public ConstellationBase getConstellationObj() {
        return constellation;
    }

    private static final Logger LOGGER = ModLog.getLogger(LogGroup.CHARACTER);

    private final Map<String, ActionSet> actionSetCache = new HashMap<>();

    public PGCharacter() {
        this.data = new PGCharacterData();
        this.data.setParentCharacter(this);
    }

    public PGCharacter(
            int characterUUID, int starRating, Component name,
            String elementalId, CharacterAscendAttribute ascendAttribute,
            int skillMaxCooldownTick, int burstMaxCooldownTick,
            float maxObtainingEnergy, String textureId,
            Map<Identifier, Supplier<List<? extends Integer>>> statGrowthMap) {
        this.characterUUID = characterUUID;
        this.starRating = starRating;
        this.name = name;
        this.elementalId = elementalId;
        this.ascendAttribute = ascendAttribute;
        this.data = new PGCharacterData();
        this.data.setParentCharacter(this);
        this.data.setSkillShortMaxCooldownTick(skillMaxCooldownTick);
        this.data.setSkillLongMaxCooldownTick(skillMaxCooldownTick);
        this.data.setBurstMaxCooldownTick(burstMaxCooldownTick);
        this.data.setMaxObtainingEnergy(maxObtainingEnergy);
        this.textureId = textureId;
    }

    public PGCharacter(
            int characterUUID, int starRating, Component name,
            String elementalId, CharacterAscendAttribute ascendAttribute,
            int skillShortMaxCooldownTick, int skillLongMaxCooldownTick, int burstMaxCooldownTick,
            float maxObtainingEnergy, String textureId,
            Map<Identifier, Supplier<List<? extends Integer>>> statGrowthMap) {
        this.characterUUID = characterUUID;
        this.starRating = starRating;
        this.name = name;
        this.elementalId = elementalId;
        this.ascendAttribute = ascendAttribute;
        this.data = new PGCharacterData();
        this.data.setParentCharacter(this);
        this.data.setSkillShortMaxCooldownTick(skillShortMaxCooldownTick);
        this.data.setSkillLongMaxCooldownTick(skillLongMaxCooldownTick);
        this.data.setBurstMaxCooldownTick(burstMaxCooldownTick);
        this.data.setMaxObtainingEnergy(maxObtainingEnergy);
        this.textureId = textureId;
    }

    private AttributeType resolveType(Identifier id) {
        return ModAttributes.ATTRIBUTES.getRegistry().get().getValue(id);
    }

    public Class<? extends WeaponItem> getAllowedWeaponClass() {
        return WeaponItem.class;
    }

    // ============ 武器角色（这个角色属于哪一类、现在按哪一类算）============

    /**
     * 这个角色属于<b>哪一类武器角色</b>（标准六种 ＋ 只有角色才有的「全武器类」）。
     *
     * <p>默认按「角色限定的武器类」认；{@code getAllowedWeaponClass()} 放行到
     * {@link WeaponItem} 这一层（= 没限定）就是 {@link CharacterWeaponClass#ALL_WEAPON}，
     * {@link com.linweiyun.genshin.core.character.allweapon.AllWeaponCharacter} 也显式给这一档。
     */
    public CharacterWeaponClass characterWeaponClass() {
        return CharacterWeaponClass.of(WeaponPoiseTable.weaponOfClass(getAllowedWeaponClass()));
    }

    /** 是不是全武器类角色（第七种）。 */
    public boolean isAllWeaponCharacter() {
        return characterWeaponClass() == CharacterWeaponClass.ALL_WEAPON;
    }

    /**
     * 这个角色<b>现在按哪一类武器算</b> —— 标准的六种之一（认不出来时给
     * {@link WeaponPoiseTable.WeaponClass#UNKNOWN}）。
     *
     * <p>单武器角色恒等于自己那一类；<b>全武器类角色跟着当前选中的武器种类走</b>
     * （见 {@code AllWeaponCharacter#currentWeaponType()}）。削韧、冲击、伤害查表都读它。
     */
    public WeaponPoiseTable.WeaponClass currentWeaponType() {
        WeaponPoiseTable.WeaponClass type = characterWeaponClass().weaponType();
        return type == null ? WeaponPoiseTable.WeaponClass.UNKNOWN : type;
    }

    /**
     * 这个角色现在算不算某一类<b>武器角色</b> —— 取代原来散在各处的
     * {@code character instanceof SwordCharacter} 这类硬判。
     *
     * <p>对全武器类角色来说，问的是"现在这一档是不是它选中的那一类"。
     */
    public boolean isWeaponCharacter(WeaponPoiseTable.WeaponClass weaponType) {
        return weaponType != null && weaponType != WeaponPoiseTable.WeaponClass.UNKNOWN
                && currentWeaponType() == weaponType;
    }

    /** 是不是单手剑角色（= 原来的 {@code this instanceof SwordCharacter}）。 */
    public boolean isSwordCharacter() {
        return isWeaponCharacter(WeaponPoiseTable.WeaponClass.SWORD);
    }

    /** 是不是大剑角色。 */
    public boolean isClaymoreCharacter() {
        return isWeaponCharacter(WeaponPoiseTable.WeaponClass.CLAYMORE);
    }

    /** 是不是长柄角色。 */
    public boolean isPolearmCharacter() {
        return isWeaponCharacter(WeaponPoiseTable.WeaponClass.POLEARM);
    }

    /** 是不是法器角色。 */
    public boolean isCatalystCharacter() {
        return isWeaponCharacter(WeaponPoiseTable.WeaponClass.CATALYST);
    }

    /** 是不是弓角色。 */
    public boolean isBowCharacter() {
        return isWeaponCharacter(WeaponPoiseTable.WeaponClass.BOW);
    }

    /** 是不是拳头角色。 */
    public boolean isFistCharacter() {
        return isWeaponCharacter(WeaponPoiseTable.WeaponClass.FIST);
    }

    /**
     * 这把武器能不能装到<b>现在这一格</b>上 —— 换武器校验、武器选择列表的筛选项都问它。
     *
     * <p>单武器角色：只有自己那一类能装（= 原来的
     * {@code getAllowedWeaponClass().isInstance(stack.getItem())}）。
     * <b>全武器类角色</b>：只有<b>当前选中的那一类</b>能装 —— 他/她一个武器种类一格，
     * 选了大剑就不该把弓塞进大剑那一格。
     */
    public boolean canEquipWeapon(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof WeaponItem weapon)) {
            return false;
        }
        if (!getAllowedWeaponClass().isInstance(weapon)) {
            return false;
        }
        // 全武器类角色一个武器种类一格，只吃当前选中的那一类；
        // 没有确定类型的角色（连武器类都没限定的壳）不再额外收窄。
        WeaponPoiseTable.WeaponClass current = currentWeaponType();
        return current == WeaponPoiseTable.WeaponClass.UNKNOWN
                || WeaponPoiseTable.weaponOfClass(weapon.getClass()) == current;
    }

    // ============ 动作系统扩展点 ============

    public String getActionStateKey(Player player) {
        return "default";
    }

    /**
     * 客户端要不要在「这一招出手那一刻」<b>本地也跑一次</b>天赋钩子（默认不跑）。
     *
     * <p>角色天赋只在服务端执行，客户端只播动画 + 发包 —— 绝大多数招式这样就够了
     * （伤害在服务端、动画在客户端）。但有些招式的<b>表现层位移必须由技能自己算</b>：
     * 申鹤的 {@code DashSystem} 就是「客户端按格推位置、服务端沿途扫伤害」，
     * 客户端不跑天赋的话那段位移就成了死代码。
     *
     * <p>打开的角色的天赋必须在客户端安全：自己按 {@code level.isClientSide()} 分流，
     * 服务端专属逻辑（改数据、加效果、扣能量、生成实体）不要跑。
     */
    public boolean runsTalentOnClient() {
        return false;
    }

    /**
     * 辉映·星烁反应的伤害加成（反应加成区）—— <b>按分支给</b>。
     *
     * <p>覆写它就能做「天赋给星烁加成」的角色。写法有两种，对应文案的两种口径：
     * <pre>
     * // 文案写「星烁反应伤害提升 20%」→ 星扩散、星超导都给
     * public float getStellarGlimmerBonus(StellarGlimmerBranch branch) { return 0.20f; }
     *
     * // 文案只写「星扩散伤害提升 20%」（薇斯娜那一类）→ 只给星扩散
     * public float getStellarGlimmerBonus(StellarGlimmerBranch branch) {
     *     return branch == StellarGlimmerBranch.SWIRL ? 0.20f : 0f;
     * }
     * </pre>
     *
     * <p>效果/ buff 那边走 {@code ICharacterEffect.getStellarGlimmerBonus(分支)}，
     * 两边会在 {@code StellarGlimmer.bonusOf(...)} 里相加。
     */
    public float getStellarGlimmerBonus(com.linweiyun.genshin.core.system.reaction.StellarGlimmerBranch branch) {
        return 0f;
    }

    /**
     * 「擢升」加成（伤害公式里的<b>擢升区</b> = {@code 1 + 这个值}），按分支给。
     *
     * <p>和 {@link #getStellarGlimmerBonus} 的区别是<b>乘区不同</b>：
     * 那个落在反应加成区、和元素精通<b>加算</b>；
     * 这个落在擢升区、是<b>独立的乘区</b>（在暴击之后、大权之前）。
     * 文案写「擢升」的加成就走这里（例：薇斯娜满命的星扩散伤害擢升 20%）。
     */
    public float getElevationBonus(com.linweiyun.genshin.core.system.reaction.StellarGlimmerBranch branch) {
        // 两块相加：效果侧（例如队友身上沃雅妮莎 6 命的 Glimmer）+ 角色天赋自己那一份
        return data.getEffectContainer().getElevationBonus(branch) + getOwnElevationBonus(branch);
    }

    /**
     * 角色<b>天赋自己</b>给的擢升加成（默认 0；例：薇斯娜 6 命的星扩散擢升 20%）。
     *
     * <p>单独开一个方法是为了不被上面的容器聚合吞掉：覆写 {@link #getElevationBonus} 的话，
     * 队友给你的效果加成也会一起没了。
     */
    public float getOwnElevationBonus(com.linweiyun.genshin.core.system.reaction.StellarGlimmerBranch branch) {
        return 0f;
    }

    /**
     * 按<b>元素 / 反应类型</b>的额外暴击伤害（加在统一 CDG 之上），默认 0。
     *
     * <p>和 {@link #getStellarGlimmerBonus} / {@link #getElevationBonus} 同类，只是作用在暴击区：
     * 文案写「水元素伤害与冰元素伤害的暴击伤害提升 X%」这种就覆写它
     * （例：沃雅妮莎 2 命的「黑与白的双音」）。
     *
     * @param element         这次伤害的元素
     * @param stellarReaction 这次是不是星烁（星扩散/星超导）反应伤害
     */
    public float getCritDamageBonus(com.linweiyun.genshin.core.element.GenshinElement element,
                                    boolean stellarReaction) {
        // 效果侧聚合（和 getStellarGlimmerBonus 一样：效果 + 角色自己相加）
        return data.getEffectContainer().getCritDamageBonus(element, stellarReaction);
    }

    /**
     * 治疗加成（小数，{@code 0.04 = 4%}）—— 装备者实际治疗时乘进治疗量。
     *
     * <p>两块相加：效果侧（{@code ICharacterEffect#getHealingBonus} 的聚合，
     * 例如圣遗物/天赋给的治疗加成）+ 装备武器自己那一份
     * （{@code WeaponItem#getHealingBonus}，例如漩流颂歌的 +4%）。
     *
     * <p>武器那一份直接按「当前装着的武器」算，不落任何持久化状态 ——
     * 换武器即时生效，也不会被老存档的影子值盖掉。
     */
    public float getHealingBonus() {
        float total = data.getEffectContainer().getHealingBonus();
        ItemStack weapon = data.getWeapon();
        if (weapon != null && !weapon.isEmpty() && weapon.getItem() instanceof WeaponItem weaponItem) {
            total += weaponItem.getHealingBonus();
        }
        return total;
    }

    /**
     * 「大权」加成（伤害公式里的<b>大权区</b> = {@code 1 + 这个值}）。
     *
     * <p>由角色自己按层数/状态给，例如薇斯娜的「整肃」：每层 +10%，
     * 只作用在她召唤的灵剑（翔风剑二/三阶与大招那几段）上。
     * 没有这套机制的角色保持 0，大权区就是 1。
     */
    public float getSovereigntyBonus() {
        return 0f;
    }

    /**
     * 获取数据驱动的动作配置（来自角色资源的 CharacterActionData）。
     * 子类可覆盖以返回角色专属配置。
     */
    public com.linweiyun.genshin.core.system.combat.action.data.CharacterActionData getActionData() {
        return null;
    }

    public final ActionSet getActionSet(Player player) {
        String key = getActionStateKey(player);
        return actionSetCache.computeIfAbsent(key, k -> {
            SkillBase current = getSkill();
            ActionSet built = (current != null) ? current.buildActionSet(this, k) : null;
            return built != null ? built : buildFallbackActionSet();
        });
    }

    protected ActionSet buildFallbackActionSet() {
        return ActionSet.builder().build();
    }

    protected void invalidateActionSetCache() {
        actionSetCache.clear();
    }

    /**
     * 调试用：返回三个协作对象（技能 / 天赋 / 命座）是否已初始化。
     * <p>
     * 前半段沿用旧格式（{@code skill} 的类名，或 {@code null(class=Xxx)}，
     * 也就是重构前 {@code talent} 那一份），后面追加天赋 / 命座两项 ——
     * 日志里的字段名仍叫 {@code talent=}，老日志的匹配习惯不被破坏。
     */
    public String getTalentDebugInfo() {
        String skillPart = (skill == null)
                ? "null(class=" + this.getClass().getSimpleName() + ")"
                : skill.getClass().getSimpleName();
        return skillPart
                + " [talent=" + debugNameOf(talent)
                + ", constellation=" + debugNameOf(constellation) + "]";
    }

    private static String debugNameOf(Object collaborator) {
        return collaborator == null ? "null" : collaborator.getClass().getSimpleName();
    }

    // ============ E / Q 钩子（由 ActionManager 调用） ============

    /**
     * 出手<b>前置判断</b> —— 「这一招现在放不放得出来」。
     *
     * <h2>为什么需要它</h2>
     * 本 MOD 的架构是「客户端管动画、服务端管结算」：按键那一帧客户端就把动画切了，
     * 请求才发给服务端。于是凡是<b>服务端会拒绝</b>的条件（能量不够、CD 没好、没子弹…），
     * 都会变成「动画播了、什么都没有」—— 玩家看到的是「我放了，但没效果」。
     *
     * <p>所以这条判断要在<b>播动画之前</b>问一次：
     * <ul>
     *   <li><b>客户端</b>（{@code ResourceDrivenActionHandler}）：不通过就<b>不播动画、不发请求</b>；</li>
     *   <li><b>服务端</b>（{@code ActionManager}）：照旧再判一次，它才是权威。</li>
     * </ul>
     *
     * <h2>写实现时的两条约束</h2>
     * <ol>
     *   <li><b>只读双端都有的数据</b>：这个方法会在客户端跑，
     *       所以判断只能基于 {@code data}（同步过的角色数据）这类双端都有的状态，
     *       <b>不要</b>依赖 {@link #getSkill()} / {@link #getTalent()} 里的运行时状态
     *       （协作者双端都有实例，但里面的 CD / 层数 / 窗口不一定同步过）。</li>
     *   <li><b>不要有副作用</b>：它可能被每刻调用（长按重试）。
     *       提示消息走 {@link #sendCastFailedMessage(Player, ActionKind)}，那边有节流。</li>
     * </ol>
     *
     * <p>默认实现落到已有的 E/Q 钩子上：角色要加自己的条件（能量、姿态、弹药…）
     * 就覆盖 {@link #canUseElementalSkill} / {@link #canUseElementalBurst}，
     * 或者直接覆盖这个方法加全新的招式门槛。
     *
     * @param kind      这一招是什么（普攻/重击/战技/大招/闪避）
     * @param skillTime 战技的短按(0)/长按(1000)标记，其它招式忽略
     * @return true = 可以放
     */
    public boolean canCast(Player player, ActionKind kind, int skillTime) {
        return switch (kind) {
            case ELEMENTAL_SKILL_TAP, ELEMENTAL_SKILL_HOLD -> canUseElementalSkill(player, skillTime);
            case ELEMENTAL_BURST -> canUseElementalBurst(player);
            // 普攻 / 重击 / 闪避 / 下落攻击默认没有门槛
            default -> true;
        };
    }

    /**
     * 前置判断没过时的反馈（客户端与服务端共用同一套文案）。
     *
     * <p>默认按招式类别给出对应的提示；不需要提示就覆盖成空实现。
     */
    public void sendCastFailedMessage(Player player, ActionKind kind) {
        switch (kind) {
            case ELEMENTAL_SKILL_TAP, ELEMENTAL_SKILL_HOLD -> sendSkillCooldownMessage(player);
            case ELEMENTAL_BURST -> sendBurstCooldownMessage(player);
            default -> {
            }
        }
    }

    public boolean canUseElementalSkill(Player player, int skillTime) {
        return data.getElementalSkillCooldownTick() == 0;
    }

    public void applyElementalSkillCooldown(Player player, int skillTime) {
        data.setElementalSkillStacks(data.getElementalSkillStacks() - 1);
        if (skillTime < 1000) {
            data.setElementalSkillCooldownTick(data.getSkillShortMaxCooldownTick());
        } else {
            data.setElementalSkillCooldownTick(data.getSkillLongMaxCooldownTick());
        }
        syncRealtimeState();
    }

    public void sendSkillCooldownMessage(Player player) {
        player.sendSystemMessage(Component.translatable(
                "message.minegenshin.skill_cooldown",
                data.getElementalSkillCooldownTick()));
    }

    public boolean canUseElementalBurst(Player player) {
        if (data.getElementalBurstCooldownTick() > 0) return false;
        return data.getCurrentObtainingEnergy() >= data.getMaxObtainingEnergy();
    }

    public void applyElementalBurstCooldown(Player player) {
        data.setCurrentObtainingEnergy(0);
        data.setElementalBurstCooldownTick(data.getBurstMaxCooldownTick());
        syncRealtimeState();
    }

    public void sendBurstCooldownMessage(Player player) {
        if (data.getElementalBurstCooldownTick() > 0) {
            player.sendSystemMessage(Component.translatable("message.minegenshin.skill_cooldown"));
        } else {
            player.sendSystemMessage(Component.translatable("message.minegenshin.not_enough_energy"));
        }
    }

    // ============ 普攻 / 重击 ============

    public void performNormalAttack(Player player, int comboStage) {
        SkillBase current = getSkill();
        if (current != null) current.attack(player, this, comboStage);
        if (!player.level().isClientSide() && spawnsNormalAttackParticle()) {
            trySpawnNormalAttackParticle(player);
        }
    }

    /**
     * 普攻是否生成元素微粒。子类（如薇斯娜）覆盖返回 false 关闭，
     * 因为其技能内部已有自己的产球逻辑。
     */
    public boolean spawnsNormalAttackParticle() {
        return true;
    }

    public void trySpawnNormalAttackParticle(Player player) {
        if (player.level().getRandom().nextFloat() >= 0.5f) return;
        var element = getElemental();
        if (element == null || element == ModElements.FYSIKOS.get()) return;
        new ElementalOrbSpawner(player.level(), element, 1, true, player.position()).execute();
    }

    public void performChargedAttack(Player player) {
        SkillBase current = getSkill();
        if (current != null) current.chargeAttack(player, this);
    }

    public int getChargedAttackChargeTicks() {
        // 走 getSkill() 而不是字段：全武器类角色的招式对象跟着"选中哪把武器"换
        SkillBase current = getSkill();
        if (current != null) return current.getChargeTicks();
        return 20;
    }

    /**
     * 这个角色的重击是不是<b>持续型</b>（大剑：按住进入状态、松手/到时结束）。
     *
     * <p>只读技能基类的声明，不含运行时状态，所以客户端也能问
     * （客户端就是靠它在 {@code ResourceDrivenActionHandler.tickCharge} 里分岔）。
     */
    public boolean isSustainedChargedAttack() {
        SkillBase current = getSkill();
        return current != null && current.isSustainedChargedAttack();
    }

    /** 持续型重击的最高持续时间（刻）；不是持续型时是 0。见 {@link #isSustainedChargedAttack()}。 */
    public int getChargedAttackMaxTicks() {
        SkillBase current = getSkill();
        return current != null ? current.getChargedAttackMaxTicks() : 0;
    }

    public void frontTick(Player player) {}

    public void backTick(Player player) {}
    /**
     * 两端都推进 ActionManager：
     * <ul>
     *   <li>客户端：本地跑动作状态机</li>
     *   <li>服务端：权威跑动作状态机</li>
     * </ul>
     */

    public void tick(Player player) {
        data.tick();
        recalculateDirtyArtifactSlots();
        frontTick(player);
        backTick(player);

        ActionManager.get(player).tick(player, this);

        if (!player.level().isClientSide()) {
            syncRealtimeState();
        }
    }

    // ============ 圣遗物 / 武器 ============

    public void equipArtifact(ArtifactType type, ItemStack artifactStack) {
        if (artifactStack.getItem() instanceof ArtifactItem artifactItem) {
            if (artifactItem.getType() != type) return;
            int slot = ArtifactInventory.typeToSlot(type);
            data.getArtifactInventory().setItem(slot, artifactStack.copy());
        }
    }

    public void unequipArtifact(ArtifactType type) {
        int slot = ArtifactInventory.typeToSlot(type);
        data.getArtifactInventory().setItem(slot, ItemStack.EMPTY);
    }

    public void recalculateDirtyArtifactSlots() {
        ArtifactInventory inv = data.getArtifactInventory();
        if (!inv.hasDirtySlots()) return;
        for (int i = 0; i < inv.slotCount(); i++) {
            if (inv.isDirty(i)) {
                recalculateArtifactSlot(i);
                inv.clearDirty(i);
            }
        }
        refreshArtifactSetEffects();
    }

    private void recalculateArtifactSlot(int slotIndex) {
        // 5~10 都是武器槽（全武器类角色一个武器种类一格）
        if (slotIndex >= ArtifactInventory.SLOT_WEAPON) {
            recalculateWeaponSlot();
            return;
        }
        ArtifactType type = ArtifactInventory.slotToType(slotIndex);
        String source = null;
        if (type != null) {
            source = type.name().toLowerCase();
        }

        for (AttributeType attrType : ModRegistries.ATTRIBUTE_TYPE_REGISTRY) {
            data.removeAttributeModifier(attrType, source);
        }

        ItemStack stack = data.getArtifactInventory().getItem(slotIndex);
        if (!stack.isEmpty() && stack.getItem() instanceof ArtifactItem) {
            ArtifactStatsComponent stats = stack.getOrDefault(
                    ModDataComponents.ARTIFACT_STATS.get(),
                    ArtifactStatsComponent.DEFAULT
            );
            applyStatWithSet(stats.mainStat, source);
            for (TeyvatItemStat subStat : stats.subStats) {
                if (subStat.isUnlocked()) {
                    applyStatWithSet(subStat, source);
                }
            }
        }
    }

    public void recalculateWeaponSlot() {
        for (AttributeType attrType : ModRegistries.ATTRIBUTE_TYPE_REGISTRY) {
            data.removeAttributeModifier(attrType, SOURCE_WEAPON);
        }

        data.removeAttributeBaseValue(ModAttributes.ATK.get(), SOURCE_WEAPON);
        data.setWeaponBaseATK(0);

        // 读「身上那一把」而不是固定的第 5 格：全武器类角色身上那把跟着选中的武器种类走
        ItemStack stack = data.getWeapon();
        if (stack.isEmpty() || !(stack.getItem() instanceof WeaponItem weapon)) return;

        WeaponStatsComponent stats = stack.getOrDefault(
                ModDataComponents.WEAPON_STATS.get(), WeaponStatsComponent.DEFAULT);

        if (stats.mainStat != null && stats.mainStat.isInitialized()) {
            // 主词条修正：tier 只给整数基础攻击力，个别武器的主词条是小数
            //（例如蝶变 48 - 0.46 = 47.54）
            double mainValue = stats.mainStat.getValue() + weapon.getMainStatDelta();
            data.setWeaponBaseATK(mainValue);
            data.setAttributeBaseValue(ModAttributes.ATK.get(), SOURCE_WEAPON, mainValue);
        }

        if (stats.subStat != null && stats.subStat.isInitialized()) {
            AttributeType attr = stats.subStat.getAttribute();
            double value = stats.subStat.getValue();
            if (isBaseAttribute(attr)) {
                data.setAttributePercentModifier(attr, SOURCE_WEAPON, value);
            } else {
                data.setAttributeFlatModifier(attr, SOURCE_WEAPON, value);
            }
        }
    }

    private void applyStatWithSet(TeyvatItemStat stat, String source) {
        if (!stat.isInitialized()) return;
        AttributeType attr = stat.getAttribute();
        double value = stat.getValue();
        boolean isBaseAttr = isBaseAttribute(attr);
        if (isBaseAttr) {
            if (stat.getKind() == TeyvatItemStat.StatKind.FLAT) {
                data.setAttributeFlatModifier(attr, source, value);
            } else {
                data.setAttributePercentModifier(attr, source, value);
            }
        } else {
            data.setAttributeFlatModifier(attr, source, value);
        }
    }

    private static boolean isBaseAttribute(AttributeType attr) {
        if (attr == null || attr.id() == null) return false;
        AttributeType registryAttr = ModRegistries.ATTRIBUTE_TYPE_REGISTRY.getValue(attr.id());
        return registryAttr == ModAttributes.MAX_HP.get()
                || registryAttr == ModAttributes.ATK.get()
                || registryAttr == ModAttributes.DEF.get();
    }

    private void refreshArtifactSetEffects() {
        CharacterEffectContainer container = data.getEffectContainer();
        List<ICharacterEffect> toRemove = container.getEffects().stream()
                .map(CharacterEffectInstance::getEffect)
                .filter(effect -> effect instanceof ArtifactSetEffect)
                .toList();
        for (ICharacterEffect effect : toRemove) {
            CharacterEffectHelper.removeEffect(this.data.getOwnerPlayer(), this, effect);
        }
        Map<ArtifactSet, Integer> setCountMap = new HashMap<>();
        for (ItemStack stack : data.getAllArtifactsAsList()) {
            if (!(stack.getItem() instanceof ArtifactItem art)) continue;
            ArtifactSet set = art.getSet().get();
            setCountMap.merge(set, 1, Integer::sum);
        }
        setCountMap.forEach((set, count) -> {
            if (count >= 2) {
                CharacterEffectInstance inst = new CharacterEffectInstance(
                        set.twoPcEffect().get(),
                        CharacterEffectInstance.INFINITE, 1, true
                );
                CharacterEffectHelper.addEffect(this.data.getOwnerPlayer(), this, inst);
            }
            if (count >= 4 && set.hasFourPcEffect()) {
                CharacterEffectInstance inst = new CharacterEffectInstance(
                        set.fourPcEffect().get(),
                        CharacterEffectInstance.INFINITE, 1, true
                );
                CharacterEffectHelper.addEffect(this.data.getOwnerPlayer(), this, inst);
            }
            data.syncEffectsToTag();
        });
    }

    public void hurt(float amount) {
        double before = data.getCurrentHP();
        data.hurtHP(amount);
        syncRealtimeState();
        if (data.getCurrentHP() <= 0 && before > 0) {
            incapacitate();
        }
    }

    public void incapacitate() {
        Player player = data.getOwnerPlayer();
        if (player == null) return;
        PlayerCharactersAttachment attachment = player.getData(AttachmentRegistration.PLAYER_CHARACTERS_ATTACHMENT);
        int currentIndex = attachment.getCurrentCharacterIndex();
        for (int offset = 1; offset <= 4; offset++) {
            int nextIndex = (currentIndex + offset) % 4;
            PGCharacter nextChar = attachment.getPartyCharacter(nextIndex);
            if (nextChar != null && nextChar.getData().getCurrentHP() > 0) {
                attachment.setCurrentCharacterIndex(nextIndex);
                if (player instanceof ServerPlayer sp) {
                    NetworkManager.setCharacterSelectionToPlayer(sp, nextIndex);
                }
                return;
            }
        }

        player.setData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT, false);
        if (player instanceof ServerPlayer sp) {
            NetworkManager.setGenshinModeToPlayer(sp, false);
        }
        // 全灭被动退出：角色属性折算到玩家身上这件事现在才算发生
        com.linweiyun.genshin.core.system.compat.PlayerStatBridge.onGenshinModeChanged(player, false);
    }

    public void revive(int hp) {
        float maxHp = (float) data.getAttributeTotalValue(ModAttributes.MAX_HP.value());
        data.setCurrentHP(Math.min(hp, maxHp));
        enableDeployIfNeeded();
        syncRealtimeState();
    }

    public void revive(float percent) {
        float maxHp = (float) data.getAttributeTotalValue(ModAttributes.MAX_HP.value());
        data.setCurrentHP(Math.min(maxHp * percent, maxHp));
        enableDeployIfNeeded();
        syncRealtimeState();
    }

    public void revive(float percent, int extraHp) {
        float maxHp = (float) data.getAttributeTotalValue(ModAttributes.MAX_HP.value());
        data.setCurrentHP(Math.min(maxHp * percent + extraHp, maxHp));
        enableDeployIfNeeded();
        syncRealtimeState();
    }

    private void enableDeployIfNeeded() {
        if (data.getCurrentHP() <= 0) return;
        Player player = data.getOwnerPlayer();
        if (player == null) return;
        Boolean genshinMode = player.getData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT);
        if (!genshinMode) {
            player.setData(AttachmentRegistration.GENSHIN_MODE_ATTACHMENT, true);
            if (player instanceof ServerPlayer sp) {
                NetworkManager.setGenshinModeToPlayer(sp, true);
            }
        }
    }

    public Map<Identifier, Supplier<List<? extends Integer>>> getStatGrowthMap() {
        return Map.of();
    }

    public int getStatAtLevel(AttributeType type, int levelIndex) {
        Supplier<List<? extends Integer>> supplier = getStatGrowthMap().get(type.id());
        if (supplier == null) return 0;
        List<? extends Integer> list = supplier.get();
        if (levelIndex < 0 || levelIndex >= list.size()) return 0;
        return list.get(levelIndex);
    }

    public double getBaseStat(AttributeType type) {
        Supplier<List<? extends Integer>> supplier = getStatGrowthMap().get(type.id());
        if (supplier == null) return type.defaultValue();
        List<? extends Integer> list = supplier.get();
        if (list.isEmpty()) return type.defaultValue();
        return list.getFirst();
    }

    public Set<AttributeType> getStatGrowthTypes() {
        return getStatGrowthMap().keySet().stream()
                .map(this::resolveType)
                .collect(Collectors.toSet());
    }

    public void addExp(int amount) {
        if (data.getLevel() >= 90) {
            return;
        }
        var expList = CharacterXpConfig.getAllXp();
        long totalMaxExp = 0;
        for (int i = 0; i < 89; i++) {
            totalMaxExp += expList.get(i);
        }
        long currentSpent = 0;
        for (int i = 0; i < data.getLevel() - 1; i++) {
            currentSpent += expList.get(i);
        }
        long remaining = totalMaxExp - currentSpent - data.getCurrentExp();
        if (amount > remaining) {
            data.setCurrentExp(data.getCurrentExp() + (int) remaining);
        } else {
            data.setCurrentExp(data.getCurrentExp() + amount);
        }
        tryLevelUp();
    }

    /**
     * 角色「不丢经验」还能吃进多少 —— 从当前等级攒到<b>本次突破的等级上限</b>为止。
     *
     * <p>用户口径：**超出这一档上限的经验一律不保留**（升级页的材料上限与那条溢出确认框
     * 说的就是这件事，服务端 {@code NetworkManager#addEquipLevelExp} 也按这份 room 截断）。
     * 想继续升必须先突破，突破会换一档上限。
     */
    public long characterExpRoom() {
        var expList = CharacterXpConfig.getAllXp();
        int cap = data.getAscensionPhase() == 0
                ? 20
                : Math.min((data.getAscensionPhase() + 3) * 10, 90);
        long room = 0;
        int level = data.getLevel();
        int current = Math.max(0, data.getCurrentExp());
        while (level < cap) {
            int need = Math.max(1, expList.get(Math.max(0, Math.min(level - 1, expList.size() - 1))));
            room += Math.max(0, need - current);
            current = 0;
            level++;
        }
        return room;
    }

    /** 武器「不丢经验」还能吃进多少 —— 到它当前这一档等级上限为止（不含存经验）。 */
    public long weaponExpRoom() {
        ItemStack weapon = data.getWeapon();
        if (!(weapon.getItem() instanceof WeaponItem item)) {
            return 0;
        }
        WeaponStatsComponent stats = weapon.getOrDefault(
                ModDataComponents.WEAPON_STATS.get(), WeaponStatsComponent.DEFAULT);
        long room = 0;
        for (int lv = stats.level; lv < stats.getMaxLevel(); lv++) {
            long need = WeaponLevelData.getExpToNextLevel(item.getStar(), lv);
            if (need <= 0) {
                break;
            }
            room += Math.max(0, need - (lv == stats.level ? stats.exp : 0));
        }
        return room;
    }

    /** 某格圣遗物「不丢经验」还能吃进多少 —— 到它当前星级的等级上限为止。 */
    public long artifactExpRoom(int slot) {
        ItemStack artifact = data.getArtifactInventory().getItem(slot);
        if (!(artifact.getItem() instanceof ArtifactItem item)) {
            return 0;
        }
        ArtifactStatsComponent stats = artifact.getOrDefault(
                ModDataComponents.ARTIFACT_STATS.get(), ArtifactStatsComponent.DEFAULT);
        // 未激活的圣遗物一点经验都吃不下（ArtifactStatsComponent#addExp 直接返回 0）
        if (!stats.activated) {
            return 0;
        }
        long room = 0;
        int maxLevel = stats.getMaxLevel(item.getStar());
        for (int lv = stats.level; lv < maxLevel; lv++) {
            long need = ArtifactLevelData.getExpToNextLevel(item.getStar(), lv);
            if (need <= 0) {
                break;
            }
            room += Math.max(0, need - (lv == stats.level ? stats.exp : 0));
        }
        return room;
    }

    public void tryLevelUp() {
        var expList = CharacterXpConfig.getAllXp();
        int totalExpConsumed = 0;
        int levelsToGain = 0;
        int oldLevel = data.getLevel();
        int currentAscensionPhase = data.getAscensionPhase();
        while (oldLevel < 90) {
            int expNeeded = expList.get(oldLevel - 1);
            if (data.getCurrentExp() - totalExpConsumed < expNeeded) break;
            int maxLevelForPhase = currentAscensionPhase == 0
                    ? 20
                    : Math.min((currentAscensionPhase + 3) * 10, 90);
            if (oldLevel >= maxLevelForPhase) break;
            totalExpConsumed += expNeeded;
            levelsToGain++;
            oldLevel++;
        }
        if (levelsToGain == 0) return;
        data.setCurrentExp(data.getCurrentExp() - totalExpConsumed);
        data.addLevel(levelsToGain);
        int statIndex = data.getLevel() - 1 + data.getAscensionPhase();
        updateBaseStatsFromConfig(statIndex);
        data.setCurrentHP(data.getAttributeTotalValue(ModAttributes.MAX_HP.value()));

        if (data.getLevel() < 90) {
            data.setMaxExp(expList.get(data.getLevel() - 1));
        }
    }

    public void ascend() {
        int maxLevelForPhase = data.getAscensionPhase() == 0
                ? 20
                : Math.min((data.getAscensionPhase() + 3) * 10, 90);

        if (data.getLevel() != maxLevelForPhase) return;

        int newPhase = data.getAscensionPhase() + 1;
        data.setAscensionPhase(newPhase);
        int statIndex = maxLevelForPhase - 1 + newPhase;
        updateBaseStatsFromConfig(statIndex);

        int starRating = this.getStarRating();
        CharacterAscendAttribute ascendAttr = this.getAscendAttribute();
        AttributeType targetAttrType = getAscendAttributeType(ascendAttr);

        if (CharacterAscendAttribute.PERCENT_STATS.contains(ascendAttr)) {
            data.removeAttributeModifier(targetAttrType, "ascension_bonus");
        } else {
            data.removeAttributeBaseValue(targetAttrType, "character_ascend");
        }

        int bonusCount = getAscensionBonusCount(newPhase);
        if (bonusCount > 0) {
            applyAscendBonus(ascendAttr, targetAttrType, starRating, bonusCount);
        }

        tryLevelUp();
    }

    private void applyAscendBonus(CharacterAscendAttribute attr, AttributeType type,
                                  int starRating, int bonusCount) {
        int factor = starRating + 1;
        String baseKey = "character_ascend";
        switch (attr) {
            case ATK, HP:
                data.addAttributePercentModifier(type, "ascension_bonus", 0.012f * factor * bonusCount);
                break;
            case DEF:
                data.addAttributePercentModifier(type, "ascension_bonus", 0.015f * factor * bonusCount);
                break;
            case CR:
                data.setAttributeBaseValue(type, baseKey, 0.008 * factor * bonusCount);
                break;
            case CDG:
                data.setAttributeBaseValue(type, baseKey, 0.016 * factor * bonusCount);
                break;
            case HB:
                data.setAttributeBaseValue(type, baseKey, (starRating == 5 ? 0.056 : 0.047) * bonusCount);
                break;
            case ELEMENTAL_BONUS:
                data.setAttributeBaseValue(type, baseKey, 0.012 * factor * bonusCount);
                break;
            case EM:
                data.setAttributeBaseValue(type, baseKey, (double) (starRating == 5 ? 29 : 24) * bonusCount);
                break;
            case ER:
                data.setAttributeBaseValue(type, baseKey, (0.013333 * factor) * bonusCount);
                break;
        }
    }

    private static int getAscensionBonusCount(int phase) {
        return switch (phase) {
            case 0, 1 -> 0;
            case 2 -> 1;
            case 3, 4 -> 2;
            case 5 -> 3;
            case 6 -> 4;
            default -> 0;
        };
    }

    private AttributeType getAscendAttributeType(CharacterAscendAttribute ascendAttr) {
        return switch (ascendAttr) {
            case ATK -> ModAttributes.ATK.value();
            case HP -> ModAttributes.MAX_HP.value();
            case DEF -> ModAttributes.DEF.value();
            case CR -> ModAttributes.CR.value();
            case CDG -> ModAttributes.CDG.value();
            case HB -> ModAttributes.HB.value();
            case ELEMENTAL_BONUS -> getElementalDamageBonusType();
            case EM -> ModAttributes.ELEMENTAL_MASTERY.value();
            case ER -> ModAttributes.ER.value();
        };
    }

    private AttributeType getElementalDamageBonusType() {
        GenshinElement element = getElemental();
        if (element == ModElements.PYRO.get()) return ModAttributes.PYRO_BONUS.value();
        if (element == ModElements.HYDRO.get()) return ModAttributes.HYDRO_BONUS.value();
        if (element == ModElements.DENDRO.get()) return ModAttributes.DENDRO_BONUS.value();
        if (element == ModElements.ELECTRO.get()) return ModAttributes.ELECTRO_BONUS.value();
        if (element == ModElements.ANEMO.get()) return ModAttributes.ANEMO_BONUS.value();
        if (element == ModElements.CYRO.get()) return ModAttributes.CYRO_BONUS.value();
        if (element == ModElements.GEO.get()) return ModAttributes.GEO_BONUS.value();
        if (element == ModElements.FYSIKOS.get()) return ModAttributes.PHYSICAL_BONUS.value();
        return ModAttributes.PHYSICAL_BONUS.value();
    }

    public void upgradeNormalAttack() { data.upgradeNormalAttack(); }
    public void upgradeElementalSkill() { data.upgradeElementalSkill(); }
    public void upgradeElementalBurst() { data.upgradeElementalBurst(); }

    public GenshinElement getElemental() {
        if (elemental != null) return elemental;
        if (elementalId != null && !elementalId.isEmpty()) {
            String[] parts = elementalId.split(":", 2);
            Identifier id = Identifier.fromNamespaceAndPath(parts[0], parts[1]);
            elemental = ModRegistries.ELEMENT_REGISTRY.get(id).map(Holder.Reference::value).orElse(null);
            return elemental;
        }
        return ModElements.FYSIKOS.get();
    }

    public int getSkillShortMaxCooldownTick() { return data.getSkillShortMaxCooldownTick(); }
    public int getSkillLongMaxCooldownTick() { return data.getSkillLongMaxCooldownTick(); }
    public int getBurstMaxCooldownTick() { return data.getBurstMaxCooldownTick(); }
    public float getMaxObtainingEnergy() { return data.getMaxObtainingEnergy(); }

    public float getSkillDisplayCooldown() {
        return data.getElementalSkillCooldownTick();
    }

    public int getSkillDisplayMaxCooldown() {
        return data.getSkillShortMaxCooldownTick();
    }

    public float getBurstDisplayCooldown() {
        return data.getElementalBurstCooldownTick();
    }

    public int getBurstDisplayMaxCooldown() {
        return data.getBurstMaxCooldownTick();
    }

    private void updateBaseStatsFromConfig(int statIndex) {
        for (AttributeType type : this.getStatGrowthTypes()) {
            int value = this.getStatAtLevel(type, statIndex);
            data.setAttributeBaseValue(type, value);
        }
    }

    public void syncRealtimeState() {
        data.syncToClient();
        syncToClient();
    }

    // ==================== 命座 ====================

    /**
     * 命座等级（0 = 0 命，6 = 满命）。
     *
     * <p>字段本身住在 {@link PGCharacterData} 里（{@code @Persisted(key = "constellation")}，
     * 早就有了、只是以前没人用），这里只是给天赋代码一个门面。
     */
    public int getConstellation() {
        return data.getConstellation();
    }

    /**
     * 命座是否达到 {@code level}（1~6）。
     *
     * <p>天赋里判命座一律走这个方法（而不是直接比等级数字），
     * 「必须先解锁某个突破天赋」这类前置条件由天赋自己再判一次。
     */
    public boolean hasConstellation(int level) {
        return data.getConstellation() >= level;
    }

    /** 是否已满命（6 命）。 */
    public boolean isConstellationMax() {
        return data.getConstellation() >= PGCharacterData.MAX_CONSTELLATION;
    }

    /**
     * 提升一级命座 —— 抽到<b>已有</b>角色时调用。
     *
     * @return {@code true} = 这次真的升了一级；{@code false} = 已经满命，
     *         调用方应该改走满命补偿（随机一套圣遗物）
     */
    public boolean addConstellation() {
        if (!data.upgradeConstellation()) {
            return false;
        }
        syncRealtimeState();
        return true;
    }

    /** 直接设置命座等级（命令 / GM 用）。 */
    public void setConstellation(int level) {
        data.setConstellation(level);
        syncRealtimeState();
    }

    // ==================== 外观（腿部变体 + 猫耳挂件） ====================

    /**
     * 外观位掩码 —— 每条腿「穿不穿鞋」+「裸腿/白丝/黑丝」，外加猫耳挂件的显隐。
     *
     * <p>渲染期读它来决定藏哪两套网格 / 藏不藏耳朵，规则见
     * {@code core.character.appearance.LegBoneRules} 与 {@code EarBoneRules}。
     */
    public int getAppearance() {
        return data.getAppearance();
    }

    /**
     * 改外形。
     *
     * <p><b>服务端</b>调用：落数据 + 同步给客户端。
     * <b>客户端</b>调用：本地立刻生效（页面里的预览要即时反映），
     * 另外还要把角色数据整包发回服务端 —— 这一步在页面里用
     * {@code PlayerCharactersAttachment.syncSingleCharacterToServer} 做，
     * 因为服务端收到后才是权威值，别的玩家看到的也是服务端那一份。
     */
    public void setAppearance(int mask) {
        data.setAppearance(mask);
        syncRealtimeState();
    }

    // 腿部变体 / 猫耳的读写不在这里：那是"申鹤自己的外观数据"该知道的事，
    // 页面通过 {@code ShenheAppearanceData} 读掩码、再调 {@link #setAppearance(int)} 写回。
}
