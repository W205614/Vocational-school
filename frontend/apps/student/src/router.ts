import {createRouter,createWebHistory} from 'vue-router';
const Catalog=()=>import('./views/Catalog.vue');const Course=()=>import('./views/Course.vue');const Commerce=()=>import('./views/Commerce.vue');const Personal=()=>import('./views/Personal.vue');const Exam=()=>import('./views/Exam.vue');const OperationsPanel=()=>import('../../../packages/shared/src/OperationsPanel.vue');
export const router=createRouter({history:createWebHistory(),routes:[
 {path:'/',component:Catalog},{path:'/courses/:id',component:Course},
 ...['cart','coupons','orders'].map(mode=>({path:'/'+mode,component:Commerce,props:{mode}})),
 ...['learning','favorites','notes','points','messages','settings'].map(mode=>({path:'/'+mode,component:Personal,props:{mode}})),
 {path:'/simulate-payment/:id',component:()=>import('./views/SimulatorPayment.vue')},{path:'/exams',component:Exam},{path:'/operations',component:OperationsPanel}
]});
