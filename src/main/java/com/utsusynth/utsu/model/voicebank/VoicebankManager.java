package com.utsusynth.utsu.model.voicebank;

import java.io.File;
import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.google.inject.Inject;
import com.utsusynth.utsu.common.exception.ErrorLogger;
import com.utsusynth.utsu.controller.VoicebankSelector.TreeFolder;
import com.utsusynth.utsu.files.VoicebankReader;

/**
 * Manages all voicebanks in use by Utsu. This class is a singleton to ensure the same voicebank
 * does not open on two editors.
 */
public class VoicebankManager {
    private static final ErrorLogger errorLogger = ErrorLogger.getLogger();
    
    private static final File defaultVb = new File("assets/voice/Iona_Beta");
    private File userDefault = null;
    private Map.Entry<String, File> rootVbFolder = new SimpleEntry<>("UTSU Voice Folder", new File("assets/voice"));
//    private Map.Entry<String, File> userVbFolder = new SimpleEntry<>("UTSU User Voicebanks", new File("%appdata%/utsusynth/assets/voice"));
    private List<Map.Entry<String, File>> userFolders = new LinkedList<>();
    
    {
    	userFolders.add(new SimpleEntry<>("Some other shit", new File("assets")));
    	userFolders.add(new SimpleEntry<>("UTAU Root Voicebanks", new File("C:\\Program Files (x86)\\UTAU\\voice")));
//    	userFolders.add(new SimpleEntry<>("UTAU User Voicebanks", new File("%appdata%/UTAU/voice")));
    	userFolders.add(new SimpleEntry<>(null, new File("D:\\teto\\Fushi Murasaki VIOLET")));
    }
    
    public List<Map.Entry<String, File>> getEffectiveVoicebankPaths() {
    	List<Map.Entry<String,File>> res =  new LinkedList<>(Arrays.asList(rootVbFolder));
    	res.addAll(userFolders);
    	res.add(new SimpleEntry<>(null,defaultVb));
    	return Collections.unmodifiableList(res);
    }
    
    private final Map<File, FSVoicebank> voicebanks;
    private VoicebankReader vbReader;
    
    @Inject
    public VoicebankManager(VoicebankReader vbReader) {
        voicebanks = new HashMap<>();
        this.vbReader = vbReader;
    }
    
    public FSVoicebank getDefaultVoicebank() {
    	if(userDefault != null) {
    		try {
				return getVoicebank(userDefault,false);
			} catch (IOException e) {
				System.err.println("Could not read default voicebank at '"+userDefault+"'");
				e.printStackTrace();
			}
    	}
    	try {
			return getVoicebank(defaultVb, false);
		} catch (IOException e) {
			System.err.println("CRITICAL: Could not load builtin default voicebank from '"+defaultVb+"'");
			e.printStackTrace();
		}
    	return null;
    }

    public boolean hasVoicebank(File location) {
        File normalized = normalize(location);
        return voicebanks.containsKey(normalized);
    }

    public FSVoicebank getVoicebank(File location, boolean reload) throws IOException {
    	if(reload || !hasVoicebank(location)) {
    		 FSVoicebank voicebank = vbReader.loadVoicebankFromDirectory(location);
             setVoicebank(location, voicebank);
             return voicebank;
    	}
    	File normalized = normalize(location);
        return voicebanks.get(normalized);
    }

    public void setVoicebank(File location, FSVoicebank voicebank) {
        File normalized = normalize(location);
        voicebanks.put(normalized, voicebank);
    }

    public void removeVoicebank(File location) {
        File normalized = normalize(location);
        voicebanks.remove(normalized);
    }

    private File normalize(File rawFile) {
        try {
            return rawFile.getCanonicalFile();
        } catch (IOException e) {
            // TODO: Handle this
            errorLogger.logError(e);
        }
        // Return raw file if it cannot be normalized.
        return rawFile;
    }
}
