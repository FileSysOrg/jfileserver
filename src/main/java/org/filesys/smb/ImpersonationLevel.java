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

package org.filesys.smb;

/**
 * Impersonation Level enum class
 *
 * <p>Contains the possible impersonation level values for an SMB create request</p>
 *
 * @author gkspencer
 */
public enum ImpersonationLevel {
    ANONYMOUS       (0),
    IDENTIFICATION  (1),
    IMPERSONATION   (2),
    DELEGATE        (3),

    INVALID         (-1);

    private final int impLevel;

    /**
     * Enum constructor
     *
     * @param lev int
     */
    ImpersonationLevel(int lev) { impLevel = lev; }

    /**
     * Return the impersonation level as an int
     *
     * @return int
     */
    public final int intValue() { return impLevel; }

    /**
     * Create an impersonation level from an int
     *
     * @param lev int
     * @return ImpersonationLevel
     */
    public static final ImpersonationLevel fromInt(int lev) {
        ImpersonationLevel impLev = INVALID;

        switch( lev) {
            case 0:
                impLev = ANONYMOUS;
                break;
            case 1:
                impLev = IDENTIFICATION;
                break;
            case 2:
                impLev = IMPERSONATION;
                break;
            case 3:
                impLev = DELEGATE;
                break;
        }

        return impLev;
    }
}
