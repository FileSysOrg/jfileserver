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

package org.filesys.server.filesys;

/**
 * Create Disposition enum class
 *
 * <p>Contains the values for create options for an NT or SMB v2 create request</p>
 *
 * @author gkspencer
 */
public enum CreateDisposition {
    SUPERSEDE       (0),
    OPEN            (1),
    CREATE          (2),
    OPEN_IF         (3),
    OVERWRITE       (4),
    OVERWRITE_IF    (5),

    INVALID         (-1);

    private final int createDisp;

    /**
     * Enum constructor
     *
     * @param typ int
     */
    CreateDisposition(int typ) { createDisp = typ; }

    /**
     * Return the create disposition as int
     *
     * @return int
     */
    public final int intValue() { return createDisp; }

    /**
     * Create a create disposition from an int
     *
     * @param typ int
     * @return CreateDisposition
     */
    public static final CreateDisposition fromInt(int typ) {
        CreateDisposition disp = INVALID;

        switch( typ) {
            case 0:
                disp = SUPERSEDE;
                break;
            case 1:
                disp = OPEN;
                break;
            case 2:
                disp = CREATE;
                break;
            case 3:
                disp = OPEN_IF;
                break;
            case 4:
                disp = OVERWRITE;
                break;
            case 5:
                disp = OVERWRITE_IF;
                break;
        }

        return disp;
    }
}
