/*
 * Copyright (C) 2023 GK Spencer
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

package org.filesys.server.locking;

/**
 * Oplock Break Levels Class
 *
 * <p>Oplock level to break to</p>
 *
 * @author gkspencer
 */
public class BreakLevel {

    // Oplock break levels
    public static final int LEVEL_NONE           = 0x00;
    public static final int LEVEL_II             = 0x01;
    public static final int LEVEL_EXCLUSIVE      = 0x08;
    public static final int LEVEL_LEASE          = 0xFF;
}
