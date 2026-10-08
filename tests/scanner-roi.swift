import UIKit
import AVFoundation
import ObjectiveC
private var fixtureKey: UInt8 = 0
private final class FixturePayload: NSObject {
    let rect: CGRect
    let timestamp: CMTime
    let text: String
    init(_ rect: CGRect, _ timestamp: CMTime, _ text: String) {
        self.rect = rect; self.timestamp = timestamp; self.text = text
    }
}
private class FixtureCode: AVMetadataMachineReadableCodeObject {
    private var payload: FixturePayload { objc_getAssociatedObject(self, &fixtureKey) as! FixturePayload }
    override var bounds: CGRect { payload.rect }
    override var time: CMTime { payload.timestamp }
    override var stringValue: String? { payload.text }
    override var type: AVMetadataObject.ObjectType { .qr }
}
func code(_ rect: CGRect, _ time: CMTime, _ text: String = "ok") -> AVMetadataMachineReadableCodeObject {
    let object = class_createInstance(FixtureCode.self, 0) as! FixtureCode
    objc_setAssociatedObject(object, &fixtureKey, FixturePayload(rect, time, text), .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
    return object
}

// Same-file extension exercises the production private state, without adding producer test APIs.
extension ScannerPreviewView {
    fileprivate func prepareFixture(region: CGRect?) {
        running = true
        #if !BASELINE_ROI
        configuredRegion = region
        #endif
    }
    fileprivate func deliverFixture(_ objects: [AVMetadataObject], foreign: Bool = false) {
        let selectedOutput = foreign ? AVCaptureMetadataOutput() : output
        let connection = AVCaptureConnection(inputPorts: [], output: selectedOutput)
        metadataOutput(selectedOutput, didOutput: objects, from: connection)
    }
}

@main final class ROIProbeApp: UIResponder, UIApplicationDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions options: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        DispatchQueue.main.async { self.check() }
        return true
    }
    private func check() {
        let region = CGRect(x: 0.3, y: 0.3, width: 0.4, height: 0.4)
        let inside = CGRect(x: 0.35, y: 0.35, width: 0.1, height: 0.1)
        let outside = CGRect(x: 0, y: 0, width: 0.1, height: 0.1)
        let timestamp = CMTime.invalid
        var checks: [[String: Any]] = []
        func verify(_ name: String, expected: Int, ready: Bool = true, _ action: (ScannerPreviewView) -> Void) {
            var results: [String] = []
            var feedback = 0
            let view = ScannerPreviewView(onResult: { results.append($0) }, onFailure: { _ in })
            view.setFeedbackHandler { feedback += 1 }
            view.prepareFixture(region: ready ? region : nil)
            action(view)
            checks.append(["name": name, "expected": expected, "actual": results.count,
                           "feedback": feedback, "values": results, "pass": results.count == expected && feedback == expected && (expected == 0 || results == ["ok"])])
            view.releaseCamera()
        }
        verify("startup_not_ready", expected: 0, ready: false) { $0.deliverFixture([code(outside, timestamp)]) }
        verify("outside_current_roi", expected: 0) { $0.deliverFixture([code(outside, timestamp)]) }
        verify("inside_once", expected: 1) { view in
            view.deliverFixture([code(inside, timestamp)]); view.deliverFixture([code(inside, timestamp)])
        }
        verify("intersecting_boundary_preserved", expected: 1) { $0.deliverFixture([code(CGRect(x: 0.2, y: 0.4, width: 0.2, height: 0.1), timestamp)]) }
        verify("pause_rejects", expected: 0) { view in view.setRunning(false); view.deliverFixture([code(inside, timestamp)]) }
        verify("release_rejects", expected: 0) { view in view.releaseCamera(); view.deliverFixture([code(inside, timestamp)]) }
        verify("resize_invalidates_before_layout", expected: 0) { view in view.setScanFrameSize(180); view.deliverFixture([code(inside, timestamp)]) }
        verify("unchanged_size_keeps_roi", expected: 1) { view in view.setScanFrameSize(260); view.deliverFixture([code(inside, timestamp)]) }
        verify("foreign_output_rejects", expected: 0) { $0.deliverFixture([code(inside, timestamp)], foreign: true) }
        verify("skip_outside_then_accept_inside", expected: 1) { $0.deliverFixture([code(outside, timestamp, "outside"), code(inside, timestamp)]) }
        let passed = checks.allSatisfy { $0["pass"] as? Bool == true }
        let result: [String: Any] = ["pass": passed, "checks": checks,
            "scope": "Actual production UIView/AVFoundation delegate; typed metadata subclasses and controlled private ROI state (metadata timestamps intentionally invalid); no camera/device capture"]
        let directory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        try! JSONSerialization.data(withJSONObject: result, options: [.prettyPrinted, .sortedKeys]).write(to: directory.appendingPathComponent("result.json"))
        exit(passed ? 0 : 1)
    }
}
