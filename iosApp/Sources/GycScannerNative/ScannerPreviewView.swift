import AVFoundation
import UIKit

/// 主线程创建和操作；宿主负责 CAMERA 权限、前后台暂停及 releaseCamera。
public final class ScannerPreviewView: UIView, AVCaptureMetadataOutputObjectsDelegate {
    private let session = AVCaptureSession()
    private let sessionQueue = DispatchQueue(label: "io.github.gycrosskit.scanner")
    private let output = AVCaptureMetadataOutput()
    private let preview: AVCaptureVideoPreviewLayer
    private var onResult: ((String) -> Void)?
    private var onFailure: ((String) -> Void)?
    private var onFeedback: (() -> Void)?
    private var running = false
    private var released = false
    private var scanFrameSize: CGFloat
    private var generation: UInt64 = 0
    private var configuredRegion: CGRect?

    /// 创建相机预览，不主动申请权限；错误与结果交付到主线程。
    /// - Parameters:
    ///   - scanFrameSize: 正方形识别区边长，points，默认 240；宿主传正且有限的值。
    ///   - onResult: 单次二维码原始文本，结果后停止扫描。
    ///   - onFailure: 设备/权限失败诊断码，由宿主映射用户文案。
    public init(scanFrameSize: CGFloat = 240, onResult: @escaping (String) -> Void,
                onFailure: @escaping (String) -> Void) {
        precondition(scanFrameSize > 0 && scanFrameSize.isFinite)
        self.scanFrameSize = scanFrameSize
        self.onResult = onResult
        self.onFailure = onFailure
        preview = AVCaptureVideoPreviewLayer(session: session)
        super.init(frame: .zero)
        preview.videoGravity = .resizeAspectFill
        layer.addSublayer(preview)
        guard AVCaptureDevice.authorizationStatus(for: .video) == .authorized,
              let device = AVCaptureDevice.default(for: .video),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input), session.canAddOutput(output) else {
            DispatchQueue.main.async { [weak self] in
                guard let self, !self.released else { return }
                self.onFailure?("camera_unavailable_or_permission_denied")
            }
            return
        }
        session.beginConfiguration()
        session.addInput(input)
        session.addOutput(output)
        output.setMetadataObjectsDelegate(self, queue: .main)
        output.metadataObjectTypes = [.qr]
        session.commitConfiguration()
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) is unavailable") }

    public override func layoutSubviews() {
        super.layoutSubviews()
        preview.frame = bounds
        updateRegion()
    }

    /// 设置正且有限的识别区边长，points；不绘制取景框。
    public func setScanFrameSize(_ points: CGFloat) {
        precondition(points > 0 && points.isFinite)
        guard points != scanFrameSize else { return }
        scanFrameSize = points
        configuredRegion = nil
        setNeedsLayout()
    }

    /// 与 Android/Kuikly Compose 相同的宿主反馈入口；默认无反馈，在有效结果之后仅调用一次。
    public func setFeedbackHandler(_ handler: (() -> Void)?) {
        if !released { onFeedback = handler }
    }

    /// 一个 true 周期仅返回一次；结果后调用 true 可开始下一次扫描。
    public func setRunning(_ value: Bool) {
        guard !released, value != running else { return }
        running = value
        generation &+= 1
        configuredRegion = nil
        let expectedGeneration = generation
        sessionQueue.async { [weak self, session] in
            if value && !session.isRunning { session.startRunning() }
            else if !value && session.isRunning { session.stopRunning() }
            if value {
                DispatchQueue.main.async { [weak self] in
                    guard let self, self.generation == expectedGeneration else { return }
                    self.updateRegion()
                }
            }
        }
    }

    /// 主线程幂等释放 delegate 和回调；此 View 不可再次启动。
    public func releaseCamera() {
        setRunning(false)
        released = true
        onResult = nil
        onFailure = nil
        onFeedback = nil
        output.setMetadataObjectsDelegate(nil, queue: nil)
    }

    public override func didMoveToWindow() {
        super.didMoveToWindow()
        if window == nil { setRunning(false) }
    }

    private func updateRegion() {
        guard running, !released, session.isRunning, !bounds.isEmpty else {
            configuredRegion = nil
            return
        }
        let side = min(scanFrameSize, bounds.width, bounds.height)
        let rect = CGRect(x: (bounds.width-side)/2, y: (bounds.height-side)/2, width: side, height: side)
        let region = preview.metadataOutputRectConverted(fromLayerRect: rect).standardized
            .intersection(CGRect(x: 0, y: 0, width: 1, height: 1))
        guard region != configuredRegion else { return }
        configuredRegion = nil
        guard !region.isNull, !region.isEmpty,
              [region.minX, region.minY, region.width, region.height].allSatisfy({ $0.isFinite }) else { return }
        output.rectOfInterest = region
        configuredRegion = region
    }

    public func metadataOutput(_ output: AVCaptureMetadataOutput,
                              didOutput objects: [AVMetadataObject], from connection: AVCaptureConnection) {
        guard output === self.output, running, !released, let region = configuredRegion else { return }
        let code = objects.compactMap { $0 as? AVMetadataMachineReadableCodeObject }.first { code in
            let rect = code.bounds.standardized
            let intersection = rect.intersection(region)
            return code.type == .qr &&
                [rect.minX, rect.minY, rect.width, rect.height].allSatisfy({ $0.isFinite }) &&
                !intersection.isNull && !intersection.isEmpty && !(code.stringValue ?? "").isEmpty
        }
        guard let text = code?.stringValue else { return }
        setRunning(false)
        onFeedback?()
        onResult?(text)
    }

    deinit {
        let retainedSession = session
        sessionQueue.async { if retainedSession.isRunning { retainedSession.stopRunning() } }
    }
}
