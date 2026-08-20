import { createRouter, createWebHistory } from 'vue-router'
import Home from '../views/Home.vue'
import PsychologyChat from '../views/PsychologyChat.vue'
import ManusChat from '../views/ManusChat.vue'
import ChatAssistant from '../views/ChatAssistant.vue'
import ChatPDF from '../views/ChatPDF.vue'
import OperationCenter from '../views/OperationCenter.vue'
import ModelManagement from '../views/ModelManagement.vue'
import RouteManagement from '../views/RouteManagement.vue'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: Home
  },
  {
    path: '/psychology',
    name: 'PsychologyChat',
    component: PsychologyChat
  },
  {
    path: '/manus',
    name: 'ManusChat',
    component: ManusChat
  },
  {
    path: '/assistant',
    name: 'ChatAssistant',
    component: ChatAssistant
  },
  {
    path: '/pdf',
    name: 'ChatPDF',
    component: ChatPDF
  },
  {
    path: '/operations',
    name: 'OperationCenter',
    component: OperationCenter
  },
  {
    path: '/models',
    name: 'ModelManagement',
    component: ModelManagement
  },
  {
    path: '/routes',
    name: 'RouteManagement',
    component: RouteManagement
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
