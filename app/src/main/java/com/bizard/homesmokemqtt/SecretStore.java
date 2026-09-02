package com.bizard.homesmokemqtt;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Stores MQTT password encrypted by Android Keystore on modern HomeSmoke builds. */
final class SecretStore {
    private static final String ALIAS="HomeSmokeMqttSecretV1";
    private static final String KEY="mqtt_pass_enc_v1";
    private final SharedPreferences prefs;
    SecretStore(Context c){prefs=c.getSharedPreferences("homesmoke_full",Context.MODE_PRIVATE);migratePlain();}

    synchronized void put(String value){
        try{SecretKey k=key();Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,k);byte[] enc=c.doFinal((value==null?"":value).getBytes(StandardCharsets.UTF_8));String packed=Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(enc,Base64.NO_WRAP);prefs.edit().putString(KEY,packed).remove("pass").apply();}
        catch(Exception e){throw new IllegalStateException("Keystore encryption failed",e);}
    }
    synchronized String get(){
        String packed=prefs.getString(KEY,"");if(packed==null||packed.isEmpty())return "";
        try{String[] x=packed.split(":",2);if(x.length!=2)return "";byte[] iv=Base64.decode(x[0],Base64.NO_WRAP),enc=Base64.decode(x[1],Base64.NO_WRAP);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));return new String(c.doFinal(enc),StandardCharsets.UTF_8);}catch(Exception e){return "";}
    }
    private void migratePlain(){String old=prefs.getString("pass","");if(old!=null&&!old.isEmpty()&&prefs.getString(KEY,"").isEmpty())try{put(old);}catch(Exception ignored){}}
    private SecretKey key() throws Exception{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);KeyStore.Entry e=ks.getEntry(ALIAS,null);if(e instanceof KeyStore.SecretKeyEntry)return ((KeyStore.SecretKeyEntry)e).getSecretKey();KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return g.generateKey();}
}
