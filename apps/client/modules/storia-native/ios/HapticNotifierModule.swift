import Foundation
import UIKit
import UserNotifications

/// Classic RCTBridgeModule pattern: this Swift class provides the implementation,
/// and HapticNotifierModule.m's RCT_EXTERN_MODULE/RCT_EXTERN_METHOD macros do the
/// bridge registration (Swift can't use those ObjC macros directly). Exposed to JS
/// as `NativeModules.HapticNotifier.notify(title, body)` per PRD 3.6/3.7.
@objc(HapticNotifier)
class HapticNotifierModule: NSObject {

  override init() {
    super.init()
    UNUserNotificationCenter.current().delegate = ForegroundNotificationPresenter.shared
  }

  @objc
  static func requiresMainQueueSetup() -> Bool {
    true
  }

  @objc
  func notify(_ title: String, body: String) {
    DispatchQueue.main.async {
      UINotificationFeedbackGenerator().notificationOccurred(.success)
    }

    requestAuthorizationIfNeeded { granted in
      guard granted else { return }

      let content = UNMutableNotificationContent()
      content.title = title
      content.body = body
      content.sound = .default

      let request = UNNotificationRequest(
        identifier: UUID().uuidString,
        content: content,
        trigger: nil // deliver immediately
      )
      UNUserNotificationCenter.current().add(request)
    }
  }

  private func requestAuthorizationIfNeeded(completion: @escaping (Bool) -> Void) {
    let center = UNUserNotificationCenter.current()
    center.getNotificationSettings { settings in
      switch settings.authorizationStatus {
      case .authorized, .provisional:
        completion(true)
      case .notDetermined:
        center.requestAuthorization(options: [.alert, .sound]) { granted, _ in
          completion(granted)
        }
      default:
        completion(false)
      }
    }
  }
}

/// Local notifications are suppressed by default while the app is foregrounded —
/// this delegate opts back in so `notify()` still shows a banner, matching PRD
/// 3.6's "포그라운드 로컬 알림" (foreground local notification) requirement.
final class ForegroundNotificationPresenter: NSObject, UNUserNotificationCenterDelegate {
  static let shared = ForegroundNotificationPresenter()

  func userNotificationCenter(
    _ center: UNUserNotificationCenter,
    willPresent notification: UNNotification,
    withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
  ) {
    completionHandler([.banner, .sound])
  }
}
