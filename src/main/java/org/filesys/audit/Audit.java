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

import org.filesys.debug.DebugInterface;

import java.util.EnumSet;

/**
 * Audit Log Output Class
 *
 * @author gkspencer
 */
public class Audit {

    //	Global audit interface
    private static DebugInterface m_audit = null;

    // Enabled audit groups
    private static EnumSet<AuditGroup> m_enabledGroups = EnumSet.allOf( AuditGroup.class);

    /**
     * Default constructor
     */
    private Audit() {
    }

    /**
     * Get the audit interface
     *
     * @return dbg
     */
    public static final DebugInterface getAuditInterface() {
        return m_audit;
    }

    /**
     * Check if the specified audit group is enabled
     *
     * @param group AuditGroup
     * @return boolean
     */
    public static final boolean isGroupEnabled(AuditGroup group) {
        if ( m_enabledGroups == null)
            return false;
        return m_enabledGroups.contains( group);
    }

    /**
     * Check if the specified audit group is enabled for the audit type
     *
     * @param typ AuditType
     * @return boolean
     */
    public static final boolean isGroupEnabled(AuditType typ) {
        if ( m_enabledGroups == null)
            return false;
        return m_enabledGroups.contains( Audit.getAuditGroup( typ));
    }

    /**
     * Return the audit group for the specified audit type
     *
     * @param typ AuditType
     * @return AuditGroup
     */
    public static final AuditGroup getAuditGroup(AuditType typ) {
        AuditGroup group = AuditGroup.LOGON;

        switch (typ) {
            case Logon:
            case Logoff:
                group = AuditGroup.LOGON;
                break;
            case FileClose:
            case FileCreate:
            case FileDelete:
            case FileMove:
            case FileOpen:
            case FileRename:
                group = AuditGroup.FILE;
                break;
            case SessionCreated:
            case SessionClosed:
                group = AuditGroup.SESSION;
                break;
        }

        return group;
    }

    /**
     * Set the audit log interface
     *
     * @param dbg DebugInterface
     */
    public static final void setAuditInterface(DebugInterface dbg) {
        m_audit = dbg;
    }

    /**
     * Set the audit groups to enable
     *
     * @param groups EnumSet&lt;AuditGroup&gt;
     */
    public static final void setAuditGroups(EnumSet<AuditGroup> groups) {
        m_enabledGroups = groups;
    }

    /**
     * Output an audit string, and a newline.
     *
     * @param str String
     */
    public static final void println(String str) {
        m_audit.debugPrintln(str);
    }
}
