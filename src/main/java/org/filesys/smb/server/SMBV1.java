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

import org.filesys.netbios.RFCNetBIOSProtocol;
import org.filesys.smb.PacketTypeV1;

/**
 * SMB v1 Constants Class
 *
 * @author gkspencer
 */
public class SMBV1 {

    // SMB packet offsets, assuming an RFC NetBIOS transport
    public static final int SIGNATURE 		= RFCNetBIOSProtocol.HEADER_LEN;
    public static final int COMMAND 		= 4 + RFCNetBIOSProtocol.HEADER_LEN;
    public static final int ERRORCODE 		= 5 + RFCNetBIOSProtocol.HEADER_LEN;
    public static final int ERRORCLASS 		= 5 + RFCNetBIOSProtocol.HEADER_LEN;
    public static final int ERROR 			= 7 + RFCNetBIOSProtocol.HEADER_LEN;
    public static final int FLAGS 			= 9 + RFCNetBIOSProtocol.HEADER_LEN;
    public static final int FLAGS2 			= 10 + RFCNetBIOSProtocol.HEADER_LEN;
    public static final int PIDHIGH 		= 12 + RFCNetBIOSProtocol.HEADER_LEN;
    public static final int SID 			= 18 + RFCNetBIOSProtocol.HEADER_LEN;
    public static final int SEQNO 			= 20 + RFCNetBIOSProtocol.HEADER_LEN;
    public static final int TID 			= 24 + RFCNetBIOSProtocol.HEADER_LEN;
    public static final int PID 			= 26 + RFCNetBIOSProtocol.HEADER_LEN;
    public static final int UID 			= 28 + RFCNetBIOSProtocol.HEADER_LEN;
    public static final int MID 			= 30 + RFCNetBIOSProtocol.HEADER_LEN;
    public static final int WORDCNT 		= 32 + RFCNetBIOSProtocol.HEADER_LEN;
    public static final int ANDXCOMMAND 	= 33 + RFCNetBIOSProtocol.HEADER_LEN;
    public static final int ANDXRESERVED 	= 34 + RFCNetBIOSProtocol.HEADER_LEN;
    public static final int PARAMWORDS 		= 33 + RFCNetBIOSProtocol.HEADER_LEN;

    // SMB packet header length
    public static final int HeaderLength	= PARAMWORDS - RFCNetBIOSProtocol.HEADER_LEN;

    // SMB packet header length for a transaction type request
    public static final int TRANS_HEADERLEN = 66 + RFCNetBIOSProtocol.HEADER_LEN;

    // Minimum receive length for a valid SMB packet
    public static final int MIN_RXLEN = 32;

    // Flag bits
    public static final int FLG_SUBDIALECT 	= 0x01;
    public static final int FLG_CASELESS 	= 0x08;
    public static final int FLG_CANONICAL 	= 0x10;
    public static final int FLG_OPLOCK 		= 0x20;
    public static final int FLG_NOTIFY 		= 0x40;
    public static final int FLG_RESPONSE 	= 0x80;

    // Flag2 bits
    public static final int FLG2_LONGFILENAMES 		= 0x0001;
    public static final int FLG2_EXTENDEDATTRIB 	= 0x0002;
    public static final int FLG2_SECURITYSIGS 		= 0x0004;
    public static final int FLG2_EXTENDEDSECURITY 	= 0x0800;
    public static final int FLG2_DFSRESOLVE 		= 0x1000;
    public static final int FLG2_READIFEXE 			= 0x2000;
    public static final int FLG2_LONGERRORCODE 		= 0x4000;
    public static final int FLG2_UNICODE 			= 0x8000;

    // Security mode bits
    public static final int SEC_USER 	= 0x0001;
    public static final int SEC_ENCRYPT = 0x0002;

    // Raw mode bits
    public static final int RAW_READ 	= 0x0001;
    public static final int RAW_WRITE 	= 0x0002;

    // No chained AndX command indicator
    public static final int NO_ANDX_CMD = 0x00FF;

