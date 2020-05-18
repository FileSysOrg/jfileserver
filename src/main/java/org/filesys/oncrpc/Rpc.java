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

import org.filesys.oncrpc.nfs.v3.NFS3;

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
    public enum MessageType {
        Call(0),
        Reply(1),

        Invalid(0xFFFF);

        private final int msgType;

        /**
         * Enum constructor
         *
         * @param typ int
         */
        MessageType(int typ) { msgType = typ; }

        /**
         * Return the message type as an int
         *
         * @return int
         */
        public final int intValue() { return msgType; }

        /**
         * Create a message type from an int
         *
         * @param typ int
         * @return MessageType
         */
        public static final Rpc.MessageType fromInt(int typ) {
            Rpc.MessageType mType = Invalid;

            switch ( typ) {
                case 0:
                    mType = Call;
                    break;
                case 1:
                    mType = Reply;
                    break;
            }

            return mType;
        }
    }

    //	Call status
    public enum CallStatus {
        Accepted(0),
        Denied(1),

        Invalid(0xFFFF);

        private final int callSts;

        /**
         * Enum constructor
         *
         * @param sts int
         */
        CallStatus(int sts) { callSts = sts; }

        /**
         * Return the call status as an int
         *
         * @return int
         */
        public final int intValue() { return callSts; }

        /**
         * Create a call status from an int
         *
         * @param sts int
         * @return CallStatus
         */
        public static final Rpc.CallStatus fromInt(int sts) {
            Rpc.CallStatus cSts = Invalid;

            switch ( sts) {
                case 0:
                    cSts = Accepted;
                    break;
                case 1:
                    cSts = Denied;
                    break;
            }

            return cSts;
        }
    }

    //	Required RPC version
    public static final int RpcVersion      = 2;

    //	Call accepted status codes
    public enum AcceptSts {
        Success(0),
        ProgUnavail(1),
        ProgMismatch(2),
        ProcUnavail(3),
        BadArgs(4),

        Invalid(0xFFFF);

        private final int acceptSts;

        /**
         * Enum constructor
         *
         * @param sts int
         */
        AcceptSts(int sts) { acceptSts = sts; }

        /**
         * Return the accept status as an int
         *
         * @return int
         */
        public final int intValue() { return acceptSts; }

        /**
         * Create an accept status from an int
         *
         * @param sts int
         * @return AcceptSts
         */
        public static final Rpc.AcceptSts fromInt(int sts) {
            Rpc.AcceptSts aSts = Invalid;

            switch ( sts) {
                case 0:
                    aSts = Success;
                    break;
                case 1:
                    aSts = ProgUnavail;
                    break;
                case 2:
                    aSts = ProgMismatch;
                    break;
                case 3:
                    aSts = ProcUnavail;
                    break;
                case 4:
                    aSts = BadArgs;
                    break;
            }

            return aSts;
        }
    }

    //	Call rejected status codes
    public enum RejectSts {
        RpcMismatch(0),
        AuthError(1),

        Invalid(0xFFFF);

        private final int rejectSts;

        /**
         * Enum constructor
         *
         * @param sts int
         */
        RejectSts(int sts) { rejectSts = sts; }

        /**
         * Return the reject status as an int
         *
         * @return int
         */
        public final int intValue() { return rejectSts; }

        /**
         * Create a reject status from an int
         *
         * @param sts int
         * @return RejectSts
         */
        public static final Rpc.RejectSts fromInt(int sts) {
            Rpc.RejectSts rSts = Invalid;

            switch ( sts) {
                case 0:
                    rSts = RpcMismatch;
                    break;
                case 1:
                    rSts = AuthError;
                    break;
            }

            return rSts;
        }
    }

    //	Authentication failure status codes
    public enum AuthSts {
        BadCred(1),
        RejectCred(2),
        BadVerf(3),
        RejectedVerf(4),
        TooWeak(5),

        Invalid(0xFFFF);

        private final int authSts;

        /**
         * Enum constructor
         *
         * @param sts int
         */
        AuthSts(int sts) { authSts = sts; }

        /**
         * Return the authentication status as an int
         *
         * @return int
         */
        public final int intValue() { return authSts; }

        /**
         * Create an authentication status from an int
         *
         * @param sts int
         * @return AuthSts
         */
        public static final Rpc.AuthSts fromInt(int sts) {
            Rpc.AuthSts aSts = Invalid;

            switch ( sts) {
                case 1:
                    aSts = BadCred;
                    break;
                case 2:
                    aSts = RejectCred;
                    break;
                case 3:
                    aSts = BadVerf;
                    break;
                case 4:
                    aSts = RejectedVerf;
                    break;
                case 5:
                    aSts = TooWeak;
                    break;
            }

            return aSts;
        }
    }

    //	True/false values
    public static final int True    = 1;
    public static final int False   = 0;

    //	Protocol ids
    public enum ProtocolId {
        TCP(6),
        UDP(17),

        Invalid(0xFFFF);

        private final int protId;

        /**
         * Enum constructor
         *
         * @param id int
         */
        ProtocolId(int id) { protId = id; }

        /**
         * Return the protocol id as an int
         *
         * @return int
         */
        public final int intValue() { return protId; }

        /**
         * Create a protocol id from an int
         *
         * @param id int
         * @return ProtocolId
         */
        public static final Rpc.ProtocolId fromInt(int id) {
            Rpc.ProtocolId prot = Invalid;

            switch ( id) {
                case 6:
                    prot = TCP;
                    break;
                case 17:
                    prot = UDP;
                    break;
            }

            return prot;
        }
    }

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
