package com.linweiyun.genshin.core.world;

import com.linweiyun.genshin.Minegenshin;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public class TeyvatWorldInvasion extends SavedData {

    /** 存档文件名，落在 {@code <世界>/data/<id>.dat}。 */
    public static final String FILE_ID = Minegenshin.MOD_ID + "_teyvat_invasion";

    /** 是否处于「提瓦特入侵」状态的存档键。 */
    private static final String KEY_INVADED = "invaded";

    public static final SavedData.Factory<TeyvatWorldInvasion> FACTORY =
            new SavedData.Factory<>(TeyvatWorldInvasion::new, TeyvatWorldInvasion::load);

    private boolean invaded;

    public TeyvatWorldInvasion() {
        this(false);
    }

    public TeyvatWorldInvasion(boolean invaded) {
        this.invaded = invaded;
    }

    public boolean isInvaded() {
        return invaded;
    }

    public void setInvaded(boolean invaded) {
        this.invaded = invaded;
        this.setDirty();
    }

    public static TeyvatWorldInvasion get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean(KEY_INVADED, invaded);
        return tag;
    }

    private static TeyvatWorldInvasion load(CompoundTag tag, HolderLookup.Provider registries) {
        return new TeyvatWorldInvasion(tag.getBoolean(KEY_INVADED));
    }

    private static boolean clientInvaded = false;

    public static boolean isClientInvaded() {
        return clientInvaded;
    }

    public static void setClientInvaded(boolean invaded) {
        clientInvaded = invaded;
    }
}