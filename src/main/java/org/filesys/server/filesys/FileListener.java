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

package org.filesys.server.filesys;

import org.filesys.server.SrvSession;

/**
 * File Listener Interface.
 *
 * <p>Generates events when files are opened/closed on the server.
 *
 * @author gkspencer
 */
public interface FileListener {

    /**
     * File has been closed.
     *
     * @param sess SrvSession
     * @param file NetworkFile
     */
    void fileClosed(SrvSession sess, NetworkFile file);

    /**
     * File has been opened.
     *
     * @param sess SrvSession
     * @param file NetworkFile
     */
    void fileOpened(SrvSession sess, NetworkFile file);
}
