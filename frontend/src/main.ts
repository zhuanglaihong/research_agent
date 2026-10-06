import { createApp } from 'vue'

import App from './App.vue'
import router from './router'

import { Alert, Button, Card, Checkbox, Col, Empty, Form, Input, Layout, Modal,
  Popconfirm, Radio, Row, Select, Spin, Steps, Tabs, Tag } from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'


const app = createApp(App)

app.use(router)
for (const component of [Alert, Button, Card, Checkbox, Col, Empty, Form, Input, Layout,
  Modal, Popconfirm, Radio, Row, Select, Spin, Steps, Tabs, Tag]) app.use(component)

app.mount('#app')
