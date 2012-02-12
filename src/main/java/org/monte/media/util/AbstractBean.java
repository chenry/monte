/*
 * @(#)AbstractBean.java
 *
 * Copyright (c) 2011 Werner Randelshofer, Immensee, Switzerland.
 * All rights reserved.
 * 
 * You may not use, copy or modify this file, except in compliance with the
 * license agreement you entered into with Werner Randelshofer.
 * For details see accompanying license terms.
 */
package org.monte.media.util;

import java.beans.*;

/**
 * This abstract class can be extended to implement a JavaBean.
 * <p>
 * Implements the methods required for adding and removing property change
 * listeners.
 *
 * @author Werner Randelshofer
 * @version $Id: AbstractBean.java 134 2011-12-02 16:23:00Z werner $
 */
public class AbstractBean extends Object implements java.io.Serializable, Cloneable {

    protected PropertyChangeSupport propertySupport = new PropertyChangeSupport(this);

    /** Adds a {@code PropertyChangeListener} which can optionally be wrapped
     * into a {@code WeakPropertyChangeListener}.
     * @param listener
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertySupport.addPropertyChangeListener(listener);
    }

    /** Adds a {@code PropertyChangeListener} which can optionally be wrapped
     * into a {@code WeakPropertyChangeListener}.
     * @param listener
     */
    public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        propertySupport.addPropertyChangeListener(propertyName, listener);
    }

    /** Removes a {@code PropertyChangeListener}. If the listener was added
     * wrapped into a {@code WeakPropertyChangeListener}, the
     * {@code WeakPropertyChangeListener} is removed.
     *
     * @param listener
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        // Removes a property change listener from our list.
        // We need a somewhat complex procedure here in case a listener
        // has been registered using addPropertyChangeListener(new WeakPropertyChangeListener(listener));
        for (PropertyChangeListener l : propertySupport.getPropertyChangeListeners()) {
            if (l == listener) {
                propertySupport.removePropertyChangeListener(l);
                break;
            }
            if (l instanceof WeakPropertyChangeListener) {
                WeakPropertyChangeListener wl = (WeakPropertyChangeListener) l;
                PropertyChangeListener target = wl.getTarget();
                if (target == listener) {
                    propertySupport.removePropertyChangeListener(l);
                    break;
                }
            }
        }
    }

    /** Removes a {@code PropertyChangeListener}. If the listener was added
     * wrapped into a {@code WeakPropertyChangeListener}, the
     * {@code WeakPropertyChangeListener} is removed.
     *
     * @param listener
     */
    public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
        // Removes a property change listener from our list.
        // We need a somewhat complex procedure here in case a listener
        // has been registered using addPropertyChangeListener(propertyName, new WeakPropertyChangeListener(listener));
        for (PropertyChangeListener l : propertySupport.getPropertyChangeListeners(propertyName)) {
            if (l == listener) {
                propertySupport.removePropertyChangeListener(propertyName, l);
                break;
            }
            if (l instanceof WeakPropertyChangeListener) {
                WeakPropertyChangeListener wl = (WeakPropertyChangeListener) l;
                PropertyChangeListener target = wl.getTarget();
                if (target == listener) {
                    propertySupport.removePropertyChangeListener(propertyName, l);
                    break;
                }
            }
        }
    }

    protected void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {
        propertySupport.firePropertyChange(propertyName, oldValue, newValue);
    }

    protected void firePropertyChange(String propertyName, int oldValue, int newValue) {
        propertySupport.firePropertyChange(propertyName, oldValue, newValue);
    }

    protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        propertySupport.firePropertyChange(propertyName, oldValue, newValue);
    }

    @Override
    public AbstractBean clone() {
        AbstractBean that;
        try {
            that = (AbstractBean) super.clone();
        } catch (CloneNotSupportedException ex) {
            InternalError error = new InternalError("Clone failed");
            error.initCause(ex);
            throw error;
        }
        that.propertySupport = new PropertyChangeSupport(that);
        return that;
    }

    public PropertyChangeListener[] getPropertyChangeListeners() {
        return propertySupport.getPropertyChangeListeners();
    }
}