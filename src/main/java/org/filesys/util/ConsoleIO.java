/*
 * Copyright (C) 2006-2010 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */

package org.filesys.util;

import java.io.IOException;

/**
 * Console IO Class
 *
 * <p>Provides a wrapper class for conole I/O functions to allow Java and J#/.NET versions.
 *
 * @author gkspencer
 */
public class ConsoleIO {

    /**
     * Check if the console input is connected to a valid stream
     *
     * @return boolean
     */
    public final static boolean isValid() {
        try {
            System.in.available();
            return true;
        }
        catch (IOException ex) {
        }
        return false;
    }

    /**
     * Check if there is input available
     *
     * @return int
     */
    public final static int available() {
        try {
            return System.in.available();
        }
        catch (Exception ex) {
        }
        return -1;
    }

    /**
     * Read a character from the console
     *
     * @return int
     */
    public final static int readCharacter() {
        try {
            return System.in.read();
        }
        catch (Exception ex) {
        }
        return -1;
    }
}
