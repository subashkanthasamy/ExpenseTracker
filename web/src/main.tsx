import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

import { App } from './App'
import { applyTheme, loadTheme } from './theme'
import './index.css'

// Applied before the first paint so a dark-mode user never sees a light flash.
applyTheme(loadTheme())

const container = document.getElementById('root')
if (!container) throw new Error('#root missing from index.html')

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
