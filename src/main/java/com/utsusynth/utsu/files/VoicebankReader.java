package com.utsusynth.utsu.files;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.MalformedInputException;
import java.nio.charset.UnmappableCharacterException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FileUtils;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.utsusynth.utsu.common.exception.ErrorLogger;
import com.utsusynth.utsu.model.voicebank.LyricConfig;
import com.utsusynth.utsu.model.voicebank.Voicebank;
import com.utsusynth.utsu.model.voicebank.FSVoicebank;

public class VoicebankReader {
    private static final ErrorLogger errorLogger = ErrorLogger.getLogger();

    private static final Pattern LYRIC_PATTERN = Pattern.compile("(.+\\.wav)=([^,]*),");
    private static final Pattern PITCH_PATTERN =
            Pattern.compile("([a-gA-G]#?[1-7])\\t(\\S*)\\t(\\S.*)");

    private final File defaultVoicePath;
    private final File lyricConversionPath;
    private final Provider<Voicebank> voicebankProvider;

    @Inject
    public VoicebankReader(
            File defaultVoicePath,
            File lyricConversionPath,
            Provider<Voicebank> voicebankProvider) {
        this.defaultVoicePath = defaultVoicePath;
        this.lyricConversionPath = lyricConversionPath;
        this.voicebankProvider = voicebankProvider;
    }

    public File getDefaultPath() {
        return defaultVoicePath;
    }
    
    /**
     * Returns {@code true} if the specified directory defines a voicebank.
     * <br><br>
     * This implementation uses the naive assumption that any directory containing
     * either an 'oto.ini' or an 'oto_ini.txt' file is a valid {@link FSVoicebank}. Therefore
     * it only checks if either of those files exist.
     * 
     * @param sourceDir the directory to check
     * @return if the specified directory is a valid voicebank
     */
    public static boolean isVoicebank(File sourceDir) {
    	if(!sourceDir.isDirectory())
    		sourceDir = sourceDir.getParentFile();
    	return new File(sourceDir,"oto.ini").exists() || new File(sourceDir,"oto_ini.txt").exists();
    }

