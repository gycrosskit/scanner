// 运行真实 ArkTS 编译后的逻辑，设备 SDK 用最小异步桩替代。
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require(process.env.TYPESCRIPT_PATH || '/Applications/DevEco-Studio.app/Contents/tools/hvigor/hvigor/node_modules/typescript');
let completeScan, scanFailure, decodeFailure, removed = 0, writes = 0;
const sdk = {
  '@kit.ArkTS': { util: { generateRandomUUID: () => 'test-id' } },
  '@kit.AbilityKit': {}, '@kit.BasicServicesKit': {},
  '@kit.ScanKit': {
    scanCore: { ScanType: { QR_CODE: 1 } },
    scanBarcode: { startScanForResult: () => scanFailure ? Promise.reject(scanFailure) : new Promise(r => { completeScan = r; }) },
    detectBarcode: { decode: async () => { if (decodeFailure) throw Error('decode'); return [{ originalValue: 'hello' }]; } }
  },
  '@kit.CoreFileKit': {
    fileUri: { getUriFromPath: x => x },
    fileIo: { OpenMode: { CREATE: 1, WRITE_ONLY: 2, TRUNC: 4 }, open: async () => ({ fd: 1 }),
      write: async (_fd, bytes) => { writes++; return Math.min(2, bytes.byteLength); },
      close: async () => {}, unlink: async () => { removed++; } }
  }
};
const source = fs.readFileSync(`${__dirname}/../ohos/scanner-native/src/main/ets/GycScanner.ets`, 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText;
const mod = { exports: {} }; vm.runInNewContext(compiled, { exports: mod.exports, require: key => sdk[key], console });
const { GycScanner } = mod.exports;
(async () => {
  const scanner = new GycScanner({ cacheDir: '/tmp' });
  const scan = scanner.scan();
  assert.equal((await scanner.scan()).status, 'busy');
  completeScan({ originalValue: 'text' });
  assert.equal((await scan).value, 'text');
  scanFailure = { code: 1000500002 }; assert.equal((await scanner.scan()).status, 'cancelled');
  scanFailure = { code: 201 }; assert.equal((await scanner.scan()).status, 'permission_denied');
  scanFailure = null;
  assert.equal((await scanner.decode(new ArrayBuffer(0))).status, 'invalid_content');
  assert.equal((await scanner.decode(new ArrayBuffer(5))).value, 'hello');
  assert.equal(writes, 3); assert.equal(removed, 1);
  decodeFailure = true; assert.equal((await scanner.decode(new ArrayBuffer(1))).status, 'failed');
  assert.equal(removed, 2);
  const late = scanner.scan(); scanner.dispose(); completeScan({ originalValue: 'late' });
  assert.equal((await late).status, 'cancelled');
  assert.equal((await scanner.scan()).status, 'cancelled');
  console.log('scanner lifecycle, errors and temporary file cleanup: passed');
})().catch(error => { console.error(error); process.exitCode = 1; });
