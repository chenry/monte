/*
 * @(#)MovieReader.java  
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
import java.io.IOException;

/**
 * A simple API for reading movie data (audio and video) from a file.
 * 
 * <p>
 * FIXME - MovieReader should extend Demultiplexer
 *
 * @author Werner Randelshofer
 * @version $Id: MovieReader.java 136 2011-12-26 10:10:26Z werner $
 */
public interface MovieReader {
    /** Returns the number of tracks. */
    public int getTrackCount() throws IOException;
    /** Returns the total duration of the movie in time scale units. */
    public Rational getMovieDuration();
    
   /** Returns the sample number for the specified time. */
    public long timeToSample(int track, Rational seconds);
   /** Returns the time for the specified sample number. */
    public Rational sampleToTime(int track, long sample);
     
    /** Returns the file format. */
    public Format getFileFormat() throws IOException;
    
    /** Returns the media format of the specified track.
     * 
     * @param track Track number.
     * @return The media format of the track.
     */
    public Format getFormat(int track) throws IOException;
    
    /** Returns the number of media data chunks in the specified track. 
     * A chunk contains one or more samples.
     */
    public long getChunkCount(int track) throws IOException;
    
    /** Reads the next sample chunk from the specified track.
     *
     * @param track Track number.
     * @param buffer The buffer into which to store the sample data.
     */
    public void read(int track, Buffer buffer) throws IOException;
    /** Reads the next sample chunk from the next track in playback sequence.
     * The variable buffer.track contains the track number.
     *
     * @param buf The buffer into which to store the sample data.
     */
    //public void read(Buffer buffer) throws IOException;
    
    /** Returns the index of the next track in playback sequence.
     * 
     * @return Index of next track or -1 if end of media reached.
     */
    public int nextTrack() throws IOException;
    
    public void close() throws IOException;

    /** Sets the read time of all tracks to the closest sync sample before or
     * at the specified time.
     * 
     * @param newValue Time in seconds.
     */
    public void setMovieReadTime(Rational newValue);

    /** Returns the current time of the track. */
    public Rational getReadTime(int track) throws IOException;

}
