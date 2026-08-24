#import <React/RCTBridgeModule.h>

// Bridges HapticNotifierModule.swift's `HapticNotifier` (@objc(HapticNotifier))
// into the classic RN bridge. Swift can't use RCT_EXTERN_MODULE/RCT_EXTERN_METHOD
// directly, so this small Obj-C shim does the registration.
@interface RCT_EXTERN_MODULE(HapticNotifier, NSObject)

RCT_EXTERN_METHOD(notify:(NSString *)title body:(NSString *)body)

@end
