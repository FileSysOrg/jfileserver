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

package org.filesys.server.auth.asn;

/**
 * DER Class
 *
 * <p>Contains constants for ASN.1 DER encoding.
 *
 * @author gkspencer
 */
public class DER {

    // Object types
    public static final int Boolean         = 0x01;
    public static final int Integer         = 0x02;
    public static final int BitString       = 0x03;
    public static final int OctetString     = 0x04;
    public static final int Null            = 0x05;
    public static final int ObjectIdentifier= 0x06;
    public static final int External        = 0x08;
    public static final int Enumerated      = 0x0A;
    public static final int UTF8String      = 0x0C;
    public static final int Sequence        = 0x10;
    public static final int NumericString   = 0x12;
    public static final int PrintableString = 0x13;
    public static final int GeneralizedTime = 0x18;
    public static final int GeneralString   = 0x1B;
    public static final int UniversalString = 0x1C;

    // Type mask
    public static final int TypeMask        = 0x1F;

    // Tag class
    public static final int Universal       = 0x00;
    public static final int Application     = 0x40;
    public static final int ContextSpecific = 0x80;
    public static final int Private         = 0xC0;

    public static final int Constructed     = 0x20;
    public static final int Tagged          = 0x80;

    /**
     * Return the type field
     *
     * @param typ int
     * @return int
     */
    public static final int isType(int typ) {
        return typ & TypeMask;
    }

    /**
     * Check if a type is constructed
     *
     * @param typ int
     * @return boolean
     */
    public final static boolean isConstructed(int typ) {
        return (typ & Constructed) != 0 ? true : false;
    }

    /**
     * Check if a type is tagged
     *
     * @param typ int
     * @return boolean
     */
    public final static boolean isTagged(int typ) {
        return (typ & Tagged) != 0 ? true : false;
    }

    /**
     * Check for context specific flag
     *
     * @param typ int
     * @return boolean
     */
    public final static boolean isContextSpecific(int typ) {
        return (typ & ContextSpecific) != 0 ? true : false;
    }

    /**
     * Check for the application flag
     *
     * @param typ int
     * @return boolean
     */
    public final static boolean isApplicationSpecific(int typ) {
        return (typ & Application) != 0 ? true : false;
    }

    /**
     * Return the type as a string
     *
     * @param typ int
     * @return String
     */
    public final static String isTypeString(int typ) {
        String typStr = null;

        switch (typ) {
            case Boolean:
                typStr = "Boolean";
                break;
            case Integer:
                typStr = "Integer";
                break;
            case BitString:
                typStr = "BitString";
                break;
            case OctetString:
                typStr = "OctetString";
                break;
            case Null:
                typStr = "Null";
                break;
            case ObjectIdentifier:
                typStr = "ObjectIdentifier";
                break;
            case External:
                typStr = "External";
                break;
            case Enumerated:
                typStr = "Enumerated";
                break;
            case UTF8String:
                typStr = "UTF8String";
                break;
            case Sequence:
                typStr = "Sequence";
                break;
            case NumericString:
                typStr = "NumericString";
                break;
            case PrintableString:
                typStr = "PrintableString";
                break;
            case GeneralizedTime:
                typStr = "GeneralizedTime";
                break;
            case GeneralString:
                typStr = "GeneralString";
                break;
            case UniversalString:
                typStr = "UniversalString";
                break;
            default:
                typStr = "UnknownType (" + typ + ")";
                break;
        }

        return typStr;
    }
}
