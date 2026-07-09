# StrClient — 模块化 Fabric 1.21.1 生存辅助

单人生存辅助模组，通过浏览器 WebUI 管理所有功能模块。
游戏运行时打开 **http://localhost:5000** 即可控制。

包名：`com.strongspy.strclient`

---

## 安装

1. 安装 [Fabric Loader 0.16.5+](https://fabricmc.net/)（Minecraft 1.21.1）
2. 安装 [Fabric API](https://modrinth.com/mod/fabric-api)
3. 把编译好的 `.jar` 放进 `mods/` 文件夹
4. 启动游戏，进入**单人世界**
5. 浏览器打开 http://localhost:5000

---

## WebUI 使用说明

WebUI 界面文本为**全英文**，采用 **Liquid Glass**（液态玻璃）风格设计。

- **侧边栏分类** — 左侧按 Combat / Movement / Visual / Utility 四个分类筛选模块，"All" 显示所有模块
- **开关** — 每个模块卡片右侧的绿色 iOS 风格开关直接启用/禁用该模块，立即生效
- **设置面板** — 如果模块有可调设置，卡片右上角会出现 **⋯** 圆形按钮；点击它展开/收起该模块的设置面板。没有设置的模块不会显示这个按钮
- **设置项** — 仅显示名称和控件（滑块/开关/下拉），不显示额外描述文字，保持简洁
- **分类标签** — 卡片描述下方显示该模块所属分类，以及当前绑定的快捷键（蓝色标签，⌨ 图标）
- 所有改动（开关状态 + 设置值）会自动写入 `.minecraft/config/strclient/settings.json`，重启游戏后保留
- 页面每 2.5 秒自动刷新一次，游戏内外操作会双向同步

### 游戏内 HUD

- **顶部居中（灵动岛）** — 常驻半透明圆角胶囊，显示玩家名 + 当前帧率（FPS ≥60 绿色 / ≥30 默认色 / <30 红色）
- **左上角** — 实时显示当前已启用的模块列表，从上往下排列，半透明黑色背景面板
- **右下角** — 切换模块开关时会弹出一个简短的半透明提示（如 "Kill Aura enabled" / "Kill Aura disabled"），约 1.6 秒后自动消失

---

## 🎮 自定义快捷键

每个模块都自带一个**可绑定的开关快捷键**，默认未绑定。

### 如何绑定

1. 游戏内按 `Esc` 打开菜单 → **Options（选项）** → **Controls（控制）** → **Key Binds（按键绑定）**
2. 滚动到底部找到分类 **"StrClient Modules"**
3. 点击对应模块名称右侧的按钮，按下你想要的键即可绑定
4. 绑定后，在游戏中按这个键就能直接开关该模块（会触发右下角的半透明提示 + 左上角列表更新）

### 持久化

快捷键绑定由 **Minecraft 原生按键系统**管理，自动保存在 `.minecraft/options.txt` 里，
**关闭游戏后不会丢失**，下次启动自动恢复 —— 完全不需要额外的存储逻辑。

### WebUI 中查看

每个模块卡片的分类标签旁会显示当前绑定的键（如 `⌨ H`），未绑定则显示 `⌨ None`。
这个标签仅用于显示，**重新绑定仍需在游戏内的 Controls 菜单完成**。

### 新模块自动获得快捷键

新增模块时**不需要任何额外代码** —— `StrClientMod.java` 会在注册阶段自动为每个模块
创建一个对应的 KeyBinding（以模块的 `displayName` 作为按键名称），WebUI 和游戏内 HUD 都会自动识别。

> ⚠️ 由于按键的翻译键直接使用 `displayName`，请确保每个模块的显示名称是唯一的
> （这本来也是 UI 设计的基本要求）。

---

# 🧩 如何添加一个新模块（完整教程）

新增模块分为三步：**①写模块类 → ②（可选）注册设置 → ③在入口里注册一行**。
完成后模块会自动出现在 WebUI 里，自动分类、自动持久化，**不需要改动 WebUI 或 SettingsManager 任何代码**。

## 第一步：创建模块文件

每个模块对应一个独立的 `.java` 文件，建议放在自己的子文件夹里，方便管理：

```
src/main/java/com/strongspy/strclient/modules/<模块名>/<模块名>Module.java
```

例如新增一个 "Auto Sprint"（自动疾跑）模块：

```
src/main/java/com/strongspy/strclient/modules/autosprint/AutoSprintModule.java
```

最基础的模块（无设置）：

```java
package com.strongspy.strclient.modules.autosprint;

import com.strongspy.strclient.core.AbstractModule;
import net.minecraft.client.MinecraftClient;

public class AutoSprintModule extends AbstractModule {

    public AutoSprintModule() {
        super(
            "autosprint",          // 唯一 ID — 用于设置文件和 API 路径，全小写、不要有空格
            "Auto Sprint",          // WebUI 显示名称
            Category.MOVEMENT,       // 分类：COMBAT / MOVEMENT / VISUAL / UTILITY
            "移动时自动保持疾跑"      // WebUI 里显示的简介
        );
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;
        if (client.player.input.hasForwardMovement()) {
            client.player.setSprinting(true);
        }
    }
}
```

### `AbstractModule` 的核心方法

| 方法 | 何时调用 | 用途 |
|------|----------|------|
| `registerSettings()` | 构造时一次 | 注册该模块的所有设置项（见第二步） |
| `onEnable(client)` | 模块被启用的瞬间 | 初始化状态、记录变量等 |
| `onTick(client)` | **仅当模块启用时**，每个客户端 tick 调用一次 | 模块的主逻辑写在这里 |
| `onDisable(client)` | 模块被禁用的瞬间 | 清理状态，恢复原始设置等 |

> ⚠️ `onTick` 只在模块 **enabled = true** 时才会被调用，不需要自己再判断 `isEnabled()`。

---

## 第二步：注册模块设置（如果需要）

如果你的模块需要可调参数（就像 KillAura 的 range / fov / multitarget / tickDelay），
在 `registerSettings()` 里用 `registerSetting(...)` 注册即可。

支持 **4 种设置类型**，对应 WebUI 里的滑块 / 整数滑块 / 开关 / 下拉框。
WebUI 只显示 **key 对应的显示名称 + 控件本身**，不显示额外描述文字，所以保持显示名称简洁清晰即可。

> 每个工厂方法的第三个参数仍然是 `description`（用于代码内部文档/未来扩展），
> 但目前 **不会** 显示在 WebUI 里 —— 随便写一句简短说明即可。

### 1️⃣ 数值滑块 — `ModuleSetting.ofDouble`

```java
registerSetting(ModuleSetting.ofDouble(
    "speed",       // key：代码里用 getDouble("speed") 取值
    "Speed",        // WebUI 显示名称
    "Movement speed multiplier", // 内部说明，不显示在 WebUI
    1.5,           // 默认值
    1.0,           // 最小值
    5.0            // 最大值
));
```
→ 在 WebUI 里渲染为一个滑块，范围 1.0–5.0，默认 1.5，保留 1 位小数。

### 2️⃣ 整数滑块 — `ModuleSetting.ofInt`

适合 tick 数、次数等只需要整数的设置（比如 KillAura 的 tickDelay）：

```java
registerSetting(ModuleSetting.ofInt(
    "tickDelay",   // key：代码里用 getInt("tickDelay") 取值
    "Tick Delay",   // WebUI 显示名称
    "Minimum ticks between attacks", // 内部说明
    1,             // 默认值
    1,             // 最小值
    20             // 最大值
));
```
→ 渲染为一个步长为 1 的滑块，显示整数（不带小数点）。

### 3️⃣ 开关 — `ModuleSetting.ofBoolean`

```java
registerSetting(ModuleSetting.ofBoolean(
    "ignoreWalls",   // key
    "Ignore Walls",   // 显示名称
    "Allow attacking through walls", // 内部说明
    false            // 默认值
));
```
→ 渲染为一个 iOS 风格的绿色开关。

### 4️⃣ 下拉选择 — `ModuleSetting.ofString`

```java
registerSetting(ModuleSetting.ofString(
    "targetMode",                  // key
    "Target Mode",                  // 显示名称
    "Which entities to target",     // 内部说明
    "HOSTILE",                      // 默认值（必须是下面 options 之一）
    "HOSTILE", "ALL", "PLAYERS"     // 所有可选项
));
```
→ 渲染为一个下拉框。

### 完整示例：带设置的模块

```java
package com.strongspy.strclient.modules.autosprint;

import com.strongspy.strclient.core.AbstractModule;
import com.strongspy.strclient.core.ModuleSetting;
import net.minecraft.client.MinecraftClient;

public class AutoSprintModule extends AbstractModule {

    public AutoSprintModule() {
        super("autosprint", "Auto Sprint", Category.MOVEMENT, "Always sprint while moving");
    }

    @Override
    protected void registerSettings() {
        registerSetting(ModuleSetting.ofBoolean(
            "onlyOnGround", "Ground Only", "Don't force sprint while airborne", true));

        registerSetting(ModuleSetting.ofDouble(
            "hungerThreshold", "Hunger Threshold",
            "Stop sprinting below this food level (0 = no limit)", 0.0, 0.0, 20.0));
    }

    @Override
    public void onTick(MinecraftClient client) {
        if (client.player == null) return;

        boolean onlyOnGround = getBoolean("onlyOnGround");
        double hungerThreshold = getDouble("hungerThreshold");

        if (onlyOnGround && !client.player.isOnGround()) return;
        if (hungerThreshold > 0 && client.player.getHungerManager().getFoodLevel() < hungerThreshold) return;

        if (client.player.input.hasForwardMovement()) {
            client.player.setSprinting(true);
        }
    }
}
```

### 在代码里读取设置值

`AbstractModule` 提供了便捷方法，按设置类型选择：

```java
double range   = getDouble("range");         // ModuleSetting.ofDouble  对应
int tickDelay  = getInt("tickDelay");          // ModuleSetting.ofInt    对应
boolean multi  = getBoolean("multitarget");    // ModuleSetting.ofBoolean 对应
String  mode   = getString("targetMode");      // ModuleSetting.ofString  对应
```

key 必须和 `registerSetting(...)` 里写的第一个参数完全一致。

---

## 第三步：在入口文件注册模块

打开 `StrClientMod.java`，在 `onInitializeClient()` 里加一行：

```java
// ── Register modules here ──────────────────────────────────────
moduleManager.register(new KillAuraModule());
moduleManager.register(new AutoSprintModule());   // ← 新增的这一行
// ──────────────────────────────────────────────────────────────
```

记得在文件顶部加上 import：

```java
import com.strongspy.strclient.modules.autosprint.AutoSprintModule;
```

---

## ✅ 完成！

重新编译并启动游戏后：

- 模块会自动出现在 WebUI 对应的分类里（按 `Category` 自动分组）
- 如果注册了设置项，卡片右上角会出现 **⋯** 圆形按钮，点击展开设置面板
- 开关状态和所有设置值会自动保存到 `.minecraft/config/strclient/settings.json`，下次启动自动恢复
- 自动获得一个可在 Options → Controls → Key Binds 里绑定的开关快捷键（无需任何额外代码）
- 切换开关时会在游戏右下角显示半透明提示，并实时更新左上角已启用模块列表
- **完全不需要修改** `WebServer.java`、`SettingsManager.java` 或任何前端代码

---

## 模块分类（Category）

新增模块时通过构造函数第三个参数指定分类，决定它出现在 WebUI 哪个侧边栏分组：

| Category | WebUI 分组 | 适用场景示例 |
|----------|-----------|--------------|
| `COMBAT` | Combat | KillAura、Auto Block、Criticals |
| `MOVEMENT` | Movement | Auto Sprint、Speed、Auto Jump |
| `VISUAL` | Visual | Full Bright、ESP、Zoom |
| `UTILITY` | Utility | Auto Eat、Inventory Sorter、Notifier |

---

## 内置模块

### ⚔ Kill Aura

每 tick 自动攻击范围内的实体，攻击频率完全由 `tickDelay` 控制（不再受原版攻击冷却限制）。

| 设置 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| range | 滑块 | 4.5 格 | 攻击半径 |
| fov | 滑块 | 360° | 攻击视锥角度，360 = 全方向无死角 |
| multitarget | 开关 | 关 | 每 tick 攻击范围内所有目标，而非仅最近的 |
| targetMode | 下拉 | HOSTILE | HOSTILE = 仅怪物 · ALL = 一切生物（含猪牛等） |
| tickDelay | 整数滑块 | 1 | 两次攻击之间最少间隔的 tick 数（1–20）。**1 = 每 tick 都攻击，无视攻击冷却** |

- **HOSTILE 模式** — 刷怪塔神器（僵尸、骷髅、苦力怕、Warden 等全部自动打）
- **ALL 模式** — 古城里的远古守卫太难打？开 ALL + range 拉满 + multitarget，自动清场

---

## REST API

WebUI 调用的接口，也可以自己写脚本调用：

```
GET  /api/modules                          → 所有模块及当前设置（含分类、设置类型/范围/选项）
POST /api/modules/{id}/toggle              → 启用/禁用模块
POST /api/modules/{id}/setting             → 修改设置
  Body: { "key": "range", "value": 6.0 }
```

`/api/modules` 返回示例（注意：每个 setting **不包含** `description` 字段，WebUI 不显示它）：

```json
[
  {
    "id": "killaura",
    "displayName": "Kill Aura",
    "category": "COMBAT",
    "description": "Automatically attacks entities in range — great for mob farms and the Ancient City",
    "enabled": true,
    "settings": [
      { "key": "range", "displayName": "Range",
        "type": "DOUBLE", "value": 4.5, "min": 1.0, "max": 10.0 },
      { "key": "tickDelay", "displayName": "Tick Delay",
        "type": "INT", "value": 1, "min": 1, "max": 20 },
      { "key": "targetMode", "displayName": "Target Mode",
        "type": "STRING", "value": "HOSTILE", "options": ["HOSTILE", "ALL"] }
    ]
  }
]
```

---

## 项目结构

```
src/main/java/com/strongspy/strclient/
├── StrClientMod.java             ← 入口，在这里注册模块（第三步）
├── core/
│   ├── AbstractModule.java       ← 所有模块的基类（第一步继承这个）
│   ├── ModuleSetting.java        ← 类型化设置项 ofDouble/ofInt/ofBoolean/ofString（第二步）
│   ├── ModuleManager.java        ← 注册表 + tick 分发，无需修改
│   ├── SettingsManager.java      ← JSON 持久化，无需修改
│   ├── HudOverlay.java           ← 游戏内 HUD：已启用模块列表 + 开关提示，无需修改
│   └── Notifications.java        ← 模块开关变化时推送 HUD 提示，无需修改
├── modules/
│   ├── killaura/
│   │   └── KillAuraModule.java
│   └── <你的模块名>/
│       └── <你的模块名>Module.java   ← 新模块放在这里，一个文件夹一个模块
├── web/
│   └── WebServer.java            ← HTTP 服务器 + WebUI（Liquid Glass），无需修改
```

设置文件：`.minecraft/config/strclient/settings.json`
