# Digits 真实实验案例

人工编写的 CPU 分类基线，使用 scikit-learn 内置 Digits 数据集；不调用模型 API，不声称由 Agent 自动生成，也不声称复现特定论文。

在仓库根目录执行（Python 3.10–3.13）：

```powershell
python -m venv .venv
.\.venv\Scripts\python -m pip install -r examples/digits-baseline/requirements.txt
.\.venv\Scripts\python examples/digits-baseline/train.py --output-dir data/real-cases/digits
```

macOS/Linux 将解释器替换为 `.venv/bin/python`。CPU 即可，无 GPU、密钥或运行时数据下载。

固定分层划分为 60% 训练、20% 验证、20% 测试；只用验证损失选择 checkpoint。使用 42、43、44 三个优化器随机种子，同一数据划分，最多 60 轮，验证损失连续 5 轮未改善即早停。生成 JSONL 指标、结果 JSON、Markdown 报告、训练 SVG 和混淆矩阵 PNG。样本标准差使用 ddof=1。

将 `train.py` 放入已生成任务的产物目录，配置 `RESEARCH_PYTHON_EXECUTABLE` 为安装上述依赖的虚拟环境解释器；开启 Runner，审阅并确认运行。脚本默认将产物写入工作目录，`metrics.jsonl` 是 seed 42 的验证指标，其他种子的日志单独保存。

网页的实测快照可通过下面命令重新生成：

```powershell
python examples/digits-baseline/train.py --output-dir data/real-cases/digits --demo-json frontend/src/fixtures/digits-case.json
```

结果保存环境版本、数据与脚本 SHA256。三个随机种子只衡量优化器初始化变化，不等价于跨数据划分或跨数据集评估；此案例用于验证真实训练与展示，模型代码生成仍需另行验证。

来源：[Digits 数据集官方说明](https://scikit-learn.org/stable/modules/generated/sklearn.datasets.load_digits.html)、[SGDClassifier 官方说明](https://scikit-learn.org/stable/modules/generated/sklearn.linear_model.SGDClassifier.html)。
