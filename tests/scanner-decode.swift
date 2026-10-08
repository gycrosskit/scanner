import CoreImage
import Foundation
let filter = CIFilter(name: "CIQRCodeGenerator")!
filter.setValue(Data("https://example.com/qr".utf8), forKey: "inputMessage")
let image = filter.outputImage!.transformed(by: CGAffineTransform(scaleX: 8, y: 8))
let data = CIContext().pngRepresentation(of: image, format: .RGBA8, colorSpace: CGColorSpaceCreateDeviceRGB())!
precondition(QrCodeDecoder.decode(data: data) == "https://example.com/qr")
var oversized = data
oversized.append(Data(count: 32 * 1024 * 1024 + 1 - data.count))
precondition(QrCodeDecoder.decode(data: oversized) == nil, "oversized valid QR must be rejected")
precondition(QrCodeDecoder.decode(data: Data()) == nil)
print("Production CoreImage decoder: valid, empty, 32MiB+1 input passed")
