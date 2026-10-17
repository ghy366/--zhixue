// 用 CDP 驱动无头 Chrome：注入登录态 + 截取指定 hash 路由页面
import { spawn } from 'node:child_process'
import { writeFileSync, readFileSync } from 'node:fs'

const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const PORT = 9222
const OUT = 'G:/AI/智学选课/.workbuddy/shots/'
const cred = JSON.parse(readFileSync(OUT + (process.env.CRED_FILE || 'login.json'), 'utf-8'))
const token = cred.data.token
const user = JSON.stringify(cred.data.user)

const routes = process.argv.slice(2)
const label = process.argv[2] ? '' : ''
const prefix = process.env.SHOT_PREFIX || 'stu'

const chrome = spawn(CHROME, [
  '--headless=new', '--disable-gpu', '--no-sandbox', '--hide-scrollbars',
  '--window-size=1440,1000', `--remote-debugging-port=${PORT}`, 'about:blank',
], { stdio: 'ignore' })

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function getWs() {
  for (let i = 0; i < 40; i++) {
    try {
      const r = await fetch(`http://127.0.0.1:${PORT}/json`)
      const list = await r.json()
      const page = list.find((t) => t.type === 'page')
      if (page) return page.webSocketDebuggerUrl
    } catch { /* retry */ }
    await sleep(250)
  }
  throw new Error('no chrome ws')
}

let idc = 0
const pending = new Map()
function send(ws, method, params = {}) {
  const id = ++idc
  ws.send(JSON.stringify({ id, method, params }))
  return new Promise((res) => pending.set(id, res))
}

const wsUrl = await getWs()
const ws = new WebSocket(wsUrl)
await new Promise((r) => (ws.onopen = r))
ws.onmessage = (e) => {
  const m = JSON.parse(e.data)
  if (m.id && pending.has(m.id)) { pending.get(m.id)(m.result); pending.delete(m.id) }
}
await send(ws, 'Page.enable')
await send(ws, 'Runtime.enable')

// 先到同源页面写 localStorage
await send(ws, 'Page.navigate', { url: 'http://localhost:8080/#/login' })
await sleep(2200)
await send(ws, 'Runtime.evaluate', {
  expression: `localStorage.setItem('zhixue_token', ${JSON.stringify(token)});
               localStorage.setItem('zhixue_user', ${JSON.stringify(user)});`,
})

// 整页加载一次，让应用从 localStorage 恢复登录态（此后 hash 切换不再整页刷新）
await send(ws, 'Page.navigate', { url: 'http://localhost:8080/?boot=1' })
await sleep(3000)

for (const route of routes) {
  await send(ws, 'Page.navigate', { url: 'http://localhost:8080/#' + route })
  await sleep(3200)
  const shot = await send(ws, 'Page.captureScreenshot', { format: 'png' })
  const name = prefix + '-' + route.replace(/\//g, '_') + '.png'
  writeFileSync(OUT + name, Buffer.from(shot.data, 'base64'))
  console.log('saved', name)
}

ws.close()
chrome.kill()
process.exit(0)
