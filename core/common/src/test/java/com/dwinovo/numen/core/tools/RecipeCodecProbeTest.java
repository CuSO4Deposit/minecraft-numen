package com.dwinovo.numen.core.tools;

import com.dwinovo.numen.sdk.Codec;
import com.dwinovo.numen.sdk.LuaCodecs;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

class RecipeCodecProbeTest {

    @SuppressWarnings("unchecked")
    @Test
    void encodeRecipe() {
        var r = new Recipe("minecraft:oak_planks", Recipe.Station.CRAFTING, "minecraft:oak_planks", 4,
                List.of("oak_log"), Optional.of(2), Optional.empty(), Optional.empty());
        Codec<Recipe> codec = (Codec<Recipe>) LuaCodecs.of(Recipe.class, "numen");
        Object enc = codec.encode(r);
        System.out.println("PROBE ENC = " + enc);
        Object grid = (enc instanceof java.util.Map<?, ?> m) ? m.get("grid") : "NOT-A-MAP";
        System.out.println("PROBE grid = " + grid + " (" + (grid == null ? "null" : grid.getClass().getName()) + ")");
    }
}
