import { KRAny, KuiklyRenderBaseModule, KuiklyRenderCallback } from '@kuikly-open/render';
import { util } from '@kit.ArkTS';
import { GycScanner, ScanResult } from './GycScanner';
interface DecodeArgs { data?: string; }

/** 每页的异步扫码桥；销毁后停止回执，Base64 输入限制为 32 MiB 图片。 */
export class GycScannerModule extends KuiklyRenderBaseModule {
  /** 原生注册名，与 Kotlin ScannerModule.NAME 一致。 */
  static readonly MODULE_NAME: string = 'GycScanner';
  private scanner: GycScanner | null = null;
  private destroyed: boolean = false;
  syncMode(): boolean { return false; }

  call(method: string, params: KRAny, callback: KuiklyRenderCallback | null): KRAny {
    this.perform(method, params).then((result: ScanResult) => {
      if (!this.destroyed) { callback?.(result); }
    }).catch(() => { if (!this.destroyed) { callback?.({ status: 'failed' }); } });
    return null;
  }

  private async perform(method: string, params: KRAny): Promise<ScanResult> {
    if (this.destroyed) { return { status: 'cancelled' }; }
    const context = this.controller?.getUIAbilityContext();
    if (!context) { return { status: 'failed' }; }
    if (!this.scanner) { this.scanner = new GycScanner(context); }
    if (method === 'scan') { return this.scanner.scan(); }
    if (method !== 'decode' || typeof params !== 'string') { return { status: 'invalid_content' }; }
    const args = JSON.parse(params) as DecodeArgs;
    // ponytail: Base64 临时内存较大，单次最多 32 MiB；更大图片需要宿主文件 URI 协议。
    if (!args || typeof args.data !== 'string' || args.data.length > Math.ceil(32 * 1024 * 1024 / 3) * 4) {
      return { status: 'invalid_content' };
    }
    const bytes = new util.Base64Helper().decodeSync(args.data);
    return this.scanner.decode(bytes.buffer as ArrayBuffer);
  }

  onDestroy(): void {
    this.destroyed = true;
    this.scanner?.dispose();
    super.onDestroy();
  }
}
