import Foundation
import LocalAuthentication
/*
import UIKit
import "KeychainWrapper.h"
import Cordova
#include <sys/types.h>
#include <sys/sysctl.h>

 Maybe for later if above does not work.
#import <UIKit/UIKit.h>
#import "KeychainWrapper.h"
#import <Foundation/Foundation.h>
#import <Cordova/CDV.h>
#import <LocalAuthentication/LocalAuthentication.h>
*/

@objc(Fingerprint) class Fingerprint : CDVPlugin {

@property (strong,nonatomic)NSString* TAG;
@property (strong, nonatomic) KeychainWrapper* MyKeychainWrapper;
@property (strong, nonatomic) LAContext* laContext;

    enum PluginError:Int {
        case BIOMETRIC_UNKNOWN_ERROR = -100
        case BIOMETRIC_UNAVAILABLE = -101
        case BIOMETRIC_AUTHENTICATION_FAILED = -102
        case BIOMETRIC_PERMISSION_NOT_GRANTED = -105
        case BIOMETRIC_NOT_ENROLLED = -106
        case BIOMETRIC_DISMISSED = -108
        case BIOMETRIC_SCREEN_GUARD_UNSECURED = -110
        case BIOMETRIC_LOCKED_OUT = -111
    }

    struct ErrorCodes {
        var code: Int
    }

@objc(setLocale:)
    func setLocale(_ command: CDVInvokedUrlCommand){
  CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
  [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

@objc(has:)
    func has(_ command: CDVInvokedUrlCommand){
  	self.TAG = (NSString*)[command.arguments objectAtIndex:0];
    BOOL hasLoginKey = [[NSUserDefaults standardUserDefaults] boolForKey:self.TAG];
    if(hasLoginKey){
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }
    else{
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: @"No Password in chain"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }
}

@objc(save:)
    func save(_ command: CDVInvokedUrlCommand){
	 	self.TAG = (NSString*)[command.arguments objectAtIndex:0];
    NSString* password = (NSString*)[command.arguments objectAtIndex:1];
    @try {
        self.MyKeychainWrapper = [[KeychainWrapper alloc]init];
        [self.MyKeychainWrapper mySetObject:password forKey:(__bridge id)(kSecValueData)];
        [self.MyKeychainWrapper writeToKeychain];
        [[NSUserDefaults standardUserDefaults]setBool:true forKey:self.TAG];
        [[NSUserDefaults standardUserDefaults]synchronize];

        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }
    @catch(NSException *exception){
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: @"Password could not be save in chain"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }
}

@objc(delete:)
    func dekete(_ command: CDVInvokedUrlCommand){
	 	self.TAG = (NSString*)[command.arguments objectAtIndex:0];
    @try {

        if(self.TAG && [[NSUserDefaults standardUserDefaults] objectForKey:self.TAG])
        {
            self.MyKeychainWrapper = [[KeychainWrapper alloc]init];
            [self.MyKeychainWrapper resetKeychainItem];
        }


        [[NSUserDefaults standardUserDefaults] removeObjectForKey:self.TAG];
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }
    @catch(NSException *exception) {
        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: @"Could not delete password from chain"];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }
}


@objc(verify:)
    func verify(_ command: CDVInvokedUrlCommand){
	 	self.TAG = (NSString*)[command.arguments objectAtIndex:0];
	  NSString* message = (NSString*)[command.arguments objectAtIndex:1];
    self.laContext = [[LAContext alloc] init];
    self.MyKeychainWrapper = [[KeychainWrapper alloc]init];

    BOOL hasLoginKey = [[NSUserDefaults standardUserDefaults] boolForKey:self.TAG];
    if(hasLoginKey){
        NSError * error;
        BOOL touchIDAvailable = [self.laContext canEvaluatePolicy:LAPolicyDeviceOwnerAuthenticationWithBiometrics error:&error];

        if(touchIDAvailable){
            [self.laContext evaluatePolicy:LAPolicyDeviceOwnerAuthenticationWithBiometrics localizedReason:message reply:^(BOOL success, NSError *error) {
                dispatch_async(dispatch_get_main_queue(), ^{

                if(success){
                    NSString *password = [self.MyKeychainWrapper myObjectForKey:@"v_Data"];
                    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString: password];
                    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
                }
                    if(error != nil) {
                        NSDictionary *errorDictionary = @{@"OS":@"iOS",@"ErrorCode":[NSString stringWithFormat:@"%li", (long)error.code],@"ErrorMessage":error.localizedDescription};
                        CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:errorDictionary];
                        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
                    }
                });
            }];

        }
        else{
            if(error)
            {
                //If an error is returned from LA Context (should always be true in this situation)
                NSDictionary *errorDictionary = @{@"OS":@"iOS",@"ErrorCode":[NSString stringWithFormat:@"%li", (long)error.code],@"ErrorMessage":error.localizedDescription};
                CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:errorDictionary];
                [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            }
            else
            {
                //Should never come to this, but we treat it anyway
                CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: @"Touch ID not available"];
                [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
            }
        }
    }
    else{
           CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString: @"-1"];
            [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }
}

    @objc(isAvailable:)
    func isAvailable(_ command: CDVInvokedUrlCommand){
        let authenticationContext = LAContext();
        var biometryType = "finger";
        var errorResponse: [AnyHashable: Any] = [
            "code": 0,
            "message": "Not Available"
        ];
        var error:NSError?;
        let policy:LAPolicy = .deviceOwnerAuthenticationWithBiometrics;
        var pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "Not available");
        let available = authenticationContext.canEvaluatePolicy(policy, error: &error);

        var results: [String : Any]

        if(error != nil){
            biometryType = "none";
            errorResponse["code"] = error?.code;
            errorResponse["message"] = error?.localizedDescription;
        }

        if (available == true) {
            if #available(iOS 11.0, *) {
                switch(authenticationContext.biometryType) {
                case .none:
                    biometryType = "none";
                case .touchID:
                    biometryType = "finger";
                case .faceID:
                    biometryType = "face"
                }
            } else {
                biometryType = "finger";
            }

            pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: biometryType);
        }else{
            var code: Int;
            switch(error!._code) {
                case Int(kLAErrorBiometryNotAvailable):
                    code = PluginError.BIOMETRIC_UNAVAILABLE.rawValue;
                    break;
                case Int(kLAErrorBiometryNotEnrolled):
                    code = PluginError.BIOMETRIC_NOT_ENROLLED.rawValue;
                    break;

                default:
                    code = PluginError.BIOMETRIC_UNKNOWN_ERROR.rawValue;
                    break;
            }
            results = ["code": code, "message": error!.localizedDescription];
            pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: results);
        }

        commandDelegate.send(pluginResult, callbackId:command.callbackId);
    }


    @objc(authenticate:)
    func authenticate(_ command: CDVInvokedUrlCommand){
        let authenticationContext = LAContext();
        var errorResponse: [AnyHashable: Any] = [
            "message": "Something went wrong"
        ];
        var pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: errorResponse);
        var reason = "Authentication";
        var policy:LAPolicy = .deviceOwnerAuthentication;
        let data  = command.arguments[0] as AnyObject?;

        if let disableBackup = data?["disableBackup"] as! Bool? {
            if disableBackup {
                authenticationContext.localizedFallbackTitle = "";
                policy = .deviceOwnerAuthenticationWithBiometrics;
            } else {
                if let fallbackButtonTitle = data?["fallbackButtonTitle"] as! String? {
                    authenticationContext.localizedFallbackTitle = fallbackButtonTitle;
                }else{
                    authenticationContext.localizedFallbackTitle = "Use Pin";
                }
            }
        }

        // Localized reason
        if let description = data?["description"] as! String? {
            reason = description;
        }

        authenticationContext.evaluatePolicy(
            policy,
            localizedReason: reason,
            reply: { [unowned self] (success, error) -> Void in
                if( success ) {
                    pluginResult = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: "Success");
                }else {
                    if (error != nil) {

                        var errorCodes = [Int: ErrorCodes]()
                        var errorResult: [String : Any] = ["code":  PluginError.BIOMETRIC_UNKNOWN_ERROR.rawValue, "message": error?.localizedDescription ?? ""];

                        errorCodes[1] = ErrorCodes(code: PluginError.BIOMETRIC_AUTHENTICATION_FAILED.rawValue)
                        errorCodes[2] = ErrorCodes(code: PluginError.BIOMETRIC_DISMISSED.rawValue)
                        errorCodes[5] = ErrorCodes(code: PluginError.BIOMETRIC_SCREEN_GUARD_UNSECURED.rawValue)
                        errorCodes[6] = ErrorCodes(code: PluginError.BIOMETRIC_UNAVAILABLE.rawValue)
                        errorCodes[7] = ErrorCodes(code: PluginError.BIOMETRIC_NOT_ENROLLED.rawValue)
                        errorCodes[8] = ErrorCodes(code: PluginError.BIOMETRIC_LOCKED_OUT.rawValue)

                        let errorCode = abs(error!._code)
                        if let e = errorCodes[errorCode] {
                           errorResult = ["code": e.code, "message": error!.localizedDescription];
                        }

                        pluginResult = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: errorResult);
                    }
                }
                self.commandDelegate.send(pluginResult, callbackId:command.callbackId);
            }
        );
    }

    override func pluginInitialize() {
        super.pluginInitialize()
    }
}
