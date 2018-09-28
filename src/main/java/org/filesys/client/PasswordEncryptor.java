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

import java.security.*;

/**
 * Password Encryptor Abstract Class
 * 
 * <p>Generates LanMan and NTLMv1 encrypted passwords from the plain text password and challenge key.
 * 
 * @author gkspencer
 */
public abstract class PasswordEncryptor {

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
	public PasswordEncryptor() {
	}

	/**
	 * Check if the required algorithms are available
	 * 
	 * @return boolean
	 */
	public boolean checkEncryptionAlgorithms() {
		return true;
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
	public abstract byte[] generateEncryptedPassword(String plainPwd, byte[] encryptKey, int alg)
		throws NoSuchAlgorithmException;

	/**
	 * Generate a session key using the specified password and key.
	 * 
	 * @param plainPwd Plaintext password string
	 * @param encryptKey byte[] Encryption key
	 * @param alg int Encryption algorithm
	 * @return byte[] Encrypted password
	 * @exception NoSuchAlgorithmException If a required encryption algorithm is not available
	 */
	public abstract byte[] generateSessionKey(String plainPwd, byte[] encryptKey, int alg)
		throws NoSuchAlgorithmException;

	/**
	 * P16 encryption
	 * 
	 * @param pwd String
	 * @param s8 byte[]
	 * @return byte[]
	 * @exception NoSuchAlgorithmException If a required encryption algorithm is not available
	 */
	public abstract byte[] P16(String pwd, byte[] s8)
		throws NoSuchAlgorithmException;

	/**
	 * P24 DES encryption
	 * 
	 * @param p21 Plain password or hashed password bytes
	 * @param ch Challenge bytes
	 * @return Encrypted password
	 * @exception NoSuchAlgorithmException If a required encryption algorithm is not available
	 */
	protected abstract byte[] P24(byte[] p21, byte[] ch)
		throws NoSuchAlgorithmException;

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
}
