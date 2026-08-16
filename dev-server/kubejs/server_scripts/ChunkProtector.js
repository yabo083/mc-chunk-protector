// ============================================================
// ChunkProtector — 服务端区块保护（单文件）
// 部署到 <server>/kubejs/server_scripts/ChunkProtector.js
//
// 模式 A place-block     阻止玩家放置方块（类出生点保护）
// 模式 B freeze-updates  允许放置，但抑制该区域内任何方块更新
//
// 实现要点（经 2101.7.2 源码 + 本地 server 实测确认）：
//   - 全局事件桥：NativeEvents.onEvent('类名', fn)
//   - 取消：event.setCanceled(true)（两事件皆 ICancellableEvent）
//   - 配置读取：JsonIO.read('kubejs/config/regions.json') 返回 JS map
//   - 跨脚本共享一律走本文件内部的闭包状态（避免写 global 的只读 map）
//   - ServerEvents.loaded 在 /reload 时重新触发；另接 tick 每 2s 热重载
// 语法兼容 Rhino：不用 globalThis/可选链/对象简写/nullish。
// ============================================================

var PLACE_BLOCK = 'place-block';
var FREEZE_UPDATES = 'freeze-updates';

// 模块内部状态（闭包，不依赖跨脚本 global）
var STATE = {
  placeBlockIndex: {}, // dim -> [ {minX,minZ,maxX,maxZ}, ... ]
  freezeIndex: {},
  loaded: false,
  lastConfigSig: null
};

function configPath() {
  return 'kubejs/config/regions.json';
}

function readConfig() {
  try {
    var obj = JsonIO.read(configPath());
    if (obj && obj.regions) return obj;
    return { version: 1, regions: [] };
  } catch (e) {
    console.warn('[ChunkProtector] readConfig error: ' + e);
    return { version: 1, regions: [] };
  }
}

function rebuildIndex(cfg) {
  STATE.placeBlockIndex = {};
  STATE.freezeIndex = {};
  if (!cfg || !cfg.regions) { STATE.loaded = true; return; }

  for (var i = 0; i < cfg.regions.length; i++) {
    var reg = cfg.regions[i];
    if (!reg || !reg.enabled) continue;
    var dim = reg.dimension || 'minecraft:overworld';
    var target = reg.mode === FREEZE_UPDATES ? STATE.freezeIndex : STATE.placeBlockIndex;
    if (!target[dim]) target[dim] = [];

    var fences = reg.chunkFences || [];
    for (var j = 0; j < fences.length; j++) {
      var f = fences[j];
      if (!Array.isArray(f) || f.length < 4) continue;
      target[dim].push({ minX: Number(f[0]), minZ: Number(f[1]), maxX: Number(f[2]), maxZ: Number(f[3]) });
    }
  }
  STATE.loaded = true;
}

function inFence(rects, cx, cz) {
  if (!rects) return false;
  for (var i = 0; i < rects.length; i++) {
    var r = rects[i];
    if (cx >= r.minX && cx <= r.maxX && cz >= r.minZ && cz <= r.maxZ) return true;
  }
  return false;
}

function queryBlock(dim, cx, cz) {
  if (inFence(STATE.placeBlockIndex[dim], cx, cz)) return PLACE_BLOCK;
  if (inFence(STATE.freezeIndex[dim], cx, cz)) return FREEZE_UPDATES;
  return null;
}

function hotReload(logMsg) {
  try {
    var cfg = readConfig();
    var sig = JSON.stringify(cfg);
    if (sig !== STATE.lastConfigSig) {
      rebuildIndex(cfg);
      STATE.lastConfigSig = sig;
      if (logMsg) console.info('[ChunkProtector] ' + logMsg + ' [' + (cfg.regions ? cfg.regions.length : 0) + ' regions]');
    }
  } catch (e) {
    console.warn('[ChunkProtector] reload error: ' + e);
  }
}

