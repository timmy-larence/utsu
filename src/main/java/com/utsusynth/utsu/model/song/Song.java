package com.utsusynth.utsu.model.song;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import com.google.common.base.Optional;
import com.utsusynth.utsu.common.RegionBounds;
import com.utsusynth.utsu.common.data.MutateResponse;
import com.utsusynth.utsu.common.data.NoteData;
import com.utsusynth.utsu.common.data.NoteUpdateData;
import com.utsusynth.utsu.common.data.PitchbendData;
import com.utsusynth.utsu.common.exception.NoteAlreadyExistsException;
import com.utsusynth.utsu.common.utils.PitchUtils;
import com.utsusynth.utsu.model.song.pitch.PitchCurve;
import com.utsusynth.utsu.model.voicebank.FSVoicebank;
import com.utsusynth.utsu.model.voicebank.VoicebankManager;

import javafx.beans.property.SimpleObjectProperty;

/**
 * In-code representation of a song. Compatible with UST versions 1.2 and 2.0.
 */
public class Song {
    public static final int MIN_TEMPO = 50;
    public static final int MAX_TEMPO = 260;
    
    private SimpleObjectProperty<FSVoicebank> voicebank;
    private final NoteStandardizer standardizer;

    // Settings. (Anything marked with [#SETTING])
    // TODO:Insert Time signatures here
    private double tempo;
    private String projectName;
    private File outputFile;
    private String flags;
    private boolean mode2 = true;
    private Optional<File> instrumental; // Not yet written to UST.

    // Set to a value after rendering song, INVALID whenever song changes.
    private RegionBounds lastRenderedRegion = RegionBounds.INVALID;

    // Notes. (Anything marked with [#0000]-[#9999], [#TRACKEND] marks the end of these)
    private NoteList noteList;

    // Pitchbends, kept in a format suitable for rendering.
    private PitchCurve pitchbends;

    public class Builder {
        private final Song newSong;
        private final NoteList.Builder noteListBuilder;

        private Builder(Song newSong) {
            this.newSong = newSong;
            this.noteListBuilder = newSong.noteList.toBuilder();
        }

        public Builder setTempo(double tempo) {
            // Use default tempo value if file has unreasonable tempo.
            if (tempo >= MIN_TEMPO && tempo <= MAX_TEMPO) {
                newSong.tempo = tempo;
            } else {
                System.out.println("Warning: Tempo of " + tempo + " is out of bounds.");
            }
            return this;
        }

        public Builder setProjectName(String projectName) {
            newSong.projectName = projectName;
            return this;
        }

        public Builder setOutputFile(File outputFile) {
            newSong.outputFile = outputFile;
            return this;
        }

        public Builder setVoicebank(FSVoicebank voicebank) {
            newSong.setVoicebank(voicebank);
            return this;
        }

        public Builder setFlags(String flags) {
            newSong.flags = flags;
            return this;
        }

        public Builder setMode2(boolean mode2) {
            newSong.mode2 = mode2;
            return this;
        }

        public Builder setInstrumental(Optional<File> instrumental) {
            newSong.instrumental = instrumental;
            return this;
        }

        public Builder addNote(Note note) {
            Optional<Note> prevNote = noteListBuilder.getLatestNote();

            // Add this note to the list of notes.
            noteListBuilder.appendNote(note);

            // Add pitchbends for this note.
            newSong.pitchbends.addPitchbends(
                    noteListBuilder.getLatestDelta(),
                    note.getLength(),
                    note.getPitchbends(),
                    prevNote.isPresent() ? prevNote.get().getNoteNum() : note.getNoteNum(),
                    note.getNoteNum());
            return this;
        }

        public Builder addRestNote(Note note) {
            noteListBuilder.appendRestNote(note);
            return this;
        }

        public Builder addInvalidNote(Note note) {
            noteListBuilder.appendInvalidNote(note.getDelta(), note.getLength());
            return this;
        }

        public Song build() {
            noteListBuilder.standardize(newSong.standardizer, newSong.getVoicebank());
            newSong.noteList = noteListBuilder.build();
            return newSong;
        }
    }

    public Song(
            FSVoicebank voicebank,
            NoteStandardizer standardizer,
            NoteList songNoteList,
            PitchCurve pitchbends) {
        setVoicebank(voicebank);
        this.standardizer = standardizer;
        this.noteList = songNoteList;
        this.pitchbends = pitchbends;
        this.outputFile = new File("outputFile");
        this.tempo = 125.0;
        this.projectName = "(no title)";
        this.flags = "";
        this.instrumental = Optional.absent();
    }

    public Builder toBuilder() {
        // Returns the builder of a new Song with this one's attributes.
        // The old Song's noteList and pitchbends objects are used in the new Song.
        return new Builder(
                new Song(this.getVoicebank(), this.standardizer, this.noteList, this.pitchbends))
                        .setTempo(this.tempo).setProjectName(this.projectName)
                        .setOutputFile(this.outputFile).setFlags(this.flags).setMode2(this.mode2)
                        .setInstrumental(this.instrumental);
    }

