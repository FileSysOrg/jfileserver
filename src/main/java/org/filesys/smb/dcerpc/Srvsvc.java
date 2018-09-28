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

package org.filesys.smb.dcerpc;

/**
 * Srvsvc Operation Ids Class
 *
 * <p>Contains constants for the DCE/RPC server service requests.
 *
 * @author gkspencer
 */
public class Srvsvc {

    //	Srvsvc opcodes
    public static final int NetrServerGetInfo   = 0x15;
    public static final int NetrServerSetInfo   = 0x16;
    public static final int NetrShareEnum       = 0x0F;
    public static final int NetrShareEnumSticky = 0x24;
    public static final int NetrShareGetInfo    = 0x10;
    public static final int NetrShareSetInfo    = 0x11;
    public static final int NetrShareAdd        = 0x0E;
    public static final int NetrShareDel        = 0x12;
    public static final int NetrSessionEnum     = 0x0C;
    public static final int NetrSessionDel      = 0x0D;
    public static final int NetrConnectionEnum  = 0x08;
    public static final int NetrFileEnum        = 0x09;
    public static final int NetrRemoteTOD       = 0x1C;

    /**
     * Convert an opcode to a function name
     *
     * @param opCode int
     * @return String
     */
    public final static String getOpcodeName(int opCode) {

        String ret = "";
        switch (opCode) {
            case NetrServerGetInfo:
                ret = "NetrServerGetInfo";
                break;
            case NetrServerSetInfo:
                ret = "NetrServerSetInfo";
                break;
            case NetrShareEnum:
                ret = "NetrShareEnum";
                break;
            case NetrShareEnumSticky:
                ret = "NetrShareEnumSticky";
                break;
            case NetrShareGetInfo:
                ret = "NetrShareGetInfo";
                break;
            case NetrShareSetInfo:
                ret = "NetrShareSetInfo";
                break;
            case NetrShareAdd:
                ret = "NetrShareAdd";
                break;
            case NetrShareDel:
                ret = "NetrShareDel";
                break;
            case NetrSessionEnum:
                ret = "NetrSessionEnum";
                break;
            case NetrSessionDel:
                ret = "NetrSessionDel";
                break;
            case NetrConnectionEnum:
                ret = "NetrConnectionEnum";
                break;
            case NetrFileEnum:
                ret = "NetrFileEnum";
                break;
            case NetrRemoteTOD:
                ret = "NetrRemoteTOD";
                break;
        }
        return ret;
    }
}
