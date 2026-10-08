import CoreImage

/** CoreImage 只负责媒体 primitive，二维码业务格式仍由 shared 校验。 */
/// 原始二维码图片解码入口；调用线程执行，不包含相机 UI 或业务格式校验。
public enum QrCodeDecoder {
    /// 返回首个非空二维码文本；无效图片/无二维码返回 nil。
    /// - Parameter data: 完整图片文件数据，最大 32 MiB；空/超限返回 nil。
    public static func decode(data: Data) -> String? {
        guard !data.isEmpty, data.count <= 32 * 1024 * 1024,
              let image = CIImage(data: data),
              let detector = CIDetector(
                ofType: CIDetectorTypeQRCode,
                context: nil,
                options: [CIDetectorAccuracy: CIDetectorAccuracyHigh]
              ) else {
            return nil
        }
        return detector.features(in: image)
            .compactMap { ($0 as? CIQRCodeFeature)?.messageString }
            .first { !$0.isEmpty }
    }
}
