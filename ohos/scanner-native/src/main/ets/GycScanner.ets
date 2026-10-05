import { common } from '@kit.AbilityKit';
import { detectBarcode, scanCore, scanBarcode } from '@kit.ScanKit';
import { fileIo as fs, fileUri } from '@kit.CoreFileKit';
import { util } from '@kit.ArkTS';
import { BusinessError } from '@kit.BasicServicesKit';

/** 扫码终态；decoded 才有原始文本，cancelled 不等于 SDK 失败。 */
export interface ScanResult {
  /** decoded/not_found/busy/cancelled/permission_denied/failed/invalid_content。 */
  status: string;
  /** 仅识别成功时提供，不校验业务格式。 */
  value?: string;
}

/** 一个页面持有一个实例，离开页面 dispose；系统扫码页无法通过 ScanKit 主动关闭。 */
export class GycScanner {
  private destroyed: boolean = false;
  private scanning: boolean = false;
  private sequence: number = 0;
  private readonly identifier: string = util.generateRandomUUID(true);
  private readonly context: common.UIAbilityContext;
  /** @param context 当前页面 UIAbility，用其 cacheDir 保存临时图片；生命周期由宿主管理。 */
  constructor(context: common.UIAbilityContext) { this.context = context; }

  /** 主线程打开系统扫码页，宿主先申请 CAMERA；并行调用返回 busy，dispose 抑制迟到结果。 */
  async scan(): Promise<ScanResult> {
    if (this.destroyed) { return { status: 'cancelled' }; }
    if (this.scanning) { return { status: 'busy' }; }
    this.scanning = true;
    try {
      const result = await scanBarcode.startScanForResult(this.context, {
        scanTypes: [scanCore.ScanType.QR_CODE], enableMultiMode: false, enableAlbum: true
      });
      if (this.destroyed) { return { status: 'cancelled' }; }
      return result.originalValue ? { status: 'decoded', value: result.originalValue } : { status: 'not_found' };
    } catch (error) {
      if (this.destroyed) { return { status: 'cancelled' }; }
      const failure = error as BusinessError;
      return { status: failure.code === 1000500002 ? 'cancelled' :
        failure.code === 201 ? 'permission_denied' : 'failed' };
    } finally { this.scanning = false; }
  }

  /** 解码完整图片并在终态清理临时文件。@param bytes 1..32 MiB 图片；不支持时返回失败状态。 */
  async decode(bytes: ArrayBuffer): Promise<ScanResult> {
    if (this.destroyed) { return { status: 'cancelled' }; }
    if (bytes.byteLength === 0 || bytes.byteLength > 32 * 1024 * 1024) {
      return { status: 'invalid_content' };
    }
    const path = `${this.context.cacheDir}/gyc_scanner_${this.identifier}_${this.sequence++}.img`;
    try {
      const file = await fs.open(path, fs.OpenMode.CREATE | fs.OpenMode.WRITE_ONLY | fs.OpenMode.TRUNC);
      try {
        let offset = 0;
        while (offset < bytes.byteLength) {
          const count = await fs.write(file.fd, bytes.slice(offset));
          if (count <= 0) { throw new Error('Incomplete image write'); }
          offset += count;
        }
      } finally { await fs.close(file); }
      if (this.destroyed) { return { status: 'cancelled' }; }
      const results = await detectBarcode.decode({ uri: fileUri.getUriFromPath(path) }, {
        scanTypes: [scanCore.ScanType.QR_CODE]
      });
      if (this.destroyed) { return { status: 'cancelled' }; }
      const value = results.length > 0 ? results[0].originalValue : '';
      return value ? { status: 'decoded', value } : { status: 'not_found' };
    } catch (_) { return { status: this.destroyed ? 'cancelled' : 'failed' }; }
    finally { try { await fs.unlink(path); } catch (_) { /* 系统可能已清理缓存。 */ } }
  }

  /** 主线程幂等撤销实例；不主动关闭系统扫码页，已开始的文件任务完成后清理。 */
  dispose(): void { this.destroyed = true; }
}
