<template>
  <section class="tour" aria-labelledby="tour-heading">
    <header class="tour-header">
      <div>
        <p class="kicker">YOUR RESEARCH WORKSPACE</p>
        <h2 id="tour-heading">一个项目，五个科研工作场景</h2>
        <p>从阅读资料到实验结论，先了解你能如何下达任务。</p>
      </div>
      <span class="preview-label">交互式产品导览 · 预设内容</span>
    </header>
    <div class="tour-layout">
      <nav class="scenario-nav" aria-label="科研功能场景">
        <button
          v-for="(item, index) in scenarios"
          :key="item.id"
          :class="{ selected: selected === index }"
          :aria-pressed="selected === index"
          @click="selected = index"
        >
          <span class="number">0{{ index + 1 }}</span
          ><span
            ><strong>{{ item.title }}</strong
            ><small>{{ item.subtitle }}</small></span
          ><span class="arrow">↗</span>
        </button>
      </nav>
      <article class="scenario-detail" aria-live="polite">
        <div class="detail-top">
          <span class="category">{{ current.label }}</span
          ><span class="status">{{ current.status }}</span>
        </div>
        <h3>{{ current.headline }}</h3>
        <div class="request">
          <span>你可以这样说</span>
          <p>“{{ current.prompt }}”</p>
        </div>
        <div class="detail-grid">
          <div>
            <h4>助手的工作流程</h4>
            <ol>
              <li v-for="(step, index) in current.steps" :key="step">
                <span>{{ index + 1 }}</span
                >{{ step }}
              </li>
            </ol>
          </div>
          <div class="outputs">
            <h4>你拿到的成果</h4>
            <div v-for="output in current.outputs" :key="output"><span>▤</span>{{ output }}</div>
          </div>
        </div>
        <p class="boundary"><strong>使用方式：</strong>{{ current.actual }}</p>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
const selected = ref(0)
const scenarios = [
  {
    id: 'knowledge',
    title: '科研知识管理',
    subtitle: '论文、笔记与项目记忆',
    label: 'KNOWLEDGE',
    status: '资料与记忆',
    headline: '让资料成为项目上下文',
    prompt: '关注时间序列预测论文，结合我的笔记检索相关方法，回答时给出来源。',
    steps: [
      '按主题获取论文并去重',
      '解析 PDF 与笔记，按项目分块检索',
      '结合项目记忆回答并保留来源',
    ],
    outputs: ['论文与文献笔记', '带来源的检索片段', '项目 memory.md'],
    actual: '通过 arXiv、PDF、项目笔记与文件记忆组织研究资料，检索结果附带来源。',
  },
  {
    id: 'paper',
    title: '论文创建项目',
    subtitle: '方法片段到代码产物',
    label: 'PAPER TO CODE',
    status: '方法到代码任务',
    headline: '把方法描述转成可检查的实现',
    prompt: '根据论文方法实现最小 Python 版本，列出数据、依赖和尚未明确的假设。',
    steps: ['导入论文并审阅方法片段', '确认代码任务与项目约束', 'Agent 调用文件工具生成代码和说明'],
    outputs: ['代码与配置文件', '依赖和运行说明', '待验证方法与假设'],
    actual: '上传方法片段、确认需求，由模型生成可审阅的代码和运行说明。',
  },
  {
    id: 'coding',
    title: '已有项目编程',
    subtitle: '阅读代码、提出修改',
    label: 'PROJECT CODING',
    status: '工作区与产物',
    headline: '围绕你的仓库协作',
    prompt: '阅读训练入口，生成加入早停和固定随机种子的修改版本，保留原文件。',
    steps: ['读取授权工作区的目录和文本', '结合科研要求生成独立修改产物', '人工检查后选择后续操作'],
    outputs: ['独立修改文件', '实现说明', '项目历史上下文'],
    actual: '修改产物独立保存，供用户检查和下载，保留原项目文件。',
  },
  {
    id: 'experiment',
    title: '实验执行与监控',
    subtitle: '运行状态、日志与指标',
    label: 'EXPERIMENTS',
    status: '批准运行',
    headline: '在网页看见实验发生了什么',
    prompt: '运行已检查的训练脚本，记录 loss 与准确率，结束后保留结果。',
    steps: ['检查脚本并明确确认运行', '后台执行并读取日志和数值指标', '查看结果，或取消与处理超时'],
    outputs: ['运行日志与退出状态', 'JSONL / CSV 指标', '基础训练曲线'],
    actual: 'Runner 按需开启，单次预算 5 分钟；先审阅可信脚本，再批准运行。',
  },
  {
    id: 'analysis',
    title: '结果分析与绘图',
    subtitle: '从指标到可追溯结论',
    label: 'RESULTS',
    status: '指标与曲线',
    headline: '让每个结论对应实验记录',
    prompt: '查看这次训练的 loss 变化，整理起始、最终和最优值，并生成曲线。',
    steps: ['读取对应运行的数值文件', '计算基础指标摘要', '显示 SVG 曲线并草拟后续分析任务'],
    outputs: ['起始 / 最终 / 最优值', '可下载 SVG 曲线', '分析任务草稿'],
    actual: '从运行指标生成数值摘要和 SVG 曲线，并保留原始数据与分析任务。',
  },
]
const current = computed(() => scenarios[selected.value]!)
</script>

