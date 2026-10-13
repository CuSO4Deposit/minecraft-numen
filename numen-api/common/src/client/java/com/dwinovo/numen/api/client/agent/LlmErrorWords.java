package com.dwinovo.numen.api.client.agent;

import com.dwinovo.numen.api.agent.http.LlmHttpException;
import net.minecraft.network.chat.Component;

/**
 * LLM 失败 → 分类人话的唯一真源。话术纪律:说人话 + 说下一步,不甩堆栈
 * (堆栈的去处是日志,传输层已经记全了)。设置屏的检测按钮与回合失败的
 * HUD/聊天栏播报共用这一张表——同一种错在哪里看到都是同一句话。
 */
import com.dwinovo.numen.api.data.ModLanguageData;

public final class LlmErrorWords {

    private LlmErrorWords() {}

    public static String classify(Throwable error) {
        Throwable cause = error;
        while (cause != null && !(cause instanceof LlmHttpException) && cause.getCause() != null
                && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        if (cause instanceof LlmHttpException http) {
            return withReply(http, category(http));
        }
        return t(ModLanguageData.Keys.GUI_PROVIDERS_CHECK_NETWORK);
    }

    /** 状态码对应的分类话术。 */
    private static String category(LlmHttpException http) {
        if (http.isUnauthorized()) return t(ModLanguageData.Keys.GUI_PROVIDERS_CHECK_UNAUTHORIZED);
        if (http.statusCode() == 404) return t(ModLanguageData.Keys.GUI_PROVIDERS_CHECK_NOT_FOUND);
        if (http.isRateLimited()) return t(ModLanguageData.Keys.GUI_PROVIDERS_CHECK_RATE_LIMITED);
        if (http.statusCode() >= 500) return t(ModLanguageData.Keys.GUI_PROVIDERS_CHECK_SERVER_ERROR);
        return t(ModLanguageData.Keys.GUI_PROVIDERS_CHECK_BAD_REQUEST) + " (HTTP " + http.statusCode() + ")";
    }

    /**
     * 分类话术后面带上是谁回的、说了什么原话。同一个 401,key 发错了地方(回话来自另一家)和 key 本身不对
     * (厂家说 “User not found”)是两回事,只说“密钥无效”玩家和我们都分不出。
     */
    private static String withReply(LlmHttpException http, String category) {
        String reply = http.providerMessage();
        if (reply.isEmpty()) return category;
        return category + " · " + Component.translatable(ModLanguageData.Keys.GUI_PROVIDERS_CHECK_REPLY,
                http.host(), reply).getString();
    }

    private static String t(String key) {
        return Component.translatable(key).getString();
    }
}