    /**
     * Adds a note or notes to the song object.
     * 
     * @param toAdd In-order list of notes to add.
     * @throws NoteAlreadyExistsException
     */
    public void addNotes(List<NoteData> notesToAdd) {
        if (notesToAdd.isEmpty()) {
            System.out.println("Error: Add notes called on empty list!");
            return;
        }
        NoteNode curNode = null; // Where to start search for place to insert new note.
        int searchStartMs = 0;
        for (NoteData toAdd : notesToAdd) {
            Note note = new Note();
            // New note's delta/length may be overridden while inserting into note list.
            note.setDelta(toAdd.getPosition());
            note.safeSetDuration(toAdd.getDuration());
            note.safeSetLength(toAdd.getDuration());
            note.setLyric(toAdd.getLyric());
            note.setNoteNum(PitchUtils.pitchToNoteNum(toAdd.getPitch()));
            if (toAdd.getEnvelope().isPresent()) {
                note.setEnvelope(toAdd.getEnvelope().get());
            }
            if (toAdd.getPitchbend().isPresent()) {
                note.setPitchbends(toAdd.getPitchbend().get());
            }
            if (toAdd.getConfigData().isPresent()) {
                note.setConfigData(toAdd.getConfigData().get());
            }

            int positionMs = toAdd.getPosition();
            try {
                if (curNode == null) {
                    curNode = this.noteList.insertNote(note, positionMs);
                } else {
                    curNode = this.noteList.insertNote(note, positionMs, curNode, searchStartMs);
                }
                searchStartMs = positionMs - curNode.getNote().getDelta();
            } catch (NoteAlreadyExistsException e) {
                // Swallow this for now.
            }
        }
    }

    /** Removes all notes at the specified positions from the song object. */
    public MutateResponse removeNotes(Set<Integer> positions) {
        if (positions.isEmpty()) {
            System.out.println("Error: Remove notes called on empty collection!");
            return null;
        }

        HashSet<NoteUpdateData> removedNotes = new HashSet<>(); // Return value.
        int firstNeighbor = Integer.MAX_VALUE;
        int lastNeighbor = Integer.MIN_VALUE;

        NoteNode curNode;
        for (int position : positions) {
            curNode = this.noteList.removeNote(position);
            this.pitchbends.removePitchbends(
                    position,
                    curNode.getNote().getDuration(),
                    curNode.getNote().getPitchbends());
            removedNotes.add(curNode.getNote().getUpdateData(position));

            if (curNode.getPrev().isPresent()) {
                int prevPosition = position - curNode.getNote().getDelta();
                if (prevPosition < firstNeighbor && !positions.contains(prevPosition)) {
                    firstNeighbor = prevPosition;
                }
                if (prevPosition > lastNeighbor && !positions.contains(prevPosition)) {
                    lastNeighbor = prevPosition;
                }
            }
            if (curNode.getNext().isPresent()) {
                int nextPosition = position + curNode.getNote().getLength();
                if (nextPosition < firstNeighbor && !positions.contains(nextPosition)) {
                    firstNeighbor = nextPosition;
                }
                if (nextPosition > lastNeighbor && !positions.contains(nextPosition)) {
                    lastNeighbor = nextPosition;
                }
            }
        }

        Optional<NoteUpdateData> prevNote = Optional.absent();
        Optional<NoteUpdateData> nextNote = Optional.absent();
        // If no neighbors were detected, skip this step.
        if (lastNeighbor >= firstNeighbor) {
            Note firstNote = noteList.getNote(firstNeighbor).getNote();
            prevNote = Optional.of(firstNote.getUpdateData(firstNeighbor));
            Note lastNote = noteList.getNote(lastNeighbor).getNote();
            nextNote = Optional.of(lastNote.getUpdateData(lastNeighbor));
        }

        return new MutateResponse(removedNotes, prevNote, nextNote);
    }

    /** Modifies a note in-place without changing its lyric, position, or duration. */
    public NoteUpdateData modifyNote(NoteData toModify) {
        int positionMs = toModify.getPosition();
        NoteNode node = this.noteList.getNote(positionMs);
        Note note = node.getNote();
        if (toModify.getEnvelope().isPresent()) {
            note.setEnvelope(toModify.getEnvelope().get());
        }
        if (toModify.getPitchbend().isPresent()) {
            this.pitchbends.removePitchbends(positionMs, note.getDuration(), note.getPitchbends());
            PitchbendData newPitchbend = toModify.getPitchbend().get();
            note.setPitchbends(newPitchbend);

            int prevNoteNum =
                    node.getPrev().isPresent() ? node.getPrev().get().getNote().getNoteNum()
                            : note.getNoteNum();
            this.pitchbends.addPitchbends(
                    positionMs,
                    note.getDuration(),
                    newPitchbend,
                    prevNoteNum,
                    note.getNoteNum());
        }
        return note.getUpdateData(positionMs);
    }

