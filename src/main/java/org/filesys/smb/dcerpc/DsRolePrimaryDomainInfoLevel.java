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
package org.filesys.smb.dcerpc;

/**
 * Ds Role Primary Domain Information Level Class
 *
 * @author gkspencer
 */
public class DsRolePrimaryDomainInfoLevel {

    // Available information levels
    public static final int DsRolePrimaryDomainInfoBasic  = 1;
    public static final int DsRoleUpgradeStatus           = 2;
    public static final int DsRoleOperationState          = 3;

    /**
     * Return the information level as a string
     *
     * @param infoLevel int
     * @return String
     */
    public static String getInfoLevelAsString(int infoLevel) {
        String infoName = null;

        switch( infoLevel) {
            case DsRolePrimaryDomainInfoBasic:
                infoName = "DsRolePrimaryDomainInfoBasic";
                break;
            case DsRoleUpgradeStatus:
                infoName = "DsRoleUpgradeStatus";
                break;
            case DsRoleOperationState:
                infoName = "DsRoleOperationState";
                break;
        }

        return infoName;
    }
}
