# 模型接入:目录、协议与兼容项

状态:设计稿(10-14),用户 10-14 同意,第九节四项按建议定。第 1、2、3、5 步已落地(第十一节写现状与差异),第 4 步待做。决策的来路见 `docs/adr/llm/`;这份写打算怎么改。

## 一、要解决的问题

1. **目录是手写的。** `ai/src/main/resources/numen_providers.json` 只有 15 家,每家几个型号与上下文长度,都会过时;
   能不能看图、输出上限、价格这些没有;玩家要接表里没有的型号,只能当成"自定义",能力一概不知。
2. **思考的写法按"家"定,表达不了"按型号定"。** 每家在目录里选一种 `thinkingFormat`(llm 0008)。
   可同一家不同型号控制方式不同(DeepSeek 新型号是开关 + 档位并存,Anthropic 新型号是自适应思考),只能写死或打补丁。
3. **厂家的差异散在三处:** 目录里的 `thinkingFormat` 与 `protocol`、`LlmProvider` 接口上的方言常量与 `defaultBaseUrl`、
   DeepSeek 和 Moonshot 两个子类(缓存计费、`reasoning_content` 回传)。加一家要知道该改哪处。
4. **Responses 从没接过真接口**(llm 0009),OpenAI 官方仍走 Chat Completions。
5. **OpenRouter 报 "Invalid key"**(已对外承诺 0.1.5 修),根因见第七节。

## 二、参照:pi-mono 的模型层(读过源码)

pi-mono 的 `packages/ai`(本地 `D:\01_Projects\pi-mono`)与我们要的几乎同构,而且它的型号目录就是从 models.dev 生成的
(`scripts/generate-models.ts`):

- **一个型号一条记录**(`src/types.ts` 的 `Model`):`id`、`name`、`api`(走哪种协议)、`provider`、`baseUrl`、`reasoning`、
  `thinkingLevelMap`(统一档位 → 这个型号的取值,`null` 表示不支持)、`input`(文字/图片)、`cost`、`contextWindow`、`maxTokens`、
  `headers`、`compat`(这种协议下的兼容项)。
- **协议由 models.dev 的 `npm` 字段推出:** `@ai-sdk/openai` → `openai-responses`,`@ai-sdk/anthropic` → `anthropic-messages`,
  其余(含 `@ai-sdk/openai-compatible`)→ `openai-completions`。
- **思考档位由 `reasoning_options` 推出**(`scripts/models-dev-reasoning-options.ts`):档位型直接映射到统一档位,不支持的记 `null`。
- **兼容项按协议分三套**,缺省按厂家与地址自动认出,个别可以覆盖。Chat 这一套里的 `thinkingFormat`(`openai`、`openrouter`、`deepseek`、
  `zai`、`qwen` 等)就是我们的"六族思考方言";还有 `maxTokensField`、`supportsDeveloperRole`、`supportsUsageInStreaming`、
  `requiresReasoningContentOnAssistantMessages`(DeepSeek、Kimi 要求回传 `reasoning_content`)等。
  Anthropic 这套有 `forceAdaptiveThinking`、`supportsTemperature`;Responses 这套有 `supportsDeveloperRole` 等。

我们照这个分法,只做我们要的三种协议,兼容项只收我们现在真有差异的那几项。

## 三、分三层

```
型号目录(数据)          协议(代码,三种)              兼容项(极少的代码知识)
models.dev 快照/缓存  →  Chat Completions            按协议各一组,缺省按厂家/地址认出,
+ 玩家手填               Responses                   自定义厂家可以覆盖
                        Anthropic Messages
```

### 1. 型号目录(`Catalog`,取代 `ProviderRegistry`)

**厂家:** `id`、名字、缺省地址、协议、鉴权方式、额外报头、兼容项。
**型号:** `id`、名字、厂家、协议(缺省随厂家)、上下文长度、输出上限、能不能看图、能不能调工具、能不能调温度、思考档位表、价格。

- **协议:** 按 `npm` 推(同 pi)。models.dev 里 OpenAI、Anthropic、xAI、Google 没写 `api` 地址(由 SDK 内置),我们补一张这几家的缺省地址;
  Google 原生协议我们不做,照旧走它的 OpenAI 兼容端点(`generativelanguage.googleapis.com/v1beta/openai`)。
