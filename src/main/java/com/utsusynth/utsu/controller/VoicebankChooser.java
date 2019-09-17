package com.utsusynth.utsu.controller;

import java.io.File;
import java.io.IOException;
import java.util.ResourceBundle;
import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.utsusynth.utsu.common.i18n.Localizable;
import com.utsusynth.utsu.common.i18n.Localizer;
import com.utsusynth.utsu.common.utils.RoundUtils;
import com.utsusynth.utsu.engine.Engine;
import com.utsusynth.utsu.model.song.SongContainer;
import com.utsusynth.utsu.model.voicebank.FSVoicebank;
import com.utsusynth.utsu.model.voicebank.FSVoicebank;
import com.utsusynth.utsu.model.voicebank.VoicebankManager;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import javafx.stage.Window;

/**
 * 'SongPropertiesScene.fxml' Controller Class
 */
public class VoicebankChooser extends Stage {
    private final VoicebankManager voicebankManager;
    
    @FXML
    private Button resetButton;
    
    @FXML
    private Button applyButton;
    
    @FXML
    private Button cancelButton;
    
    @FXML
    private Label lblSelFromList;
    
    @FXML
    private Label lblSelFromFS;
    
    @FXML
    private VBox vbSelBox;
    
    @FXML
    private VoicebankSelector vbSelector;
    
    @FXML
    private TextField inFilePath;
    
    @FXML
    private RadioButton selFile;
    

    @Inject
    public VoicebankChooser(Window owner, String title, Provider<FXMLLoader> fxmlLoaderProvider , VoicebankManager vbManager) {
        this.voicebankManager = vbManager;
        
        setTitle(title);
        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        
        FXMLLoader loader = fxmlLoaderProvider.get();
        loader.setLocation(getClass().getResource("/fxml/VoicebankChooserScene.fxml"));
        
		try {
			loader.setController(this);
			
			setScene(new Scene(loader.load()));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		vbSelector = new VoicebankSelector(fxmlLoaderProvider, vbManager);
		vbSelBox.getChildren().setAll(vbSelector);
		
		vbSelector.selectedVoicebankProperty().addListener(o->{
			selectedVb.set(vbSelector.getSelectedVoicebank());
		});
    }
    
    public final ReadOnlyObjectProperty<FSVoicebank> selectedVoicebankProperty() { return selectedVb.getReadOnlyProperty(); }
    private ReadOnlyObjectWrapper<FSVoicebank> selectedVb = new ReadOnlyObjectWrapper<>(this, "selectedVoicebank");
    
    public FSVoicebank getSelectedVoicebank() {
    	return selectedVoicebankProperty().get();
    }
    
    public FSVoicebank askUserForVoicebank() {
        showAndWait();
        return getSelectedVoicebank();
    }

    /* Initializes properties panel with a SongContainer with the song to edit. */
    void setData() {
    	
    }
    
    @FXML
    void browseFS(ActionEvent event) {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Select voicebank");
        File file = dc.showDialog(null);
        if (file != null) {
            new Thread(() -> {
                try {
                    selectedVb.set(voicebankManager.getVoicebank(file, false));
                    Platform.runLater(() -> {
                        inFilePath.setText(file.getPath());
                        selFile.setSelected(true);
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                    Alert alert = new Alert(AlertType.ERROR, "The specified folder is not a voicebank", ButtonType.CLOSE);
                    alert.showAndWait();
                    // TODO Auto-generated catch block
                }
            }).run();
        }
    }
    
    @FXML
    void restoreDefaults(ActionEvent event) {
    	vbSelector.reloadTree();
    }

    @FXML
    void applyProperties(ActionEvent event) {
        close();
    }

    @FXML
    void closeProperties(ActionEvent event) {
        selectedVb.set(null);
    	close();
    }
}
