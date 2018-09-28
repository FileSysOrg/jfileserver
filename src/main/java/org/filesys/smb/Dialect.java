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

package org.filesys.smb;

/**
 * SMB Dialect Class
 *
 * <p>This class contains the available SMB protocol dialects that may be
 * negotiated when an SMB session is setup.
 *
 * @author gkspencer
 */
public final class Dialect {

    // SMB dialect strings, encoded into the SMB session setup packet.
    private static final String[] protList =
            {
                    "PC NETWORK PROGRAM 1.0",
                    "MICROSOFT NETWORKS 1.03",
                    "MICROSOFT NETWORKS 3.0",
                    "DOS LANMAN1.0",
                    "LANMAN1.0",
                    "DOS LM1.2X002",
                    "LM1.2X002",
                    "DOS LANMAN2.1",
                    "LANMAN2.1",
                    "Samba",
                    "NT LM 0.12",
                    "NT LANMAN 1.0",
                    "SMB 2.002",
                    "SMB 2.210",
                    "SMB 2.???",
                    "SMB 3.000",
                    "SMB 3.002",
                    "SMB 3.110"
            };

    // SMB dialect type strings
    private static final String[] protType =
            {
                    "Core",
                    "CorePlus",
                    "DOS LANMAN 1.0",
                    "LANMAN1.0",
                    "DOS LANMAN 2.1",
                    "LM1.2X002",
                    "LANMAN2.1",
                    "NT LM 0.12",
                    "SMB 2.002",
                    "SMB 2.210",
                    "SMB 2.ANY",
                    "SMB 3.000",
                    "SMB 3.002",
                    "SMB 3.110"
            };

    // Dialect constants
    public static final int Core        = 0;
    public static final int CorePlus    = 1;
    public static final int DOSLanMan1  = 2;
    public static final int LanMan1     = 3;
    public static final int DOSLanMan2  = 4;
    public static final int LanMan2     = 5;
    public static final int LanMan2_1   = 6;
    public static final int NT          = 7;
    public static final int SMB2_202    = 8;
    public static final int SMB2_210    = 9;
    public static final int SMB2_Any    = 10;
    public static final int SMB3_300    = 11;
    public static final int SMB3_302    = 12;
    public static final int SMB3_311    = 13;

    public static final int Max         = 13;

    public static final int UpToSMBv1   = NT + 1;
    public static final int UpToSMBv2   = SMB2_Any + 1;
    public static final int UpToSMBv3   = SMB3_311 + 1;

    public static final int Unknown     = -1;

    // SMB dialect type to string conversion array
    private static final int[] protIdx =
            {
                    Core,
                    CorePlus,
                    DOSLanMan1,
                    DOSLanMan1,
                    LanMan1,
                    DOSLanMan2,
                    LanMan2,
                    LanMan2_1,
                    LanMan2_1,
                    NT,
                    NT,
                    NT,
                    SMB2_202,
                    SMB2_210,
                    SMB2_Any,
                    SMB3_300,
                    SMB3_302,
                    SMB3_311
            };

    //  SMB dialect type to string conversion array length
    public static final int SMB_PROT_MAXSTRING = protIdx.length;

    //	Table that maps SMB commands to the minimum required SMB dialect
    private static final int[] cmdtable = {
            Core, // CreateDirectory
            Core, // DeleteDirectory
            Core, // OpenFile
            Core, // CreateFile
            Core, // CloseFile
            Core, // FlushFile
            Core, // DeleteFile
            Core, // RenameFile
            Core, // QueryFileInfo
            Core, // SetFileInfo
            Core, // Read
            Core, // Write
            Core, // LockFile
            Core, // UnlockFile
            Core, // CreateTemporary
            Core, // CreateNew
            Core, // CheckDirectory
            Core, // ProcessExit
            Core, // SeekFile
            LanMan1, // V1LockAndRead
            LanMan1, // WriteAndUnlock
            0, // Unused
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            LanMan1, // ReadRaw
            LanMan1, // WriteMpxSecondary
            LanMan1, // WriteRaw
            LanMan1, // WriteMpx
            0, // Unused
            LanMan1, // WriteComplete
            0, // Unused
            LanMan1, // SetInformation2
            LanMan1, // QueryInformation2
            LanMan1, // LockingAndX
            LanMan1, // Transaction
            LanMan1, // TransactionSecondary
            LanMan1, // Ioctl
            LanMan1, // Ioctl2
            LanMan1, // Copy
            LanMan1, // Move
            LanMan1, // Echo
            LanMan1, // WriteAndClose
            LanMan1, // OpenAndX
            LanMan1, // ReadAndX
            LanMan1, // WriteAndX
            0, // Unused
            LanMan1, // CloseAndTreeDisconnect
            LanMan2, // Transaction2
            LanMan2, // Transaction2Secondary
            LanMan2, // FindClose2
            LanMan1, // FindNotifyClose
            0, // Unused
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            Core, // TreeConnect
            Core, // TreeDisconnect
            Core, // Negotiate
            Core, // SessionSetupAndX
            LanMan1, // LogoffAndX
            LanMan1, // TreeConnectAndX
            0, // Unused
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            Core, // DiskInformation
            Core, // Search
            LanMan1, // Find
            LanMan1, // FindUnique
            0, // Unused
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            NT, // NTTransact
            NT, // NTTransactSecondary
            NT, // NTCreateAndX
            NT, // NTCancel
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            Core, // OpenPrintFile
            Core, // WritePrintFile
            Core, // ClosePrintFile
            Core, // GetPrintQueue
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            0, // ..
            -1, // SendMessage
            -1, // SendBroadcast
            -1, // SendForward
            -1, // CancelForward
            -1, // GetMachineName
            -1, // SendMultiStart
            -1, // SendMultiEnd
            -1  // SendMultiText
    };

