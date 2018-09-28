/*
 * Copyright (C) 2005-2010 Alfresco Software Limited.
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
package org.springframework.extensions.config;

import java.util.List;
import java.util.Map;

/**
 * Configuration Element Class
 * 
 * <p>The methods must match the methods of the ConfigElement interface in the Alfresco code.
 */
public interface ConfigElement {

  /**
   * Returns the name of this config element
   * 
   * @return Name of this config element
   */
  public String getName();

  /**
   * Gets the value of the attribute with the given name
   * 
   * @param name
   *            The name of the attribute to get the value for
   * @return The value of the attrbiute or null if the attribute doesn't exist
   */
  public String getAttribute(String name);

  /**
   * Returns the list of attributes held by this config element
   * 
   * @return The list of attributes
   */
  public Map<String, String> getAttributes();

  /**
   * Determines whether the config element has the named attribute
   * 
   * @param name
   *            Name of the attribute to check existence for
   * @return true if it exists, false otherwise
   */
  public boolean hasAttribute(String name);

  /**
   * Returns the number of attributes this config element has
   * 
   * @return The number of attributes
   */
  public int getAttributeCount();

  /**
   * Gets the value of this config element. If this config element has
   * children then this method may return null
   * 
   * @return Value of this config element or null if there is no value
   */
  public String getValue();

  /**
   * Returns a child config element of the given name 
   * 
   * @param name The name of the config element to retrieve
   * @return The ConfigElement or null if it does not exist
   */
  public ConfigElement getChild(String name);

  /**
   * Returns a list of children held by this ConfigElement
   * 
   * @return The list of children.
   */
  public List<ConfigElement> getChildren();

  /**
   * Determines whether this config element has any children. It is more
   * effecient to call this method rather than getChildren().size() as a
   * collection is not created if it is not required
   * 
   * @return true if it has children, false otherwise
   */
  public boolean hasChildren();

  /**
   * Returns the number of children this config element has
   * 
   * @return The number of children
   */
  public int getChildCount();

  /**
   * Combines the given config element with this config element and returns a
   * new instance containing the resulting combination. The combination of the
   * two objects MUST NOT change this instance.
   * 
   * @param configElement
   *            The config element to combine into this one
   * @return The combined ConfigElement
   */
  public ConfigElement combine(ConfigElement configElement);

  /**
   * Set the element name
   * 
   * @param name String
   */
  public void setName( String name);

  /**
   * Set the value
   * 
   * @param value String
   */
  public void setValue( String value);

  /**
   * Add an attribute
   * 
   * @param attrName String
   * @param attrVal String
   */
  public void addAttribute( String attrName, String attrVal);

  /**
   * Add a child element
   * 
   * @param child ConfigElement
   */
  public void addChild( ConfigElement child);
}
