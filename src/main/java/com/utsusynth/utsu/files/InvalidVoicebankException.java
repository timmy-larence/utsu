package com.utsusynth.utsu.files;

import java.io.File;
import java.io.IOException;

/**
 * Thrown to indicate that the program has tried to read an invalid voicebank directory.
 */
public class InvalidVoicebankException extends IOException {
	private File file;
	
	public InvalidVoicebankException(File file) {
		super("The specified directory "+file.getPath()+" does not define a valid voicebank");
		this.file = file;
	}
	
	public File getDirectory() {
		return this.file;
	}
}
