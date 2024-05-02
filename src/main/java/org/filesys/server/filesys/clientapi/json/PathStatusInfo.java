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

import com.google.gson.annotations.SerializedName;

/**
 * Path Status Info Class
 *
 * <p>Contains details of a path status check for a particular path</p>
 *
 * @author gkspencer
 */
public class PathStatusInfo {

    @SerializedName( value = "path")
    private String m_path;

    @SerializedName( value = "status")
    private boolean m_status;

    @SerializedName( value = "add_info")
    private String m_additionalInfo;

    /**
     * Class constructor
     *
     * @param path String
     */
    public PathStatusInfo( String path) {
        m_path = path;
        m_status = false;
    }

    /**
     * Class constructor
     *
     * @param path String
     * @param sts boolean
     * @param addInfo String
     */
    public PathStatusInfo( String path, boolean sts, String addInfo) {
        m_path = path;
        m_status = sts;
        m_additionalInfo = addInfo;
    }

    /**
     * Set the path status
     *
     * @param sts boolean
     */
    public final void setStatus( boolean sts) { m_status = sts; }

    /**
     * Set the additional information
     *
     * @param addInfo String
     */
    public final void setAdditionalInformation( String addInfo) { m_additionalInfo = addInfo; }

    /**
     * Return the path status information as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append("[Path=");
        str.append( m_path);
        str.append(",sts=");
        str.append( m_status);

        if ( m_additionalInfo != null) {
            str.append(",addInfo=");
            str.append( m_additionalInfo);
        }

        str.append("]");

        return str.toString();
    }
}