- **来源与优先级(llm 0012):** 玩家手填 > 厂家自己的 `/models` 接口 > models.dev 缓存 > 随包快照。**按字段取最高的那一个,不合并、不猜。**
  - 随包快照:构建时从 `models.dev/api.json` 生成一份精简的 JSON(只留我们用到的字段)打进 `ai` 模块。
  - 运行时:客户端启动后后台拉一次 `api.json`,存 `config/numen/cache/models-dev.json`,一天最多刷一次;拉不到就用缓存或快照,不报错。
  - 厂家 `/models`:只用来列出 models.dev 没有的型号 id(例如本地的 Ollama、LM Studio、vLLM);能力仍按"不知道"处理。
- **列哪些厂家:** models.dev 有 228 家。界面上先列常用的一组(OpenAI、Anthropic、DeepSeek、OpenRouter、Kimi、智谱、通义、豆包、硅基流动、
  MiniMax、Google、xAI,国内外两个端点分开),其余可搜;本地部署(Ollama、LM Studio、vLLM)作为"自定义厂家"的模板。

### 2. 协议(`Api`,取代 `LlmProvider` 里与厂家相关的部分)

三种:`chat`(Chat Completions)、`responses`(OpenAI Responses)、`anthropic`(Anthropic Messages)。我们现有的
`OpenAIProvider`、`OpenAIResponsesProvider`、`AnthropicProvider` 就是这三种,改的是它们和外面的接口。

**中立的请求**(循环与会话只认它):系统提示、会话(`ConvoState.Msg`,经 `ProtocolView` 修成合法序列,llm 0011)、工具表(`IToolSpec`)、
选项(输出上限、温度、思考档位、缓存提示)。

**协议实现只做两件事:**
- 把中立请求 + 型号 + 兼容项编成一个 HTTP 请求(路径、鉴权头、请求体);
- 把流式返回解成中立事件:文字片段、思考片段、工具调用开始/参数片段/结束、用量、结束原因。最后得到 `AssistantTurn`。

**回传的私货:** `AssistantTurn` 里协议要求原样带回的东西(`reasoning_content`、思考签名、Responses 的加密思考 item)仍放在 `extras`,
出处(协议 + 厂家 + 型号)对不上就脱掉(llm 0010)。

传输(重试、退避、看门狗、取消,llm 0004)、用量四元与缓存记账(llm 0006)不变,用量字段的解读归各协议(Chat 协议把几家的缓存字段都认一遍:
`prompt_tokens_details.cached_tokens`、`prompt_cache_hit_tokens` 等)。

### 3. 兼容项(`Compat`,按协议各一组)

只收现在真有差异的几项:

| 协议 | 兼容项 | 取代现在的 |
|---|---|---|
| chat | `thinkingFormat`:思考参数怎么写(`openai` 顶层 `reasoning_effort` / `openrouter` 嵌套 `reasoning.effort` / `deepseek`、`zai` 写 `thinking.type` / `qwen` 写 `enable_thinking` / `none`) | 目录里的 `thinkingFormat` 与 `LlmProvider` 上的 `THINKING_*` 常量 |
| chat | `maxTokensField`(`max_tokens` 或 `max_completion_tokens`) | `applyGenerationParams` 写死的 `max_tokens` |
| chat | `replayReasoningContent`:回传时带上 `reasoning_content` | `DeepSeekProvider`、`MoonshotProvider` 两个子类 |
| chat | `streamUsage`:收不收 `stream_options.include_usage` | `applyStreaming` 的默认 |
| responses | `developerRole`:系统提示用 `developer` 还是 `system` | 新增 |
| anthropic | `adaptiveThinking`:用 `thinking.type:adaptive` + `output_config.effort`,还是 `budget_tokens` | 目录里的 `budget` 形态 |
| anthropic | `temperature`:收不收温度 | 新增 |

**认出兼容项只在一处:** 按厂家 id 与地址推出缺省值的一个函数;自定义厂家在界面上可以改。

## 四、思考档位

- **统一档位:** 自动、关、低、中、高、最高。"自动"什么思考参数都不发,这条不变(llm 0008)。
- **每个型号支持哪几档,由目录里的思考档位表给出**(由 models.dev 的 `reasoning_options` 推出):
  - 档位型(`effort`,带取值表):只列取值表里有的档位;
  - 开关型(`toggle`):只有开、关;
  - 预算型(`budget_tokens`,带下限):档位映射成预算,预算表一张(待定,见第九节),不低于下限。