    public MutateResponse standardizeNotes(int firstPosition, int lastPosition) {
        LinkedList<NoteUpdateData> updatedNotes = new LinkedList<>();
        Optional<NoteUpdateData> prevNeighbor = Optional.absent();
        Optional<NoteUpdateData> nextNeighbor = Optional.absent();

        NoteNode startNode = this.noteList.getNote(lastPosition);
        if (startNode == null) {
            // TODO: Handle this better.
            System.out.println("Could not find last note when standardizing notes!");
            return new MutateResponse(updatedNotes, Optional.absent(), Optional.absent());
        }

        // Include the next neighbor of the last note, if present.
        int startPosition = lastPosition;
        if (startNode.getNext().isPresent()) {
            startPosition += startNode.getNote().getLength();
            startNode = startNode.getNext().get();
            nextNeighbor = Optional.of(startNode.getNote().getUpdateData(startPosition));
        }

        int curPosition = startPosition;
        Optional<NoteNode> curNode = Optional.of(startNode);
        while (curNode.isPresent()) {
            Note note = curNode.get().getNote();
            // Standardize.
            curNode.get().standardize(standardizer, getVoicebank());
            if (!nextNeighbor.isPresent() || curPosition < startPosition) {
                updatedNotes.addFirst(note.getUpdateData(curPosition));
            }
            // Pitch curve corrections.
            int prevNoteNum = curNode.get().getPrev().isPresent()
                    ? curNode.get().getPrev().get().getNote().getNoteNum()
                    : note.getNoteNum();
            this.pitchbends.removePitchbends(curPosition, note.getDuration(), note.getPitchbends());
            this.pitchbends.addPitchbends(
                    curPosition,
                    note.getDuration(),
                    note.getPitchbends(),
                    prevNoteNum,
                    note.getNoteNum());
            // Break if going further back would pass firstPosition.
            curPosition -= note.getDelta();
            curNode = curNode.get().getPrev();
            if (curPosition < firstPosition) {
                break;
            }
        }

        // Include the prev neighbor of the first note, if present. No need to change pitch.
        if (curNode.isPresent()) {
            curNode.get().standardize(standardizer, getVoicebank());
            prevNeighbor = Optional.of(curNode.get().getNote().getUpdateData(curPosition));
        }
        return new MutateResponse(updatedNotes, prevNeighbor, nextNeighbor);
    }

    public LinkedList<NoteData> getNotes() {
        LinkedList<NoteData> notes = new LinkedList<>();
        NoteIterator iterator = noteList.iterator();
        int totalDelta = 0;
        while (iterator.hasNext()) {
            Note note = iterator.next();
            totalDelta += note.getDelta();
            notes.add(
                    new NoteData(
                            totalDelta,
                            note.getDuration(),
                            PitchUtils.noteNumToPitch(note.getNoteNum()),
                            note.getLyric(),
                            Optional.of(note.getTrueLyric()),
                            Optional.of(note.getEnvelope()),
                            Optional.of(note.getPitchbends()),
                            Optional.of(note.getConfigData())));
        }
        return notes;
    }

    public Optional<Integer> getNextNote(int currentPos) {
        NoteNode curNode = noteList.getNote(currentPos);
        if (curNode != null && curNode.getNext().isPresent()) {
            return Optional.of(currentPos + curNode.getNote().getLength());
        }
        return Optional.absent();
    }

    public Optional<Integer> getPrevNote(int currentPos) {
        NoteNode curNode = noteList.getNote(currentPos);
        if (curNode != null && curNode.getPrev().isPresent()) {
            return Optional.of(currentPos - curNode.getNote().getDelta());
        }
        return Optional.absent();
    }

    // Can be changed without converting song to a builder and back.
    public void setRendered(RegionBounds region) {
        this.lastRenderedRegion = region;
    }

    public RegionBounds getLastRenderedRegion() {
        return lastRenderedRegion;
    }

    public String getProjectName() {
        return projectName;
    }

    public File getOutputFile() {
        return outputFile;
    }

    public File getVoiceDir() {
        return getVoicebank().getLocation();
    }

    public FSVoicebank getVoicebank() {
        return voicebank.get();
    }
    
    public void setVoicebank(FSVoicebank voicebank) {
        this.voicebank.set(voicebank);
    }

    public NoteIterator getNoteIterator() {
        return noteList.iterator();
    }

    public NoteIterator getNoteIterator(RegionBounds bounds) {
        return noteList.boundedIterator(bounds);
    }

    public String getFlags() {
        return flags;
    }

    public double getTempo() {
        return tempo;
    }

    public boolean getMode2() {
        return mode2;
    }

    public Optional<File> getInstrumental() {
        return instrumental;
    }

    public int getNumNotes() {
        return noteList.getSize();
    }

    public String getPitchString(int firstPitchStep, int lastPitchStep, int noteNum) {
        return pitchbends.renderPitchbends(firstPitchStep, lastPitchStep, noteNum);
    }
}
