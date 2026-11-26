package org.examplee.wangsword.util;

import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 饰品每 tick 派发器：扫一遍玩家身上真实戴着的饰品，把实现 {@link EquippedTickable}
 * 的那几个挨个推一次。
 *
 * <p>存在的理由：{@code Accessory#tick} 那套派发跟 Accessories 的容器刷新绑在一起，
 * 重新登录、换维度、死亡重生之后可能整段没跑到，结果是「戴着但没效果」，
 * 必须摘下来再戴一次才恢复。这里不依赖那套状态，每 tick 重新读一遍槽位，
 * 所以进游戏就生效，不需要任何手动操作。
 *
 * <p>代价是每 tick 遍历一次饰品栏（玩家数 × 槽位数，量很小），换掉的是「状态不同步」这类玄学问题。
 */
public final class AccessoryTicker {

    private AccessoryTicker() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                tick(player);
            }
        });
    }

    /** 单独推一次某个玩家，调试或强制刷新时可以直接调 */
    public static void tick(ServerPlayerEntity player) {
        if (player.isDead() || player.isSpectator()) {
            return;
        }
        AccessoriesCapability.getOptionally(player).ifPresent(capability -> {
            for (AccessoriesContainer container : capability.getContainers().values()) {
                var accessories = container.getAccessories();
                for (int i = 0; i < accessories.size(); i++) {
                    ItemStack stack = accessories.getStack(i);
                    if (stack.isEmpty()) {
                        continue;
                    }
                    if (stack.getItem() instanceof EquippedTickable tickable) {
                        tickable.tickEquipped(player);
                    }
                }
            }
        });
    }
}