- **请求怎么写**由协议 + 兼容项的 `thinkingFormat` 决定。models.dev 只说"有开关",不说开关字段叫什么——这正是兼容项要管的。
- **查不到能力的型号**(自定义型号、`/models` 里列出来的)只给"自动"。

界面照旧只有一个下拉(llm 0008),选项随型号变。

## 五、档案与界面

- **档案**(`ProviderLibrary`,llm 0005)存:名字、厂家 id、型号 id、key、地址覆盖、代理、思考档位、上下文覆盖。能力一律从目录查。
- **选厂家 → 型号列表来自目录**,可搜;型号下面显示上下文、能不能看图、支持哪些思考档位。也可以直接手填型号 id。
- **自定义厂家:** 地址 + 协议(三选一)+ key,兼容项给缺省、可改;存进档案库,与内置厂家同等对待。
- **老档案迁移:** 一次性把老的厂家 id 映射到 models.dev 的 id(`moonshot`→`moonshotai`、`zhipu`→`zhipuai`、`dashscope`→`alibaba-cn`、
  `siliconflow`→`siliconflow-cn`、`gemini`→`google`、`grok`→`xai`;`ollama`、`lmstudio`、`vllm` 变成自定义厂家),映射表只用于迁移,迁完不再读。

## 六、OpenAI 默认走 Responses

models.dev 里 OpenAI 的 `npm` 是 `@ai-sdk/openai`,按 pi 的规则走 Responses。这一步需要真接口验证(思考 item 回传、`developer` 角色、
工具表扁平形状、用量字段),要用你的 OpenAI key 跑一次真实对话。

## 七、OpenRouter "Invalid key"

查到的事实:
- 我们把**所有 401** 都显示成"密钥无效"(`LlmErrorWords`),厂家的原话没给玩家看。
- 用一个假的 OpenRouter key 实测:发到 OpenRouter 回 `401 User not found.`;发到 OpenAI 回 `401 invalid_api_key`;key 只有空白时回 `Missing Authentication header`。
- 我们从不去掉 key 首尾的空白。复制 key 时常带上空格或换行,发出去就是一个错的 key。

可能的根因有两个,都修:
1. **key 带空白:** 在档案保存与解析成 `LlmEndpoint` 处去掉首尾空白(只此一处)。
2. **请求发错了地方:** 报错时带上请求的主机名与厂家原话(例如"openrouter.ai 回复:User not found."),玩家与我们一眼看出是哪种。

报告者在 1.20.1,能复现最好;不能复现就以这两处修好为准,在 issue 里说明。

## 八、删掉什么

- `numen_providers.json` 与 `ProviderRegistry`(由目录取代;别名表只留迁移用的那张)。
- `DeepSeekProvider`、`MoonshotProvider`(由兼容项取代)。
- `LlmProvider` 上的 `THINKING_*` 常量、`defaultBaseUrl`、`pickProvider` 的分支(由目录与兼容项取代)。
- 目录里每个型号的手写 `temperature`/`maxTokens`(生成参数改由档案或兼容项给;要不要保留见第九节)。

## 九、已定(10-14 用户)

1. **厂家:** 界面先列常用的十几家(国内外端点分开),其余可搜。
2. **运行时拉 models.dev:** 默认开;拉不到静默用缓存或随包快照。
3. **预算型的档位映射:** 低 2048、中 8192、高 16384、最高 32768 token,不低于型号的下限。
4. **温度与输出上限:** 改到档案上设,不再按型号写在目录里。

## 十、分步

1. **目录:** 构建时生成快照、运行时缓存与刷新、`Catalog` 读口;老档案迁移。行为不变(同样的厂家、同样的请求)。
2. **协议与兼容项:** 三种协议改吃中立请求与兼容项;删两个子类与方言常量。用录下来的请求与流式返回做单测(每种协议、每种思考写法一组)。
3. **思考档位与界面:** 档位表、下拉随型号变、型号能力展示、自定义厂家。
4. **Responses 设为 OpenAI 缺省:** 真接口验证。
5. **OpenRouter:** key 去空白、报错带主机与原话。

每步:构建、单测、日常 GameTest;第 2、4 步要真接口跑一次(DeepSeek、OpenAI、Anthropic、OpenRouter 各一次,用你的 key,不跑 bench)。

