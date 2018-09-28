/*
 * Copyright (C) 2018 GK Spencer
 *
 * JFileSrv is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JFileSrv is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with JFileSrv. If not, see <http://www.gnu.org/licenses/>.
 */

package org.springframework.extensions.config.element;

import org.springframework.extensions.config.ConfigElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ConfigElementAdapter class
 *
 */
public class ConfigElementAdapter implements ConfigElement {

    //  Element name and value
    private String m_name;
    private String m_value;

    //  Attributes
    private Map<String, String> m_attributes;

    //  Children
    private List<ConfigElement> m_children;

    /**
     * Class constructor
     *
     * @param name String
     * @param value String
     */
    public ConfigElementAdapter( String name, String value) {
        m_name  = name;
        m_value = value;
    }

    /**
     * Returns the name of this config element
     *
     * @return Name of this config element
     */
    public String getName() {
        return m_name;
    }

    /**
     * Gets the value of the attribute with the given name
     *
     * @param name
     *            The name of the attribute to get the value for
     * @return The value of the attrbiute or null if the attribute doesn't exist
     */
    public String getAttribute(String name) {
        if ( m_attributes == null)
            return null;
        return m_attributes.get( name);
    }

    /**
     * Returns the list of attributes held by this config element
     *
     * @return The list of attributes
     */
    public Map<String, String> getAttributes() {
        return m_attributes;
    }

    /**
     * Determines whether the config element has the named attribute
     *
     * @param name
     *            Name of the attribute to check existence for
     * @return true if it exists, false otherwise
     */
    public boolean hasAttribute(String name) {
        if ( m_attributes != null)
            return m_attributes.containsKey( name);
        return false;
    }

    /**
     * Returns the number of attributes this config element has
     *
     * @return The number of attributes
     */
    public int getAttributeCount() {
        if ( m_attributes != null)
            return m_attributes.size();
        return 0;
    }

    /**
     * Gets the value of this config element. If this config element has
     * children then this method may return null
     *
     * @return Value of this config element or null if there is no value
     */
    public String getValue() {
        return m_value;
    }

    /**
     * Returns a child config element of the given name
     *
     * @param name The name of the config element to retrieve
     * @return The ConfigElement or null if it does not exist
     */
    public ConfigElement getChild(String name) {
        if ( m_children != null) {
            for ( ConfigElement child : m_children) {
                if ( child.getName().equals( name))
                    return child;
            }
        }
        return null;
    }

    /**
     * Returns a list of children held by this ConfigElement
     *
     * @return The list of children.
     */
    public List<ConfigElement> getChildren() {
        return m_children;
    }

    /**
     * Determines whether this config element has any children. It is more
     * effecient to call this method rather than getChildren().size() as a
     * collection is not created if it is not required
     *
     * @return true if it has children, false otherwise
     */
    public boolean hasChildren() {
        if ( m_children == null)
            return false;
        return m_children.size() > 0 ? true : false;
    }

    /**
     * Returns the number of children this config element has
     *
     * @return The number of children
     */
    public int getChildCount() {
        if ( m_children != null)
            return m_children.size();
        return 0;
    }

    /**
     * Combines the given config element with this config element and returns a
     * new instance containing the resulting combination. The combination of the
     * two objects MUST NOT change this instance.
     *
     * @param configElement
     *            The config element to combine into this one
     * @return The combined ConfigElement
     */
    public ConfigElement combine(ConfigElement configElement) {
        return null;
    }

    /**
     * Set the element name
     *
     * @param name String
     */
    public void setName( String name) {
        m_name = name;
    }

    /**
     * Set the value
     *
     * @param value String
     */
    public void setValue( String value) {
        m_value = value;
    }

    /**
     * Add an attribute
     *
     * @param attrName String
     * @param attrVal String
     */
    public void addAttribute( String attrName, String attrVal) {
        if ( m_attributes == null)
            m_attributes = new HashMap<String, String>();
        m_attributes.put( attrName, attrVal);
    }

    /**
     * Add a child element
     *
     * @param child ConfigElement
     */
    public void addChild( ConfigElement child) {
        if ( m_children == null)
            m_children = new ArrayList<ConfigElement>();
        m_children.add( child);
    }

    /**
     * Return the configuration element as a string
     *
     * @return String
     */
    public String toString() {
        StringBuilder str = new StringBuilder();

        str.append( "[");
        str.append( getName());
        str.append( "=");
        str.append( getValue());
        str.append( ",Attrs=");
        str.append( getAttributeCount());
        str.append( ",Children=");
        str.append( getChildCount());
        str.append( "]");

        return str.toString();
    }
}
