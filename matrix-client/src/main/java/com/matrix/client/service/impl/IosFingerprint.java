//// Swift 示例代码
//import UIKit
//import AdSupport
//import SystemConfiguration
//
//class DeviceFingerprint {
//
//    // 收集设备信息
//    static func collectDeviceInfo() -> [String: Any] {
//        var fingerprint = [String: Any]()
//
//        // 1. 设备基本信息
//        let device = UIDevice.current
//        fingerprint["systemName"] = device.systemName
//        fingerprint["systemVersion"] = device.systemVersion
//        fingerprint["model"] = device.model
//        fingerprint["name"] = device.name
//        fingerprint["identifierForVendor"] = device.identifierForVendor?.uuidString
//
//        // 2. 设备型号识别
//        fingerprint["modelIdentifier"] = getModelIdentifier()
//
//        // 3. 屏幕信息
//        let screen = UIScreen.main
//        fingerprint["screenScale"] = screen.scale
//        fingerprint["screenBounds"] = "\(screen.bounds.width)x\(screen.bounds.height)"
//        fingerprint["nativeScale"] = screen.nativeScale
//
//        // 4. 语言和地区
//        fingerprint["preferredLanguage"] = Locale.preferredLanguages.first
//        fingerprint["currentLocale"] = Locale.current.identifier
//        fingerprint["timeZone"] = TimeZone.current.identifier
//
//        // 5. 电池信息
//        device.isBatteryMonitoringEnabled = true
//        fingerprint["batteryLevel"] = device.batteryLevel
//        fingerprint["batteryState"] = device.batteryState.rawValue
//
//        // 6. 存储空间
//        fingerprint["diskSpace"] = getDiskSpace()
//
//        // 7. 网络信息
//        fingerprint["networkType"] = getNetworkType()
//
//        // 8. 越狱检测
//        fingerprint["isJailbroken"] = isJailbroken()
//
//        return fingerprint
//    }
//
//    // 获取设备型号标识
//    private static func getModelIdentifier() -> String {
//        var systemInfo = utsname()
//        uname(&systemInfo)
//        let machineMirror = Mirror(reflecting: systemInfo.machine)
//        let identifier = machineMirror.children.reduce("") { identifier, element in
//            guard let value = element.value as? Int8, value != 0 else { return identifier }
//            return identifier + String(UnicodeScalar(UInt8(value)))
//        }
//        return identifier
//    }
//
//    // 获取磁盘空间
//    private static func getDiskSpace() -> [String: Int64] {
//        do {
//            let systemAttributes = try FileManager.default.attributesOfFileSystem(forPath: NSHomeDirectory())
//                let freeSpace = (systemAttributes[.systemFreeSize] as? NSNumber)?.int64Value ?? 0
//                let totalSpace = (systemAttributes[.systemSize] as? NSNumber)?.int64Value ?? 0
//                return ["free": freeSpace, "total": totalSpace]
//        } catch {
//            return ["free": 0, "total": 0]
//        }
//    }
//
//    // 网络类型检测
//    private static func getNetworkType() -> String {
//        // 使用 Reachability 或 Network.framework
//        return "unknown"
//    }
//
//    // 越狱检测
//    private static func isJailbroken() -> Bool {
//        // 常见的越狱检测方法
//        let paths = [
//        "/Applications/Cydia.app",
//                "/Library/MobileSubstrate/MobileSubstrate.dylib",
//                "/bin/bash",
//                "/usr/sbin/sshd"
//        ]
//
//        for path in paths {
//            if FileManager.default.fileExists(atPath: path) {
//                return true
//            }
//        }
//
//        return false
//    }
//
//    // 生成指纹
//    static func generateFingerprint() -> String {
//        let info = collectDeviceInfo()
//        let jsonData = try! JSONSerialization.data(withJSONObject: info, options: [])
//        let jsonString = String(data: jsonData, encoding: .utf8) ?? ""
//
//        // 计算 SHA256
//        let data = jsonString.data(using: .utf8)!
//                var hash = [UInt8](repeating: 0, count: Int(CC_SHA256_DIGEST_LENGTH))
//        data.withUnsafeBytes {
//            _ = CC_SHA256($0.baseAddress, CC_LONG(data.count), &hash)
//        }
//        return hash.map { String(format: "%02x", $0) }.joined()
//    }
//}
//
//
