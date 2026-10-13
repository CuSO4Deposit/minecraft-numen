# 架构决策记录(ADR)

每条记录写一个决定:当时的背景、定了什么、带来什么后果。格式照 Michael Nygard 的
[Documenting Architecture Decisions](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions)(2011)。

- 一条记录一个文件,按编号排,编号不复用。
- 记录定下以后不改内容;决定变了就写一条新的,旧的状态改成"被 NNNN 取代"或"被 NNNN 修订",两条互相链接。
- 状态:已接受、被取代、被修订。

| 模块 | 目录 |
|---|---|
| 寻路 | [pathing/](pathing/README.md) |
