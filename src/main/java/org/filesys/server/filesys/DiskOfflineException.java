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

import java.io.IOException;

/**
 * <p>This exception may be thrown by a disk interface when the filesystem is offline data is not available due to the file being archived
 * or the repository being unavailable.
 *
 * @author gkspencer
 */
public class DiskOfflineException extends IOException {

    private static final long serialVersionUID = -3055330216300723042L;

    /**
     * Class constructor.
     */
    public DiskOfflineException() {
        super();
    }

    /**
     * Class constructor.
     *
     * @param s java.lang.String
     */
    public DiskOfflineException(String s) {
        super(s);
    }
}
