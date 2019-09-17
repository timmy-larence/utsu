package com.utsusynth.utsu.controller;

import java.io.File;
import java.util.ResourceBundle;
import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.utsusynth.utsu.common.i18n.Localizable;
import com.utsusynth.utsu.common.i18n.Localizer;
import com.utsusynth.utsu.common.utils.RoundUtils;
import com.utsusynth.utsu.engine.Engine;
import com.utsusynth.utsu.model.song.SongContainer;
import com.utsusynth.utsu.model.voicebank.FSVoicebank;
import com.utsusynth.utsu.model.voicebank.FSVoicebank;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;

/**
 * 'SongPropertiesScene.fxml' Controller Class
 */
public class SongPropertiesController implements Localizable {
    private final Localizer localizer;
    private final SimpleObjectProperty<FSVoicebank> voicebank = new SimpleObjectProperty<>();

    private SongContainer songContainer;
    private Engine engine;
    private File resamplerPath;
    private File wavtoolPath;
    private Optional<File> instrumentalPath;
    private Runnable onSongChange; // Call when applying properties.

    @FXML // fx:id="root"
    private BorderPane root; // Value injected by FXMLLoader

    @FXML // fx:id="projectNameLabel"
    private Label projectNameLabel;

    @FXML // fx:id="projectNameTF"
    private TextField projectNameTF;

    @FXML // fx:id="outputFileLabel"
    private Label outputFileLabel;

    @FXML // fx:id="outputFileTF"
    private TextField outputFileTF;

    @FXML // fx:id="flagsLabel"
    private Label flagsLabel;

    @FXML // fx:id="flagsTF"
    private TextField flagsTF;

    @FXML // fx:id="resamplerLabel"
    private Label resamplerLabel;

    @FXML // fx:id="resamplerName"
    private TextField resamplerName; // Value injected by FXMLLoader

    @FXML // fx:id="wavtoolLabel"
    private Label wavtoolLabel;

    @FXML // fx:id="wavtoolName"
    private TextField wavtoolName; // Value injected by FXMLLoader

    @FXML // fx:id="voicebankLabel"
    private Label voicebankLabel;

    @FXML // fx:id="voicebankName"
    private TextField voicebankName; // Value injected by FXMLLoader

    @FXML // fx:id="instrumentalLabel"
    private Label instrumentalLabel; // Value injected by FXMLLoader

    @FXML // fx:id="instrumentalName"
    private TextField instrumentalName; // Value injected by FXMLLoader

    @FXML // fx:id="tempoLabel"
    private Label tempoLabel;

    @FXML // fx:id="tempoSlider"
    private Slider tempoSlider; // Value injected by FXMLLoader

    @FXML // fx:id="curTempo"
    private Label curTempo; // Value injected by FXMLLoader

    @FXML // fx:id="applyButton"
    private Button applyButton; // Value injected by FXMLLoader

    @FXML // fx:id="cancelButton"
    private Button cancelButton; // Value injected by FXMLLoader

    @Inject
    public SongPropertiesController(Localizer localizer) {
        this.localizer = localizer;
    }

    public void initialize() {
        // Set up localization.
        localizer.localize(this);
    }

    /* Initializes properties panel with a SongContainer with the song to edit. */
    void setData(SongContainer songContainer, Engine engine, Runnable callback) {
        this.songContainer = songContainer;
        this.engine = engine;
        this.onSongChange = callback;

        // Set values to save.
        resamplerPath = engine.getResamplerPath();
        wavtoolPath = engine.getWavtoolPath();
        voicebank.set(songContainer.get().getVoiceDir());
        instrumentalPath = songContainer.get().getInstrumental();

        // Set text boxes.
        projectNameTF.setText(songContainer.get().getProjectName());
        outputFileTF.setText(songContainer.get().getOutputFile().getAbsolutePath());
        flagsTF.setText(songContainer.get().getFlags());
        resamplerName.setText(resamplerPath.getName());
        wavtoolName.setText(wavtoolPath.getName());
        voicebankName.setText(voicebank.get().getName());
        instrumentalName.setText(instrumentalPath.or(new File("")).getName());

        // Setup tempo slider.
        tempoSlider.valueProperty().addListener((event) -> {
            int sliderValue = RoundUtils.round(tempoSlider.getValue());
            tempoSlider.setValue(sliderValue);
            curTempo.setText(Integer.toString(sliderValue));
        });
        tempoSlider.setValue(songContainer.get().getTempo());
    }

