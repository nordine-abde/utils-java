package com.encdec;


import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Base64;

public class EncDecAes {

    public static void main(String[] args) {
	    //args
        //1 Keystore path
        //2 Keystore password
        //3 Key Alias
        //4 enc/dec mode (1 or 2) one for encrypt 2 for decrypt
        //5 plaintext or ciphertext
        //6 Key Passord (Not mandatory)

        if(args.length !=5 && args.length !=6){
            throw new RuntimeException("invalid args number: "+ args.length);
        }

        String keystorePath= args[0];
        String keystorePassword= args[1];
        String keyAlias= args[2];

        int encdecMode;
        try {
            encdecMode= Integer.parseInt(args[3]);
        }catch (Exception e){
            throw new RuntimeException("Enc/dec mode must be 1 or two");
        }

        String inputText= args[4];
        String keyPassword;
        if(args.length ==6){
            keyPassword= args[5];
        }else{
            keyPassword= keystorePassword;
        }

        KeyStore keyStore;
        try {
            keyStore= loadKeystore("JCEKS", keystorePath, keystorePassword);
        }catch (Exception e){
            throw new RuntimeException("Cannot open keystore");
        }
        SecretKey secretKey;
        try {
            secretKey= (SecretKey) keyStore.getKey(keyAlias, keyPassword.toCharArray());
        }catch (Exception e){
            throw new RuntimeException("failed to get secretKey");
        }

        System.out.println(encDecCommon(encdecMode, inputText, secretKey, "AES/ECB/PKCS5Padding"));
    }

    private static KeyStore loadKeystore(String keyStoreType, String keyStorePath, String keyStorePassword)
            throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException {
        KeyStore keyStore = KeyStore.getInstance(keyStoreType);
        InputStream is = new FileInputStream(keyStorePath);
        keyStore.load(is, keyStorePassword.toCharArray());
        return keyStore;

    }

    public static String encDecCommon(int mode, String value, SecretKey secretKey, String algorithm) {
        if (value == null) {
            return null;
        }
        try {
            final Cipher c = Cipher.getInstance(algorithm);
            c.init(mode, secretKey);
            if (mode == Cipher.DECRYPT_MODE) {
                return new String(c.doFinal(Base64.getDecoder().decode(value.getBytes(StandardCharsets.UTF_8))));
            } else {
                return new String(Base64.getEncoder().encode(c.doFinal(value.getBytes())), StandardCharsets.UTF_8);
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
