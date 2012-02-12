/*
 * @(#)PassThroughCodec
 * 
 * Copyright (c) 2011 Werner Randelshofer, Immensee, Switzerland.
 * All rights reserved.
 * 
 * You may not use, copy or modify this file, except in compliance with the
 * license agreement you entered into with Werner Randelshofer.
 * For details see accompanying license terms. 
 */
package org.monte.media;

import org.monte.media.math.Rational;

/**
 * {@code PassThroughCodec} passes through all buffers in the specified time
 * range.
 *
 * @author Werner Randelshofer
 * @version $Id: PassThroughCodec.java 145 2012-01-12 22:44:30Z werner $
 */
public class PassThroughCodec extends AbstractCodec {

    private Rational startTime;
    private Rational endTime;

    public PassThroughCodec() {
        super(new Format[]{
                    new Format(), //
                },
                new Format[]{
                    new Format(), //
                });
        name = "Pass Through";
    }

    /** Sets the start time of the buffers.
     * 
     * @param newValue Start time. Specify null, to pass through all buffers.
     */
    public void setStartTime(Rational newValue) {
        startTime = newValue;
    }

    public Rational getStartTime() {
        return startTime;
    }

    public Rational getEndTime() {
        return endTime;
    }

    /** Sets the end time of the buffers.
     * 
     * @param newValue Start time. Specify null, to pass through all buffers.
     */
    public void setEndTime(Rational newValue) {
        this.endTime = newValue;
    }

    @Override
    public int process(Buffer in, Buffer out) {
        out.setMetaTo(in);
        out.setDataTo(in);

        Rational bufStartTS = out.timeStamp;
        Rational bufEndTS = out.timeStamp.add(out.sampleDuration.multiply(out.sampleCount));

        if (!out.isFlag(BufferFlag.DISCARD)
                && startTime != null) {
            if (bufEndTS.compareTo(startTime) <= 0) {
                // Buffer is fully outside time range
                out.setFlag(BufferFlag.DISCARD);
            } else if (bufStartTS.compareTo(startTime) < 0) {
                // Buffer is partially outside time range
System.out.println("PassThroughCodec partially before time range ts:"+bufStartTS+".."+bufEndTS+" st:"+startTime);                
            } else {
                // Buffer is fully inside time range
            }
        }
        if (!out.isFlag(BufferFlag.DISCARD)
                && endTime != null) {
            if (bufStartTS.compareTo(endTime) >= 0) {
                // Buffer is fully outside time range
                out.setFlag(BufferFlag.DISCARD);
            } else if (bufEndTS.compareTo(endTime) > 0) {
                // Buffer is partially outside time range
System.out.println("PassThroughCodec partially after time range ts:"+bufStartTS+".."+bufEndTS+" et:"+endTime);                
            } else {
                // Buffer is fully inside time range
            }
        }

        return CODEC_OK;
    }
}