## 十一、落地情况(第 1、2、3、5 步)

**目录**(`ai` 的 `agent.catalog`)
- 随包快照 `ai/src/main/resources/numen_models_snapshot.json`:models.dev 全部 228 家的裁剪版(2.6 MB,一家一行),没有再按“常用”裁——
  常用只是 `Catalog.popular()` 的一份标记,界面第 3 步用。手动更新:`./gradlew :ai:updateModelsSnapshot`(可 `-PmodelsProxy=host:port`),普通构建不联网。
- 运行时缓存 `config/numen/cache/models-dev.json`:`ProviderLibrary.refreshCatalog()` 在客户端启动时后台刷新,一天最多一次,拉不到静默。
- 来源是 `CatalogSource`(缓存 `DownloadCache`、快照 `BundledSnapshot`),只读自己那份;优先级只在 `Catalog` 的构造顺序里,整条记录取最高来源的。
  厂家 `/models` 这一级还没做:以后作为一个 `CatalogSource` 插在缓存之前。玩家手填的值在档案里,由 `EndpointResolver` 盖在目录结果之上。
- 档案 `ConnectionProfile` + 目录 → `LlmEndpoint`,只在 `EndpointResolver` 一处。请求、压缩阈值、界面都取这个结果。

**协议与兼容项**(`agent.provider`)
- 三个协议实现由 `LlmProvider.of(compat)` 组装;编请求只吃 `ModelSpec`(型号与档位表)、`RequestOptions`(档案里的温度、输出上限、思考档位)和各自的兼容项。
- 兼容项缺省的认出与档案覆盖项只在 `Compats.resolve` 一处;思考档位表是 `ThinkingTable`,预算映射表只在那里。

**与上文不一致的地方**
- `adaptiveThinking` 的缺省取决于**型号**(只有 effort 取值、没有预算写法),所以 `Compats.resolve` 多收一个档位表。
- Chat 的 `thinkingFormat`:Z.ai、智谱、Kimi、火山方舟同形(只写 `thinking.type`),都归 `zai`;不再为 xAI 特判,有没有档位由型号的档位表决定。
- 回合出处戳改成 `协议/厂家/型号`(以前是适配器类名/型号,同一协议的不同厂家分不开)。老存档里的戳对不上,升级后老回合的私货会被脱掉一次。
- OpenAI 官方暂按 Chat(`VendorDefaults.API_OVERRIDES` 一行,第 4 步删掉)。Responses 请求不再附带 `stream_options`。
- 老档案迁移在 `ConfigMigrations`:档案库根上的 `models_dev_ids` 标记是“已迁过”的判据(`lmstudio` 既是老 id 又是 models.dev 的 id,只看 id 分不出)。
- 密钥去首尾空白在 `LlmEndpoint` 的构造里,是全仓唯一一处;报错带主机与厂家原话在 `LlmHttpException`;失败原因是 agent 的原因码(`Cause`),界面按语言由 `CauseWords` 说。

**思考档位与界面**(第 3 步)
- "这一档在这个型号、这个协议与兼容项下实际写成什么"只在 `ThinkingPlan`(`agent.provider`)算一次:请求体、界面列哪几档、每档右边的说明都问它。
  发不出参数的档位不列;只剩"自动"时界面不出思考一栏。
- 照 pi:OpenRouter 对推理型号一律写 `reasoning.effort`(目录只给预算或开关的,缺省给低、中、高),关写 `none`;Anthropic 的关写 `thinking.type=disabled`。
- 已弃用(`status: deprecated`)的型号在读目录时丢掉,界面不显示也不提示;档案里用着的老型号按手填处理。快照里仍留着它们。
- 型号能力多留输出上限与输入模态,界面叫法照 pi 与 models.dev:上下文、最大输出、思考、多模态、工具调用。
- 界面照定稿草图:厂家、型号用窗口中央的选择框 `PickerBox`(搜索在顶,型号带规格列);短列表就地下拉,按可见区域摆放。

**与上文不一致的地方(第 3 步)**
- 第四节说预算型"不低于下限";另外还去掉不小于型号输出上限的档(预算算在输出里,发出去必被拒)。
- 第五节说型号下面显示"能不能看图";改为输入模态(图像、PDF 等),跟 models.dev 一致。
