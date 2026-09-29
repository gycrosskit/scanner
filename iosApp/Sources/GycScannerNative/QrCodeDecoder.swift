import CoreImage

/** CoreImage 只负责媒体 primitive，二维码业务格式仍由 shared 校验。 */
public enum QrCodeDecoder {
    public static func decode(data: Data) -> String? {
        guard let image = CIImage(data: data),
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