    /**
     * Return the required SMB dialect string.
     *
     * @param i SMB dialect string index.
     * @return SMB dialect string.
     */
    public static String DialectString(int i) {

        //  Validate the dialect index
        if (i >= protList.length)
            return null;
        return protList[i];
    }

    /**
     * Determine if the SMB dialect supports the SMB command
     *
     * @param dialect int  SMB dialect type.
     * @param cmd     int      SMB command code.
     * @return boolean
     */
    public final static boolean DialectSupportsCommand(int dialect, int cmd) {

        //  Range check the command
        if (cmd > cmdtable.length)
            return false;

        //  Check if the SMB dialect supports the SMB command.
        if (cmdtable[cmd] <= dialect)
            return true;
        return false;
    }

    /**
     * Return the SMB dialect type for the specified SMB dialect string index.
     *
     * @param i SMB dialect type.
     * @return SMB dialect string index.
     */
    public static int DialectType(int i) {
        return protIdx[i];
    }

    /**
     * Return the SMB dialect type for the specified string.
     *
     * @param diastr String
     * @return int
     */
    public static int DialectType(String diastr) {

        //  Search the protocol string list
        int i = 0;

        while (i < protList.length && protList[i].compareTo(diastr) != 0)
            i++;

        //  Return the protocol id
        if (i < protList.length)
            return DialectType(i);
        else
            return Unknown;
    }

    /**
     * Return the dialect type as a string.
     *
     * @param dia SMB dialect type.
     * @return SMB dialect type string.
     */
    public static String DialectTypeString(int dia) {
        return protType[dia];
    }

    /**
     * Return the number of available SMB dialect strings.
     *
     * @return Number of available SMB dialect strings.
     */
    public static int NumberOfDialects() {
        return protList.length;
    }

    /**
     * Convert an SMB v2 dialect id to a protocol index
     *
     * @param v2Id int
     * @return int
     */
    public static int SMB2DialectToId( int v2Id) {

        int protoId = -1;

        switch ( v2Id) {
            case 0x0202:
                protoId = SMB2_202;
                break;
            case 0x0210:
                protoId = SMB2_210;
                break;
            case 0x0300:
                protoId = SMB3_300;
                break;
            case 0x0302:
                protoId = SMB3_302;
                break;
            case 0x0311:
                protoId = SMB3_311;
                break;
        }

        return protoId;
    }

    /**
     * Convert the dialect id to an SMB v2 dialect id
     *
     * @param diaId int
     * @return int
     */
    public static int SMB2IdToDialect( int diaId) {

        int v2Id = -1;

        switch ( diaId) {
            case SMB2_202:
                v2Id = 0x0202;
                break;
            case SMB2_210:
                v2Id = 0x0210;
                break;
            case SMB2_Any:
                v2Id = 0x02FF;
                break;
            case SMB3_300:
                v2Id = 0x0300;
                break;
            case SMB3_302:
                v2Id = 0x0302;
                break;
            case SMB3_311:
                v2Id = 0x0311;
                break;
        }

        return v2Id;
    }

    /**
     * Determine if a dialect is an SMB v1
     *
     * @param dialectId int
     * @return boolean
     */
    public static final boolean isSMB1( int dialectId) {
        if ( dialectId >= 0 && dialectId <= NT)
            return true;
        return false;
    }

    /**
     * Determine if a dialect is an SMB v2
     *
     * @param dialectId int
     * @return boolean
     */
    public static final boolean isSMB2( int dialectId) {
        if ( dialectId >= SMB2_202)
            return true;
        return false;
    }

    /**
     * Determine if a dialect is an SMB v3
     *
     * @param dialectId int
     * @return boolean
     */
    public static final boolean isSMB3( int dialectId) {
        if ( dialectId >= SMB3_300)
            return true;
        return false;
    }

    /**
     * Get the major SMB version for the selected dialect
     *
     * @param dialectId int
     * @return int
     */
    public static final int getMajorSMBVersion(int dialectId) {
        if ( dialectId < SMB2_202)
            return 1;
        else if ( dialectId <= SMB2_Any)
            return 2;
        else
            return 3;
    }
}
