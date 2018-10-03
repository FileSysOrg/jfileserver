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
 * File Mode Class
 *
 * @author gkspencer
 */
public class Mode {

    // Mode flags for information requests
    public static final int WriteThrough    = 0x00000002;
    public static final int SequentialOnly  = 0x00000004;
    public static final int NoBuffering     = 0x00000008;
    public static final int SyncIOAlert     = 0x00000010;
    public static final int SyncIONonAlert  = 0x00000020;
    public static final int DeleteOnClose   = 0x00001000;
}
