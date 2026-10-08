import { KRAny, KuiklyRenderBaseView, KuiklyRenderCallback } from '@kuikly-open/render';
import { ComponentContent } from '@ohos.arkui.node';
import { customScan, scanBarcode, scanCore } from '@kit.ScanKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { hilog } from '@kit.PerformanceAnalysisKit';

/** 只承载 ScanKit 相机预览；取景框、权限说明、相册和扫码业务由公共 Page 负责。 */
@Observed
export class GycScannerPreviewView extends KuiklyRenderBaseView {
  /** 与 Kotlin ScannerPreviewView.NAME 一致的原生 View 注册名。 */
  static readonly VIEW_NAME: string = 'GycScannerPreviewView';
  /** 本 View 独占的 XComponent Surface controller，由 ArkUI 挂载和销毁。 */
  readonly surfaceController: XComponentController = new XComponentController();
  private running: boolean = false;
  private destroyed: boolean = false;
  private surfaceId: string = '';
  private width: number = 0;
  private height: number = 0;
  private frameSize: number = 260;
  private onResult: KuiklyRenderCallback | null = null;
  private onFailure: KuiklyRenderCallback | null = null;
  private generation: number = 0;
  // UI 请求代次与相机会话代次分开：异步 stop 期间先隔离旧帧，不取消当前待启动请求。
  private sessionGeneration: number = 0;
  private initialized: boolean = false;
  private started: boolean = false;
  // customScan 是进程级单实例；导航过渡中旧 View 的释放必须完成后，新 View 才能初始化。
  private static owner: GycScannerPreviewView | null = null;
  private static task: Promise<void> = Promise.resolve();

  setProp(key: string, value: KRAny | KuiklyRenderCallback): boolean {
    if (this.destroyed) return true;
    if (key === 'onResult') { this.onResult = value as KuiklyRenderCallback; return true; }
    if (key === 'onFailure') { this.onFailure = value as KuiklyRenderCallback; return true; }
    if (key === 'scanFrameSize') {
      const size = Number(value);
      if (Number.isFinite(size) && size > 0) this.frameSize = size;
      return true;
    }
    if (key === 'running') {
      const running = value === true;
      if (this.running !== running) { this.running = running; this.synchronize(); }
      return true;
    }
    return super.setProp(key, value);
  }

  createArkUIView(): ComponentContent<KuiklyRenderBaseView> {
    return new ComponentContent<KuiklyRenderBaseView>(this.getUIContext() as UIContext,
      wrapBuilder<[KuiklyRenderBaseView]>(createScannerPreview), this);
  }

  /** ArkUI Surface 创建后调用；尺寸有效且 running 时启动预览。 */
  surfaceLoaded(): void {
    if (this.destroyed) return;
    this.surfaceId = this.surfaceController.getXComponentSurfaceId();
    this.synchronize();
  }

  /** ArkUI Surface 销毁时撤销当前代次并排队释放相机。 */
  surfaceDestroyed(): void { this.surfaceId = ''; this.synchronize(); }

  /** @param area XComponent 的 vp 尺寸；无效/零尺寸不会启动相机。 */
  areaChanged(area: Area): void {
    const width = Number(area.width);
    const height = Number(area.height);
    if (width === this.width && height === this.height) return;
    this.width = width;
    this.height = height;
    this.synchronize();
  }

  private live(generation: number): boolean {
    return !this.destroyed && this.running && this.generation === generation && !!this.surfaceId &&
      Number.isFinite(this.width) && this.width > 0 && Number.isFinite(this.height) && this.height > 0;
  }

