package com.utsusynth.utsu.view;

import com.google.common.base.Optional;
import com.utsusynth.utsu.UtsuController.Mode;
import com.utsusynth.utsu.common.QuantizedNote;
import com.utsusynth.utsu.common.exception.NoteAlreadyExistsException;

/**
 * A way of communicating TrackNote information back to its parent Track.
 */
interface TrackNoteCallback {
	void setHighlighted(TrackNote note, boolean highlighted);
	
	boolean isHighlighted(TrackNote note);
	
	boolean isInBounds(int rowNum);
	
	Optional<String> addSongNote(
			TrackNote note, QuantizedNote toAdd, int rowNum, String lyric) 
			throws NoteAlreadyExistsException;
	
	void removeSongNote(QuantizedNote toRemove);

	void removeTrackNote(TrackNote trackNote);
	
	Mode getCurrentMode();
}
