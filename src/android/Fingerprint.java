package de.niklasmerz.cordova.biometric;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.support.annotation.RequiresApi;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;

import com.exxbrain.android.biometric.BiometricManager;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.UnrecoverableEntryException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Locale;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;


public class Fingerprint extends CordovaPlugin {

  private static final String TAG = "Fingerprint";
  private CallbackContext mCallbackContext = null;

  private static final int REQUEST_CODE_BIOMETRIC = 1;
  private PromptInfo.Builder mPromptInfoBuilder;

  // from keychain touch id plugin
  private boolean setUserAuthenticationRequired = false;
  private static final String SHARED_PREFS_NAME = "FingerSPref";
  private static final String ANDROID_KEY_STORE = "AndroidKeyStore";

  // Alias for our key in the Android key store
  public KeyStore keyStore;
  private final static String CLIENT_ID = "CordovaTouchPlugin";
  private KeyGenerator keyGenerator;
  private Cipher cipher;

  // Used to encrypt token
  private String keyID;
  private String toEncrypt;
  private int currentMode;
  private String langCode = "DK";
  private KeyguardManager keyguardManager;

  public void initialize(CordovaInterface cordova, CordovaWebView webView) {
    super.initialize(cordova, webView);
    Log.v(TAG, "Init Fingerprint");
    Log.v(TAG, "Testing");
    mPromptInfoBuilder = new PromptInfo.Builder(cordova.getActivity());

    if (android.os.Build.VERSION.SDK_INT < 23) {
      return;
    }

    keyguardManager = cordova.getActivity().getSystemService(KeyguardManager.class);

    try {
      keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);
      keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Failed to get an instance of KeyGenerator", e);
    } catch (NoSuchProviderException e) {
      throw new RuntimeException("Failed to get an instance of KeyGenerator", e);
    } catch (KeyStoreException e) {
      throw new RuntimeException("Failed to get an instance of KeyStore", e);
    }