  private synchronize(): void {
    const generation = ++this.generation;
    const task = GycScannerPreviewView.task.then(async () => {
      if (this.generation !== generation) return;
      if (GycScannerPreviewView.owner === this) await this.releaseCamera();
      if (!this.live(generation)) return;
      const previous = GycScannerPreviewView.owner;
      if (previous !== null) await previous.releaseCamera();
      if (!this.live(generation)) return;
      GycScannerPreviewView.owner = this;
      const session = ++this.sessionGeneration;
      try {
        customScan.init({ scanTypes: [scanCore.ScanType.QR_CODE], enableMultiMode: false, enableAlbum: false });
        this.initialized = true;
        // ScanKit 自定义预览的结果坐标与 ViewControl 输入同单位，此处均为 vp。
        const control: customScan.ViewControl = {
          surfaceId: this.surfaceId, width: this.width, height: this.height,
        };
        customScan.start(control, (error: BusinessError, results: scanBarcode.ScanResult[]) => {
          if (!this.live(generation) || GycScannerPreviewView.owner !== this || session !== this.sessionGeneration) return;
          if (error) { this.failed(generation, error.code); return; }
          let result: scanBarcode.ScanResult | undefined;
          try {
            result = results.find(item => !!item.originalValue && this.inFrame(item));
            if (!result) { customScan.rescan(); return; }
          } catch (failure) { this.failed(generation, (failure as BusinessError).code); return; }
          // 同一帧只交付一次；绑定期间停止相机，迟到解码不能覆盖后续相册/导航。
          this.running = false;
          this.synchronize();
          this.onResult?.({ value: result.originalValue });
        });
        this.started = true;
        hilog.info(0x30, 'Scanner', '%{public}s', 'Camera preview started');
      } catch (error) {
        this.failed(generation, (error as BusinessError).code);
        await this.releaseCamera();
      }
    });
    GycScannerPreviewView.task = task.catch(() => {
      if (this.live(generation)) this.failed(generation, 0);
    });
  }

  private inFrame(result: scanBarcode.ScanResult): boolean {
    const rect = result.scanCodeRect;
    if (!rect || ![rect.left, rect.top, rect.right, rect.bottom].every(Number.isFinite) ||
      rect.right <= rect.left || rect.bottom <= rect.top) return false;
    const side = Math.min(this.frameSize, this.width, this.height);
    // 与 AVFoundation ROI 一致：正面积相交即可，框外或缺少位置证据继续扫描。
    return rect.right > (this.width - side) / 2 && rect.left < (this.width + side) / 2 &&
      rect.bottom > (this.height - side) / 2 && rect.top < (this.height + side) / 2;
  }

  private failed(generation: number, code: number): void {
    if (!this.live(generation)) return;
    this.running = false;
    this.synchronize();
    hilog.warn(0x30, 'Scanner', 'Camera preview failed, code=%{public}d', code);
    this.onFailure?.({ message: 'Camera preview unavailable' });
  }

  private async releaseCamera(): Promise<void> {
    if (GycScannerPreviewView.owner !== this) return;
    this.sessionGeneration++;
    let stopFailure: BusinessError | null = null;
    if (this.started) {
      try { await customScan.stop(); this.started = false; }
      catch (error) { stopFailure = error as BusinessError; }
    }
    // 即使 stop 失败仍尝试 release；成功 release 才能解除全局 owner，避免新 View 抢占未释放相机。
    if (this.initialized) await customScan.release();
    this.started = false;
    this.initialized = false;
    GycScannerPreviewView.owner = null;
    if (stopFailure) hilog.warn(0x30, 'Scanner', 'Camera stop failed before release, code=%{public}d', stopFailure.code ?? 0);
  }

  onDestroy(): void {
    this.destroyed = true;
    this.running = false;
    this.onResult = null;
    this.onFailure = null;
    this.synchronize();
    super.onDestroy();
  }
}

@Component
struct ScannerPreview {
  @ObjectLink renderView: GycScannerPreviewView;
  build() {
    XComponent({ id: this.renderView.nodeId, type: XComponentType.SURFACE,
      controller: this.renderView.surfaceController })
      .onLoad(() => this.renderView.surfaceLoaded())
      .onDestroy(() => this.renderView.surfaceDestroyed())
      .onAreaChange((_oldArea, area) => this.renderView.areaChanged(area))
      .width('100%').height('100%')
  }
}

@Builder
function createScannerPreview(view: KuiklyRenderBaseView) {
  ScannerPreview({ renderView: view as GycScannerPreviewView });
}
