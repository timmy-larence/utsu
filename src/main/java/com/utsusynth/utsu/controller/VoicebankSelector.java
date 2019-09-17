package com.utsusynth.utsu.controller;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.utsusynth.utsu.common.i18n.Localizable;
import com.utsusynth.utsu.common.i18n.Localizer;
import com.utsusynth.utsu.files.VoicebankReader;
import com.utsusynth.utsu.model.voicebank.FSVoicebank;
import com.utsusynth.utsu.model.voicebank.FSVoicebank;
import com.utsusynth.utsu.model.voicebank.VoicebankManager;

import javafx.beans.InvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectPropertyBase;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

/**
 * 'SongPropertiesScene.fxml' Controller Class
 */
public class VoicebankSelector extends VBox {
    private final VoicebankManager vbManager;
    
    @FXML // fx:id="voicebankTreeView"
    private TreeTableView<TreeFolder> voicebankTreeView;
    
    public class TreeFolder {
    	public static final int AUTO = 0;
    	public static final int FOLDER = 1;
    	public static final int VOICEBANK = 2;
    	public static final int CATEGORY = 3;
    	public static final int MESSAGE = 4;
    	public static final int ERROR = 4;
    	public static final int ROOT = -1;
    	
		private File file;
    	private String name;
    	private int type;
    	
    	public TreeFolder(String name, File file, int type) {
    		this.name = name;
    		this.file = file;
    		this.type = type;
    	}
    	
    	public String getDisplayName() {
    		if(type == VOICEBANK) {
    			String vbName;
    			if(file==null) return null;
//    			return file.getName();
    			try {
					vbName = vbManager.getVoicebank(file, false).getName();
				} catch (IOException e) {
					e.printStackTrace();
					return null;
				}
    			if(name!=null)
    				return name+" ("+vbName+")";
    			else return vbName;
    		}
    		if(name != null) return name;
    		if(file == null) return null;
    		return file.getName();
    	}
    	
    	public File getPath() {
    		if(type==CATEGORY) return null;
    		return file;
    	}
    	
    	public int getType() {
    		return type;
    	}
    }
    
    @Inject
    public VoicebankSelector(Provider<FXMLLoader> fxmlLoaderProvider , VoicebankManager vbManager) {
        this.vbManager = vbManager;

      	FXMLLoader loader = fxmlLoaderProvider.get();
        loader.setLocation(getClass().getResource("/fxml/VoicebankSelector.fxml"));
        
		try {
			loader.setRoot(this);
			loader.setController(this);
			loader.load();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    @FXML
    private TreeTableColumn<TreeFolder,String> vbCol;
    
    @FXML
    private TreeTableColumn<TreeFolder,File> locCol;
    
	public void initialize() {
        
        vbCol.prefWidthProperty().bind(voicebankTreeView.widthProperty().multiply(0.59));
        vbCol.setCellValueFactory(
            (TreeTableColumn.CellDataFeatures<TreeFolder, String> param) -> 
            new ReadOnlyStringWrapper(param.getValue().getValue().getDisplayName())
        );
        
        locCol.prefWidthProperty().bind(voicebankTreeView.widthProperty().multiply(0.4));
        locCol.setCellValueFactory(
            (TreeTableColumn.CellDataFeatures<TreeFolder, File> param) -> 
            new ReadOnlyObjectWrapper<>(param.getValue().getValue().getPath())
        );
        
        selectedProperty().addListener(o->{
    		TreeItem<TreeFolder> item = getSelected();
    		if(item!=null && item.getValue().getType()==TreeFolder.CATEGORY)
    			item=item.getParent();
    		if(item!=null && item.getValue().getType()==TreeFolder.VOICEBANK) {
    			try {
					selectedVb.set(vbManager.getVoicebank(item.getValue().getPath(), false));
					return;
				} catch (IOException e) {
					e.printStackTrace();
				}
    		}
    		selectedVb.set(null);
    	});
        
        setData();
    }

    /* Initializes properties panel with a SongContainer with the song to edit. */
    void setData() {
    	TreeItem<TreeFolder> root = new TreeItem<>(new TreeFolder(null,null,TreeFolder.ROOT));
        root.getChildren().clear();

        LinkedList<TreeItem<TreeFolder>> queue = new LinkedList<>();
        queue.add(root);
        
        while(!queue.isEmpty()) {
        	TreeItem<TreeFolder> item = queue.pop();
        	
        	//Somehow get this
        	List<TreeFolder> children = new LinkedList<>();
        	
        	if(item.getValue().getType() == TreeFolder.ROOT) {
        		for(Map.Entry<String, File> entry : vbManager.getEffectiveVoicebankPaths()) {
        			children.add(new TreeFolder(entry.getKey(), entry.getValue(),
        					VoicebankReader.isVoicebank(entry.getValue())?TreeFolder.VOICEBANK:TreeFolder.FOLDER));
        		}
        		//Read user-defined folder list
//        		children.add(new TreeFolder("UTSU Voice Folder", new File("assets/voice"), TreeFolder.FOLDER));
//        		children.add(new TreeFolder("Some other shit", new File("assets"), TreeFolder.FOLDER));
//        		children.add(new TreeFolder("UTAU Voicebanks", new File("C:\\Program Files (x86)\\UTAU\\voice"), TreeFolder.FOLDER));
//        		children.add(new TreeFolder(null, new File("D:\\teto\\Fushi Murasaki VIOLET"), TreeFolder.VOICEBANK));
        	}
        	
        	if(item.getValue().getType() == TreeFolder.FOLDER) {
        		File f = item.getValue().getPath();
        		for(String sub : f.list((current, name) -> new File(current, name).isDirectory()
        				&& VoicebankReader.isVoicebank(new File(current, name)))) {
        			children.add(new TreeFolder(null, new File(f,sub), TreeFolder.VOICEBANK));
        		}
        	}
        	
        	if(item.getValue().getType() == TreeFolder.VOICEBANK) {
        		File f = item.getValue().getPath();
        		for(String sub : f.list((current, name) -> 
        				new File(current, name).isDirectory() && new File(current,name+"/oto.ini").exists())) {
        			children.add(new TreeFolder(null, new File(f,sub), TreeFolder.CATEGORY));
        		}
        	}
        	
        	item.getChildren().setAll(children.stream().map(o -> new TreeItem<>(o)).map(o-> {
        		if(o.getValue().getType()!=TreeFolder.CATEGORY && o.getValue().getType()!=TreeFolder.MESSAGE)
        			queue.add(o);
        		return o;
        	}).collect(Collectors.toList()));
        	
        	if(item.getValue().getType() == TreeFolder.FOLDER && children.isEmpty()) {
        		item.getChildren().setAll(new TreeItem<>(new TreeFolder("no content",null,TreeFolder.MESSAGE)));
        	}
        }        
    	
    	voicebankTreeView.setRoot(root);
    }
    
    public ReadOnlyObjectProperty<TreeItem<TreeFolder>> selectedProperty() {
    	return voicebankTreeView.getSelectionModel().selectedItemProperty();
    }
    
    public TreeItem<TreeFolder> getSelected() {
    	return selectedProperty().get();
    }
    
    public final ReadOnlyObjectProperty<FSVoicebank> selectedVoicebankProperty() { return selectedVb.getReadOnlyProperty(); }
    private ReadOnlyObjectWrapper<FSVoicebank> selectedVb = new ReadOnlyObjectWrapper<>(this, "selectedVoicebank");
    
    public FSVoicebank getSelectedVoicebank() {
    	return selectedVoicebankProperty().get();
    }
    
    void reloadTree() {
    	setData();
    }
}
