package com.linweiyun.genshin.core.world.leveling;

import com.linweiyun.genshin.core.attachment.AdventurerInfoAttachment;
import com.linweiyun.genshin.core.attachment.AttachmentRegistration;
import com.linweiyun.genshin.config.entity.EntityConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MonsterLevelCalculator {

    private static final int[][] WORLD_LEVEL_RANGES = {
            {1, 20, 0, 1, 10},
            {20, 25, 1, 11, 21},
            {25, 30, 2, 21, 40},
            {30, 35, 3, 41, 50},
            {35, 40, 4, 51, 60},
            {40, 45, 5, 61, 70},
            {45, 50, 6, 71, 80},
            {50, 55, 7, 81, 89},
            {55, 58, 8, 90, 91},
            {58, 61, 9, 92, 103}
    };

    public static int getWorldLevel(int adventureRank) {
        for (int[] range : WORLD_LEVEL_RANGES) {
            if (adventureRank >= range[0] && adventureRank < range[1]) {
                return range[2];
            }
        }
        if (adventureRank >= 58) return 9;
        return 0;
    }

    public static double getProgressInWorldLevel(int adventureRank) {
        for (int[] range : WORLD_LEVEL_RANGES) {
            if (adventureRank >= range[0] && adventureRank < range[1]) {
                double span = range[1] - range[0];
                if (span <= 0) return 1.0;
                return (adventureRank - range[0]) / span;
            }
        }
        return adventureRank >= 58 ? 1.0 : 0.0;
    }

    public static int[] getMonsterLevelRange(int worldLevel) {
        for (int[] range : WORLD_LEVEL_RANGES) {
            if (range[2] == worldLevel) {
                return new int[]{range[3], range[4]};
            }
        }
        return new int[]{1, 10};
    }

    public static int getMonsterLevelFixed(int fixedLevel) {
        return Math.max(1, Math.min(103, fixedLevel));
    }

    public static int getMonsterLevelNatural(ServerLevel level, Vec3 spawnPos, long randomSeed) {
        List<ServerPlayer> players = getPlayersInSpawnRange(level, spawnPos);
        double progress = getNearestProgress(players, spawnPos);
        EntityConfig.CalculationMode calcMode = EntityConfig.getCalculationMode();
        int worldLevel = calculateWorldLevel(players, spawnPos, calcMode);
        int[] range = getMonsterLevelRange(worldLevel);
        return weightedRandom(range[0], range[1], progress, randomSeed);
    }

    public static int getMonsterLevelWithBias(ServerLevel level, Vec3 spawnPos, long randomSeed, int worldLevelBias) {
        List<ServerPlayer> players = getPlayersInSpawnRange(level, spawnPos);
        double progress = getNearestProgress(players, spawnPos);
        EntityConfig.CalculationMode calcMode = EntityConfig.getCalculationMode();
        int worldLevel = calculateWorldLevel(players, spawnPos, calcMode);
        worldLevel = Math.max(0, Math.min(9, worldLevel + worldLevelBias));
        int[] range = getMonsterLevelRange(worldLevel);
        return weightedRandom(range[0], range[1], progress, randomSeed);
    }

    private static List<ServerPlayer> getPlayersInSpawnRange(ServerLevel level, Vec3 spawnPos) {
        double radius = EntityConfig.getSpawnRadius();
        return level.getEntitiesOfClass(ServerPlayer.class, new AABB(spawnPos.add(-radius, -radius, -radius), spawnPos.add(radius, radius, radius)));
    }

    private static double getNearestProgress(List<ServerPlayer> players, Vec3 spawnPos) {
        ServerPlayer nearest = players.stream()
                .min(Comparator.comparingDouble(p -> p.distanceToSqr(spawnPos)))
                .orElse(null);
        if (nearest == null) return 0.0;
        AdventurerInfoAttachment info = nearest.getData(AttachmentRegistration.ADVENTURER_INFO_ATTACHMENT);
        return getProgressInWorldLevel(info.getAdventureRank());
    }

    private static int calculateWorldLevel(List<ServerPlayer> players, Vec3 spawnPos,
                                            EntityConfig.CalculationMode mode) {
        if (players.isEmpty()) return 0;

        return switch (mode) {
            case NEAREST -> {
                ServerPlayer p = players.stream()
                        .min(Comparator.comparingDouble(pl -> pl.distanceToSqr(spawnPos)))
                        .orElse(players.get(0));
                yield p.getData(AttachmentRegistration.ADVENTURER_INFO_ATTACHMENT).getWorldLevel();
            }
            case HIGHEST -> {
                ServerPlayer p = players.stream()
                        .max(Comparator.comparingInt(pl -> pl.getData(AttachmentRegistration.ADVENTURER_INFO_ATTACHMENT).getAdventureRank()))
                        .orElse(players.get(0));
                yield p.getData(AttachmentRegistration.ADVENTURER_INFO_ATTACHMENT).getWorldLevel();
            }
            case LOWEST -> {
                ServerPlayer p = players.stream()
                        .min(Comparator.comparingInt(pl -> pl.getData(AttachmentRegistration.ADVENTURER_INFO_ATTACHMENT).getAdventureRank()))
                        .orElse(players.get(0));
                yield p.getData(AttachmentRegistration.ADVENTURER_INFO_ATTACHMENT).getWorldLevel();
            }
            case COMPREHENSIVE -> {
                List<Integer> ranks = new ArrayList<>();
                for (ServerPlayer p : players) {
                    ranks.add(p.getData(AttachmentRegistration.ADVENTURER_INFO_ATTACHMENT).getAdventureRank());
                }
                if (ranks.size() <= 1) {
                    ServerPlayer p = players.get(0);
                    yield p.getData(AttachmentRegistration.ADVENTURER_INFO_ATTACHMENT).getWorldLevel();
                }
                ranks.sort(Comparator.reverseOrder());
                int secondHighest = ranks.get(1);
                List<Integer> filtered = new ArrayList<>();
                for (int r : ranks) {
                    if (r >= secondHighest - 25) filtered.add(r);
                }
                if (filtered.isEmpty()) filtered.add(secondHighest);
                double avg = filtered.stream().mapToInt(Integer::intValue).average().orElse(secondHighest);
                yield getWorldLevel((int) Math.round(avg));
            }
        };
    }

    private static int weightedRandom(int minLevel, int maxLevel, double progress, long seed) {
        if (minLevel == maxLevel) return minLevel;
        java.util.Random rnd = new java.util.Random(seed);
        int range = maxLevel - minLevel;
        double totalWeight = 0;
        double[] weights = new double[range + 1];
        for (int i = 0; i <= range; i++) {
            double lowBias = maxLevel - minLevel - i + 1;
            double highBias = i + 1;
            weights[i] = lowBias * (1 - progress) + highBias * progress;
            totalWeight += weights[i];
        }
        double roll = rnd.nextDouble() * totalWeight;
        double cum = 0;
        for (int i = 0; i <= range; i++) {
            cum += weights[i];
            if (roll <= cum) return minLevel + i;
        }
        return maxLevel;
    }
}