    @Override
    public void localize(ResourceBundle bundle) {
        projectNameLabel.setText(bundle.getString("properties.projectName"));
        outputFileLabel.setText(bundle.getString("properties.outputFile"));
        flagsLabel.setText(bundle.getString("properties.flags"));
        resamplerLabel.setText(bundle.getString("properties.resampler"));
        wavtoolLabel.setText(bundle.getString("properties.wavtool"));
        voicebankLabel.setText(bundle.getString("properties.voicebank"));
        instrumentalLabel.setText(bundle.getString("properties.instrumental"));
        tempoLabel.setText(bundle.getString("properties.tempo"));
        applyButton.setText(bundle.getString("general.apply"));
        cancelButton.setText(bundle.getString("general.cancel"));
    }

    @FXML
    void changeResampler(ActionEvent event) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select executable file");
        fc.getExtensionFilters().addAll(
                new ExtensionFilter("Executables", "*", "*.exe"),
                new ExtensionFilter("OSX Executables", "*.out", "*.app"),
                new ExtensionFilter("All Files", "*.*"));
        File file = fc.showOpenDialog(null);
        if (file != null) {
            resamplerPath = file;
            resamplerName.setText(resamplerPath.getName());
        }
    }

    @FXML
    void changeWavtool(ActionEvent event) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select executable file");
        fc.getExtensionFilters().addAll(
                new ExtensionFilter("Executables", "*", "*.exe"),
                new ExtensionFilter("OSX Executables", "*.out", "*.app"),
                new ExtensionFilter("All Files", "*.*"));
        File file = fc.showOpenDialog(null);
        if (file != null) {
            wavtoolPath = file;
            wavtoolName.setText(wavtoolPath.getName());
        }
    }

    @FXML
    void changeVoicebank(ActionEvent event) {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Select voicebank");
        File file = dc.showDialog(null);
        if (file != null) {
            new Thread(() -> {
                
                voicebankContainer.setVoicebank(file);
                String name = voicebankContainer.getOrDefault().getName();
                Platform.runLater(() -> voicebankName.setText(name));
            }).run();
        }
    }

    @FXML
    void changeInstrumental(ActionEvent event) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select sound file");
        fc.getExtensionFilters().addAll(
                new ExtensionFilter("Sound files", "*.wav", "*.mp3"),
                new ExtensionFilter("All Files", "*.*"));
        File file = fc.showOpenDialog(null);
        if (file != null) {
            instrumentalPath = Optional.of(file);
            instrumentalName.setText(file.getName());
        }
    }

    @FXML
    void applyProperties(ActionEvent event) {
        new Thread(() -> {
            songContainer.setSong(
                    songContainer.get().toBuilder().setProjectName(projectNameTF.getText())
                            .setOutputFile(new File(outputFileTF.getText()))
                            .setFlags(flagsTF.getText())
                            .setVoiceDirectory(voicebankContainer.getLocation())
                            .setTempo(RoundUtils.round(tempoSlider.getValue()))
                            .setInstrumental(instrumentalPath).build());
            engine.setResamplerPath(resamplerPath);
            engine.setWavtoolPath(wavtoolPath);
            onSongChange.run();
        }).start();
        Stage currentStage = (Stage) root.getScene().getWindow();
        currentStage.close();
    }

    @FXML
    void closeProperties(ActionEvent event) {
        Stage currentStage = (Stage) root.getScene().getWindow();
        currentStage.close();
    }
}
