/*
 * Copyright (C) 2020 GK Spencer
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

package org.filesys.oncrpc.nfs.v3;

import org.filesys.smb.PacketTypeV2;

import java.util.HashMap;
import java.util.Map;

/**
 * NFS v3 Protocol Constants Class
 */
public class NFS3 {

    //	Program and version id
    public static final int ProgramId = 100003;
    public static final int VersionId = 3;

    // Fixed structure lengths
    public static final int LenAttributes3      = 84;
    public static final int LenPostOpAttr3      = LenAttributes3 + 4;

    // NFS v3 RPC procedure ids
    public enum ProcedureId {
        Null(0),
        GetAttr(1),
        SetAttr(2),
        Lookup(3),
        Access(4),
        ReadLink(5),
        Read(6),
        Write(7),
        Create(8),
        MkDir(9),
        SymLink(10),
        MkNode(11),
        Remove(12),
        RmDir(13),
        Rename(14),
        Link(15),
        ReadDir(16),
        ReadDirPlus(17),
        FsStat(18),
        FsInfo(19),
        PathConf(20),
        Commit(21),

        Invalid(0xFFFF);

        private final int procId;

        // Mapping procedure name to id
        private static Map<Integer, ProcedureId> _idMap = new HashMap<>();

        /**
         * Static initializer
         */
        static {
            for ( ProcedureId id : ProcedureId.values())
                _idMap.put( id.intValue(), id);
        }

        /**
         * Enum constructor
         *
         * @param id int
         */
        ProcedureId(int id) { procId = id; }

        /**
         * Return the procedure id as an int
         *
         * @return int
         */
        public final int intValue() { return procId; }

        /**
         * Create a procedure id type from an int
         *
         * @param typ int
         * @return ProcedureId
         */
        public static final ProcedureId fromInt(int typ) {

            if ( _idMap.containsKey( typ))
                return _idMap.get( typ);

            return Invalid;
        }
    }

    // NFS v3 status codes
    public enum StatusCode {
        Success(0),
        Perm(1),
        NoEnt(2),
        IO(5),
        NxIO(6),
        Access(13),
        Exist(17),
        XDev(18),
        NoDev(19),
        NotDir(20),
        IsDir(21),
        InVal(22),
        FBig(27),
        NoSpc(28),
        ROFS(30),
        MLink(31),
        NameTooLong(63),
        NotEmpty(66),
        DQuot(69),
        Stale(70),
        Remote(71),
        BadHandle(10001),
        NotSync(10002),
        BadCookie(10003),
        NotSupp(10004),
        TooSmall(10005),
        ServerFault(10006),
        BadType(10007),
        JukeBox(10008),

        Invalid(0xFFFF);

        private final int stsCode;

        // Mapping status code to id
        private static Map<Integer, StatusCode> _stsMap = new HashMap<>();

        /**
         * Static initializer
         */
        static {
            for ( StatusCode sts : StatusCode.values())
                _stsMap.put( sts.intValue(), sts);
        }

        /**
         * Enum constructor
         *
         * @param id int
         */
        StatusCode(int id) { stsCode = id; }

        /**
         * Return the status code as an int
         *
         * @return int
         */
        public final int intValue() { return stsCode; }

        /**
         * Create a status code type from an int
         *
         * @param sts int
         * @return StatusCode
         */
        public static final StatusCode fromInt(int sts) {

            if ( _stsMap.containsKey( sts))
                return _stsMap.get( sts);

            return Invalid;
        }

        /**
         * Return an error status string for the specified status code
         *
         * @return String
         */
        public final String getStatusString() {
            String str = null;

            switch ( this) {
                case Success:
                    str = "Success status";
                    break;
                case Access:
                    str = "Access denied";
                    break;
                case BadCookie:
                    str = "Bad cookie";
                    break;
                case BadHandle:
                    str = "Bad handle";
                    break;
                case BadType:
                    str = "Bad type";
                    break;
                case DQuot:
                    str = "Quota exceeded";
                    break;
                case Perm:
                    str = "No permission";
                    break;
                case Exist:
                    str = "Already exists";
                    break;
                case FBig:
                    str = "File too large";
                    break;
                case InVal:
                    str = "Invalid argument";
                    break;
                case IO:
                    str = "I/O error";
                    break;
                case IsDir:
                    str = "Is directory";
                    break;
                case JukeBox:
                    str = "Jukebox";
                    break;
                case MLink:
                    str = "Too many hard links";
                    break;
                case NameTooLong:
                    str = "Name too long";
                    break;
                case NoDev:
                    str = "No such device";
                    break;
                case NoEnt:
                    str = "No entity";
                    break;
                case NoSpc:
                    str = "No space left on device";
                    break;
                case NotSync:
                    str = "Update synchronization mismatch";
                    break;
                case NotDir:
                    str = "Not directory";
                    break;
                case NotEmpty:
                    str = "Not empty";
                    break;
                case NotSupp:
                    str = "Not supported";
                    break;
                case NxIO:
                    str = "Nxio";
                    break;
                case Remote:
                    str = "Too many levels of remote in path";
                    break;
                case ROFS:
                    str = "Readonly filesystem";
                    break;
                case ServerFault:
                    str = "Server fault";
                    break;
                case Stale:
                    str = "Stale";
                    break;
                case TooSmall:
                    str = "Too small";
                    break;
                case XDev:
                    str = "Cross device hard link attempted";
                    break;
            }

            return str;
        }
    }

