package org.examplee.wangsword.util;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

/**
 * 世界级进度：剑冢守卫有没有被击杀过。
 *
 * <p>存在存档的 {@code data/wangsword_wraith_progress.dat} 里，退出重进不会丢。
 */
public class WraithProgress extends PersistentState {

    private static final String STATE_ID = "wangsword_wraith_progress";
    private static final String KEY_GUARDIAN_SLAIN = "guardian_slain";

    private boolean guardianSlain;

    public static WraithProgress create() {
        return new WraithProgress();
    }

    public static WraithProgress fromNbt(NbtCompound nbt) {
        WraithProgress state = new WraithProgress();
        state.guardianSlain = nbt.getBoolean(KEY_GUARDIAN_SLAIN);
        return state;
    }

    /** 统一从主世界取，换维度也读到同一份 */
    public static WraithProgress get(ServerWorld world) {
        return world.getServer().getOverworld().getPersistentStateManager()
                .getOrCreate(WraithProgress::fromNbt, WraithProgress::create, STATE_ID);
    }

    public static void markGuardianSlain(ServerWorld world) {
        net.minecraft.world.PersistentStateManager manager =
                world.getServer().getOverworld().getPersistentStateManager();
        WraithProgress state = manager.getOrCreate(
                WraithProgress::fromNbt, WraithProgress::create, STATE_ID);
        if (!state.guardianSlain) {
            state.guardianSlain = true;
            state.markDirty();
            // 击杀守卫是一次性事件，立刻落盘，别等自动保存——中途崩了也不会丢
            manager.save();
        }
    }

    public boolean isGuardianSlain() {
        return this.guardianSlain;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putBoolean(KEY_GUARDIAN_SLAIN, this.guardianSlain);
        return nbt;
    }
}
