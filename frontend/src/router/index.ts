import { createRouter, createWebHashHistory } from 'vue-router'
import ResearchProjectsPage from '@/pages/research/ResearchProjectsPage.vue'

const staticDemo = import.meta.env.VITE_STATIC_DEMO === 'true'

const router = createRouter({
  history: createWebHashHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/demo',
      name: '交互演示',
      component: () => import('@/pages/research/ResearchDemoPage.vue'),
    },
    {
      path: '/projects/:id',
      name: '科研工作台',
      ...(staticDemo ? { redirect: '/demo' } : { component: () => import('@/pages/research/ResearchWorkspacePage.vue') }),
    },
    {
      path: '/',
      name: '科研项目',
      ...(staticDemo ? { redirect: '/demo' } : { component: ResearchProjectsPage }),
    },
  ],
})

export default router
