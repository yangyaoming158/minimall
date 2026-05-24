import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

// Design tokens must load AFTER Element Plus so our :root variables and
// global base styles win the cascade. Phase A1 introduces tokens / base /
// typography utilities only — Element Plus variables are not yet
// overridden (deferred to the end of Phase B). See
// docs/phase1-ui-redesign.md §8 Phase A1.
import './styles/tokens.css'
import './styles/base.css'
import './styles/typography.css'

import App from './App.vue'
import router from './router'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus)

app.mount('#app')
