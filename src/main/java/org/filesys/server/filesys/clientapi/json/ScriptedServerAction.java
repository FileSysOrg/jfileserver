/*
 * Copyright (C) 2024 GK Spencer
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

package org.filesys.server.filesys.clientapi.json;

import java.util.EnumSet;

/**
 * Scripted Server Action Class
 *
 * <p>Contains the details of a scripted server side action</p>
 * @author gkspencer
 */
public abstract class ScriptedServerAction extends ServerAction {

    // Script server side path and last modified date/time
    private transient String m_scriptPath;
    private transient long m_scriptModifiedAt;

    // Script text
    private transient String m_scriptText;

    /**
     * Class constructor
     *
     * @param name String
     * @param desc String
     * @param flags EnumSet&lt;Flags&gt;
     */
    public ScriptedServerAction(String name, String desc, EnumSet<Flags> flags, String scriptPath) {
        super(name, desc, flags);

        m_scriptPath = scriptPath;
    }

    /**
     * Return the script path
     *
     * @return String
     */
    public final String getScriptPath() { return m_scriptPath; }

    /**
     * Check if the script text has been loaded
     *
     * @return boolean
     */
    public final boolean hasScriptText() { return m_scriptText != null; }

    /**
     * Return the script text
     *
     * @return String
     */
    public final String getScriptText() { return m_scriptText; }

    /**
     * Set the script text
     *
     * @param scriptText String
     */
    public final void setScriptText(String scriptText) { m_scriptText = scriptText; }

    /**
     * Return the script file last modified at date/time
     *
     * @return long
     */
    public final long getScriptModifiedAt() { return m_scriptModifiedAt; }

    /**
     * Set the script file last modified at date/time
     *
     * @param modifyAt long
     */
    public final void setScriptModifiedAt(long modifyAt) { m_scriptModifiedAt = modifyAt; }

    /**
     * Return the scripted action details as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[Name=");
        str.append( getName());
        str.append(",Description=");
        str.append( getDescription());
        str.append(",Flags=");
        str.append( getFlags());
        str.append(",Path=");
        str.append( getScriptPath());
        str.append(",modifiedAt=");
        str.append( getScriptModifiedAt());

        if ( hasScriptText())
            str.append(",Loaded");
        str.append("]");

        return str.toString();
    }
}