    //	Data structure limits
    public static final int FileHandleSize  = 32;        //	can be 64 for NFS v3
    public static final int WriteVerfSize   = 8;
    public static final int CreateVerfSize  = 8;
    public static final int CookieVerfSize  = 8;

    // File types
    public enum FileType {
        Regular(1),
        Directory(2),
        Block(3),
        Character(4),
        Link(5),
        Socket(6),
        Fifo(7),

        Invalid(0xFFFF);

        private final int fileType;

        /**
         * Enum constructor
         *
         * @param typ int
         */
        FileType(int typ) { fileType = typ; }

        /**
         * Return the file type as an int
         *
         * @return int
         */
        public final int intValue() { return fileType; }

        /**
         * Create a file type from an int
         *
         * @param typ int
         * @return FileType
         */
        public static final FileType fromInt(int typ) {
            FileType fType = Invalid;

            switch ( typ) {
                case 1:
                    fType = Regular;
                    break;
                case 2:
                    fType = Directory;
                    break;
                case 3:
                    fType = Block;
                    break;
                case 4:
                    fType = Character;
                    break;
                case 5:
                    fType = Link;
                    break;
                case 6:
                    fType = Socket;
                    break;
                case 7:
                    fType = Fifo;
                    break;
            }

            return fType;
        }
    }

    //	Filesystem properties
    public static final int FileSysLink         = 0x0001;        //	supports hard links
    public static final int FileSysSymLink      = 0x0002;        //	supports symbolic links
    public static final int FileSysHomogeneuos  = 0x0004;        //	PATHCONF valid for all files
    public static final int FileSysCanSetTime   = 0x0008;        //	can set time on server side

    //	Access mask
    public static final int AccessRead      = 0x0001;
    public static final int AccessLookup    = 0x0002;
    public static final int AccessModify    = 0x0004;
    public static final int AccessExtend    = 0x0008;
    public static final int AccessDelete    = 0x0010;
    public static final int AccessExecute   = 0x0020;

    public static final int AccessAll       = 0x003F;

    // Create mode values
    public enum CreateMode {
        Unchecked(1),
        Guarded(2),
        Exclusive(3),

        Invalid(0xFFFF);

        private final int createMode;

        /**
         * Enum constructor
         *
         * @param mode int
         */
        CreateMode(int mode) { createMode = mode; }

        /**
         * Return the create mode as an int
         *
         * @return int
         */
        public final int intValue() { return createMode; }

        /**
         * Create a create mode from an int
         *
         * @param mode int
         * @return CreateMode
         */
        public static final CreateMode fromInt(int mode) {
            CreateMode cMode = Invalid;

            switch( mode) {
                case 1:
                    cMode = Unchecked;
                    break;
                case 2:
                    cMode = Guarded;
                    break;
                case 3:
                    cMode = Exclusive;
                    break;

            }

            return cMode;
        }
    }

    //	Write request stable values
    public enum WriteStable {
        Unstable(0),
        DataSync(1),
        FileSync(2),

        Invalid(0xFFFF);

        private final int writeStable;

        /**
         * Enum constructor
         *
         * @param wrStable int
         */
        WriteStable(int wrStable) { writeStable = wrStable; }

        /**
         * Return the write stable value as an int
         *
         * @return int
         */
        public final int intValue() { return writeStable; }

        /**
         * Create a write stable value from an int
         *
         * @param wrVal int
         * @return WriteStable
         */
        public static final WriteStable fromInt(int wrVal) {
            WriteStable wrStable = Invalid;

            switch( wrVal) {
                case 0:
                    wrStable = Unstable;
                    break;
                case 1:
                    wrStable = DataSync;
                    break;
                case 2:
                    wrStable = FileSync;
                    break;

            }

            return wrStable;
        }
    }

    //	Set attributes file timestamp settings
    public enum SetAttrTimestamp {
        DoNotSet(0),
        TimeServer(1),
        TimeClient(2),

        Invalid(0xFFFF);

        private final int setTime;

        /**
         * Enum constructor
         *
         * @param setAttr int
         */
        SetAttrTimestamp(int setAttr) { setTime = setAttr; }

        /**
         * Return the set timestamp value as an int
         *
         * @return int
         */
        public final int intValue() { return setTime; }

        /**
         * Create a set timestamp value from an int
         *
         * @param setVal int
         * @return SetAttrTimestamp
         */
        public static final SetAttrTimestamp fromInt(int setVal) {
            SetAttrTimestamp setAttr = Invalid;

            switch( setVal) {
                case 0:
                    setAttr = DoNotSet;
                    break;
                case 1:
                    setAttr = TimeServer;
                    break;
                case 2:
                    setAttr = TimeClient;
                    break;

            }

            return setAttr;
        }
    }
}
