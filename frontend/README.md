# research_agent 前端

Vue 3 + TypeScript + Vite + Ant Design Vue。根目录 [README](../README.md) 包含完整安装与运行说明。

```bash
npm ci
npm run dev
```

本地后端默认监听 `127.0.0.1:8123`，Vite 会把 `/api` 转发过去。`npm run build` 执行类型检查和生产构建。

GitHub Pages 使用独立固定样例，不需要后端：

```bash
VITE_STATIC_DEMO=true VITE_BASE_PATH=/research_agent/ npm run build
```

静态演示只展示预置流程，不调用模型、不执行训练。仓库名不同请调整 `VITE_BASE_PATH`；Windows PowerShell 用 `$env:VITE_STATIC_DEMO='true'` 和 `$env:VITE_BASE_PATH='/research_agent/'` 设置变量。
