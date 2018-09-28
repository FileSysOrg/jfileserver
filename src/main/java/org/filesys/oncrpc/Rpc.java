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

package org.filesys.oncrpc;

/**
 * ONC/RPC Constants Class
 *
 * @author gkspencer
 */
public class Rpc {

    //	RPC length/flags
    public static final int LastFragment    = 0x80000000;
    public static final int LengthMask      = 0x7FFFFFFF;

    //	RPC message types
    public static final int Call            = 0;
    public static final int Reply           = 1;

    //	Call status
    public static final int CallAccepted    = 0;
    public static final int CallDenied      = 1;

    //	Required RPC version
    public static final int RpcVersion      = 2;

    //	Call accepted status codes
    public static final int StsSuccess      = 0;    //	RPC executed successfully
    public static final int StsProgUnavail  = 1;    //	program not available
    public static final int StsProgMismatch = 2;    //	program version mismatch
    public static final int StsProcUnavail  = 3;    //	program does not support procedure
    public static final int StsBadArgs      = 4;    //	bad arguments in request

    //	Call rejected status codes
    public static final int StsRpcMismatch  = 0;    //	RPC version number does not equal 2
    public static final int StsAuthError    = 1;    //	authentication error

    //	Authentication failure status codes
    public static final int AuthBadCred     = 1;    //	bad credentials
    public static final int AuthRejectCred  = 2;    //	client must begin new session
    public static final int AuthBadVerf     = 3;    //	bad verifier
    public static final int AuthRejectedVerf = 4;    //	verifier rejected or replayed
    public static final int AuthTooWeak     = 5;    //	rejected for security reasons

    //	True/false values
    public static final int True    = 1;
    public static final int False   = 0;

    //	Protocol ids
    public static final int TCP     = 6;
    public static final int UDP     = 17;

    /**
     * Return a program id as a service name
     *
     * @param progId int
     * @return String
     */
    public final static String getServiceName(int progId) {
        String svcName = null;

        switch (progId) {
            case 100005:
                svcName = "Mount";
                break;
            case 100003:
                svcName = "NFS";
                break;
            case 100000:
                svcName = "Portmap";
                break;
            default:
                svcName = "" + progId;
                break;
        }

        return svcName;
    }
}
