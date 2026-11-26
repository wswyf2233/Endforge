package org.examplee.wangsword.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.sound.SoundEvents;
import org.examplee.wangsword.util.SkillCooldowns;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 物品栏右侧的技能冷却图标（通用版）。
 *
 * <p>每把武器用 {@link #register} 声明自己有哪些带冷却的技能，手里拿着它就在热栏右侧
 * 横排画出这些技能的格子——一件武器两个技能就是两个格子，各走各的冷却。
 *
 * <p>图标全部用 {@code fill} 拼，不新增贴图：深底 + 按剩余比例填充 + 居中秒数，
 * 就绪时亮起并画一个小闪电。
 *
 * <p><b>键名不写死。</b>格子下方那行小字读的是玩家当前的实际绑定
 * （{@link KeyBinding#getBoundKeyLocalizedText()}），所以他在设置里把 X 改成 K、
 * 把右键换成鼠标侧键之后，下一帧显示就跟着变，功能本来也是跟着 {@code KeyBinding} 走的。
 */
@Environment(EnvType.CLIENT)
public final class SkillCooldownHud {

    /** 冷却短于这个值就不画：一闪就没的格子只是噪音（60 tick = 3 秒） */
    private static final int MIN_SHOW_TICKS = 60;
    /** 最多横排几格（屏幕放不下时会自动再收窄） */
    private static final int MAX_CELLS = 5;
    /** 格子下方键名的最长字符数，超了截断 */
    private static final int MAX_KEY_NAME = 6;

    /**
     * 技能绑的是哪一类按键。
     *
     * <p>存枚举而不是 {@code KeyBinding} 实例，是因为 {@link #register} 发生在按键注册之前，
     * 那一刻 {@code WangSwordClientInit.ultKey} 还是 {@code null}。存枚举、画的时候再解析，
     * 调用顺序就无关了。
     */
    public enum KeyRef {
        /** 右键（原版「使用物品 / 放置方块」键，玩家可改） */
        USE,
        /** 模组的 X 键终结技（{@code key.wangsword.ultimate}） */
        ULT,
        /** 模组的 V 键火力全开 */
        AOE,
        /** 模组的 Z 键阿瓦隆 */
        AVALON,
        /** 模组的 G 键：熔岩套「熔岩喷发」 */
        ARMOR;

        KeyBinding resolve() {
            MinecraftClient client = MinecraftClient.getInstance();
            return switch (this) {
                case USE -> client.options.useKey;
                case ULT -> WangSwordClientInit.ultimateKeyBinding();
                case AOE -> WangSwordClientInit.aoeKeyBinding();
                case AVALON -> WangSwordClientInit.avalonKeyBinding();
                case ARMOR -> WangSwordClientInit.armorSkillKeyBinding();
            };
        }
    }

    /** 一个冷却技能的显示配置 */
    public record Spec(String id, String label, KeyRef key, int cooldownTicks, int color) {
    }

    /** 物品 → 它的技能列表（顺序即显示顺序） */
    private static final Map<Item, List<Spec>> SPECS = new LinkedHashMap<>();
    /**
     * 不绑定手持物品的技能（例如阿瓦隆：放在背包里按 Z 就用）。
     *
     * <p>这类只在<b>冷却中</b>绘制——就绪时不该白占屏幕。武器技能则不同，
     * 手里拿着就显示，因为玩家需要随时确认「下一发好了没」。
     */
    private static final List<Spec> GLOBAL_SPECS = new ArrayList<>();
    /** 技能 ID → 可以再放的时刻（本地世界时间） */
    private static final Map<String, Long> READY_AT = new HashMap<>();
    /** 技能 ID → 上一帧的剩余 tick，用来判断「刚好就绪」 */
    private static final Map<String, Integer> LAST_REMAIN = new HashMap<>();
    /** 技能 ID → 就绪闪光持续到的 tick */
    private static final Map<String, Long> FLASH_UNTIL = new HashMap<>();

    private SkillCooldownHud() {
    }

    /**
     * 注册一个技能的显示配置。
     *
     * @param item          武器物品（手里拿着它时才画）
     * @param id            技能 ID，必须和 {@code SkillCooldowns} 里用的一致
     * @param label         技能名，目前只用于语义标识
     * @param key           哪个键放这个技能，用来在格子下方显示实时键名
     * @param cooldownTicks 冷却总长；短于 1.5 秒的技能会被忽略（没有显示价值）
     * @param color         格子主色（填充与就绪边框共用）
     */
    public static void register(Item item, String id, String label, KeyRef key, int cooldownTicks, int color) {
        if (cooldownTicks < MIN_SHOW_TICKS) {
            // 一秒出头就转好的技能，画出来只会闪一下，不如不画
            return;
        }
        SPECS.computeIfAbsent(item, k -> new ArrayList<>())
                .add(new Spec(id, label, key, cooldownTicks, color));
    }

    /**
     * 注册一个不绑定手持物品的技能（背包道具类，比如阿瓦隆的 Z 键）。
     *
     * <p>只在冷却中绘制，就绪时自动消失。
     */
    public static void registerGlobal(String id, String label, KeyRef key, int cooldownTicks, int color) {
        if (cooldownTicks < MIN_SHOW_TICKS) {
            return;
        }
        GLOBAL_SPECS.add(new Spec(id, label, key, cooldownTicks, color));
    }

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(SkillCooldowns.SYNC_PACKET,
                (client, handler, buf, responseSender) -> {
                    String id = buf.readString();
                    int ticks = buf.readVarInt();
                    client.execute(() -> {
                        if (client.world != null) {
                            READY_AT.put(id, client.world.getTime() + ticks);
                        }
                    });
                });

        HudRenderCallback.EVENT.register(SkillCooldownHud::render);
    }

    private static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return;
        }
        List<Spec> held = SPECS.get(player.getMainHandStack().getItem());
        boolean hasHeld = held != null && !held.isEmpty();
        boolean hasGlobal = false;
        for (Spec spec : GLOBAL_SPECS) {
            if (isCooling(client, spec)) {
                hasGlobal = true;
                break;
            }
        }
        if (!hasHeld && !hasGlobal) {
            return;
        }

        final int size = 18;
        final int gap = 2;
        // 物品栏是居中的 182 宽、22 高；这里从它右端外侧 6px 起，横向排开
        int startX = ctx.getScaledWindowWidth() / 2 + 91 + 6;
        // 比热栏顶再高 4px：格子下面要留一行半号字写键名，不然会顶出屏幕
        int y = ctx.getScaledWindowHeight() - 26;

        // 窄屏（GUI 缩放拉很大）时放不下 5 格，按可用宽度算一遍能画几个
        int fit = Math.max(1, (ctx.getScaledWindowWidth() - startX - 2) / (size + gap));
        int limit = Math.min(MAX_CELLS, fit);

        int index = 0;
        if (hasHeld) {
            for (Spec spec : held) {
                if (index >= limit) {
                    break;
                }
                drawCell(ctx, client, player, spec, startX + index * (size + gap), y, size);
                index++;
            }
        }
        // 全局技能接在后面，且只在冷却中占位
        for (Spec spec : GLOBAL_SPECS) {
            if (index >= limit) {
                break;
            }
            if (!isCooling(client, spec)) {
                // 就绪即消失；顺手把边沿状态清零，免得下次进冷却时误触发闪光音效
                LAST_REMAIN.put(spec.id(), 0);
                continue;
            }
            drawCell(ctx, client, player, spec, startX + index * (size + gap), y, size);
            index++;
        }
    }

    /** 这个技能现在是否在冷却中 */
    private static boolean isCooling(MinecraftClient client, Spec spec) {
        Long readyAt = READY_AT.get(spec.id());
        return readyAt != null && client.world.getTime() < readyAt;
    }

    private static void drawCell(DrawContext ctx, MinecraftClient client, PlayerEntity player,
                                 Spec spec, int x, int y, int size) {
        long now = client.world.getTime();
        Long readyAt = READY_AT.get(spec.id());
        int remain = readyAt == null ? 0 : (int) Math.max(0L, readyAt - now);
        // 换世界 / 切维度后本地时间可能回退，会算出天文数字；夹到冷却总长兜住
        remain = Math.min(remain, spec.cooldownTicks());

        // 刚转好：闪一下 + 一声轻响
        Integer last = LAST_REMAIN.get(spec.id());
        if (last != null && last > 0 && remain == 0) {
            FLASH_UNTIL.put(spec.id(), now + 20L);
            player.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.5F, 1.7F);
        }
        LAST_REMAIN.put(spec.id(), remain);

        boolean ready = remain <= 0;
        Long flashUntil = FLASH_UNTIL.get(spec.id());
        boolean flashing = ready && flashUntil != null && now < flashUntil;

        // 外框 + 底
        ctx.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xFF101010);
        ctx.fill(x, y, x + size, y + size, ready ? 0xFF12403C : 0xFF2B2B2B);

        if (ready) {
            int frame = flashing ? 0xFFB8FFF2 : spec.color();
            ctx.fill(x, y, x + size, y + 1, frame);
            ctx.fill(x, y + size - 1, x + size, y + size, frame);
            ctx.fill(x, y, x + 1, y + size, frame);
            ctx.fill(x + size - 1, y, x + size, y + size, frame);
            drawBolt(ctx, x + size / 2, y + 3);
        } else {
            // 填充比例 = 剩余 / 总长；格子小，稳定比花哨重要，不做缩放动画
            float ratio = Math.min(1.0F, remain / (float) Math.max(1, spec.cooldownTicks()));
            int barW = (int) (size * ratio);
            if (barW > 0) {
                ctx.fill(x, y, x + barW, y + size, (spec.color() & 0x00FFFFFF) | 0xCC000000);
            }
            String text = String.valueOf((remain + 19) / 20);
            int textW = client.textRenderer.getWidth(text);
            ctx.drawText(client.textRenderer, text, x + (size - textW) / 2, y + 5, 0xFFFFFFFF, true);
        }

        drawKeyName(ctx, client, spec, x, y + size, size);
    }

    /**
     * 格子正下方的键名，半号字。
     *
     * <p>每帧现取 {@code getBoundKeyLocalizedText()}，所以玩家改键、绑鼠标键、清空绑定
     * 都能如实反映：清空时画一个「—」并把字压暗。
     */
    private static void drawKeyName(DrawContext ctx, MinecraftClient client, Spec spec,
                                    int x, int bottom, int size) {
        KeyBinding binding = spec.key().resolve();
        boolean unbound = binding == null || binding.isUnbound();
        String name = unbound ? "—" : binding.getBoundKeyLocalizedText().getString();
        if (name.length() > MAX_KEY_NAME) {
            name = name.substring(0, MAX_KEY_NAME - 1) + "…";
        }

        int textW = client.textRenderer.getWidth(name);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + size / 2.0F, bottom + 1.0F, 0.0F);
        ctx.getMatrices().scale(0.5F, 0.5F, 1.0F);
        // 缩放后坐标是虚拟的两倍，所以居中要拿未缩放的宽度来算
        ctx.drawText(client.textRenderer, name, -textW / 2, 0,
                unbound ? 0xFF6E6E6E : 0xFFCFCFCF, true);
        ctx.getMatrices().pop();
    }

    /** 用几块斜向色块拼一个小闪电，不依赖任何字形 */
    private static void drawBolt(DrawContext ctx, int cx, int top) {
        final int white = 0xFFFFFFFF;
        ctx.fill(cx, top, cx + 3, top + 5, white);
        ctx.fill(cx - 2, top + 4, cx + 3, top + 9, white);
        ctx.fill(cx - 2, top + 8, cx + 1, top + 13, white);
    }
}
