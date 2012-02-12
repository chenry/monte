/*
 * @(#)AbortException.java  1.0  1999-10-19
 *
 * Copyright (c) 1999 Werner Randelshofer, Immensee, Switzerland.
 * All rights reserved.
 *
 * You may not use, copy or modify this file, except in compliance with the
 * license agreement you entered into with Werner Randelshofer.
 * For details see accompanying license terms.
 */
package org.monte.media;

/**
This exception is thrown when the production of an image
has been aborted.

@author  Werner Randelshofer, Hausmatt 10, CH-6405 Immensee, Switzerland

@version  1998-10-19
 */
public class AbortException extends Exception {

    /**
    Creates a new exception.
     */
    public AbortException() {
        super();
    }

    /**
    Creates a new exception.

     */
    public AbortException(String message) {
        super(message);
    }
}
