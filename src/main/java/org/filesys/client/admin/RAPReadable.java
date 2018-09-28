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

package org.filesys.client.admin;

import java.util.*;

/**
 * RAP Readable Interface
 * 
 * <p>
 * A class that implements the RAP readable interface can load itself from an object list.
 * 
 * @author gkspencer
 */
public interface RAPReadable {

	/**
	 * Read the object state from the supplied list of objects
	 * 
	 * @param objs Vector
	 * @param infoLevel int
	 */
	public void readRAPObject(int infoLevel, Vector objs);
}
