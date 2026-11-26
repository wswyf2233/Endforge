package org.examplee.wangsword.util;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.PersistentState;

/**
 * 按玩家存的一小坨 NBT，真正落在存档里。
 *
 * <p>为什么不用内存 Map：饰品的层数、印记、待结算的残响、护盾冷却都属于「跨会话」的数据，
 * 重登、换维度、死亡复活之后不能归零，否则玩家一退出游戏就能把代价抹掉。
 *
 * <p>为什么挂在主世界的 {@code PersistentStateManager}：它跟着存档走、全维度共用一份，
 * 不会被下界/末地的独立存档分层，也不会因为换维度就换一个实例。
 *
 * <p>存储结构：{@code { <玩家UUID>: { <模块名>: { ... } } }}。
 * 每个模块自己负责序列化，这里只做读写和落盘标记。
 */
public final class PlayerStore {

    /** 存档里的文件名：saves/<world>/data/wangsword_players.dat */
    private static final String FILE_NAME = "wangsword_players";

    private PlayerStore() {
    }

    /** 存档级的容器，真正被序列化的就是里面的 {@link #players} */
    public static final class Data extends PersistentState {

        private final NbtCompound players = new NbtCompound();

        static Data fromNbt(NbtCompound nbt) {
            Data data = new Data();
            for (String key : nbt.getKeys()) {
                data.players.put(key, nbt.get(key).copy());
            }
            return data;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt) {
            for (String key : players.getKeys()) {
                nbt.put(key, players.get(key).copy());
            }
            return nbt;
        }
    }

    private static Data data(ServerPlayerEntity player) {
        return player.getServer().getOverworld().getPersistentStateManager()
                .getOrCreate(Data::fromNbt, Data::new, FILE_NAME);
    }

    /** 读一个模块的数据；没有就返回空 compound（不是 null，省得各处判空） */
    public static NbtCompound get(ServerPlayerEntity player, String key) {
        NbtCompound mine = data(player).players.getCompound(player.getUuidAsString());
        if (mine.contains(key, NbtElement.COMPOUND_TYPE)) {
            return mine.getCompound(key).copy();
        }
        return new NbtCompound();
    }

    /** 写一个模块的数据，并标记存档需要保存 */
    public static void put(ServerPlayerEntity player, String key, NbtCompound value) {
        Data data = data(player);
        String uuid = player.getUuidAsString();
        NbtCompound mine = data.players.getCompound(uuid);
        mine.put(key, value);
        data.players.put(uuid, mine);
        data.markDirty();
    }

    /** 删掉一个模块的数据（摘下饰品、层数清空等） */
    public static void erase(ServerPlayerEntity player, String key) {
        Data data = data(player);
        String uuid = player.getUuidAsString();
        if (!data.players.contains(uuid, NbtElement.COMPOUND_TYPE)) {
            return;
        }
        NbtCompound mine = data.players.getCompound(uuid);
        if (!mine.contains(key)) {
            return;
        }
        mine.remove(key);
        if (mine.isEmpty()) {
            data.players.remove(uuid);
        } else {
            data.players.put(uuid, mine);
        }
        data.markDirty();
    }
}
