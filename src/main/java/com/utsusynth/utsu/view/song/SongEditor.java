package com.utsusynth.utsu.view.song;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.utsusynth.utsu.common.RegionBounds;
import com.utsusynth.utsu.common.data.EnvelopeData;
import com.utsusynth.utsu.common.data.MutateResponse;
import com.utsusynth.utsu.common.data.NoteData;
import com.utsusynth.utsu.common.data.NoteUpdateData;
import com.utsusynth.utsu.common.data.PitchbendData;
import com.utsusynth.utsu.common.exception.NoteAlreadyExistsException;
import com.utsusynth.utsu.common.quantize.Quantizer;
import com.utsusynth.utsu.common.quantize.Scaler;
import com.utsusynth.utsu.common.utils.PitchUtils;
import com.utsusynth.utsu.common.utils.RoundUtils;
import com.utsusynth.utsu.view.song.note.Note;
import com.utsusynth.utsu.view.song.note.NoteCallback;
import com.utsusynth.utsu.view.song.note.NoteFactory;
import com.utsusynth.utsu.view.song.note.envelope.EnvelopeCallback;
import com.utsusynth.utsu.view.song.note.pitch.PitchbendCallback;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Group;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public class SongEditor {
    private final PlaybackBarManager playbackManager;
    private final ContextMenu editorContextMenu;
    private final SongClipboard clipboard;
    private final NoteFactory noteFactory;
    private final NoteMap noteMap;
    private final Quantizer quantizer;
    private final Scaler scaler;

    // Whether the vibrato editor is active for this song editor.
    private final BooleanProperty vibratoEditor;

    private HBox measures;
    private HBox dynamics;
    private Rectangle selection;
    private int numMeasures;
    private SongCallback model;

    // Temporary cache values.
    private enum SubMode {
        NOT_DRAGGING, DRAG_CREATE, DRAG_SELECT,
    }

    private SubMode subMode;
    private double curX;
    private double curY;

    @Inject
    public SongEditor(
            PlaybackBarManager playbackManager,
            SongClipboard clipboard,
            NoteFactory trackNoteFactory,
            NoteMap noteMap,
            Quantizer quantizer,
            Scaler scaler) {
        this.playbackManager = playbackManager;
        this.clipboard = clipboard;
        this.noteFactory = trackNoteFactory;
        this.noteMap = noteMap;
        this.quantizer = quantizer;
        this.scaler = scaler;

        vibratoEditor = new SimpleBooleanProperty(false);

        // Initialize context menu here so we can reuse it everywhere.
        editorContextMenu = new ContextMenu();
        MenuItem pasteItem = new MenuItem("Paste");
        pasteItem.disableProperty().bind(clipboard.clipboardFilledProperty().not());
        pasteItem.setOnAction(event -> pasteSelected());
        SeparatorMenuItem separator = new SeparatorMenuItem();
        MenuItem selectAllItem = new MenuItem("Select All");
        selectAllItem.setOnAction(event -> selectAll());
        MenuItem deselectItem = new MenuItem("Deselect");
        deselectItem.setOnAction(event -> playbackManager.clearHighlights());
        editorContextMenu.getItems().addAll(pasteItem, separator, selectAllItem, deselectItem);
    }

    /** Initialize track with data from the controller. Not song-specific. */
    public void initialize(SongCallback callback) {
        this.model = callback;
    }

    /** Initialize track with data for a specific song. */
    public HBox createNewTrack(List<NoteData> notes) {
        clearTrack();
        if (notes.isEmpty()) {
            return measures;
        }

        // Add as many octaves as needed.
        NoteData lastNote = notes.get(notes.size() - 1);
        setNumMeasures((lastNote.getPosition() / Quantizer.COL_WIDTH / 4) + 4);

        // Add all notes.
        NoteData prevNote = notes.get(0);
        for (NoteData note : notes) {
            Note newNote = noteFactory.createNote(note, noteCallback, vibratoEditor);
            int position = note.getPosition();
            try {
                noteMap.putNote(position, newNote);
                if (note.getEnvelope().isPresent()) {
                    noteMap.putEnvelope(
                            position,
                            note.getEnvelope().get(),
                            getEnvelopeCallback(position));
                }
                if (note.getPitchbend().isPresent()) {
                    noteMap.putPitchbend(
                            position,
                            prevNote.getPitch(),
                            note.getPitchbend().get(),
                            getPitchbendCallback(position),
                            vibratoEditor);
                }
            } catch (NoteAlreadyExistsException e) {
                // TODO: Throw an error here?
                System.out.println("UST read found two notes in the same place :(");
            }
            noteMap.addNoteElement(newNote);
            prevNote = note;
        }
        return measures;
    }

    public Group getNotesElement() {
        return noteMap.getNotesElement();
    }

    public HBox getDynamicsElement() {
        if (dynamics == null) {
            // TODO: Handle this;
            System.out.println("Dynamics element is empty!");
        }
        return dynamics;
    }

    public Group getEnvelopesElement() {
        return noteMap.getEnvelopesElement();
    }

    public Group getPitchbendsElement() {
        return noteMap.getPitchbendsElement();
    }

    public Group getPlaybackElement() {
        return playbackManager.getElement();
    }

    public Rectangle getSelectionElement() {
        return selection;
    }

    /** Start the playback bar animation. It will end on its own. */
    public DoubleProperty startPlayback(RegionBounds rendered, Duration duration) {
        int firstPosition = noteMap.getFirstPosition(rendered);
        int lastPosition = noteMap.getLastPosition(rendered);
        if (noteMap.hasNote(firstPosition) && noteMap.hasNote(lastPosition)) {
            int firstNoteStart = noteMap.getEnvelope(firstPosition).getStartMs();
            int renderStart = Math.min(firstNoteStart, rendered.getMinMs());
            int renderEnd = lastPosition + noteMap.getNote(lastPosition).getDurationMs();
            return playbackManager
                    .startPlayback(duration, new RegionBounds(renderStart, renderEnd));
        }
        return null;
    }

    /** Attempts to pause. Does nothing if there is no ongoing playback. */
    public void pausePlayback() {
        playbackManager.pausePlayback();
    }

    /** Attempts to resume. Does nothing if there is no ongoing paused playback. */
    public void resumePlayback() {
        playbackManager.resumePlayback();
    }

    /** Manually stop any ongoing playback bar animation. Idempotent. */
    public void stopPlayback() {
        playbackManager.stopPlayback();
    }

    public double getWidthX() {
        double measureWidth = 4 * scaler.scaleX(Quantizer.COL_WIDTH);
        return measureWidth * (numMeasures + 1); // Include pre-roll.
    }

    public BooleanProperty clibboardFilledProperty() {
        return clipboard.clipboardFilledProperty();
    }

    public BooleanProperty isAnythingSelectedProperty() {
        return playbackManager.isAnythingHighlightedProperty();
    }

    public RegionBounds getPlayableTrack() {
        return playbackManager.getPlayableRegion();
    }

    public RegionBounds getSelectedTrack() {
        return playbackManager.getSelectedRegion();
    }

    public void selectRegion(RegionBounds region) {
        playbackManager.highlightRegion(region, noteMap.getAllValidNotes());
    }

    public void selectAll() {
        playbackManager.highlightAll(noteMap.getAllValidNotes());
    }

    public void copySelected() {
        List<NoteData> notesToCopy = playbackManager.getHighlightedNotes().stream()
                .map(curNote -> curNote.getNoteData()).collect(Collectors.toList());
        clipboard.setNotes(notesToCopy);
    }

    public void pasteSelected() {
        List<NoteData> toPaste = clipboard.getNotes();
        if (toPaste.isEmpty()) {
            return;
        }
        int curPosition = playbackManager.getCursorPosition();
        int positionDelta = curPosition - toPaste.get(0).getPosition();
        LinkedList<NoteData> toAdd = new LinkedList<>();
        LinkedList<Note> newNotes = new LinkedList<>();
        for (NoteData noteData : toPaste) {
            Note newNote = noteFactory.createNote(noteData, noteCallback, vibratoEditor);
            newNote.moveNoteElement(positionDelta, 0);
            newNotes.add(newNote);
            curPosition = newNote.getAbsPositionMs();
            noteMap.addNoteElement(newNote);
            try {
                noteMap.putNote(curPosition, newNote);
            } catch (NoteAlreadyExistsException e) {
                newNote.setValid(false);
                continue;
            }
            toAdd.add(newNote.getNoteData());
        }

        if (toAdd.isEmpty()) {
            return;
        }
        model.addNotes(toAdd);
        refreshNotes(toAdd.getFirst().getPosition(), toAdd.getLast().getPosition());
        model.recordAction(() -> {
            playbackManager.clearHighlights();
            undoDeleteNotes(newNotes);
        }, () -> {
            playbackManager.clearHighlights();
            deleteNotes(newNotes);
        });
    }

    public void deleteSelected() {
        List<Note> toDelete = playbackManager.getHighlightedNotes();
        deleteNotes(toDelete);
        model.recordAction(() -> {
            playbackManager.clearHighlights();
            deleteNotes(toDelete);
        }, () -> {
            playbackManager.clearHighlights();
            undoDeleteNotes(toDelete);
        });
        playbackManager.clearHighlights();
    }

    private void deleteNotes(List<Note> notes) {
        Set<Integer> positionsToRemove = notes.stream().filter(curNote -> curNote.isValid())
                .map(curNote -> curNote.getAbsPositionMs()).collect(Collectors.toSet());
        RegionBounds toStandardize = removeNotes(positionsToRemove);
        if (!toStandardize.equals(RegionBounds.INVALID)) {
            refreshNotes(toStandardize.getMinMs(), toStandardize.getMaxMs());
        }
        for (Note note : notes) {
            noteMap.removeNoteElement(note);
        }
    }

    private void undoDeleteNotes(List<Note> notes) {
        LinkedList<NoteData> toAdd = new LinkedList<>();
        for (Note note : notes) {
            noteMap.addNoteElement(note);
            note.setValid(true);
            try {
                noteMap.putNote(note.getAbsPositionMs(), note);
            } catch (NoteAlreadyExistsException e) {
                note.setValid(false);
                continue;
            }
            toAdd.add(note.getNoteData());
        }
        if (toAdd.isEmpty()) {
            return;
        }
        model.addNotes(toAdd);
        refreshNotes(toAdd.getFirst().getPosition(), toAdd.getLast().getPosition());
    }

    /**
     * Removes notes from the backend song, returns RegionBounds of notes that need refreshing.
     */
    private RegionBounds removeNotes(Set<Integer> positionsToRemove) {
        if (positionsToRemove.isEmpty()) {
            return RegionBounds.INVALID; // If no valid song notes to remove, do nothing.
        }
        MutateResponse response = model.removeNotes(positionsToRemove);
        // Removd all deleted notes from note map.
        for (NoteUpdateData updateData : response.getNotes()) {
            // Should never happen but let's check just in case.
            if (noteMap.hasNote(updateData.getPosition())) {
                noteMap.removeFullNote(updateData.getPosition());
            } else {
                System.out.println("Error: Note present in backend but not in frontend!");
            }
        }

        if (response.getPrev().isPresent() && response.getNext().isPresent()) {
            return new RegionBounds(
                    response.getPrev().get().getPosition(),
                    response.getNext().get().getPosition());
        } else if (response.getPrev().isPresent()) {
            int prevPosition = response.getPrev().get().getPosition();
            return new RegionBounds(prevPosition, prevPosition);
        } else if (response.getNext().isPresent()) {
            int nextPosition = response.getNext().get().getPosition();
            return new RegionBounds(nextPosition, nextPosition);
        }
        return RegionBounds.INVALID;
    }

    public void refreshSelected() {
        List<Note> highlightedNotes = playbackManager.getHighlightedNotes();
        if (highlightedNotes.isEmpty()) {
            return;
        }
        refreshNotes(
                highlightedNotes.get(0).getAbsPositionMs(),
                highlightedNotes.get(highlightedNotes.size() - 1).getAbsPositionMs());
    }

    private void refreshNotes(int firstPosition, int lastPosition) {
        MutateResponse standardizeResponse = model.standardizeNotes(firstPosition, lastPosition);
        String prevPitch = "";
        Note prevNote = null;
        if (standardizeResponse.getPrev().isPresent()) {
            NoteUpdateData prevData = standardizeResponse.getPrev().get();
            prevNote = noteMap.getNote(prevData.getPosition());
            prevNote.setBackupData(prevData);
            prevPitch = PitchUtils.rowNumToPitch(prevNote.getRow());
            noteMap.putEnvelope(
                    prevData.getPosition(),
                    prevData.getEnvelope(),
                    getEnvelopeCallback(prevData.getPosition()));
        }
        Iterator<NoteUpdateData> dataIterator = standardizeResponse.getNotes().iterator();
        NoteUpdateData curData = null;
        Note curNote = null;
        while (dataIterator.hasNext()) {
            curData = dataIterator.next();
            curNote = noteMap.getNote(curData.getPosition());
            curNote.setBackupData(curData);
            curNote.setTrueLyric(curData.getTrueLyric());
            noteMap.putEnvelope(
                    curData.getPosition(),
                    curData.getEnvelope(),
                    getEnvelopeCallback(curData.getPosition()));
            noteMap.putPitchbend(
                    curData.getPosition(),
                    prevPitch.isEmpty() ? PitchUtils.rowNumToPitch(curNote.getRow()) : prevPitch,
                    curData.getPitchbend(),
                    getPitchbendCallback(curData.getPosition()),
                    vibratoEditor);
            if (prevNote != null) {
                prevNote.adjustForOverlap(curData.getPosition() - prevNote.getAbsPositionMs());
            }
            prevNote = curNote;
            prevPitch = PitchUtils.rowNumToPitch(curNote.getRow());
        }
        if (standardizeResponse.getNext().isPresent()) {
            NoteUpdateData nextData = standardizeResponse.getNext().get();
            Note nextNote = noteMap.getNote(nextData.getPosition());
            nextNote.setBackupData(nextData);
            nextNote.setTrueLyric(nextData.getTrueLyric());
            noteMap.putEnvelope(
                    nextData.getPosition(),
                    nextData.getEnvelope(),
                    getEnvelopeCallback(nextData.getPosition()));
            noteMap.putPitchbend(
                    nextData.getPosition(),
                    prevPitch.isEmpty() ? PitchUtils.rowNumToPitch(nextNote.getRow()) : prevPitch,
                    nextData.getPitchbend(),
                    getPitchbendCallback(nextData.getPosition()),
                    vibratoEditor);
            if (curNote != null) {
                curNote.adjustForOverlap(nextData.getPosition() - curData.getPosition());
            }
        } else if (curNote != null) {
            curNote.adjustForOverlap(Integer.MAX_VALUE);
            // If this is the last note, adjust number of measures.
            setNumMeasures((curNote.getBounds().getMaxMs() / Quantizer.COL_WIDTH / 4) + 4);
        }
    }

    private void moveNotes(List<Note> notes, int positionDelta, int rowDelta) {
        Set<Integer> positionsToRemove = notes.stream().filter(curNote -> curNote.isValid())
                .map(curNote -> curNote.getAbsPositionMs()).collect(Collectors.toSet());
        RegionBounds toStandardize = removeNotes(positionsToRemove);

        LinkedList<NoteData> toAdd = new LinkedList<>();
        for (Note curNote : notes) {
            curNote.moveNoteElement(positionDelta, rowDelta);
            curNote.setValid(true);
            try {
                noteMap.putNote(curNote.getAbsPositionMs(), curNote);
            } catch (NoteAlreadyExistsException e) {
                curNote.setValid(false);
                continue;
            }
            toAdd.add(curNote.getNoteData());
        }
        // Standardize and return early if nothing needs to be added.
        if (toAdd.isEmpty()) {
            if (!toStandardize.equals(RegionBounds.INVALID)) {
                refreshNotes(toStandardize.getMinMs(), toStandardize.getMaxMs());
            }
            return;
        }
        model.addNotes(toAdd);
        RegionBounds addRegion =
                new RegionBounds(toAdd.getFirst().getPosition(), toAdd.getLast().getPosition());
        toStandardize = toStandardize.mergeWith(addRegion);
        refreshNotes(toStandardize.getMinMs(), toStandardize.getMaxMs());
    }

    public Optional<Integer> getFocusNote() {
        List<Note> highlightedNotes = playbackManager.getHighlightedNotes();
        if (!highlightedNotes.isEmpty()) {
            return Optional.of(highlightedNotes.get(0).getAbsPositionMs());
        }
        return Optional.absent();
    }
    
    public Note getNote(int position) {
    	return noteMap.getNote(position);
    }
    
    public int getFirstNotePosition() {
    	return noteMap.getFirstPosition(RegionBounds.WHOLE_SONG);
    }
    
    public int getLastNotePosition() {
    	return noteMap.getLastPosition(RegionBounds.WHOLE_SONG);
    }

    public void focusOnNote(int position) {
        if (!noteMap.hasNote(position)) {
            return;
        }
        // If old focus has lyric open, new focus should have it open too.
        boolean shouldOpenLyricInput = false;
        List<Note> highlightedNotes = playbackManager.getHighlightedNotes();
        if (!highlightedNotes.isEmpty()) {
            shouldOpenLyricInput = highlightedNotes.get(0).isLyricInputOpen();
        }
        Note newFocus = noteMap.getNote(position);
        playbackManager.clearHighlights();
        playbackManager.highlightNote(newFocus);
        playbackManager.realign();
        if (shouldOpenLyricInput) {
            newFocus.openLyricInput();
        }
    }

    public void openLyricInput(int position) {
        if (noteMap.hasNote(position)) {
            noteMap.getNote(position).openLyricInput();
        }
    }

    public void selectivelyShowRegion(double centerPercent, double margin) {
        int measureWidthMs = 4 * Quantizer.COL_WIDTH;
        int marginMeasures = ((int) (margin / Math.round(scaler.scaleX(measureWidthMs)))) + 3;
        int centerMeasure = RoundUtils.round((numMeasures) * centerPercent) - 1; // Pre-roll.
        int clampedStartMeasure =
                Math.min(Math.max(centerMeasure - marginMeasures, 0), numMeasures - 1);
        int clampedEndMeasure =
                Math.min(Math.max(centerMeasure + marginMeasures, 0), numMeasures - 1);
        // Use measures to we don't have to redraw the visible region too much.
        noteMap.setVisibleRegion(
                new RegionBounds(
                        clampedStartMeasure * measureWidthMs,
                        (clampedEndMeasure + 1) * measureWidthMs));
    }

    private void clearTrack() {
        // Remove current track.
        playbackManager.clear();
        noteMap.clear();
        measures = new HBox();
        dynamics = new HBox();
        selection = new Rectangle();

        numMeasures = 0;
        addMeasure(false);
        setNumMeasures(4);
    }

    private void setNumMeasures(int newNumMeasures) {
        if (newNumMeasures < 0) {
            return;
        } else if (newNumMeasures > numMeasures) {
            for (int i = numMeasures; i < newNumMeasures; i++) {
                addMeasure(true);
            }
        } else if (newNumMeasures == numMeasures) {
            // Nothing needs to be done.
            return;
        } else {
            int measureWidth = 4 * RoundUtils.round(scaler.scaleX(Quantizer.COL_WIDTH));
            int maxWidth = measureWidth * (newNumMeasures + 1); // Include pre-roll.
            // Remove measures.
            measures.getChildren().removeIf((child) -> {
                return RoundUtils.round(child.getLayoutX()) >= maxWidth;
            });
            // Remove dynamics columns.
            dynamics.getChildren().removeIf((child) -> {
                return RoundUtils.round(child.getLayoutX()) >= maxWidth;
            });
            numMeasures = newNumMeasures;
        }
    }

    private void addMeasure(boolean enabled) {
        GridPane newMeasure = new GridPane();
        int rowNum = 0;
        for (int octave = 7; octave > 0; octave--) {
            for (String pitch : PitchUtils.REVERSE_PITCHES) {
                // Add row to track.
                for (int colNum = 0; colNum < 4; colNum++) {
                    Pane newCell = new Pane();
                    newCell.setPrefSize(
                            Math.round(scaler.scaleX(Quantizer.COL_WIDTH)),
                            Math.round(scaler.scaleY(Quantizer.ROW_HEIGHT)));
                    newCell.getStyleClass().add("track-cell");
                    if (enabled) {
                        newCell.getStyleClass()
                                .add(pitch.endsWith("#") ? "black-key" : "white-key");
                    } else {
                        newCell.getStyleClass().add("gray-key");
                    }
                    if (colNum == 0) {
                        newCell.getStyleClass().add("measure-start");
                    } else if (colNum == 3) {
                        newCell.getStyleClass().add("measure-end");
                    }
                    newMeasure.add(newCell, colNum, rowNum);
                }
                rowNum++;
            }
        }
        measures.getChildren().add(newMeasure);

        // Add new columns to dynamics.
        GridPane newDynamics = new GridPane();
        for (int colNum = 0; colNum < 4; colNum++) {
            AnchorPane topCell = new AnchorPane();
            topCell.setPrefSize(scaler.scaleX(Quantizer.COL_WIDTH), 50);
            topCell.getStyleClass().add("dynamics-top-cell");
            if (colNum == 0) {
                topCell.getStyleClass().add("measure-start");
            }
            newDynamics.add(topCell, colNum, 0);
            AnchorPane bottomCell = new AnchorPane();
            bottomCell.setPrefSize(scaler.scaleX(Quantizer.COL_WIDTH), 50);
            bottomCell.getStyleClass().add("dynamics-bottom-cell");
            if (colNum == 0) {
                bottomCell.getStyleClass().add("measure-start");
            }
            newDynamics.add(bottomCell, colNum, 1);
        }
        dynamics.getChildren().add(newDynamics);

        if (enabled) {
            activateMeasure(newMeasure);
            numMeasures++;
        }
    }

    private void activateMeasure(GridPane measure) {
        measure.setOnMouseReleased(event -> {
            selection.setVisible(false); // Remove selection box if present.
            int quantSize = Quantizer.COL_WIDTH / quantizer.getQuant();
            double measureWidth = 4 * scaler.scaleX(Quantizer.COL_WIDTH);
            double endX = Math
                    .min(getWidthX(), Math.max(measureWidth, measure.getLayoutX() + event.getX()));
            int startMs = RoundUtils.round(scaler.unscalePos(curX) / quantSize) * quantSize;
            int endMs = RoundUtils.round(scaler.unscalePos(endX) / quantSize) * quantSize;
            if (subMode == SubMode.DRAG_CREATE) {
                int startRow = (int) scaler.unscaleY(curY) / Quantizer.ROW_HEIGHT;
                // Create new note if size would be nonzero.
                if (endMs > startMs) {
                    Note newNote = noteFactory.createDefaultNote(
                            startRow,
                            startMs,
                            endMs - startMs,
                            noteCallback,
                            vibratoEditor);
                    noteMap.addNoteElement(newNote);
                    List<Note> noteList = ImmutableList.of(newNote);
                    model.recordAction(() -> {
                        playbackManager.clearHighlights();
                        undoDeleteNotes(noteList);
                    }, () -> {
                        playbackManager.clearHighlights();
                        deleteNotes(noteList);
                    });
                }
            } else if (subMode == SubMode.DRAG_SELECT
                    && !playbackManager.getHighlightedNotes().isEmpty()) {
                playbackManager.realign();
            } else if (event.isShiftDown() || event.getButton() != MouseButton.PRIMARY) {
                if (event.getButton() == MouseButton.SECONDARY) {
                    editorContextMenu.show(measure, event.getScreenX(), event.getScreenY());
                }
                // Set cursor.
                playbackManager.setCursor(endMs);
            }
        });
        measure.setOnMouseDragged(event -> {
            double measureWidth = 4 * scaler.scaleX(Quantizer.COL_WIDTH);
            double endX = Math
                    .min(getWidthX(), Math.max(measureWidth, measure.getLayoutX() + event.getX()));
            if (subMode == SubMode.DRAG_SELECT || event.isShiftDown()
                    || event.getButton() != MouseButton.PRIMARY) {
                subMode = SubMode.DRAG_SELECT;
                // Draw selection rectangle.
                double endY = Math.min(measure.getHeight(), Math.max(0, event.getY()));
                selection.setVisible(true);
                selection.getStyleClass().setAll("select-box");
                selection.setX(Math.min(curX, endX));
                selection.setY(Math.min(curY, endY));
                selection.setWidth(Math.abs(endX - curX));
                selection.setHeight(Math.abs(endY - curY));
                // Update highlighted notes.
                int startRow = (int) scaler.unscaleY(curY) / Quantizer.ROW_HEIGHT;
                int endRow = (int) scaler.unscaleY(endY) / Quantizer.ROW_HEIGHT;
                int startMs = RoundUtils.round(scaler.unscalePos(curX));
                int endMs = RoundUtils.round(scaler.unscalePos(endX));
                RegionBounds horizontalBounds = endMs >= startMs ? new RegionBounds(startMs, endMs)
                        : new RegionBounds(endMs, startMs);
                playbackManager.clearHighlights();
                for (Note note : noteMap.getAllValidNotes()) {
                    int noteRow = note.getRow();
                    if (note.getValidBounds().intersects(horizontalBounds)
                            && Math.abs(endRow - noteRow) + Math.abs(noteRow - startRow) == Math
                                    .abs(endRow - startRow)) {
                        playbackManager.highlightNote(note);
                    }
                }
            } else {
                subMode = SubMode.DRAG_CREATE;
                int quantSize = Quantizer.COL_WIDTH / quantizer.getQuant();
                int startMs = RoundUtils.round(scaler.unscalePos(curX) / quantSize) * quantSize;
                int startRow = (int) scaler.unscaleY(curY) / Quantizer.ROW_HEIGHT;
                int endMs = RoundUtils.round(scaler.unscalePos(endX) / quantSize) * quantSize;
                if (endMs > startMs) {
                    // Draw selection rectangle.
                    selection.setVisible(true);
                    selection.getStyleClass().setAll("add-note-box");
                    selection.setX(scaler.scalePos(startMs));
                    selection.setY(scaler.scaleY(startRow * Quantizer.ROW_HEIGHT));
                    selection.setWidth(scaler.scaleX(endMs - startMs));
                    selection.setHeight(scaler.scaleY(Quantizer.ROW_HEIGHT));
                } else {
                    selection.setVisible(false);
                }
            }
        });
        measure.setOnMousePressed(event -> {
            editorContextMenu.hide();
            subMode = SubMode.NOT_DRAGGING;
            curX = measure.getLayoutX() + event.getX();
            curY = event.getY();
        });
    }

    private final NoteCallback noteCallback = new NoteCallback() {
        @Override
        public void highlightExclusive(Note note) {
            playbackManager.clearHighlights();
            playbackManager.highlightNote(note);
            playbackManager.realign();
        }

        @Override
        public void highlightInclusive(Note note) {
            RegionBounds merged =
                    note.getValidBounds().mergeWith(playbackManager.getSelectedRegion());
            playbackManager.highlightRegion(merged, noteMap.getAllValidNotes());
        }

        @Override
        public void realignHighlights() {
            playbackManager.realign();
        }

        @Override
        public boolean isExclusivelyHighlighted(Note note) {
            return playbackManager.isExclusivelyHighlighted(note);
        }

        @Override
        public void updateNote(Note note) {
            int positionMs = note.getAbsPositionMs();
            if (note.isValid()) {
                // Removes note if necessary.
                removeNotes(ImmutableSet.of(positionMs));
            }
            try {
                // Replaces note if possible.
                noteMap.putNote(positionMs, note);
                note.setValid(true);
                model.addNotes(ImmutableList.of(note.getNoteData()));
            } catch (NoteAlreadyExistsException e) {
                note.setValid(false);
            }
            // Refreshes notes regardless of whether a new one was placed.
            refreshNotes(positionMs, positionMs);
        }

        @Override
        public void moveNote(Note note, int positionDelta, int rowDelta) {
            List<Note> toMove =
                    playbackManager.isHighlighted(note) ? playbackManager.getHighlightedNotes()
                            : ImmutableList.of(note);
            moveNotes(toMove, positionDelta, rowDelta);
        }

        @Override
        public void recordNoteMovement(Note note, int positionDelta, int rowDelta) {
            // Records one note movement and how to undo/redo it.
            List<Note> toMove =
                    playbackManager.isHighlighted(note) ? playbackManager.getHighlightedNotes()
                            : ImmutableList.of(note);
            model.recordAction(() -> {
                playbackManager.clearHighlights();
                moveNotes(toMove, positionDelta, rowDelta);
            }, () -> {
                playbackManager.clearHighlights();
                moveNotes(toMove, -positionDelta, -rowDelta);
            });
        }

        @Override
        public void copyNote(Note note) {
            List<Note> notesToCopy =
                    playbackManager.isHighlighted(note) ? playbackManager.getHighlightedNotes()
                            : ImmutableList.of(note);
            List<NoteData> dataToCopy = notesToCopy.stream().map(curNote -> curNote.getNoteData())
                    .collect(Collectors.toList());
            clipboard.setNotes(dataToCopy);
        }

        @Override
        public void deleteNote(Note note) {
            List<Note> toDelete =
                    playbackManager.isHighlighted(note) ? playbackManager.getHighlightedNotes()
                            : ImmutableList.of(note);
            deleteNotes(toDelete);
            model.recordAction(() -> {
                playbackManager.clearHighlights();
                deleteNotes(toDelete);
            }, () -> {
                playbackManager.clearHighlights();
                undoDeleteNotes(toDelete);
            });
            if (playbackManager.isHighlighted(note)) {
                playbackManager.clearHighlights();
            }
        }

        @Override
        public RegionBounds getBounds(Note note) {
            return playbackManager.isHighlighted(note) ? playbackManager.getSelectedRegion()
                    : note.getBounds();
        }

        @Override
        public void recordAction(Runnable redoAction, Runnable undoAction) {
            model.recordAction(redoAction, undoAction);
        }

        @Override
        public boolean hasVibrato(int position) {
            if (noteMap.hasPitchbend(position)) {
                return noteMap.getPitchbend(position).hasVibrato();
            }
            return false;
        }

        @Override
        public void setHasVibrato(int position, boolean hasVibrato) {
            if (noteMap.hasPitchbend(position)) {
                noteMap.getPitchbend(position).setHasVibrato(hasVibrato);
            }
        }

        @Override
        public void openNoteProperties(Note note) {
            if (playbackManager.isHighlighted(note)) {
                model.openNoteProperties(playbackManager.getSelectedRegion());
            } else {
                // Open on current note if current note is not highlighted.
                model.openNoteProperties(note.getValidBounds());
            }
        }
    };

    private EnvelopeCallback getEnvelopeCallback(final int positionMs) {
        return new EnvelopeCallback() {
            @Override
            public void modifySongEnvelope(EnvelopeData oldData, EnvelopeData newData) {
                Runnable redoAction = () -> {
                    modifyBackend(newData);
                    refreshNotes(positionMs, positionMs); // Update frontend.
                };
                Runnable undoAction = () -> {
                    modifyBackend(oldData);
                    refreshNotes(positionMs, positionMs); // Update frontend.
                };
                model.recordAction(redoAction, undoAction);
                noteMap.getNote(positionMs).setBackupData(modifyBackend(newData));
            }

            private NoteUpdateData modifyBackend(EnvelopeData updateData) {
                Note toModify = noteMap.getNote(positionMs);
                NoteData mutation = new NoteData(
                        positionMs,
                        toModify.getDurationMs(),
                        PitchUtils.rowNumToPitch(toModify.getRow()),
                        toModify.getLyric(),
                        updateData);
                return model.modifyNote(mutation);
            }
        };
    }

    private PitchbendCallback getPitchbendCallback(final int positionMs) {
        return new PitchbendCallback() {
            @Override
            public void modifySongPitchbend(PitchbendData oldData, PitchbendData newData) {
                Optional<int[]> vibrato = noteMap.getPitchbend(positionMs).getVibrato();
                Runnable redoAction = () -> {
                    modifyBackend(newData.withVibrato(vibrato));
                    refreshNotes(positionMs, positionMs); // Update frontend.
                };
                Runnable undoAction = () -> {
                    modifyBackend(oldData.withVibrato(vibrato));
                    refreshNotes(positionMs, positionMs); // Update frontend.
                };
                model.recordAction(redoAction, undoAction);
                NoteUpdateData update = modifyBackend(newData.withVibrato(vibrato));
                noteMap.getNote(positionMs).setBackupData(update);
            }

            @Override
            public void modifySongVibrato(int[] oldVibrato, int[] newVibrato) {
                PitchbendData data = noteMap.getPitchbend(positionMs).getData();
                Runnable redoAction = () -> {
                    modifyBackend(data.withVibrato(Optional.of(newVibrato)));
                    refreshNotes(positionMs, positionMs); // Update frontend.
                };
                Runnable undoAction = () -> {
                    modifyBackend(data.withVibrato(Optional.of(oldVibrato)));
                    refreshNotes(positionMs, positionMs); // Update frontend.
                };
                model.recordAction(redoAction, undoAction);
                NoteUpdateData update = modifyBackend(data.withVibrato(Optional.of(newVibrato)));
                noteMap.getNote(positionMs).setBackupData(update);
            }

            private NoteUpdateData modifyBackend(PitchbendData updateData) {
                Note toModify = noteMap.getNote(positionMs);
                NoteData mutation = new NoteData(
                        positionMs,
                        toModify.getDurationMs(),
                        PitchUtils.rowNumToPitch(toModify.getRow()),
                        toModify.getLyric(),
                        updateData);
                return model.modifyNote(mutation);
            }
        };
    }
}
