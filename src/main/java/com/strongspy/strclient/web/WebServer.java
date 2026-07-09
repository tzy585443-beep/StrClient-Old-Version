package com.strongspy.strclient.web;

import com.google.gson.*;
import com.strongspy.strclient.StrClientMod;
import com.strongspy.strclient.core.*;
import com.sun.net.httpserver.*;
import net.minecraft.client.MinecraftClient;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 内嵌 HTTP 服务器，localhost:5000。
 * 提供 WebUI 和 REST API。
 *
 * API:
 *   GET  /api/modules              → 所有模块列表 + 设置
 *   POST /api/modules/{id}/toggle  → 切换启用状态
 *   POST /api/modules/{id}/setting → 修改设置值
 *   GET  /                         → WebUI
 */
public class WebServer {

    private final ModuleManager moduleManager;
    private final SettingsManager settingsManager;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private HttpServer server;

    public WebServer(ModuleManager moduleManager, SettingsManager settingsManager) {
        this.moduleManager = moduleManager;
        this.settingsManager = settingsManager;
    }

    public void start(int port) {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
            server.createContext("/api/modules", this::handleModules);
            server.createContext("/", this::handleUI);
            server.setExecutor(null);
            server.start();
            StrClientMod.LOGGER.info("[StrClient] WebUI → http://localhost:{}", port);
        } catch (IOException e) {
            StrClientMod.LOGGER.error("[StrClient] WebServer failed to start: {}", e.getMessage());
        }
    }

    public void stop() { if (server != null) server.stop(0); }

    // ── REST API ──────────────────────────────────────────────────────

    private void handleModules(HttpExchange ex) throws IOException {
        addCors(ex);
        if (ex.getRequestMethod().equals("OPTIONS")) { ex.sendResponseHeaders(204, -1); return; }

        String[] parts = ex.getRequestURI().getPath().split("/");
        // ["", "api", "modules", id?, action?]

        if (ex.getRequestMethod().equals("GET") && parts.length <= 3) {
            JsonArray arr = new JsonArray();
            for (AbstractModule m : moduleManager.getAll()) arr.add(serialize(m));
            sendJson(ex, 200, arr);
            return;
        }

        if (parts.length >= 5) {
            String id     = parts[3];
            String action = parts[4];
            AbstractModule module = moduleManager.get(id);
            if (module == null) { sendJson(ex, 404, err("Module not found")); return; }

            if (ex.getRequestMethod().equals("POST") && action.equals("toggle")) {
                CompletableFuture<JsonObject> result = new CompletableFuture<>();
                MinecraftClient.getInstance().execute(() -> {
                    module.toggle(MinecraftClient.getInstance());
                    settingsManager.save(moduleManager);
                    result.complete(serialize(module));
                });
                try {
                    sendJson(ex, 200, result.get(2, TimeUnit.SECONDS));
                } catch (TimeoutException | InterruptedException | java.util.concurrent.ExecutionException e) {
                    sendJson(ex, 500, err("Toggle timed out"));
                }
                return;
            }

            if (ex.getRequestMethod().equals("POST") && action.equals("setting")) {
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                JsonObject req = JsonParser.parseString(body).getAsJsonObject();
                String key = req.get("key").getAsString();
                JsonElement value = req.get("value");
                ModuleSetting<?> setting = module.getSetting(key);
                if (setting == null) { sendJson(ex, 404, err("Setting not found")); return; }
                Object val = switch (setting.getType()) {
                    case DOUBLE, INT -> value.getAsDouble();
                    case BOOLEAN -> value.getAsBoolean();
                    case STRING  -> value.getAsString();
                };
                setting.setFromObject(val);
                settingsManager.save(moduleManager);
                sendJson(ex, 200, serialize(module));
                return;
            }
        }

        sendJson(ex, 405, err("Method not allowed"));
    }

    private void handleUI(HttpExchange ex) throws IOException {
        addCors(ex);
        byte[] bytes = buildWebUI().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }

    // ── Serialization ─────────────────────────────────────────────────

    private JsonObject serialize(AbstractModule m) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id",          m.getId());
        obj.addProperty("displayName", m.getDisplayName());
        obj.addProperty("category",    m.getCategory().name());
        obj.addProperty("description", m.getDescription());
        obj.addProperty("enabled",     m.isEnabled());

        String keybind = "None";
        if (m.getKeyBinding() != null && !m.getKeyBinding().isUnbound()) {
            keybind = m.getKeyBinding().getBoundKeyLocalizedText().getString();
        }
        obj.addProperty("keybind", keybind);

        JsonArray sa = new JsonArray();
        for (ModuleSetting<?> s : m.getSettings().values()) {
            JsonObject so = new JsonObject();
            so.addProperty("key",         s.getKey());
            so.addProperty("displayName", s.getDisplayName());
            so.addProperty("type",        s.getType().name());
            Object v = s.getValue();
            if (v instanceof Double d)   so.addProperty("value", d);
            else if (v instanceof Boolean b) so.addProperty("value", b);
            else so.addProperty("value", v.toString());
            if (s.getType() == ModuleSetting.Type.DOUBLE || s.getType() == ModuleSetting.Type.INT) {
                so.addProperty("min", s.getMin());
                so.addProperty("max", s.getMax());
            }
            if (s.getOptions() != null) {
                JsonArray opts = new JsonArray();
                for (String o : s.getOptions()) opts.add(o);
                so.add("options", opts);
            }
            sa.add(so);
        }
        obj.add("settings", sa);
        return obj;
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private void sendJson(HttpExchange ex, int status, JsonElement body) throws IOException {
        byte[] bytes = gson.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }

    private void addCors(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin",  "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private JsonObject err(String msg) {
        JsonObject o = new JsonObject(); o.addProperty("error", msg); return o;
    }

    // ── WebUI ─────────────────────────────────────────────────────────

    private String buildWebUI() {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>StrClient</title>
<style>
  @import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500;600&display=swap');

  :root {
    --glass:        rgba(244,244,248,0.55);
    --glass-strong: rgba(244,244,248,0.80);
    --glass-border: rgba(255,255,255,0.6);
    --glass-shadow: 0 8px 32px rgba(31,38,135,0.10);
    --text:     rgba(20,20,28,0.92);
    --text-sub: rgba(20,20,28,0.52);
    --accent:   #007aff;
    --green:    #34c759;
    --red:      #ff3b30;
    --track:    rgba(0,0,0,0.08);
    --r-xl: 28px;
    --r-lg: 22px;
    --r-md: 16px;
    --sans: -apple-system, BlinkMacSystemFont, 'SF Pro Display', 'SF Pro Text', 'Inter', sans-serif;
    --mono: 'SF Mono', 'JetBrains Mono', monospace;
  }
  * { box-sizing: border-box; margin: 0; padding: 0; -webkit-font-smoothing: antialiased; }
  html, body { min-height: 100%; }
  body {
    font-family: var(--sans);
    color: var(--text);
    background:
      radial-gradient(circle at 12% 18%, rgba(255,148,189,0.55), transparent 38%),
      radial-gradient(circle at 88% 12%, rgba(120,160,255,0.55), transparent 40%),
      radial-gradient(circle at 78% 82%, rgba(140,255,214,0.50), transparent 42%),
      radial-gradient(circle at 18% 88%, rgba(255,213,140,0.50), transparent 42%),
      linear-gradient(160deg, #e7ebf6 0%, #dde2ef 100%);
    background-attachment: fixed;
    min-height: 100vh;
    display: flex;
    flex-direction: column;
  }

  /* ── Liquid glass base ─────────────────────────────────────────── */
  .glass {
    background: var(--glass);
    backdrop-filter: blur(28px) saturate(180%);
    -webkit-backdrop-filter: blur(28px) saturate(180%);
    border: 1px solid var(--glass-border);
    box-shadow: var(--glass-shadow);
  }

  /* ── Header ────────────────────────────────────────────────────── */
  header {
    margin: 18px 20px 0;
    padding: 14px 22px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    border-radius: var(--r-xl);
    position: sticky;
    top: 18px;
    z-index: 100;
  }
  .logo { font-family: var(--mono); font-weight: 700; font-size: 15px; letter-spacing: .04em; color: var(--text); }
  .logo span { font-weight: 400; color: var(--text-sub); font-size: 11px; margin-left: 8px; letter-spacing: .05em; }
  .hright { display: flex; align-items: center; gap: 12px; }
  .pill {
    font-family: var(--mono); font-size: 10px; letter-spacing: .06em;
    color: var(--text-sub);
    background: rgba(255,255,255,0.5);
    border: 1px solid rgba(255,255,255,0.6);
    padding: 4px 10px; border-radius: 20px;
  }
  .dot { width: 8px; height: 8px; border-radius: 50%; background: var(--green); box-shadow: 0 0 8px var(--green); transition: .3s; }
  .dot.offline { background: var(--red); box-shadow: 0 0 8px var(--red); }

  /* ── Layout ────────────────────────────────────────────────────── */
  .shell { display: flex; flex: 1; padding: 0 20px 24px; align-items: flex-start; }

  aside {
    width: 188px;
    margin: 18px 18px 0 0;
    padding: 14px;
    border-radius: var(--r-xl);
    display: flex;
    flex-direction: column;
    gap: 4px;
    position: sticky;
    top: 92px;
    flex-shrink: 0;
  }
  .side-label {
    font-family: var(--mono); font-size: 10px; letter-spacing: .14em;
    color: var(--text-sub); text-transform: uppercase;
    padding: 4px 10px 8px;
  }
  .side-btn {
    display: flex; align-items: center; gap: 10px;
    width: 100%; padding: 10px 12px;
    border: none; background: transparent; border-radius: var(--r-md);
    color: var(--text-sub); font-size: 13px; font-weight: 500; cursor: pointer;
    transition: background .15s, color .15s;
    text-align: left;
  }
  .side-btn:hover { background: rgba(255,255,255,0.45); color: var(--text); }
  .side-btn.active { background: var(--glass-strong); color: var(--accent); box-shadow: 0 2px 10px rgba(0,0,0,0.06); }
  .side-icon { font-size: 13px; opacity: .75; width: 16px; text-align: center; }
  .side-count {
    margin-left: auto; font-family: var(--mono); font-size: 10px;
    background: rgba(0,0,0,0.06); color: var(--text-sub);
    padding: 1px 7px; border-radius: 10px; min-width: 20px; text-align: center;
  }
  .side-btn.active .side-count { background: rgba(0,122,255,0.12); color: var(--accent); }

  main { flex: 1; margin-top: 18px; min-width: 0; }
  .page-head { display: flex; align-items: baseline; gap: 10px; margin: 0 0 16px 4px; }
  .page-title { font-size: 20px; font-weight: 700; color: var(--text); letter-spacing: -0.3px; }
  .page-count { font-family: var(--mono); font-size: 11px; color: var(--text-sub); }

  /* ── Module grid ───────────────────────────────────────────────── */
  .grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }
  @media (max-width: 1100px) { .grid { grid-template-columns: repeat(2, 1fr); } }
  @media (max-width: 720px)  { .grid { grid-template-columns: 1fr; } }

  .card { border-radius: var(--r-xl); overflow: hidden; transition: box-shadow .25s, border-color .25s; }
  .card.on { border-color: rgba(52,199,89,0.45); box-shadow: 0 8px 32px rgba(52,199,89,0.18); }

  .card-head { display: flex; align-items: center; gap: 14px; padding: 16px 18px; }
  .card-info { flex: 1; min-width: 0; }
  .card-name { font-size: 15px; font-weight: 600; color: var(--text); margin-bottom: 3px; }
  .card-desc { font-size: 12px; color: var(--text-sub); line-height: 1.4; }
  .card-tag-row { display: flex; align-items: center; gap: 6px; margin-top: 8px; }
  .card-tag {
    display: inline-block;
    font-family: var(--mono); font-size: 9px; letter-spacing: .08em; text-transform: uppercase;
    color: var(--text-sub); background: rgba(0,0,0,0.05);
    padding: 2px 8px; border-radius: 8px;
  }
  .card-tag.kb { color: var(--accent); background: rgba(0,122,255,0.10); }

  /* ── iOS-style toggle switch ───────────────────────────────────── */
  .tog { position: relative; display: inline-block; width: 46px; height: 28px; flex-shrink: 0; transition: transform .15s; }
  .tog:hover { transform: scale(1.05); }
  .tog input { opacity: 0; width: 0; height: 0; }
  .tog-track {
    position: absolute; inset: 0; cursor: pointer;
    background: rgba(0,0,0,0.1);
    border-radius: 28px;
    transition: background .25s cubic-bezier(.4,0,.2,1);
  }
  .tog-track::before {
    content: ''; position: absolute; height: 22px; width: 22px; left: 3px; bottom: 3px;
    background: #fff; border-radius: 50%;
    box-shadow: 0 2px 6px rgba(0,0,0,0.15);
    transition: transform .25s cubic-bezier(.4,0,.2,1);
  }
  .tog input:checked + .tog-track { background: rgba(52,199,89,0.65); }
  .tog input:checked + .tog-track::before { transform: translateX(18px); }

  .tog.small { width: 40px; height: 24px; }
  .tog.small .tog-track::before { height: 18px; width: 18px; }
  .tog.small input:checked + .tog-track::before { transform: translateX(16px); }

  /* ── Settings menu button ──────────────────────────────────────── */
  .menu-btn {
    width: 30px; height: 30px; flex-shrink: 0;
    display: flex; align-items: center; justify-content: center;
    border: none; border-radius: 50%;
    background: rgba(255,255,255,0.55);
    color: var(--text-sub); font-size: 16px; line-height: 1;
    cursor: pointer; transition: background .15s, color .15s, transform .15s;
  }
  .menu-btn:hover { background: rgba(255,255,255,0.9); color: var(--text); transform: scale(1.06); }
  .card.open .menu-btn { background: var(--accent); color: #fff; }
  .menu-spacer { width: 30px; flex-shrink: 0; }

  /* ── Settings drawer ───────────────────────────────────────────── */
  .drawer { display: none; padding: 0 18px 16px; }
  .card.open .drawer { display: block; }

  .srow {
    display: flex; align-items: center; gap: 14px;
    padding: 12px 14px;
    background: rgba(255,255,255,0.35);
    border-radius: var(--r-md);
    margin-top: 8px;
  }
  .sname { font-size: 13px; font-weight: 500; color: var(--text); flex: 1; min-width: 0; }

  /* ── Glass slider ──────────────────────────────────────────────── */
  .slider-wrap { display: flex; align-items: center; gap: 10px; }
  input[type=range] {
    -webkit-appearance: none; appearance: none;
    width: 130px; height: 8px; border-radius: 8px;
    background: var(--track); outline: none;
  }
  input[type=range]::-webkit-slider-thumb {
    -webkit-appearance: none; appearance: none;
    width: 20px; height: 20px; border-radius: 50%;
    background: #fff; box-shadow: 0 2px 8px rgba(0,0,0,0.18);
    cursor: pointer; transition: transform .1s;
  }
  input[type=range]::-webkit-slider-thumb:hover { transform: scale(1.12); }
  .sval { font-family: var(--mono); font-size: 12px; color: var(--accent); min-width: 38px; text-align: right; font-weight: 600; }

  /* ── Glass select ──────────────────────────────────────────────── */
  .sel {
    background: rgba(255,255,255,0.6);
    border: 1px solid rgba(255,255,255,0.7);
    color: var(--text); font-family: var(--mono); font-size: 11px; font-weight: 600;
    padding: 6px 10px; border-radius: 10px; cursor: pointer; outline: none;
  }
  .sel option { background: #fff; color: #111; }

  /* ── Toast ─────────────────────────────────────────────────────── */
  .toast {
    position: fixed; bottom: 24px; right: 24px;
    padding: 12px 20px; border-radius: var(--r-lg);
    font-size: 13px; font-weight: 500; color: var(--text);
    background: var(--glass-strong);
    backdrop-filter: blur(28px) saturate(180%);
    -webkit-backdrop-filter: blur(28px) saturate(180%);
    border: 1px solid var(--glass-border);
    box-shadow: var(--glass-shadow);
    transform: translateY(60px); opacity: 0;
    transition: transform .25s, opacity .25s;
    pointer-events: none; z-index: 999;
  }
  .toast.show { transform: translateY(0); opacity: 1; }
  .toast.err { border-color: rgba(255,59,48,0.5); color: #c0392b; }

  /* ── Empty / loading ───────────────────────────────────────────── */
  .empty, .loading {
    display: flex; flex-direction: column; align-items: center; justify-content: center;
    height: 240px; color: var(--text-sub); font-size: 13px; gap: 10px;
    border-radius: var(--r-xl);
  }
  .spin { width: 18px; height: 18px; border: 2.5px solid rgba(0,0,0,0.1); border-top-color: var(--accent); border-radius: 50%; animation: spin .7s linear infinite; }
  @keyframes spin { to { transform: rotate(360deg); } }
</style>
</head>
<body>

<header class="glass">
  <div class="logo">StrClient<span>v1.5.0 · 1.21.1</span></div>
  <div class="hright">
    <span class="pill">localhost:5000</span>
    <div class="dot" id="dot"></div>
  </div>
</header>

<div class="shell">
  <aside class="glass">
    <div class="side-label">Categories</div>
    <button class="side-btn active" onclick="filter('ALL',this)">
      <span class="side-icon">◇</span> All
      <span class="side-count" id="c-ALL">0</span>
    </button>
    <button class="side-btn" onclick="filter('COMBAT',this)">
      <span class="side-icon">⚔</span> Combat
      <span class="side-count" id="c-COMBAT">0</span>
    </button>
    <button class="side-btn" onclick="filter('MOVEMENT',this)">
      <span class="side-icon">◈</span> Movement
      <span class="side-count" id="c-MOVEMENT">0</span>
    </button>
    <button class="side-btn" onclick="filter('VISUAL',this)">
      <span class="side-icon">◉</span> Visual
      <span class="side-count" id="c-VISUAL">0</span>
    </button>
    <button class="side-btn" onclick="filter('UTILITY',this)">
      <span class="side-icon">◧</span> Utility
      <span class="side-count" id="c-UTILITY">0</span>
    </button>
  </aside>

  <main>
    <div class="page-head">
      <span class="page-title" id="ptitle">All Modules</span>
      <span class="page-count" id="pcount"></span>
    </div>
    <div id="root">
      <div class="glass loading"><div class="spin"></div>Connecting to Minecraft…</div>
    </div>
  </main>
</div>

<div class="toast glass" id="toast"></div>

<script>
let all = [], cat = 'ALL';

async function load() {
  try {
    const r = await fetch('/api/modules');
    if (!r.ok) throw new Error();
    all = await r.json();
    render();
    counts();
    document.getElementById('dot').className = 'dot';
  } catch {
    document.getElementById('root').innerHTML =
      '<div class="glass empty"><div>⚠</div><span>Cannot connect — make sure the game is running</span></div>';
    document.getElementById('dot').className = 'dot offline';
  }
}

function counts() {
  ['ALL','COMBAT','MOVEMENT','VISUAL','UTILITY'].forEach(k => {
    const el = document.getElementById('c-' + k);
    if (el) el.textContent = k === 'ALL' ? all.length
      : all.filter(m => m.category === k).length;
  });
}

function render() {
  const list = cat === 'ALL' ? all : all.filter(m => m.category === cat);
  document.getElementById('pcount').textContent = list.length + (list.length === 1 ? ' module' : ' modules');
  if (!list.length) {
    document.getElementById('root').innerHTML =
      '<div class="glass empty"><div>◧</div><span>No modules in this category</span></div>';
    return;
  }
  const expanded = new Set(
    [...document.querySelectorAll('.card.open')].map(el => el.dataset.id)
  );
  document.getElementById('root').innerHTML =
    '<div class="grid">' + list.map(m => card(m, expanded.has(m.id))).join('') + '</div>';
}

function card(m, open) {
  const hasSets = m.settings.length > 0;
  const tagNames = { COMBAT:'Combat', MOVEMENT:'Movement', VISUAL:'Visual', UTILITY:'Utility' };
  return `
<div class="card glass${m.enabled?' on':''}${open&&hasSets?' open':''}" id="card-${m.id}" data-id="${m.id}">
  <div class="card-head">
    <div class="card-info">
      <div class="card-name">${m.displayName}</div>
      <div class="card-desc">${m.description}</div>
      <div class="card-tag-row">
        <span class="card-tag">${tagNames[m.category] || m.category}</span>
        <span class="card-tag kb" title="Set in Options → Controls → Key Binds">⌨ ${m.keybind}</span>
      </div>
    </div>
    <label class="tog">
      <input type="checkbox"${m.enabled?' checked':''} onchange="toggle('${m.id}',this)">
      <div class="tog-track"></div>
    </label>
    ${hasSets
      ? `<button class="menu-btn" title="Settings" onclick="expand('${m.id}')">⋯</button>`
      : '<span class="menu-spacer"></span>'}
  </div>
  ${hasSets ? `<div class="drawer">${m.settings.map(s=>srow(m.id,s)).join('')}</div>` : ''}
</div>`;
}

function srow(mid, s) {
  let ctrl = '';
  if (s.type === 'DOUBLE') {
    const step = (s.max - s.min) <= 10 ? 0.1 : 1;
    const disp = parseFloat(s.value).toFixed(1);
    ctrl = `<div class="slider-wrap">
      <input type="range" min="${s.min}" max="${s.max}" step="${step}" value="${s.value}"
        oninput="this.nextElementSibling.textContent=parseFloat(this.value).toFixed(1)"
        onchange="setSetting('${mid}','${s.key}',parseFloat(this.value))">
      <span class="sval">${disp}</span>
    </div>`;
  } else if (s.type === 'INT') {
    ctrl = `<div class="slider-wrap">
      <input type="range" min="${s.min}" max="${s.max}" step="1" value="${s.value}"
        oninput="this.nextElementSibling.textContent=Math.round(this.value)"
        onchange="setSetting('${mid}','${s.key}',Math.round(this.value))">
      <span class="sval">${Math.round(s.value)}</span>
    </div>`;
  } else if (s.type === 'BOOLEAN') {
    ctrl = `<label class="tog small">
      <input type="checkbox"${s.value?' checked':''}
        onchange="setSetting('${mid}','${s.key}',this.checked)">
      <div class="tog-track"></div>
    </label>`;
  } else if (s.type === 'STRING' && s.options) {
    const opts = s.options.map(o=>`<option${o===s.value?' selected':''}>${o}</option>`).join('');
    ctrl = `<select class="sel" onchange="setSetting('${mid}','${s.key}',this.value)">${opts}</select>`;
  }
  return `<div class="srow">
    <div class="sname">${s.displayName}</div>
    ${ctrl}
  </div>`;
}

function expand(id) {
  document.getElementById('card-' + id).classList.toggle('open');
}

async function toggle(id, cb) {
  try {
    const r = await fetch('/api/modules/' + id + '/toggle', { method: 'POST' });
    const d = await r.json();
    const card = document.getElementById('card-' + id);
    card.classList.toggle('on', d.enabled);
    cb.checked = d.enabled;
    toast(d.displayName + (d.enabled ? ' enabled' : ' disabled'));
  } catch {
    cb.checked = !cb.checked;
    toast('Toggle failed', true);
  }
}

async function setSetting(mid, key, value) {
  try {
    await fetch('/api/modules/' + mid + '/setting', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ key, value })
    });
  } catch { toast('Failed to save setting', true); }
}

function filter(c, btn) {
  cat = c;
  document.querySelectorAll('.side-btn').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  const titles = { ALL:'All Modules', COMBAT:'Combat', MOVEMENT:'Movement', VISUAL:'Visual', UTILITY:'Utility' };
  document.getElementById('ptitle').textContent = titles[c] || c;
  render();
}

function toast(msg, isErr = false) {
  const t = document.getElementById('toast');
  t.textContent = msg;
  t.className = 'toast glass show' + (isErr ? ' err' : '');
  clearTimeout(t._t);
  t._t = setTimeout(() => t.className = 'toast glass', 2000);
}

load();
setInterval(load, 2500);
</script>
</body>
</html>
""";
    }
}
