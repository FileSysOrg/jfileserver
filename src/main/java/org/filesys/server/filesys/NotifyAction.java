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
 * Change Notification Actions Enum Class
 *
 * @author gkspencer
 */
public enum NotifyAction {
    Added           (1),
    Removed         (2),
    Modified        (3),
    RenamedOldName  (4),
    RenamedNewName  (5),
    AddedStream     (6),
    RemovedStream   (7),
    ModifiedStream  (8);

    private final int actionTyp;

    /**
     * Enum constructor
     *
     * @param action int
     */
    NotifyAction(int action) { actionTyp = action; }

    /**
     * Return the action as an int
     *
     * @return int
     */
    public final int intValue() { return actionTyp; }
}
