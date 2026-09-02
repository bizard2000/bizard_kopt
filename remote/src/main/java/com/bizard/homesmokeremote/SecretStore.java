package com.bizard.homesmokeremote;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Keystore on API23+, explicit plaintext fallback for Android 5.0/5.1. */
final class SecretStore {
    private static final String ALIAS="HomeSmokeRemoteMqttV1",ENC="mqtt_pass_enc",PLAIN="mqtt_pass_legacy";
    private final SharedPreferences p;
    SecretStore(Context c){p=c.getSharedPreferences("homesmoke_remote",Context.MODE_PRIVATE);migrate();}
    boolean isEncrypted(){return Build.VERSION.SDK_INT>=23;}
    void put(String value){value=value==null?"":value;if(Build.VERSION.SDK_INT<23){p.edit().putString(PLAIN,value).apply();return;}try{Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());String packed=Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(c.doFinal(value.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);p.edit().putString(ENC,packed).remove(PLAIN).remove("pass").apply();}catch(Exception e){throw new IllegalStateException(e);}}
    String get(){if(Build.VERSION.SDK_INT<23)return p.getString(PLAIN,p.getString("pass",""));String packed=p.getString(ENC,"");if(packed.isEmpty())return "";try{String[] x=packed.split(":",2);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(x[0],Base64.NO_WRAP)));return new String(c.doFinal(Base64.decode(x[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);}catch(Exception e){return "";}}
    private void migrate(){String old=p.getString("pass","");if(old!=null&&!old.isEmpty()){try{put(old);}catch(Exception ignored){}}}
    private SecretKey key()throws Exception{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);KeyStore.Entry e=ks.getEntry(ALIAS,null);if(e instanceof KeyStore.SecretKeyEntry)return ((KeyStore.SecretKeyEntry)e).getSecretKey();KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return g.generateKey();}
}