// 自检（开发验证用）：ServerEvents.loaded（含 /reload）后打印索引摘要与命中断言。
function selfCheck(logMsg) {
  try {
    var cfg = readConfig();
    var count = cfg.regions ? cfg.regions.length : 0;
    // 汇总索引中的矩形数
    var fenceCount = 0;
    for (var dim in STATE.placeBlockIndex) fenceCount += STATE.placeBlockIndex[dim].length;
    for (var dim2 in STATE.freezeIndex) fenceCount += STATE.freezeIndex[dim2].length;
    console.info('[ChunkProtector] ' + logMsg + ' regions=' + count + ' fences=' + fenceCount);
    // 断言：若存在"测试"区域则打印典型命中
    if (count > 0 && cfg.regions[0].dimension) {
      var id = String(cfg.regions[0].id);
      if (id.indexOf('test') === 0) {
        var first = cfg.regions[0];
        if (first.chunkFences && first.chunkFences.length > 0) {
          var f = first.chunkFences[0];
          var hit = queryBlock(first.dimension, f[0], f[1]);
          var miss = queryBlock(first.dimension, f[2] + 5, f[3] + 5);
          console.info('[ChunkProtector]   selfCheck in(' + f[0] + ',' + f[1] + ')=' + hit + '  out(' + (f[2] + 5) + ',' + (f[3] + 5) + ')=' + miss);
        }
      }
    }
  } catch (e) {
    console.warn('[ChunkProtector] selfCheck error: ' + e);
  }
}

// ---------------- 加载 / 热重载 ----------------
ServerEvents.loaded(function (event) {
  hotReload('ServerEvents.loaded');
  selfCheck('server loaded');
});

var __reloadCooldown = 0;
ServerEvents.tick(function (event) {
  if (__reloadCooldown > 0) { __reloadCooldown--; return; }
  __reloadCooldown = 40; // 每 ~2 秒检测一次
  hotReload(false);
});

// 稳健取维度资源名（兼容 level.dimension 是属性(ResourceKey)还是方法）
// 返回如 'minecraft:overworld'
function dimString(level) {
  try {
    var dim = level.dimension;                 // 可能 object(ResourceKey) 或 function
    if (typeof dim === 'function') dim = dim();
    if (!dim) return 'minecraft:overworld';
    var loc = dim.location;                    // 可能 function 或 属性(ResourceLocation)
    var s;
    if (typeof loc === 'function') s = loc();
    else if (loc !== undefined) s = loc;
    else s = dim;                              // 兜底
    s = String(s);
    // ResourceKey.toString 形如 "ResourceKey[minecraft:overworld / ...]"，需提取 id
    var m = s.match(/([a-z0-9_.\-]+:[a-z0-9_.\-]+)/);
    return m ? m[1] : s;
  } catch (e) {
    return 'minecraft:overworld';
  }
}

// ---------------- 模式 A：防放置 ----------------
NativeEvents.onEvent('net.neoforged.neoforge.event.level.BlockEvent$EntityPlaceEvent', function (event) {
  try {
    var pos = event.getPos();
    var level = event.getLevel();
    if (!pos || !level) return;
    var cx = Math.floor(pos.getX() / 16);
    var cz = Math.floor(pos.getZ() / 16);
    var dim = dimString(level);
    if (queryBlock(dim, cx, cz) === PLACE_BLOCK) {
      event.setCanceled(true);
    }
  } catch (e) {
    console.warn('[ChunkProtector] place handler error: ' + e);
  }
});

// ---------------- 模式 B：防更新 ----------------
NativeEvents.onEvent('net.neoforged.neoforge.event.level.BlockEvent$NeighborNotifyEvent', function (event) {
  try {
    var pos = event.getPos();
    var level = event.getLevel();
    if (!pos || !level) return;
    var cx = Math.floor(pos.getX() / 16);
    var cz = Math.floor(pos.getZ() / 16);
    var dim = dimString(level);
    if (queryBlock(dim, cx, cz) === FREEZE_UPDATES) {
      event.setCanceled(true);
    }
  } catch (e) {
    console.warn('[ChunkProtector] freeze handler error: ' + e);
  }
});

console.info('[ChunkProtector] ChunkProtector.js loaded');