<style scoped>
.tour {
  margin: 28px 0 36px;
  padding: 28px;
  border: 1px solid #dce5ef;
  border-radius: 24px;
  background: linear-gradient(135deg, #f5f9ff, #fff 65%);
  box-shadow: 0 12px 40px #203b6010;
}
.tour-header {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: center;
  margin-bottom: 24px;
}
.kicker {
  font-size: 11px;
  letter-spacing: 0.15em;
  color: #397295;
  font-weight: 700;
  margin: 0 0 8px;
}
.tour h2 {
  font-size: 25px;
  color: #142c46;
  margin: 0 0 10px;
}
.tour-header p:not(.kicker) {
  color: #60748a;
  margin: 0;
}
.preview-label {
  font-size: 12px;
  background: #eaf2fa;
  color: #486681;
  padding: 8px 12px;
  border-radius: 30px;
  white-space: nowrap;
}
.tour-layout {
  display: grid;
  grid-template-columns: 285px minmax(0, 1fr);
  gap: 24px;
}
.scenario-nav {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.scenario-nav button {
  display: flex;
  align-items: center;
  gap: 12px;
  text-align: left;
  border: 1px solid transparent;
  padding: 16px 12px;
  background: transparent;
  border-radius: 14px;
  cursor: pointer;
  color: #587086;
  width: 100%;
  font: inherit;
}
.scenario-nav button:hover {
  background: #eaf2fa;
}
.scenario-nav button.selected {
  border-color: #b6d7ef;
  background: #fff;
  box-shadow: 0 5px 15px #2443640b;
  color: #153f62;
}
.number {
  font-size: 12px;
  font-weight: 700;
  padding: 8px;
  border-radius: 10px;
  background: #e5eef7;
}
.scenario-nav strong {
  display: block;
  font-size: 15px;
}
.scenario-nav small {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: #687e93;
}
.arrow {
  margin-left: auto;
}
.scenario-detail {
  background: #fff;
  border: 1px solid #e2eaf1;
  border-radius: 18px;
  padding: 26px;
}
.detail-top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}
.category {
  font-size: 11px;
  letter-spacing: 0.12em;
  color: #517a9c;
  font-weight: 700;
}
.status {
  font-size: 11px;
  color: #27645c;
  background: #edf7f3;
  padding: 5px 9px;
  border-radius: 20px;
}
.tour h3 {
  font-size: 25px;
  line-height: 1.4;
  margin: 18px 0;
  color: #193550;
}
.request {
  background: #f2f6fb;
  border-left: 3px solid #4282b3;
  border-radius: 8px;
  padding: 16px;
}
.request span {
  font-size: 11px;
  color: #6a8096;
}
.request p {
  font-size: 15px;
  color: #274760;
  line-height: 1.8;
  margin: 6px 0 0;
}
.detail-grid {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 24px;
  margin-top: 22px;
}
.tour h4 {
  font-size: 12px;
  color: #7a8da1;
  margin: 0 0 14px;
}
.tour ol {
  list-style: none;
  padding: 0;
  margin: 0;
}
.tour li {
  display: flex;
  gap: 10px;
  font-size: 13px;
  line-height: 1.8;
  margin-bottom: 10px;
  color: #3e566c;
}
.tour li span {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  text-align: center;
  border-radius: 50%;
  background: #eaf2fa;
  color: #397299;
  font-size: 11px;
}
.outputs div {
  display: flex;
  gap: 10px;
  margin-bottom: 10px;
  font-size: 13px;
  color: #405c75;
}
.outputs span {
  color: #548aac;
}
.boundary {
  margin: 20px 0 0;
  border-top: 1px solid #edf1f6;
  padding-top: 16px;
  font-size: 12px;
  color: #728397;
  line-height: 1.8;
}
.boundary strong {
  color: #52677e;
}
@media (max-width: 850px) {
  .tour {
    padding: 18px;
  }
  .tour-header {
    align-items: flex-start;
    flex-direction: column;
  }
  .tour-layout {
    grid-template-columns: 1fr;
  }
  .scenario-nav {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .scenario-nav button {
    padding: 12px 8px;
  }
  .scenario-detail {
    padding: 18px;
  }
  .detail-grid {
    grid-template-columns: 1fr;
  }
  .tour h3 {
    font-size: 22px;
  }
}
@media (max-width: 480px) {
  .scenario-nav {
    grid-template-columns: 1fr;
  }
}
</style>
