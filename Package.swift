// swift-tools-version: 5.9
import PackageDescription
let package = Package(name: "GycScannerNative", platforms: [.iOS(.v15)],
    products: [.library(name: "GycScannerNative", targets: ["GycScannerNative"])],
    targets: [.target(name: "GycScannerNative", path: "iosApp/Sources/GycScannerNative")])
