/*
 * Copyright (C) 2006-2010 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */

package org.filesys.client;

import java.io.*;
import java.security.*;

import javax.crypto.*;
import javax.crypto.spec.*;

/**
 * JCE Password Encryptor Class
 * 
 * <p>Provides password encryption for SMB using the JCE framework.
 * 
 * @author gkspencer
 */
public class JCEPasswordEncryptor extends PasswordEncryptor {

	//	Encryption algorithm types
	public static final int LANMAN		= 0;
	public static final int NTLM1		= 1;
	public static final int NTLM2		= 2;
	public static final int MD4			= 3;

	//	Encrpytion algorithm names
	private final static String[] _algNames = {"LanMan", "NTLMv1", "NTLMv2", "MD4" };
	
	/**
	 * Default constructor
	 */
	public JCEPasswordEncryptor() {
	}

	/**
	 * Check if the required algorithms are available
	 * 
	 * @return boolean
	 */
	public boolean checkEncryptionAlgorithms() {

		boolean algOK = false;

		try {

			// Check if MD4 is available
			MessageDigest.getInstance("MD4");

			// Check if DES is available
			Cipher.getInstance("DES");
		}
		catch (NoSuchAlgorithmException ex) {
		}
		catch (NoSuchPaddingException ex) {
		}

		// Return the encryption algorithm status
		return algOK;
	}

	/**
	 * Encrypt the plain text password with the specified encryption key using the specified
	 * encryption algorithm.
	 * 
	 * @param plainPwd Plaintext password string
	 * @param encryptKey byte[] Encryption key
	 * @param alg int Encryption algorithm
	 * @return byte[] Encrypted password
	 * @exception NoSuchAlgorithmException If a required encryption algorithm is not available
	 */
	public byte[] generateEncryptedPassword(String plainPwd, byte[] encryptKey, int alg)
		throws NoSuchAlgorithmException {

		// Get the password
		String pwd = plainPwd;
		if ( pwd == null)
			pwd = "";

		// Determine the encryption algorithm
		byte[] encPwd = null;
		MessageDigest md4 = null;
		int len = 0;
		byte[] pwdBytes = null;

		switch (alg) {

			// LanMan DES encryption
			case LANMAN:
				encPwd = P24(pwd, encryptKey);
				break;

			// NTLM v1 encryption
			case NTLM1:

				// Create the MD4 hash
				md4 = MessageDigest.getInstance("MD4");

				try {
					pwdBytes = pwd.getBytes("UnicodeLittleUnmarked");
				}
				catch (UnsupportedEncodingException ex) {
				}

				md4.update(pwdBytes);
				byte[] p21 = new byte[21];
				System.arraycopy(md4.digest(), 0, p21, 0, 16);

				// Now use the LM encryption
				encPwd = P24(p21, encryptKey);
				break;

			// NTLM v2 encryption
			case NTLM2:
				break;

			// MD4 encryption
			case MD4:

				// Create the MD4 hash
				md4 = MessageDigest.getInstance("MD4");
				len = pwd.length();
				pwdBytes = new byte[len * 2];

				for (int i = 0; i < len; i++) {
					char ch = pwd.charAt(i);
					pwdBytes[i * 2] = (byte) ch;
					pwdBytes[i * 2 + 1] = (byte) ((ch >> 8) & 0xFF);
				}

				md4.update(pwdBytes);
				encPwd = new byte[16];
				System.arraycopy(md4.digest(), 0, encPwd, 0, 16);
				break;
		}

		// Return the encrypted password
		return encPwd;
	}

	/**
	 * Generate a session key using the specified password and key.
	 * 
	 * @param plainPwd Plaintext password string
	 * @param encryptKey byte[] Encryption key
	 * @param alg int Encryption algorithm
	 * @return byte[] Encrypted password
	 * @exception NoSuchAlgorithmException If a required encryption algorithm is not available
	 */
	public byte[] generateSessionKey(String plainPwd, byte[] encryptKey, int alg)
		throws NoSuchAlgorithmException {

		// Create the session key for the specified algorithm
		byte[] sessKey = null;
		MessageDigest md4 = null;

		String pwd = plainPwd;
		if ( pwd == null)
			pwd = "";

		switch (alg) {

			// NTLM session key
			case NTLM1:

				// Get the password bytes
				byte[] pwdBytes = new byte[pwd.length() * 2];

				for (int i = 0; i < pwd.length(); i++) {
					char ch = plainPwd.charAt(i);
					pwdBytes[i * 2] = (byte) ch;
					pwdBytes[i * 2 + 1] = (byte) ((ch >> 8) & 0xFF);
				}

				// Create the MD4 hash
				md4 = MessageDigest.getInstance("MD4");
				md4.update(pwdBytes);
				md4.update(md4.digest());
				sessKey = new byte[40];
				System.arraycopy(md4.digest(), 0, sessKey, 0, 16);

				// Second part of the session key contains the NTLM hashed password
				byte[] ntlmHash = generateEncryptedPassword(plainPwd, encryptKey, NTLM1);
				System.arraycopy(ntlmHash, 0, sessKey, 16, 24);
				break;
		}

		// Return the session key
		return sessKey;
	}

