/*
 * Copyright (C) 2018 GK Spencer
 *
 * JFileServer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JFileServer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with JFileServer. If not, see <http://www.gnu.org/licenses/>.
 */

package org.filesys.smb.server;

/**
 * Session Key Types Class
 *
 * @author gkspencer
 */
public class KeyType {

    // Session key names
    public static final String SessionKey = "SessionKey";
    public static final String SigningKey = "SigningKey";
    public static final String KeyExchangeKey = "KeyExchangeKey";
    public static final String NTLMServerSigningKey = "NTLMServerSigningKey";
    public static final String NTLMClientSigningKey = "NTLMClientSigningKey";
    public static final String NTLMServerSealingKey = "NTLMServerSealingKey";
    public static final String NTLMClientSealingKey = "NTLMClientSealingKey";
    public static final String NTLMServerRC4Context = "NTLMServerRC4Context";
    public static final String NTLMClientRC4Context = "NTLMClientRC4Context";
}