    // Define the default receive buffer size to allocate.
    public static final int DefaultBufferSize = 0x010000 + RFCNetBIOSProtocol.HEADER_LEN;
    public static final int LanManBufferSize = 8192;

    // Maximum multiplexed packets allowed (client can send up to this many SMBs before waiting for
    // a response)
    //
    // Setting NTMaxMultiplexed to one will disable asynchronous notifications on the client
    public static final int LanManMaxMultiplexed = 1;
    public static final int NTMaxMultiplexed = 4;

    // Maximum number of virtual circuits
    public static final int MaxVirtualCircuits = 0;

    /**
     * Dump the packet type
     *
     * @param cmd int
     * @return String
     */
    public static final String getPacketTypeString( int cmd) {

        String pktType = "";

        switch ( cmd) {
            case PacketTypeV1.Negotiate:
                pktType = "NEGOTIATE";
                break;
            case PacketTypeV1.SessionSetupAndX:
                pktType = "SESSION_SETUP";
                break;
            case PacketTypeV1.TreeConnect:
                pktType = "TREE_CONNECT";
                break;
            case PacketTypeV1.TreeConnectAndX:
                pktType = "TREE_CONNECT_ANDX";
                break;
            case PacketTypeV1.TreeDisconnect:
                pktType = "TREE_DISCONNECT";
                break;
            case PacketTypeV1.Search:
                pktType = "SEARCH";
                break;
            case PacketTypeV1.OpenFile:
                pktType = "OPEN_FILE";
                break;
            case PacketTypeV1.OpenAndX:
                pktType = "OPEN_ANDX";
                break;
            case PacketTypeV1.ReadFile:
                pktType = "READ_FILE";
                break;
            case PacketTypeV1.WriteFile:
                pktType = "WRITE_FILE";
                break;
            case PacketTypeV1.CloseFile:
                pktType = "CLOSE_FILE";
                break;
            case PacketTypeV1.CreateFile:
                pktType = "CREATE_FILE";
                break;
            case PacketTypeV1.GetFileAttributes:
                pktType = "GET_FILE_INFO";
                break;
            case PacketTypeV1.DiskInformation:
                pktType = "GET_DISK_INFO";
                break;
            case PacketTypeV1.CheckDirectory:
                pktType = "CHECK_DIRECTORY";
                break;
            case PacketTypeV1.RenameFile:
                pktType = "RENAME_FILE";
                break;
            case PacketTypeV1.DeleteDirectory:
                pktType = "DELETE_DIRECTORY";
                break;
            case PacketTypeV1.GetPrintQueue:
                pktType = "GET_PRINT_QUEUE";
                break;
            case PacketTypeV1.Transaction2:
                pktType = "TRANSACTION2";
                break;
            case PacketTypeV1.Transaction:
                pktType = "TRANSACTION";
                break;
            case PacketTypeV1.Transaction2Second:
                pktType = "TRANSACTION2_SECONDARY";
                break;
            case PacketTypeV1.TransactionSecond:
                pktType = "TRANSACTION_SECONDARY";
                break;
            case PacketTypeV1.Echo:
                pktType = "ECHO";
                break;
            case PacketTypeV1.QueryInformation2:
                pktType = "QUERY_INFORMATION_2";
                break;
            case PacketTypeV1.WriteAndClose:
                pktType = "WRITE_AND_CLOSE";
                break;
            case PacketTypeV1.SetInformation2:
                pktType = "SET_INFORMATION_2";
                break;
            case PacketTypeV1.FindClose2:
                pktType = "FIND_CLOSE2";
                break;
            case PacketTypeV1.LogoffAndX:
                pktType = "LOGOFF_ANDX";
                break;
            case PacketTypeV1.ReadAndX:
                pktType = "READ_ANDX";
                break;
            default:
                pktType = "0x" + Integer.toHexString( cmd);
                break;
        }

        // Return the packet type string
        return pktType;
    }
}
