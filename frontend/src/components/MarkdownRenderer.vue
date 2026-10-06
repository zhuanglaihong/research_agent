<template><div class="markdown-content" v-html="html"></div></template>

<script setup lang="ts">
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js/lib/common'
import 'highlight.js/styles/github.css'

const props = defineProps<{ content: string }>()
const escapeHtml = (value: string): string => value.replace(/[&<>"']/g, (char) => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
})[char] || char)
const parser: MarkdownIt = new MarkdownIt({
  html: false,
  linkify: false,
  highlight(code, language): string {
    const escaped = escapeHtml(code)
    if (!language || !hljs.getLanguage(language)) return `<pre><code>${escaped}</code></pre>`
    try { return `<pre class="hljs"><code>${hljs.highlight(code, { language, ignoreIllegals: true }).value}</code></pre>` }
    catch { return `<pre><code>${escaped}</code></pre>` }
  },
})
const html = computed(() => parser.render(props.content))
</script>

<style scoped>
.markdown-content { line-height: 1.7; overflow-wrap: anywhere; }
.markdown-content :deep(pre) { overflow-x: auto; padding: 12px; background: #f6f8fa; border-radius: 8px; }
.markdown-content :deep(code) { font-family: Consolas, 'Courier New', monospace; }
</style>
