/*
 * Copyright (C) 2018 GK Spencer
 *
 * JFileSrv is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JFileSrv is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with JFileSrv. If not, see <http://www.gnu.org/licenses/>.
 */

package org.filesys.netbios.win32;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import java.util.Arrays;
import java.util.List;

/**
 * NetBIOS Control Block Structure Class
 *
 * <p>Used by calls to the native NetBIOS API code on Windows</p>
 */
public class NCB extends Structure {

    public static class ByReference extends NCB implements Structure.ByReference {

        public ByReference() {
        }
    }

    // NCB fields, as per the C strucuture in nb30.h
    public byte ncb_command;
    public byte ncb_retcode;
    public byte ncb_lsn;
    public byte ncb_num;
    public Pointer ncb_buffer;
    public short ncb_length;
    public byte[] ncb_callname = new byte[NetBIOS.NCBNameSize];
    public byte[] ncb_name = new byte[NetBIOS.NCBNameSize];
    public byte ncb_rto;
    public byte ncb_sto;
    public Pointer ncb_post;
    public byte ncb_lana_num;
    public byte ncb_cmd_cplt;
    public byte[] ncb_reserve = new byte[18];
    public int ncb_event;

    /**
     * Return the structure field order for native mapping.
     *
     * @return List of field names
     */
    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList( "ncb_command", "ncb_retcode", "ncb_lsn", "ncb_num", "ncb_buffer", "ncb_length", "ncb_callname", "ncb_name",
                "ncb_rto", "ncb_sto", "ncb_post", "ncb_lana_num", "ncb_cmd_cplt", "ncb_reserve", "ncb_event");
    }
}
