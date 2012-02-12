/*
 * @(#)Format.java  
 * 
 * Copyright (c) 2011 Werner Randelshofer, Immensee, Switzerland.
 * All rights reserved.
 * 
 * You may not use, copy or modify this file, except in compliance onlyWith the
 * license agreement you entered into onlyWith Werner Randelshofer.
 * For details see accompanying license terms.
 */
package org.monte.media;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Specifies the format of a media, for example of audio and video.
 *
 * @author Werner Randelshofer
 * @version $Id: Format.java 142 2012-01-12 22:14:09Z werner $
 */
public class Format {

    /**
     * Holds the properties of the format.
     */
    private HashMap<FormatKey, Object> properties;

    /** Creates a new format onlyWith the specified properties. */
    public Format(Map<FormatKey, Object> properties) {
        for (Map.Entry<FormatKey, Object> e : properties.entrySet()) {
            if (!e.getKey().isAssignable(e.getValue())) {
                throw new ClassCastException(e.getValue() + " must be of type " + e.getKey().getValueClass());
            }
        }
        this.properties = new HashMap< FormatKey, Object>(properties);
    }

    /** Creates a new format onlyWith the specified properties.
     * The properties must be given as key value pairs.
     */
    public Format(Object... p) {
        this.properties = new HashMap< FormatKey, Object>();
        for (int i = 0; i < p.length; i += 2) {
            FormatKey key = (FormatKey) p[i];
            if (!key.isAssignable(p[i + 1])) {
                throw new ClassCastException(key+": "+p[i + 1] + " must be of type " + key.getValueClass());
            }
            this.properties.put(key, p[i + 1]);
        }
    }

    public <T> T get(FormatKey<T> key) {
        return (T) properties.get(key);
    }
    public <T> T get(FormatKey<T> key, T defaultValue) {
        return (properties.containsKey(key))?(T)properties.get(key):defaultValue;
    }
    public boolean containsKey(FormatKey key) {
        return properties.containsKey(key);
    }

    /** Gets the properties of the format as an unmodifiable map. */
    public Map<FormatKey, Object> getProperties() {
        return Collections.unmodifiableMap(properties);
    }
    /** Gets the keys of the format as an unmodifiable set. */
    public Set<FormatKey> getKeys() {
        return Collections.unmodifiableSet(properties.keySet());
    }

    /** Returns true if that format matches this format.
     * That is iff all properties defined in both format objects are identical.
     * Properties which are only defined in one of the format objects are not
     * considered.
     * 
     * @param that Another format.
     * @return True if the other format matches this format. 
     */
    public boolean matches(Format that) {
        for (Map.Entry<FormatKey, Object> e : properties.entrySet()) {
            if (that.properties.containsKey(e.getKey())) {
                Object a = e.getValue();
                Object b = that.properties.get(e.getKey());
                if (a != b && a == null || !a.equals(b)) {
                    return false;
                }

            }
        }
        return true;
    }

    /**
     * Creates a new format which contains all properties from this format
     * and additional properties from that format.
     * <p>
     * If a property is specified in both formats, then the property value
     * from this format is used. It overwrites that format.
     * <p>
     * If one of the format has more properties than the other, 
     * then the new format is more specific than this format.
     * 
     * @param that
     * @return That format with properties overwritten by this format. 
     */
    public Format append(Format that) {
        HashMap<FormatKey, Object> m = new HashMap<FormatKey, Object>(this.properties);
        for (Map.Entry<FormatKey, Object> e : that.properties.entrySet()) {
            if (!m.containsKey(e.getKey())) {
                m.put(e.getKey(), e.getValue());
            }
        }
        return new Format(m);
    }
    
    /** Creates a new format which only has the specified keys (or less).
     * <p>
     * If the keys are reduced, then the new format is less specific than this format.
     */
    public Format intersectKeys(FormatKey... keys) {
        HashMap<FormatKey, Object> m = new HashMap<FormatKey, Object>();
        for (FormatKey k:keys) {
            if (properties.containsKey(k)) m.put(k,properties.get(k));
        }
        return new Format(m);
    }
    /** Creates a new format without the specified keys.
     * <p>
     * If the keys are reduced, then the new format is less specific than this format.
     */
    public Format removeKeys(FormatKey... keys) {
        HashMap<FormatKey, Object> m = new HashMap<FormatKey, Object>(properties);
        for (FormatKey k:keys) {
            m.remove(k);
        }
        return new Format(m);
    }
    /** Returns true if the format has the specified keys.
     */
    public Format containsKeys(FormatKey... keys) {
        HashMap<FormatKey, Object> m = new HashMap<FormatKey, Object>(properties);
        for (FormatKey k:keys) {
            m.remove(k);
        }
        return new Format(m);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("Format{");
        boolean isFirst = true;
        for (Map.Entry<FormatKey, Object> e : properties.entrySet()) {
            if (isFirst) {
                isFirst = false;
            } else {
                buf.append(',');
            }
            buf.append(e.getKey().toString());
            buf.append(':');
            buf.append(e.getValue().toString());
        }
        buf.append('}');
        return buf.toString();
    }
}
