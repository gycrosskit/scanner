// 执行真实 ScanKit View 状态机；仅裁去 ArkUI 声明式渲染尾部，系统 SDK 用替身。
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require(process.env.TYPESCRIPT_PATH || '/Applications/DevEco-Studio.app/Contents/tools/ohpm/node_modules/typescript');
const source = fs.readFileSync(__dirname + '/../ohos/scanner-native/src/main/ets/GycScannerPreviewView.ets', 'utf8').split('\n@Component')[0];
function environment() {
  const calls = [], callbacks = [];
  const sdk = { calls, callbacks,
    init(options) { calls.push(['init', options]); if (sdk.initFailure) throw { code: 1 }; },
    start(control, callback) { calls.push(['start', control]); callbacks.push(callback); if (sdk.syncResult) callback(null, [{ originalValue: sdk.syncResult }]); if (sdk.startFailure) throw { code: 2 }; },
    rescan() { calls.push(['rescan']); if (sdk.rescanFailure) throw { code: 3 }; },
    async stop() { calls.push(['stop']); if (sdk.stopDeferred) await sdk.stopDeferred; if (sdk.stopFailure) throw { code: 4 }; },
    async release() { calls.push(['release']); if (sdk.releaseFailure) throw { code: 5 }; }
  };
  const exports = {};
  vm.runInNewContext(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText,
    { exports, Observed: value => value, XComponentController: class { getXComponentSurfaceId() { return 'surface'; } },
      require: name => name === '@kit.ScanKit' ? { customScan: sdk, scanCore: { ScanType: { QR_CODE: 1 } } } :
        name === '@kit.PerformanceAnalysisKit' ? { hilog: { info() {}, warn() {} } } :
        name === '@kuikly-open/render' ? { KuiklyRenderBaseView: class { setProp() { return false; } onDestroy() {} getUIContext() { return { px2vp: value => value / 2 }; } } } : {} });
  return { sdk, View: exports.GycScannerPreviewView };
}
const flush = async () => { for (let i = 0; i < 4; i++) await new Promise(resolve => setImmediate(resolve)); };
function create(View) {
  const view = new View(), results = [], failures = [];
  view.setProp('onResult', value => results.push(value.value));
  view.setProp('onFailure', value => failures.push(value.message));
  view.areaChanged({ width: 400, height: 600 }); view.surfaceLoaded(); view.setProp('running', true);
  return { view, results, failures };
}
(async () => {
  {
    const { sdk, View } = environment(), { view, results } = create(View); await flush();
    assert.equal(View.VIEW_NAME, 'GycScannerPreviewView'); assert.deepEqual(sdk.calls.map(x => x[0]), ['init', 'start']);
    assert.equal(sdk.calls[1][1].width, 400, 'ViewControl dimensions stay in vp');
    const frame = sdk.callbacks[0];
    frame(null, []); frame(null, [{ originalValue: 'outside', scanCodeRect: { left: 0, right: 10, top: 0, bottom: 10 } }]);
    assert.equal(sdk.calls.filter(x => x[0] === 'rescan').length, 2);
    frame(null, [{ originalValue: 'inside', scanCodeRect: { left: 390, right: 410, top: 590, bottom: 610 } }]);
    frame(null, [{ originalValue: 'duplicate' }]); await flush();
    assert.deepEqual(results, ['inside']); assert.deepEqual(sdk.calls.slice(-2).map(x => x[0]), ['stop', 'release']);
    view.setProp('running', true); await flush(); const next = sdk.callbacks.at(-1);
    frame(null, [{ originalValue: 'old-generation' }]); assert.equal(results.length, 1);
    view.setProp('running', false); next(null, [{ originalValue: 'late-album-frame' }]); await flush(); assert.equal(results.length, 1);
    view.setProp('running', true); await flush(); const destroyed = sdk.callbacks.at(-1); view.onDestroy(); destroyed(null, [{ originalValue: 'late-destroy' }]); await flush();
    assert.equal(results.length, 1); assert.equal(sdk.calls.at(-1)[0], 'release');
  }
  {
    const { sdk, View } = environment(); sdk.syncResult = 'synchronous'; const { view, results } = create(View); await flush();
    assert.deepEqual(results, ['synchronous']); assert.deepEqual(sdk.calls.map(x => x[0]), ['init', 'start', 'stop', 'release']); view.onDestroy(); await flush();
  }
  {
    const { sdk, View } = environment(); const first = create(View); await flush(); const oldFrame = sdk.callbacks[0];
    let finishStop; sdk.stopDeferred = new Promise(resolve => { finishStop = resolve; });
    const second = create(View); await new Promise(resolve => setImmediate(resolve));
    assert.equal(sdk.calls.filter(x => x[0] === 'init').length, 1, 'new View must wait for old stop');
    oldFrame(null, [{ originalValue: 'old-owner' }]);
    assert.deepEqual(first.results, [], 'old frames during asynchronous stop cannot reach replaced View');
    // owner is old until release, but old generation/running invalidation must prevent delivery during transfer.
    second.view.onDestroy(); finishStop(); sdk.stopDeferred = null; await flush();
    assert.equal(sdk.calls.filter(x => x[0] === 'init').length, 1, 'destroyed replacement cannot initialize after old release');
    first.view.onDestroy(); await flush();
  }
  {
    const { sdk, View } = environment(); const first = create(View); await flush();
    const old = sdk.callbacks[0], second = create(View); await flush();
    assert.deepEqual(sdk.calls.map(x => x[0]), ['init', 'start', 'stop', 'release', 'init', 'start']);
    const beforeDestroy = sdk.calls.length; first.view.onDestroy(); old(null, [{ originalValue: 'old-after-transfer' }]); await flush();
    assert.equal(sdk.calls.length, beforeDestroy, 'old View destroy cannot stop new owner'); assert.equal(first.results.length, 0);
    sdk.callbacks.at(-1)(null, [{ originalValue: 'new-owner' }]); await flush(); assert.deepEqual(second.results, ['new-owner']);
    second.view.onDestroy(); await flush();
  }
  {
    const { sdk, View } = environment(); const first = create(View); await flush();
    sdk.releaseFailure = true; const second = create(View); await flush();
    assert.equal(sdk.calls.filter(x => x[0] === 'init').length, 1, 'failed release retains owner and blocks new camera'); assert.equal(second.failures.length, 1);
    sdk.releaseFailure = false; second.view.setProp('running', true); await flush();
    const calls = sdk.calls.map(x => x[0]); assert.deepEqual(calls.slice(-3), ['release', 'init', 'start']);
    sdk.stopFailure = true; second.view.onDestroy(); await flush(); assert.equal(sdk.calls.at(-1)[0], 'release', 'stop failure still attempts release');
    first.view.onDestroy(); await flush();
  }
  {
    for (const key of ['initFailure', 'startFailure', 'rescanFailure']) {
      const { sdk, View } = environment(); sdk[key] = true; const item = create(View); await flush();
      if (key === 'rescanFailure') { sdk.callbacks[0](null, []); await flush(); }
      assert.equal(item.failures.length, 1, key + ' remains failure, never cancellation/success'); assert.equal(item.results.length, 0);
      item.view.onDestroy(); await flush();
    }
  }
  {
    const { sdk, View } = environment(), { view, results, failures } = create(View); await flush();
    view.surfaceDestroyed(); sdk.callbacks[0](null, [{ originalValue: 'late-surface' }]); await flush(); assert.equal(results.length, 0);
    view.surfaceLoaded(); await flush(); sdk.callbacks.at(-1)({ code: 201 }, []); await flush(); assert.equal(failures.length, 1);
    view.onDestroy(); await flush();
  }
  console.log('Scanner preview checks passed: vp/frame, once-only, generations, sync callback, ownership/release ordering, failures and destroy.');
})().catch(error => { console.error(error); process.exitCode = 1; });
