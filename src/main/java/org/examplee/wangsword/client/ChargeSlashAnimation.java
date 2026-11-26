package org.examplee.wangsword.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 读取 {@code assets/wangsword/player_animation/<name>.json}，按时间取关键帧。
 *
 * <p>不走 PlayerAnimator：它在这个环境里会把 Better Combat 的动画层挤掉。
 * 这里只负责“读数据”，摆骨骼交给 {@code PlayerChargePoseMixin}。
 */
public final class ChargeSlashAnimation {

    /** 大招：蓄力 + 下劈 */
    public static final ChargeSlashAnimation CHARGE = new ChargeSlashAnimation("charge_slash");
    /** 右键：风王结界 */
    public static final ChargeSlashAnimation WIND = new ChargeSlashAnimation("wind_barrier");

    private final Identifier file;
    private final Map<String, Channel[]> bones = new HashMap<>();
    private float length = 1.0F;
    private boolean loaded = false;
    private boolean failed = false;

    private ChargeSlashAnimation(String name) {
        this.file = new Identifier("wangsword", "player_animation/" + name + ".json");
    }

    public float length() {
        load();
        return length;
    }

    public boolean isLoaded() {
        load();
        return loaded;
    }

    /** 某根骨骼在 time 秒时的旋转（弧度），顺序 x/y/z；没有该骨骼返回 null */
    public float[] rotation(String bone, float time) {
        load();
        Channel[] channels = bones.get(bone);
        if (channels == null) {
            return null;
        }
        float t = Math.max(0.0F, Math.min(length, time));
        return new float[]{
                (float) Math.toRadians(channels[0].eval(t)),
                (float) Math.toRadians(channels[1].eval(t)),
                (float) Math.toRadians(channels[2].eval(t))
        };
    }

    private synchronized void load() {
        if (loaded || failed) {
            return;
        }
        failed = true;
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.getResourceManager() == null) {
                return;
            }
            try (InputStream in = client.getResourceManager().open(file)) {
                JsonObject root = JsonParser.parseReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject animations = root.getAsJsonObject("animations");
                if (animations == null) {
                    return;
                }
                for (Map.Entry<String, JsonElement> animation : animations.entrySet()) {
                    JsonObject animationObject = animation.getValue().getAsJsonObject();
                    JsonElement len = animationObject.get("animation_length");
                    if (len != null) {
                        length = len.getAsFloat();
                    }
                    JsonObject boneObjects = animationObject.getAsJsonObject("bones");
                    if (boneObjects == null) {
                        continue;
                    }
                    for (Map.Entry<String, JsonElement> bone : boneObjects.entrySet()) {
                        JsonObject rotation = bone.getValue().getAsJsonObject()
                                .getAsJsonObject("rotation");
                        if (rotation == null) {
                            continue;
                        }
                        bones.put(bone.getKey(), readChannels(rotation));
                    }
                }
            }
            loaded = true;
            failed = false;
        } catch (Exception ignored) {
            // 文件缺失或格式不对：由调用方决定怎么兜底
        }
    }

    private static Channel[] readChannels(JsonObject rotation) {
        TreeMap<Float, float[]> frames = new TreeMap<>();
        for (Map.Entry<String, JsonElement> frame : rotation.entrySet()) {
            float time;
            try {
                time = Float.parseFloat(frame.getKey());
            } catch (NumberFormatException e) {
                continue;
            }
            JsonArray vector = frame.getValue().getAsJsonObject().getAsJsonArray("vector");
            frames.put(time, new float[]{
                    vector.get(0).getAsFloat(),
                    vector.get(1).getAsFloat(),
                    vector.get(2).getAsFloat()
            });
        }
        Channel[] channels = {new Channel(), new Channel(), new Channel()};
        List<Map.Entry<Float, float[]>> sorted = new ArrayList<>(frames.entrySet());
        for (int axis = 0; axis < 3; axis++) {
            Channel channel = channels[axis];
            channel.times = new float[sorted.size()];
            channel.values = new float[sorted.size()];
            for (int i = 0; i < sorted.size(); i++) {
                channel.times[i] = sorted.get(i).getKey();
                channel.values[i] = sorted.get(i).getValue()[axis];
            }
        }
        return channels;
    }

    /** 单轴关键帧，线性插值 */
    private static final class Channel {
        private float[] times = new float[0];
        private float[] values = new float[0];

        private float eval(float time) {
            if (times.length == 0) {
                return 0.0F;
            }
            if (time <= times[0]) {
                return values[0];
            }
            if (time >= times[times.length - 1]) {
                return values[values.length - 1];
            }
            for (int i = 0; i < times.length - 1; i++) {
                if (time >= times[i] && time <= times[i + 1]) {
                    float span = times[i + 1] - times[i];
                    if (span <= 1.0E-5F) {
                        return values[i + 1];
                    }
                    return values[i] + (values[i + 1] - values[i])
                            * ((time - times[i]) / span);
                }
            }
            return values[values.length - 1];
        }
    }
}
