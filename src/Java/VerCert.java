package Java;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class VerCert {
	//사용자가 입력한 비밀번호의 유효성을 검사합니다.
	
	String SUBJECT_DN;
	String OUTPUT_FILENAME;
	String PATH;
	
	public VerCert(String CN, String Birth) {
		OUTPUT_FILENAME = Settings.getOutputFileName();
		SUBJECT_DN = Settings.getSubjectDN(CN, Birth);
		PATH = Settings.getPath();
	}
	
	public boolean CheckPassword(String PW) throws Exception {
		/* ASN.1(Der) 헤더구조
		 * 0~35 : Header (20~27 : Customized Salt, 30~31 : pbkdf1 Iteration)
		 * 36 ~ : Encoded Private Key(RSA encoded SEED CBC Mode)
		 * SEED Ref. : https://www.rootca.or.kr/kcac/down/TechSpec/2.3-KCAC.TS.ENC.pdf
		 */
		
		try {
			byte[] encodedKey = null; 
			FileInputStream fis = null; 
			ByteArrayOutputStream bos = null; 
			try { 
				fis = new FileInputStream(new File(PATH + SUBJECT_DN + "/" + OUTPUT_FILENAME + ".key")); 
				bos = new ByteArrayOutputStream(); 
				byte[] buffer = new byte[1024]; 
				int read = -1; 
				while ((read = fis.read(buffer)) != -1) { 
					bos.write(buffer, 0, read); 
				} 
				encodedKey = bos.toByteArray(); 
				} finally { 
					if (bos != null) 
					try {bos.close();} 
					catch(IOException ie) {} 
					if (fis != null) 
						try {fis.close();} catch(IOException ie) {} 
				} 
			//Extract salt, Iteration from Header
			byte[] salt = new byte[8];
			System.arraycopy(encodedKey, 20, salt, 0, 8);
			byte[] bIteration = new byte[4];
			System.arraycopy(encodedKey, 30, bIteration, 2, 2);
			int iIteration = ByteUtils.toInt(bIteration);
			
			//pbkdf1 Generate(0~15 : Key for Encoding, SHA1(16~19) : pre-Initial Vector)
			byte[] dk = pbkdf1(PW, salt, iIteration); 
			byte[] keyData = new byte[16]; 
			System.arraycopy(dk, 0, keyData, 0, 16); 
			byte[] div = new byte[20]; 
			byte[] tmp4Bytes = new byte[4]; 
			System.arraycopy(dk, 16, tmp4Bytes, 0, 4); 
			div = SHA1Utils.getHash(tmp4Bytes); 
			
			//Initial Vector(0~15 Of pre-Initial Vector)
			byte[] iv = new byte[16]; 
			System.arraycopy(div, 0, iv, 0, 16); 
			
			//Set SEED Padding into a Generated Private Key using Cipher and BouncyCastle Library
			Cipher cp = Cipher.getInstance("SEED/CBC/PKCS5Padding", new BouncyCastleProvider());
			Key key = new SecretKeySpec(keyData, "SEED");
			
			//Decrypt Private Key(36 ~)
			cp.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
			byte[] dstEnc = new byte[encodedKey.length - 36];
			System.arraycopy(encodedKey, 36, dstEnc, 0, dstEnc.length);
			byte[] oReturn = cp.update(dstEnc);
			
			//If the result of a decrypted key doesn't have Der signature, Return Invalid Password. 
			if(oReturn[0] != (byte)0x30 || oReturn[1] != (byte)0x82) {
				return false;
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
			return true;
	}
	
	private static byte[] pbkdf1(String password, byte[] salt, int iterationCount) throws NoSuchAlgorithmException { 
		byte[] dk = new byte[20]; 
		MessageDigest md = MessageDigest.getInstance("SHA1"); 
		md.update(password.getBytes()); 
		md.update(salt); 
		dk = md.digest(); 
		for (int i = 1; i < iterationCount; i++) { 
			dk = md.digest(dk); 
		} 
		return dk; 
	}
	
	public byte[] doPadding(byte[] input) {
		//SEED Padding을 수동으로 설정합니다. 
		byte[] pad;
		int len = input.length;
		if(len % 16 == 0) {
			pad = new byte[16];
			for(int x = 0; x < 16; x++) {
				pad[x] = (byte)(10);
			}
		} else {
			pad = new byte[16 - (len % 16)];
			for(int x = 0; x < pad.length; x++) {
				pad[x] = (byte)(pad.length);
			}
		}
		byte[] oReturn = new byte[pad.length + len];
		System.arraycopy(input, 0, oReturn, 0, input.length);
		System.arraycopy(pad, 0, oReturn, input.length, pad.length);
		
		return oReturn;
	}
	
	public byte[] sha1(byte[] input) {
	    byte[] sha1 = null;
	    try {
	        MessageDigest msdDigest = MessageDigest.getInstance("SHA-1");
	        msdDigest.update(input, 0, input.length);
	        sha1 = msdDigest.digest();
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
	    return sha1;
	}
}
