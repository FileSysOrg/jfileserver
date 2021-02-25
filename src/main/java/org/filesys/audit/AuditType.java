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

package org.filesys.audit;

/**
 * Audit log record types enum class
 *
 * @author gkspencer
 */
public enum AuditType {
    Logon,          // user logon
    Logoff,         // user logoff
    FileCreate,     // file created
    FileOpen,       // file opened
    FileClose,      // file closed
    FileDelete,     // file deleted
    FileRename,     // file renamed
    FileMove,       // file moved
    Consistency,    // consistency check failed
    SessionCreated, // new session created
    SessionClosed   // session closed
}
