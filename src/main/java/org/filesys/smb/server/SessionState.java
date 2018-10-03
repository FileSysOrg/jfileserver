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
 * <p>Contains the various states that an SMB server session will go through during the session
 * lifetime.
 *
 * @author gkspencer
 */
public enum SessionState {
    NETBIOS_SESS_REQUEST,
    SMB_NEGOTIATE,
    SMB_SESSSETUP,
    SMB_SESSION,
    SMB_CLOSED,
    NETBIOS_HANGUP;
}