	/**
	 * P16 encryption
	 * 
	 * @param pwd java.lang.String
	 * @param s8 byte[]
	 * @return byte[]
	 * @exception NoSuchAlgorithmException If a required encryption algorithm is not available
	 */
	public final byte[] P16(String pwd, byte[] s8)
		throws NoSuchAlgorithmException {

		// Make a 14 byte string using the password string. Truncate the
		// password or pad with nulls to 14 characters.
		StringBuffer p14str = new StringBuffer();
		p14str.append(pwd.toUpperCase());
		if ( p14str.length() > 14)
			p14str.setLength(14);

		while (p14str.length() < 14)
			p14str.append((char) 0x00);

		// Convert the P14 string to an array of bytes. Allocate the return 16 byte array.
		byte[] p14 = p14str.toString().getBytes();
		byte[] p16 = new byte[16];

		try {

			// DES encrypt the password bytes using the challenge key
			Cipher des = Cipher.getInstance("DES");

			// Set the encryption seed using the first 7 bytes of the password string.
			// Generate the first 8 bytes of the return value.
			byte[] key = generateKey(p14, 0);

			SecretKeySpec chKey = new SecretKeySpec(key, 0, key.length, "DES");
			des.init(Cipher.ENCRYPT_MODE, chKey);
			byte[] res = des.doFinal(s8);
			System.arraycopy(res, 0, p16, 0, 8);

			// Encrypt the second block
			key = generateKey(p14, 7);

			chKey = new SecretKeySpec(key, 0, key.length, "DES");
			des.init(Cipher.ENCRYPT_MODE, chKey);
			res = des.doFinal(s8);
			System.arraycopy(res, 0, p16, 8, 8);
		}
		catch (NoSuchPaddingException ex) {
			p16 = null;
		}
		catch (IllegalBlockSizeException ex) {
			p16 = null;
		}
		catch (BadPaddingException ex) {
			p16 = null;
		}
		catch (InvalidKeyException ex) {
			p16 = null;
		}

		// Return the 16 byte encrypted value
		return p16;
	}

	/**
	 * P24 DES encryption
	 * 
	 * @param pwd java.lang.String
	 * @param c8 byte[]
	 * @return byte[]
	 * @exception NoSuchAlgorithmException If a required encryption algorithm is not available
	 */
	private final byte[] P24(String pwd, byte[] c8)
		throws NoSuchAlgorithmException {

		// Generate the 16 byte encrypted value using the password string and well known value.
		byte[] s8 = "KGS!@#$%".getBytes();
		byte[] p16 = P16(pwd, s8);

		// Generate the 24 byte encrypted value
		return P24(p16, c8);
	}

	/**
	 * P24 DES encryption
	 * 
	 * @param p21 Plain password or hashed password bytes
	 * @param ch Challenge bytes
	 * @return Encrypted password
	 * @exception NoSuchAlgorithmException If a required encryption algorithm is not available
	 */
	protected byte[] P24(byte[] p21, byte[] ch)
		throws NoSuchAlgorithmException {

		byte[] enc = null;

		try {

			// DES encrypt the password bytes using the challenge key
			Cipher des = Cipher.getInstance("DES");

			// Allocate the output bytes
			enc = new byte[24];

			// Encrypt the first block
			byte[] key = generateKey(p21, 0);

			SecretKeySpec chKey = new SecretKeySpec(key, 0, key.length, "DES");
			des.init(Cipher.ENCRYPT_MODE, chKey);
			byte[] res = des.doFinal(ch);
			System.arraycopy(res, 0, enc, 0, 8);

			// Encrypt the second block
			key = generateKey(p21, 7);

			chKey = new SecretKeySpec(key, 0, key.length, "DES");
			des.init(Cipher.ENCRYPT_MODE, chKey);
			res = des.doFinal(ch);
			System.arraycopy(res, 0, enc, 8, 8);

			// Encrypt the last block
			key = generateKey(p21, 14);

			chKey = new SecretKeySpec(key, 0, key.length, "DES");
			des.init(Cipher.ENCRYPT_MODE, chKey);
			res = des.doFinal(ch);
			System.arraycopy(res, 0, enc, 16, 8);
		}
		catch (NoSuchPaddingException ex) {
			ex.printStackTrace();
			enc = null;
		}
		catch (IllegalBlockSizeException ex) {
			ex.printStackTrace();
			enc = null;
		}
		catch (BadPaddingException ex) {
			ex.printStackTrace();
			enc = null;
		}
		catch (InvalidKeyException ex) {
			ex.printStackTrace();
			enc = null;
		}

		// Return the encrypted password, or null if an error occurred
		return enc;
	}

	/**
	 * Return the encryption algorithm as a string
	 * 
	 * @param alg int
	 * @return String
	 */
	public static String getAlgorithmName(int alg) {
		if ( alg >= 0 && alg < _algNames.length)
			return _algNames[alg];
		return "Unknown";
	}

	/**
	 * Make a 7-byte string into a 64 bit/8 byte/longword key.
	 * 
	 * @param byt byte[]
	 * @param off int
	 * @return byte[]
	 */
	private byte[] generateKey(byte[] byt, int off) {

		// Allocate the key
		byte[] key = new byte[8];

		// Make a key from the input string
		key[0] = (byte) (byt[off + 0] >> 1);
		key[1] = (byte) (((byt[off + 0] & 0x01) << 6) | ((byt[off + 1] & 0xFF) >> 2));
		key[2] = (byte) (((byt[off + 1] & 0x03) << 5) | ((byt[off + 2] & 0xFF) >> 3));
		key[3] = (byte) (((byt[off + 2] & 0x07) << 4) | ((byt[off + 3] & 0xFF) >> 4));
		key[4] = (byte) (((byt[off + 3] & 0x0F) << 3) | ((byt[off + 4] & 0xFF) >> 5));
		key[5] = (byte) (((byt[off + 4] & 0x1F) << 2) | ((byt[off + 5] & 0xFF) >> 6));
		key[6] = (byte) (((byt[off + 5] & 0x3F) << 1) | ((byt[off + 6] & 0xFF) >> 7));
		key[7] = (byte) (byt[off + 6] & 0x7F);

		for (int i = 0; i < 8; i++) {
			key[i] = (byte) (key[i] << 1);
		}

		return key;
	}
}
