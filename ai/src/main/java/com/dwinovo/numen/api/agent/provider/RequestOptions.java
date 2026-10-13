package com.dwinovo.numen.api.agent.provider;

import com.dwinovo.numen.api.agent.catalog.ThinkingLevel;

/**
 * 玩家在档案上设的生成参数。都是主动选项:不设就不发,吃服务端和协议的缺省——
 * 未知参数会被部分后端 400。
 *
 * @param temperature 温度;null = 不发
 * @param maxTokens   输出上限(token);0 = 不发(Anthropic 必填,用协议自己的缺省)
 * @param thinking    想要的思考档位(型号不支持的档位按 AUTO 处理,见 {@link #thinkingFor})
 */
public record RequestOptions(Double temperature, int maxTokens, ThinkingLevel thinking) {

    public static final RequestOptions NONE = new RequestOptions(null, 0, ThinkingLevel.AUTO);

    /** 这个型号上真正要发的温度;型号不收温度就是 null。 */
    public Double temperatureFor(ModelSpec model) {
        return model.temperature() ? temperature : null;
    }

    /** 这个型号上真正生效的思考档位;型号没有这一档就是 AUTO(什么都不发)。 */
    public ThinkingLevel thinkingFor(ModelSpec model) {
        return model.thinking().effective(thinking);
    }
}