    public FSVoicebank loadVoicebankFromDirectory(File sourceDir) throws IOException {
        File pathToVoicebank;
        if (!sourceDir.exists()) {
        	throw new FileNotFoundException(sourceDir.getCanonicalPath());
//            pathToVoicebank = defaultVoicePath;
        } else {
            if (!sourceDir.isDirectory()) {
                pathToVoicebank = sourceDir.getParentFile();
            } else {
                pathToVoicebank = sourceDir;
            }
        }
        
        if(!isVoicebank(pathToVoicebank)) {
        	throw new InvalidVoicebankException(pathToVoicebank);
        }
        
        FSVoicebank.Builder builder = voicebankProvider.get().toBuilder();
        
        builder.setPathToVoicebank(pathToVoicebank);
        System.out.println("Reading voicebank at " + pathToVoicebank + "...");

        // Parse character data in English or Japanese.
        String characterData =
                readConfigFile(pathToVoicebank.toPath().resolve("character.txt").toFile());
        String characterName = pathToVoicebank.getName();
        for (String rawLine : characterData.split("\n")) {
            String line = rawLine.trim();
            if (line.toLowerCase().startsWith("name=")) {
            	characterName = line.substring("name=".length());
                builder.setName(characterName);
            } else if (line.startsWith("名前：")) {
            	characterName = line.substring("名前：".length());
                builder.setName(characterName);
            } else if (line.toLowerCase().startsWith("author=")) {
                builder.setAuthor(line.substring("author=".length()));
            } else if (line.toLowerCase().startsWith("cv：")) {
                builder.setAuthor(line.substring("CV：".length()));
            } else if (line.startsWith("image=")) {
                builder.setImageName(line.substring("image=".length()));
            } else if (line.startsWith("画像：")) {
                builder.setImageName(line.substring("画像：".length()));
            }
        }

        // Parse description.
        File readmeFile = pathToVoicebank.toPath().resolve("readme.txt").toFile();
        builder.setDescription(readConfigFile(readmeFile));

        // Parse all oto_ini.txt and oto.ini files in arbitrary order.
        try {
            Files.walkFileTree(
                    pathToVoicebank.toPath(),
                    EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                    10,
                    new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path path, BasicFileAttributes attr) {
                            for (String otoName : ImmutableSet.of("oto.ini", "oto_ini.txt")) {
                                if (path.endsWith(otoName)) {
                                    Path pathToFile = path.toFile().getParentFile().toPath();
                                    parseOtoIni(pathToVoicebank, pathToFile, otoName, builder);
                                    break;
                                }
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException e) {
            // TODO: Handle this.
            errorLogger.logError(e);
        }

        // Parse pitch map in arbitrary order, if present.
        // TODO Arbitrary order might not be the best thing to do,
        // since these two files can have contradictory contents
        for (String pitchMapName : ImmutableSet.of("prefixmap", "prefix.map")) {
            parsePitchMap(pathToVoicebank.toPath().resolve(pitchMapName).toFile(), builder);
        }

        // Parse conversion set for romaji-hiragana-katakana conversion.
        readLyricConversionsFromFile(builder);
        
        System.out.println("Successfully read '"+characterName+"' from "+pathToVoicebank);
        
        return builder.build();
    }

    private void parseOtoIni(
            File pathToVoicebank,
            Path pathToOtoFile,
            String otoFile,
            FSVoicebank.Builder builder) {
        String otoData = readConfigFile(pathToOtoFile.resolve(otoFile).toFile());
        for (String rawLine : otoData.split("\n")) {
            String line = rawLine.trim();
            Matcher matcher = LYRIC_PATTERN.matcher(line);
            if (matcher.find()) {
                String fileName = matcher.group(1); // Assuming this is a .wav file
                String lyricName = matcher.group(2);
                if (lyricName.isEmpty()) {
                    // If no alias provided, use the file name as an adhoc alias.
                    lyricName = fileName.substring(0, fileName.length() - 4);
                }
                String[] configValues = line.substring(matcher.end()).split(",");
                if (configValues.length != 5 || fileName == null || lyricName == null) {
                    System.out.println("Received unexpected results while parsing oto.ini");
                    continue;
                }
                // Search for a frq file.
                String frqName = fileName.substring(0, fileName.length() - 4) + "_wav.frq";
                File frqFile = pathToOtoFile.resolve(frqName).toFile();
                builder.addLyric(
                        new LyricConfig(
                                pathToVoicebank,
                                pathToOtoFile.resolve(fileName).toFile(),
                                lyricName,
                                configValues),
                        frqFile.canRead());
            }
        }
    }

    private void parsePitchMap(File pitchMapFile, FSVoicebank.Builder builder) {
        String pitchData = readConfigFile(pitchMapFile);
        for (String rawLine : pitchData.split("\n")) {
            String line = rawLine.trim();
            // TODO: Handle the case of prefixes rather than suffixes.
            Matcher matcher = PITCH_PATTERN.matcher(line);
            if (matcher.find()) {
                String pitch = matcher.group(1);
                String prefix = matcher.group(2);
                String suffix = matcher.group(3);
                builder.addPitchMap(pitch, prefix, suffix);
            }
        }
    }

    /* Gets disjoint set used for romaji-hiragana-katakana conversions. */
    private void readLyricConversionsFromFile(FSVoicebank.Builder builder) {
        String conversionData = readConfigFile(lyricConversionPath);
        for (String line : conversionData.split("\n")) {
            builder.addConversionGroup(line.trim().split(","));
        }
    }

    private String readConfigFile(File file) {
        if (!file.canRead() || !file.isFile()) {
            // This is often okay.
            return "";
        }
        try {
            String charset = "UTF-8";
            CharsetDecoder utf8Decoder =
                    Charset.forName("UTF-8").newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT);
            try {
                utf8Decoder.decode(ByteBuffer.wrap(FileUtils.readFileToByteArray(file)));
            } catch (MalformedInputException | UnmappableCharacterException e) {
                charset = "SJIS";
            }
            return FileUtils.readFileToString(file, charset);
        } catch (IOException e) {
            // TODO Handle this.
            errorLogger.logError(e);
        }
        return "";
    }

    /**
     * Parses a file path, and replaces the strings "${DEFAULT}" and "${HOME}" with their
     * corresponding directories.
     */
    public File parseFilePath(String line, String property) {
        String pathString = line.substring(property.length());
        pathString = pathString.replaceFirst("\\$\\{DEFAULT\\}", defaultVoicePath.getAbsolutePath())
                .replaceFirst("\\$\\{HOME\\}", System.getProperty("user.home"));
        return new File(pathString);
    }
}
