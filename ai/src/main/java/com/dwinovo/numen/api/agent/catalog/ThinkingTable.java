package com.dwinovo.numen.api.agent.catalog;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 一个型号的思考档位表:它支持统一档位里的哪几档,每一档在线上写成什么。
 * 由 models.dev 的 {@code reasoning_options} 推出,是"这个型号能怎么控制思考"的唯一出处——
 * 界面列哪几档、请求怎么写,都问它。
 *
 * <p>models.dev 描述的控制方式有三种,可以并存:
 * <ul>
 *   <li>{@code effort}(带取值表):线上就是取值表里的字,统一档位按下表认领——
 *       低 = {@code low}(没有就用 {@code minimal}),中 = {@code medium},高 = {@code high},
 *       最高 = {@code max}(没有就用 {@code xhigh});{@code none} 是"关"。表里没有的档位这个型号就没有。</li>
 *   <li>{@code budget_tokens}(可带下限):档位映射成 token 预算,见 {@link #budgetTokens};
 *       思考是选择性开启的,不发就是关。</li>
 *   <li>{@code toggle}:只有开和关。"开"在统一档位里没有位置,记成中档。</li>
 * </ul>
 * 型号不是推理型号,或者推理却没给任何控制方式、或者目录里根本查不到,都只有 {@link ThinkingLevel#AUTO}。
 *
 * @param levels    可选的档位,第一个永远是 AUTO
 * @param efforts   档位 → 线上取值(只含 effort 型给出的;{@code OFF → "none"} 也在这里)
 * @param toggle    是否只有开关(没有 effort 取值表也没有预算)
 * @param budget    是否有预算型控制
 * @param budgetMin 预算下限,没写是 0
 */
public record ThinkingTable(List<ThinkingLevel> levels, Map<ThinkingLevel, String> efforts,
                            boolean toggle, boolean budget, int budgetMin) {

    /** 查不到能力、不是推理型号、或没有可控的方式。 */
    public static final ThinkingTable AUTO_ONLY =
            new ThinkingTable(List.of(ThinkingLevel.AUTO), Map.of(), false, false, 0);

    /** 预算型的档位 → token 数:全仓唯一的一张表。 */
    private static final Map<ThinkingLevel, Integer> BUDGETS = Map.of(
            ThinkingLevel.LOW, 2_048,
            ThinkingLevel.MEDIUM, 8_192,
            ThinkingLevel.HIGH, 16_384,
            ThinkingLevel.MAX, 32_768);

    /** 每个统一档位认领的 effort 取值,按先后顺序取第一个有的。 */
    private static final Map<ThinkingLevel, List<String>> EFFORT_WORDS = Map.of(
            ThinkingLevel.LOW, List.of("low", "minimal"),
            ThinkingLevel.MEDIUM, List.of("medium"),
            ThinkingLevel.HIGH, List.of("high"),
            ThinkingLevel.MAX, List.of("max", "xhigh"));

    public ThinkingTable {
        levels = List.copyOf(levels);
        efforts = Map.copyOf(efforts);
    }

    /**
     * 从 models.dev 的 {@code reasoning} 与 {@code reasoning_options} 推出。
     *
     * @param options 可为 null(字段缺失)
     */
    public static ThinkingTable parse(boolean reasoning, JsonArray options) {
        return parse(reasoning, options, 0);
    }

    /**
     * 同上,并且预算型只列预算低于型号输出上限的档(预算是输出的一部分,不小于上限的档发出去必被拒)。
     *
     * @param maxOutput 型号的输出上限;0 = 不知道,不设限
     */
    public static ThinkingTable parse(boolean reasoning, JsonArray options, int maxOutput) {
        if (!reasoning || options == null) return AUTO_ONLY;
        List<String> words = new ArrayList<>();
        boolean toggle = false;
        boolean budget = false;
        int budgetMin = 0;
        for (JsonElement el : options) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            String type = o.has("type") ? o.get("type").getAsString() : "";
            switch (type) {
                case "toggle" -> toggle = true;
                case "budget_tokens" -> {
                    budget = true;
                    if (o.has("min") && o.get("min").isJsonPrimitive()) {
                        budgetMin = Math.max(0, o.get("min").getAsInt());
                    }
                }
                case "effort" -> {
                    if (o.has("values") && o.get("values").isJsonArray()) {
                        for (JsonElement v : o.getAsJsonArray("values")) {
                            if (v.isJsonPrimitive()) words.add(v.getAsString());
                        }
                    }
                }
                default -> { }
            }
        }

        Map<ThinkingLevel, String> efforts = new EnumMap<>(ThinkingLevel.class);
        for (var e : EFFORT_WORDS.entrySet()) {
            for (String w : e.getValue()) {
                if (words.contains(w)) {
                    efforts.put(e.getKey(), w);
                    break;
                }
            }
        }
        boolean hasNone = words.contains("none");
        if (hasNone) efforts.put(ThinkingLevel.OFF, "none");

        boolean offable = hasNone || toggle || budget;
        List<ThinkingLevel> levels = new ArrayList<>();
        levels.add(ThinkingLevel.AUTO);
        if (efforts.keySet().stream().anyMatch(l -> l != ThinkingLevel.OFF)) {
            if (offable) levels.add(ThinkingLevel.OFF);
            for (ThinkingLevel l : EFFORT_WORDS.keySet().stream().sorted().toList()) {
                if (efforts.containsKey(l)) levels.add(l);
            }
            return new ThinkingTable(levels, efforts, false, budget, budgetMin);
        }
        if (budget) {
            levels.add(ThinkingLevel.OFF);
            for (ThinkingLevel l : BUDGETS.keySet().stream().sorted().toList()) {
                int tokens = Math.max(BUDGETS.get(l), budgetMin);
                if (maxOutput <= 0 || tokens < maxOutput) levels.add(l);
            }
            return new ThinkingTable(levels, efforts, false, true, budgetMin);
        }
        if (toggle) {
            levels.addAll(List.of(ThinkingLevel.OFF, ThinkingLevel.MEDIUM));
            return new ThinkingTable(levels, efforts, true, false, 0);
        }
        return AUTO_ONLY;
    }

    /** 档案里选的档位这个型号支不支持;不支持当作 AUTO(什么都不发)。 */
    public ThinkingLevel effective(ThinkingLevel requested) {
        return requested != null && levels.contains(requested) ? requested : ThinkingLevel.AUTO;
    }

    /** 这一档在线上写成的 effort 取值;没有给 {@code null}。 */
    public String effort(ThinkingLevel level) {
        return efforts.get(level);
    }

    /** 一档在线上的样子。{@code effort} 与 {@code tokens} 只在对应的 {@link Kind} 下有意义。 */
    public record Wire(Kind kind, String effort, int tokens) {
        public enum Kind { AUTO, OFF, ON, EFFORT, BUDGET }
    }

    /**
     * 这一档实际会发什么:界面给每一档配的说明从这里来,不另写一份映射。
     * 有 effort 取值的档发取值;没有就看控制方式:开关型的开(记成中档)不带量,预算型发预算。
     */
    public Wire wire(ThinkingLevel level) {
        if (level == ThinkingLevel.AUTO) return new Wire(Wire.Kind.AUTO, null, 0);
        if (level == ThinkingLevel.OFF) return new Wire(Wire.Kind.OFF, null, 0);
        String effort = efforts.get(level);
        if (effort != null) return new Wire(Wire.Kind.EFFORT, effort, 0);
        if (budget) return new Wire(Wire.Kind.BUDGET, null, budgetTokens(level));
        return new Wire(Wire.Kind.ON, null, 0);
    }

    /** 这一档的预算(token),不低于型号下限;低/中/高/最高以外的档位给 0。 */
    public int budgetTokens(ThinkingLevel level) {
        Integer b = BUDGETS.get(level);
        return b == null ? 0 : Math.max(b, budgetMin);
    }

    /** 控制方式是"effort 取值表,没有预算":自适应思考的型号是这个形状。 */
    public boolean effortOnly() {
        return !efforts.isEmpty() && !budget;
    }
}
