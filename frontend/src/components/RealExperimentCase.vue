<template>
  <a-card title="真实实验案例 · Digits 分类基线" :bordered="false" class="real-case">
    <p>人工编写的 SGD 分类器已在本机实际运行。这里展示保存的实测结果；网页访问时不重新训练，也没有调用大模型。</p>
    <div class="case-summary">
      <strong>测试准确率 {{ (data.testAccuracyMean * 100).toFixed(2) }}%</strong>
      <span>3 个随机种子 · 样本标准差 {{ (data.testAccuracyStd * 100).toFixed(2) }} 个百分点</span>
      <span>{{ data.samples }} 条样本 · 训练/验证/测试：{{ data.split.train }}/{{ data.split.validation }}/{{ data.split.test }}</span>
    </div>
    <table><thead><tr><th>随机种子</th><th>训练轮数</th><th>最佳轮次</th><th>测试准确率</th></tr></thead>
      <tbody><tr v-for="run in data.runs" :key="run.seed"><td>{{ run.seed }}</td><td>{{ run.epochs }}</td><td>{{ run.bestEpoch }}</td><td>{{ (run.testAccuracy * 100).toFixed(2) }}%</td></tr></tbody>
    </table>
    <img :src="base + 'cases/digits/training-curves.svg'" alt="实际训练的三随机种子验证损失与准确率曲线" />
    <details><summary>查看混淆矩阵与复现信息</summary>
      <img class="matrix" :src="base + 'cases/digits/confusion-matrix.png'" alt="随机种子 42 的测试集混淆矩阵" />
      <p>Python {{ data.environment.python }} · scikit-learn {{ data.environment.scikitLearn }} · NumPy {{ data.environment.numpy }}</p>
      <p class="hash">代码 SHA256：{{ data.codeSha256 }}</p>
      <p>采用固定分层划分和验证损失早停，测试集只在选定 checkpoint 后评估。同一划分上的三个种子不能代表跨数据集泛化。</p>
    </details>
    <div class="case-links">
      <a href="https://github.com/zhuanglaihong/research_agent/tree/main/examples/digits-baseline" target="_blank" rel="noopener noreferrer">代码与复现命令</a>
      <a :href="base + 'cases/digits/results.json'" download="digits-results.json">下载原始结果 JSON</a>
      <a :href="base + 'cases/digits/training-curves.svg'" download="digits-training.svg">下载训练图 SVG</a>
    </div>
  </a-card>
</template>

<script setup lang="ts">
import data from '@/fixtures/digits-case.json'
const base = import.meta.env.BASE_URL
</script>

<style scoped>
.real-case { margin-top:24px; }
.real-case p { color:#475467; line-height:1.7; }
.case-summary { display:flex; flex-wrap:wrap; gap:16px; background:#eff6ff; padding:16px; border-radius:8px; }
table { width:100%; margin:18px 0; border-collapse:collapse; }
th,td { padding:10px; border-bottom:1px solid #e5e7eb; text-align:left; }
img { width:100%; height:auto; }
.matrix { max-width:600px; }
.case-links { display:flex; flex-wrap:wrap; gap:20px; margin-top:16px; }
.hash { overflow-wrap:anywhere; font-size:12px; }
summary { cursor:pointer; padding:12px 0; }
</style>
