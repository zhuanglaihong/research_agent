# Ollama 科研实验反馈：真实本机验收

2026-10-06，Windows x64、Java 21、Python 3.13.5、Ollama qwen3:8b。使用独立 H2 验证数据库，未修改用户现有项目。

## 已跑通的链路

1. 创建 Python 科研项目，添加带来源的二次优化方法笔记。
2. live Agent 读取检索上下文，用 writeArtifact 生成 train.py 和说明文件。
3. 人工审阅生成脚本：仅 json 标准库、有限循环、写 metrics.jsonl，无网络和子进程；随后通过实验审批接口交给 Java Runner。
4. Run 1 以退出码 0 成功结束，11 个指标点，loss 从 4.0 降到 0.04611686018427385。
5. 新任务调用 inspectExperiment(1)，读取实际日志/指标后写 analysis.md，并草拟比较学习率 0.05 的下一步任务。没有自动执行下一步。

这是基础集成验证，不是论文复现或真实模型训练质量评测。流程中的任务生成、运行和分析均有独立审批；工具输出提供数值证据，但自然语言解释仍需人工核查。

## 保存的证据

- train.py：模型实际生成的脚本副本，SHA256 `9CFB72E5C599CAC96006D8F84CEC79C90181EC8C53FFF0AF5A4A8B2A27225DBE`。
- metrics-report.json：运行 API 返回的实测指标。
- stdout.txt：Runner 实际输出。
- analysis.md 和 events.json：成功分析任务的模型报告及工具事件。

## 在自己电脑复验

按仓库 README 配置 Ollama，并显式开启 `RESEARCH_RUNNER_ENABLED=true`，指定可用 Python。创建项目后，提交如下代码任务并审阅产物：

```text
实现 Python 标准库二次函数优化实验：初始 weight=0，loss=(weight-2)^2，梯度=2*(weight-2)，学习率0.1，更新10次。记录初始和每次更新的 step、weight、loss 到 metrics.jsonl，并输出初始/最终loss。只生成 train.py 与说明文件，不执行。
```

批准实际生成的脚本后，从界面确认 Run ID，再提交分析任务：

```text
先调用 inspectExperiment 读取指定 Run ID 的真实日志和指标，将初始/最终loss、Run ID、实验局限写入analysis.md，并草拟学习率0.05的对照实验需求；不执行下一步，不编造命令参数。
```

模型可能选择不同文件路径，准备运行时应选择实际产物。生成任务的预计数值不是实测结果；本次生成阶段曾给出错误预估，运行后使用实际指标纠正。验收中另有一次后续分析失败，不能把一次成功等同于稳定性或论文质量保证。

仅重跑保存的标准库脚本可在本目录执行 `python train.py`；这复验数值计算，不等于重新验证 Agent 工具链。