    try {
      cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES
        + "/"
        + KeyProperties.BLOCK_MODE_CBC
        + "/"
        + KeyProperties.ENCRYPTION_PADDING_PKCS7);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Failed to get an instance of Cipher", e);
    } catch (NoSuchPaddingException e) {
      throw new RuntimeException("Failed to get an instance of Cipher", e);
    }
  }

  public boolean execute(final String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

    this.mCallbackContext = callbackContext;
    Log.v(TAG, "Fingerprint action: " + action);

    if (action.equals("isAvailable")) {
      executeIsAvailable();
      return true;
    } else if (action.equals("save")) {
      executeSave(args);
      return true;
    } else if (action.equals("delete")) {
      executeDelete(args);
      return true;
    } else if (action.equals("verify")) {
      executeVerify(args);
      return true;
    } else if (action.equals("has")) {
      executeHas(args);
      return true;
    } else if (action.equals("setLocale")) {
      executeSetLocale(args);
      return true;
    } else if (action.equals("move")) {
      executeMove(args);
      return true;
    }

    return false;
  }

  private void executeMove(JSONArray args) throws JSONException {
    String key = args.getString(0);
    String oldActivityPackageName = args.getString(1);

    SharedPreferences oldSharedPref = cordova.getActivity().getApplicationContext().getSharedPreferences(oldActivityPackageName,Context.MODE_PRIVATE);
    String enc = oldSharedPref.getString("fing" + key, "");

    if (!enc.equals("")) {
      SharedPreferences newSharedPref = cordova.getActivity().getApplicationContext().getSharedPreferences(SHARED_PREFS_NAME,Context.MODE_PRIVATE);
      SharedPreferences.Editor newEditor = newSharedPref.edit();
      newEditor.putString("fing" + key, oldSharedPref.getString("fing" + key, ""));
      newEditor.putString("fing_iv" + key, oldSharedPref.getString("fing_iv" + key, ""));
      newEditor.commit();

      SharedPreferences.Editor oldEditor = oldSharedPref.edit();
      oldEditor.remove("fing" + key);
      oldEditor.remove("fing_iv" + key);
      oldEditor.commit();
    }

    sendSuccess("ok boomer");
  }

  private void executeDelete(JSONArray args) throws JSONException {
    String key = args.getString(0);

    SharedPreferences sharedPref = cordova.getActivity().getApplicationContext().getSharedPreferences(SHARED_PREFS_NAME,Context.MODE_PRIVATE);
    SharedPreferences.Editor editor = sharedPref.edit();
    editor.remove("fing" + key);
    editor.remove("fing_iv" + key);
    boolean removed = editor.commit();

    if (removed) {
      sendSuccess("OK");
    } else {
      sendError(0,"Error");
    }
  }

  private void executeHas(JSONArray args) throws JSONException {
    String key = args.getString(0);

    SharedPreferences sharedPref = cordova.getActivity().getApplicationContext().getSharedPreferences(SHARED_PREFS_NAME,Context.MODE_PRIVATE);
    String enc = sharedPref.getString("fing" + key, "");

    if (!enc.equals("")) {
      sendSuccess("OK");
    } else {
      sendError(0,"Error");
    }
  }
  
  private void executeSetLocale(JSONArray args) throws JSONException {
    langCode = args.getString(0);
    
    Resources resources = cordova.getActivity().getResources();
    
    DisplayMetrics displayMetrics = resources.getDisplayMetrics();
    Configuration configuration = resources.getConfiguration();
    
    configuration.setLocale(new Locale(langCode));

    resources.updateConfiguration(configuration, displayMetrics);
  }

  private void executeVerify(JSONArray args) throws JSONException {
    final String key = args.getString(0);
    final String message = args.getString(1);


    PluginError error = canAuthenticate();

    if (error == null) {
      SecretKey secretKey = getSecretKey();

      if(secretKey != null){
        keyID = key;
        showFingerprint(Cipher.DECRYPT_MODE, args);
      } else {
        sendError(0, "No secret key");
      }
    } else {
      sendError(error);
      return;
    }
  }

  private void executeIsAvailable() {
    PluginError error = canAuthenticate();
    if (error != null) {
      sendError(error);
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      sendSuccess("biometric");
    } else {
      sendSuccess("finger");
    }
  }

  private void executeSave(JSONArray args) throws JSONException {
    final String key = args.getString(0);
    final String password = args.getString(1);
    setUserAuthenticationRequired = args.get(2).equals(null) || args.getBoolean(2);

    PluginError error = canAuthenticate();

    if (error == null) {
      SecretKey secretKey = getSecretKey();

      if (secretKey == null) {
        if (createKey(setUserAuthenticationRequired)) {
          getSecretKey();
        }
      }

      keyID = key;
      toEncrypt = password;

      if (setUserAuthenticationRequired) {
        //Show fingerprint
        showFingerprint(Cipher.ENCRYPT_MODE, args);
      }

    } else {
      sendError(error);
      return;
    }

  }

  private void showFingerprint(int mode, JSONArray args) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      currentMode = mode;
      if (initCipher(currentMode, cordova)) {
        cordova.getActivity().runOnUiThread(() -> {
          mPromptInfoBuilder.parseArgs(args);
          Intent intent = new Intent(cordova.getActivity().getApplicationContext(), BiometricActivity.class);
          intent.putExtras(mPromptInfoBuilder.build().getBundle());
          this.cordova.startActivityForResult(this, intent, REQUEST_CODE_BIOMETRIC);
        });
        PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
        pluginResult.setKeepCallback(true);
        this.mCallbackContext.sendPluginResult(pluginResult);
      }
    }
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent intent) {
    super.onActivityResult(requestCode, resultCode, intent);
    if (requestCode != REQUEST_CODE_BIOMETRIC) {
      return;
    }

    String result = "";
    String errorMessage = "";
    if (resultCode == Activity.RESULT_OK) {
      SharedPreferences sharedPref = cordova.getActivity().getApplicationContext().getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
      try {
        if (currentMode == Cipher.DECRYPT_MODE) {

          byte[] enc = Base64.decode(sharedPref.getString("fing" + keyID, ""), Base64.DEFAULT);

          byte[] decrypted = new byte[0];

          decrypted = cipher.doFinal(enc);

          result = new String(decrypted);
        } else if (currentMode == Cipher.ENCRYPT_MODE && setUserAuthenticationRequired) {
          //If setUserAuthenticationRequired encript string with key after authenticate with fingerprint
          SharedPreferences.Editor editor = sharedPref.edit();

          byte[] enc = cipher.doFinal(toEncrypt.getBytes());
          editor.putString("fing" + keyID, Base64.encodeToString(enc, Base64.DEFAULT));
          editor.putString("fing_iv" + keyID,
            Base64.encodeToString(cipher.getIV(), Base64.DEFAULT));

          editor.commit();
          toEncrypt = "";
          result = "success";
        }
      } catch (BadPaddingException e) {
        e.printStackTrace();
      } catch (IllegalBlockSizeException e) {
        e.printStackTrace();
      }

      if (!result.equals("")) {
        sendSuccess("biometric_success");
      } else {
        sendError(0, errorMessage); //TODO: RIGTIG ERROR CODE
      }
    } else if (intent != null) {
      Bundle extras = intent.getExtras();
      sendError(extras.getInt("code"), extras.getString("message"));
    } else {
      sendError(PluginError.BIOMETRIC_DISMISSED);
    }
  }

  private void executeAuthenticate(JSONArray args) {
    PluginError error = canAuthenticate();
    if (error != null) {
      sendError(error);
      return;
    }
    cordova.getActivity().runOnUiThread(() -> {
      mPromptInfoBuilder.parseArgs(args);
      Intent intent = new Intent(cordova.getActivity().getApplicationContext(), BiometricActivity.class);
      intent.putExtras(mPromptInfoBuilder.build().getBundle());
      this.cordova.startActivityForResult(this, intent, REQUEST_CODE_BIOMETRIC);
    });
    PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
    pluginResult.setKeepCallback(true);
    this.mCallbackContext.sendPluginResult(pluginResult);
  }

  private PluginError canAuthenticate() {
    int error = BiometricManager.from(cordova.getContext()).canAuthenticate();
    switch (error) {
      case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
      case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
        return PluginError.BIOMETRIC_HARDWARE_NOT_SUPPORTED;
      case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
        return PluginError.BIOMETRIC_NOT_ENROLLED;
      default:
        return null;
    }
  }

  private SecretKey getSecretKey() {
    String errorMessage = "";
    String getSecretKeyExceptionErrorPrefix = "Failed to get SecretKey from KeyStore: ";
    SecretKey key = null;
    try {
      keyStore.load(null);
      key = (SecretKey) keyStore.getKey(CLIENT_ID, null);
    } catch (KeyStoreException e) {
      errorMessage = getSecretKeyExceptionErrorPrefix + "KeyStoreException";
    } catch (CertificateException e) {
      errorMessage = getSecretKeyExceptionErrorPrefix + "CertificateException";
    } catch (UnrecoverableKeyException e) {
      errorMessage = getSecretKeyExceptionErrorPrefix + "UnrecoverableKeyException";
    } catch (IOException e) {
      errorMessage = getSecretKeyExceptionErrorPrefix + "IOException";
    } catch (NoSuchAlgorithmException e) {
      errorMessage = getSecretKeyExceptionErrorPrefix + "NoSuchAlgorithmException";
    } catch (UnrecoverableEntryException e) {
      errorMessage = getSecretKeyExceptionErrorPrefix + "UnrecoverableEntryException";
    }
    if (key == null) {
      Log.e(TAG, errorMessage);
    }
    return key;
  }

  public boolean createKey(final boolean setUserAuthenticationRequired) {
    String errorMessage = "";
    String createKeyExceptionErrorPrefix = "Failed to create key: ";
    boolean isKeyCreated = false;
    // The enrolling flow for fingerprint. This is where you ask the user to set up fingerprint
    // for your flow. Use of keys is necessary if you need to know if the set of
    // enrolled fingerprints has changed.
    try {
      keyStore.load(null);
      // Set the alias of the entry in Android KeyStore where the key will appear
      // and the constrains (purposes) in the constructor of the Builder
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        keyGenerator.init(new KeyGenParameterSpec.Builder(CLIENT_ID,
          KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT).setBlockModes(
          KeyProperties.BLOCK_MODE_CBC)
          .setUserAuthenticationRequired(setUserAuthenticationRequired)
          .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
          .build());
      }
      keyGenerator.generateKey();
      isKeyCreated = true;
    } catch (NoSuchAlgorithmException e) {
      errorMessage = createKeyExceptionErrorPrefix + "NoSuchAlgorithmException";
    } catch (InvalidAlgorithmParameterException e) {
      errorMessage = createKeyExceptionErrorPrefix + "InvalidAlgorithmParameterException";
    } catch (CertificateException e) {
      errorMessage = createKeyExceptionErrorPrefix + "CertificateException";
    } catch (IOException e) {
      errorMessage = createKeyExceptionErrorPrefix + "IOException";
    }
    if (!isKeyCreated) {
      Log.e(TAG, errorMessage);
      //TODO: Få en rigtige error code
      sendError(0, errorMessage);
    }
    return isKeyCreated;
  }

  @RequiresApi(api = Build.VERSION_CODES.M)
  private boolean initCipher(int mode, CordovaInterface cordova) {
    boolean initCipher = false;
    String errorMessage = "";
    String initCipherExceptionErrorPrefix = "Failed to init Cipher: ";
    try {
      SecretKey key = getSecretKey();

      if (mode == Cipher.ENCRYPT_MODE) {
        SecureRandom r = new SecureRandom();
        byte[] ivBytes = new byte[16];
        r.nextBytes(ivBytes);

        cipher.init(mode, key);
      } else {
        SharedPreferences sharedPref = cordova.getActivity().getApplicationContext().getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
        byte[] ivBytes =
          Base64.decode(sharedPref.getString("fing_iv" + keyID, ""), Base64.DEFAULT);

        cipher.init(mode, key, new IvParameterSpec(ivBytes));
      }

      initCipher = true;
    } catch (KeyPermanentlyInvalidatedException e) {
      try {
        keyStore.deleteEntry(CLIENT_ID);
        Log.i(TAG, "Permanently invalidated key was removed.");
      } catch (KeyStoreException error) {
        Log.e(TAG, error.getMessage());
      }
      errorMessage = "KeyPermanentlyInvalidatedException";
      sendError(0, errorMessage); //TODO: Få rigtige error code
    } catch (InvalidKeyException e) {
      errorMessage = initCipherExceptionErrorPrefix + "InvalidKeyException";
    } catch (InvalidAlgorithmParameterException e) {
      errorMessage = initCipherExceptionErrorPrefix + "InvalidAlgorithmParameterException";
      e.printStackTrace();
    }
    if (!initCipher) {
      Log.e(TAG, errorMessage);
    }
    return initCipher;
  }

  private void sendError(int code, String message) {
    JSONObject resultJson = new JSONObject();
    try {
      resultJson.put("code", code);
      resultJson.put("message", message);

      PluginResult result = new PluginResult(PluginResult.Status.ERROR, resultJson);
      result.setKeepCallback(true);
      cordova.getActivity().runOnUiThread(() ->
        Fingerprint.this.mCallbackContext.sendPluginResult(result));
    } catch (JSONException e) {
      Log.e(TAG, e.getMessage(), e);
    }
  }

  private void sendError(PluginError error) {
    sendError(error.getValue(), error.getMessage());
  }

  private void sendSuccess(String message) {
    Log.e(TAG, message);
    cordova.getActivity().runOnUiThread(() ->
      this.mCallbackContext.success(message));
  }
}
