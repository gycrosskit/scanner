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
    private var running = false
    private var released = false
    private var scanFrameSize: CGFloat

    /// 创建相机预览，不主动申请权限；错误与结果交付到主线程。
    /// - Parameters:
    ///   - scanFrameSize: 正方形识别区边长，points，默认 240；宿主传正且有限的值。
    ///   - onResult: 单次二维码原始文本，结果后停止扫描。
    ///   - onFailure: 设备/权限失败诊断码，由宿主映射用户文案。
    public init(scanFrameSize: CGFloat = 240, onResult: @escaping (String) -> Void,
                onFailure: @escaping (String) -> Void) {
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
        scanFrameSize = points
        setNeedsLayout()
    }

    /// 一个 true 周期仅返回一次；结果后调用 true 可开始下一次扫描。
    public func setRunning(_ value: Bool) {
        guard !released, value != running else { return }
        running = value
        sessionQueue.async { [session] in
            if value && !session.isRunning { session.startRunning() }
            else if !value && session.isRunning { session.stopRunning() }
        }
        if value {
            sessionQueue.async { [weak self] in
                DispatchQueue.main.async { self?.updateRegion() }
            }
        }
    }

    /// 主线程幂等释放 delegate 和回调；此 View 不可再次启动。
    public func releaseCamera() {
        setRunning(false)
        released = true
        onResult = nil
        onFailure = nil
        output.setMetadataObjectsDelegate(nil, queue: nil)
    }

    public override func didMoveToWindow() {
        super.didMoveToWindow()
        if window == nil { setRunning(false) }
    }

    private func updateRegion() {
        guard running, !released, session.isRunning, !bounds.isEmpty else { return }
        let side = min(scanFrameSize, bounds.width, bounds.height)
        let rect = CGRect(x: (bounds.width-side)/2, y: (bounds.height-side)/2, width: side, height: side)
        let region = preview.metadataOutputRectConverted(fromLayerRect: rect).standardized
            .intersection(CGRect(x: 0, y: 0, width: 1, height: 1))
        if !region.isNull && !region.isEmpty { output.rectOfInterest = region }
    }

    public func metadataOutput(_ output: AVCaptureMetadataOutput,
                              didOutput objects: [AVMetadataObject], from connection: AVCaptureConnection) {
        guard running, !released,
              let text = objects.compactMap({ ($0 as? AVMetadataMachineReadableCodeObject)?.stringValue })
                .first(where: { !$0.isEmpty }) else { return }
        setRunning(false)
        onResult?(text)
    }

    deinit {
        let retainedSession = session
        sessionQueue.async { if retainedSession.isRunning { retainedSession.stopRunning() } }
    }
}
