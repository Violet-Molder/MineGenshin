package com.linweiyun.genshin.core.attachment;

import net.minecraft.nbt.CompoundTag;

import com.linweiyun.genshin.core.network.NetworkManager;
import com.mojang.serialization.Codec;
import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class AdventurerInfoAttachment implements IPersistedSerializable {
    public final static Codec<AdventurerInfoAttachment> CODEC = PersistedParser.createCodec(AdventurerInfoAttachment::new);
    public final static StreamCodec<ByteBuf, AdventurerInfoAttachment> STREAM_CODEC = PersistedParser.createStreamCodec(AdventurerInfoAttachment::new);
    public static final int MAX_ADVENTURE_RANK = 60;
    public static final int MAX_WORLD_LEVEL = 9;

    private static final int[] EXP_TABLE = {
            0,      // AR 0 (dummy)
            375,    // AR 1 -> 2
            500,    // AR 2 -> 3
            625,    // AR 3 -> 4
            725,    // AR 4 -> 5
            850,    // AR 5 -> 6
            950,    // AR 6 -> 7
            1075,   // AR 7 -> 8
            1200,   // AR 8 -> 9
            1300,   // AR 9 -> 10
            1425,   // AR 10 -> 11
            1525,   // AR 11 -> 12
            1650,   // AR 12 -> 13
            1775,   // AR 13 -> 14
            1875,   // AR 14 -> 15
            2000,   // AR 15 -> 16
            2375,   // AR 16 -> 17
            2500,   // AR 17 -> 18
            2625,   // AR 18 -> 19
            2775,   // AR 19 -> 20
            2825,   // AR 20 -> 21
            3425,   // AR 21 -> 22
            3725,   // AR 22 -> 23
            4000,   // AR 23 -> 24
            4300,   // AR 24 -> 25
            4575,   // AR 25 -> 26
            4875,   // AR 26 -> 27
            5150,   // AR 27 -> 28
            5450,   // AR 28 -> 29
            5725,   // AR 29 -> 30
            6025,   // AR 30 -> 31
            6300,   // AR 31 -> 32
            6600,   // AR 32 -> 33
            6900,   // AR 33 -> 34
            7175,   // AR 34 -> 35
            7475,   // AR 35 -> 36
            7750,   // AR 36 -> 37
            8025,   // AR 37 -> 38
            8325,   // AR 38 -> 39
            8625,   // AR 39 -> 40
            10550,  // AR 40 -> 41
            11525,  // AR 41 -> 42
            12475,  // AR 42 -> 43
            13450,  // AR 43 -> 44
            14400,  // AR 44 -> 45
            15350,  // AR 45 -> 46
            16325,  // AR 46 -> 47
            17275,  // AR 47 -> 48
            18250,  // AR 48 -> 49
            19200,  // AR 49 -> 50
            26400,  // AR 50 -> 51
            28800,  // AR 51 -> 52
            31200,  // AR 52 -> 53
            33600,  // AR 53 -> 54
            36000,  // AR 54 -> 55
            232350, // AR 55 -> 56
            258950, // AR 56 -> 57
            285750, // AR 57 -> 58
            312825, // AR 58 -> 59
            340125  // AR 59 -> 60
    };

    @Persisted(key = "adventure_rank")
    private int adventureRank = 1;

    @Persisted(key = "world_level")
    private int worldLevel = 0;

    @Persisted(key = "breakthrough_level")
    private int breakthroughLevel = 0;

    @Persisted(key = "current_exp")
    private long currentExp = 0;

    public AdventurerInfoAttachment() {}

    // ========== Getters & Setters ==========

    public int getAdventureRank() {
        return adventureRank;
    }

    public int getWorldLevel() {
        return worldLevel;
    }

    public int getBreakthroughLevel() {
        return breakthroughLevel;
    }

    public long getCurrentExp() {
        return currentExp;
    }

    public void setAdventureRank(int adventureRank) {
        this.adventureRank = Math.max(1, Math.min(MAX_ADVENTURE_RANK, adventureRank));
    }

    public void setWorldLevel(int worldLevel) {
        this.worldLevel = Math.max(0, Math.min(MAX_WORLD_LEVEL, worldLevel));
        if (this.worldLevel < breakthroughLevel - 1) {
            this.worldLevel = breakthroughLevel - 1;
        }
    }

    public void setBreakthroughLevel(int breakthroughLevel) {
        this.breakthroughLevel = Math.max(0, Math.min(MAX_WORLD_LEVEL, breakthroughLevel));
        if (this.breakthroughLevel < this.worldLevel - 1) {
            this.worldLevel = this.breakthroughLevel;
        }
    }

    public void setCurrentExp(long currentExp) {
        this.currentExp = Math.max(0, currentExp);
    }

    // ========== 升级信息查询 ==========

    public long getExpToNextRank() {
        if (adventureRank >= MAX_ADVENTURE_RANK) {
            return -1;
        }
        return EXP_TABLE[adventureRank];
    }

    public static long getPrecomputedTotalExpToMax() {
        long total = 0;
        for (int i = 1; i < MAX_ADVENTURE_RANK; i++) {
            total += EXP_TABLE[i];
        }
        return total;
    }

    private long getTotalExpSpent() {
        long spent = 0;
        for (int i = 1; i < adventureRank; i++) {
            spent += EXP_TABLE[i];
        }
        return spent;
    }

    public boolean isMaxRank() {
        return adventureRank >= MAX_ADVENTURE_RANK;
    }

    public boolean isExpMaxForCurrentRank() {
        if (isMaxRank()) {
            return false;
        }
        return currentExp >= getExpToNextRank();
    }

    private int getRequiredWorldLevelForRank(int rank) {
        if (rank >= 56) return 8;
        if (rank >= 51) return 7;
        if (rank >= 46) return 6;
        if (rank >= 41) return 5;
        if (rank >= 36) return 4;
        if (rank >= 31) return 3;
        if (rank >= 26) return 2;
        if (rank >= 21) return 1;
        return 0;
    }

    public int getRequiredWorldLevel() {
        return getRequiredWorldLevelForRank(adventureRank);
    }

    // ========== 升级判定（使用 breakthroughLevel 确保降低 worldLevel 不影响升级） ==========

    public boolean canRankUp() {
        if (isMaxRank()) {
            return false;
        }
        if (!isExpMaxForCurrentRank()) {
            return false;
        }
        int nextRank = adventureRank + 1;
        if (nextRank >= 59) {
            return true;
        }
        int requiredWL = getRequiredWorldLevelForRank(nextRank);
        return breakthroughLevel >= requiredWL;
    }

    public boolean canBreakthroughWorldLevel() {
        if (breakthroughLevel >= MAX_WORLD_LEVEL) {
            return false;
        }
        if (breakthroughLevel == 8) {
            return adventureRank >= 58;
        }
        if (isExpMaxForCurrentRank()) {
            return breakthroughLevel == getRequiredWorldLevel();
        }
        return false;
    }

    // ========== 升级执行 ==========

    public void addExp(long exp) {
        if (exp <= 0) {
            return;
        }
        if (isMaxRank()) {
            return;
        }
        long constantTotal = getPrecomputedTotalExpToMax();
        long totalExp = getTotalExpSpent() + currentExp;
        long remaining = constantTotal - totalExp;
        if (exp > remaining) {
            currentExp += remaining;
        } else {
            currentExp += exp;
        }
        tryAutoRankUp();
    }

    public void tryAutoRankUp() {
        while (canRankUp()) {
            long required = getExpToNextRank();
            currentExp -= required;
            adventureRank++;
        }
        if (isMaxRank()) {
            currentExp = 0;
        }
    }

    public boolean breakthroughWorldLevel() {
        if (!canBreakthroughWorldLevel()) {
            return false;
        }
        breakthroughLevel++;
        worldLevel = breakthroughLevel;
        tryAutoRankUp();
        return true;
    }

    public boolean canBreakthroughTo(int targetWorldLevel) {
        if (targetWorldLevel <= breakthroughLevel) {
            return false;
        }
        if (targetWorldLevel > MAX_WORLD_LEVEL) {
            return false;
        }
        if (targetWorldLevel == 9) {
            return breakthroughLevel == 8 && adventureRank >= 58;
        }
        int requiredWL = getRequiredWorldLevel();
        if (targetWorldLevel > requiredWL) {
            return false;
        }
        return isExpMaxForCurrentRank();
    }

    public boolean breakthroughTo(int targetWorldLevel) {
        if (!canBreakthroughTo(targetWorldLevel)) {
            return false;
        }
        breakthroughLevel = targetWorldLevel;
        worldLevel = targetWorldLevel;
        tryAutoRankUp();
        return true;
    }

    // ========== 世界等级降级（临时降低难度） ==========

    public boolean canDowngradeWorldLevel() {
        if (breakthroughLevel < 5) {
            return false;
        }
        return worldLevel == breakthroughLevel;
    }

    public boolean downgradeWorldLevel() {
        if (!canDowngradeWorldLevel()) {
            return false;
        }
        worldLevel = breakthroughLevel - 1;
        return true;
    }

    public boolean canRestoreWorldLevel() {
        return worldLevel < breakthroughLevel;
    }

    public boolean restoreWorldLevel() {
        if (!canRestoreWorldLevel()) {
            return false;
        }
        worldLevel = breakthroughLevel;
        return true;
    }

    // ========== 同步方法 ==========

    public void syncToPlayer(ServerPlayer player) {
        NetworkManager.setAdventurerInfoToPlayer(player, serializeNBT(player.registryAccess()));
    }

    public void syncToServer() {
        Player clientPlayer = ClientAttachmentSync.getClientPlayer();
        NetworkManager.setAdventurerInfoToServer(serializeNBT(clientPlayer.registryAccess()));
    }

    // ========== 工具方法 ==========

    @Override
    public String toString() {
        return String.format("AdventurerInfo{AR=%d, WL=%d, BL=%d, Exp=%d/%d}",
                adventureRank, worldLevel, breakthroughLevel, currentExp, getExpToNextRank());
    }
}