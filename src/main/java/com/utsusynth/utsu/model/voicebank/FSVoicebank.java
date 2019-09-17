package com.utsusynth.utsu.model.voicebank;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import com.google.common.base.CharMatcher;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.utsusynth.utsu.common.data.LyricConfigData;
import com.utsusynth.utsu.common.data.LyricConfigData.FrqStatus;
import com.utsusynth.utsu.common.data.PitchMapData;
import com.utsusynth.utsu.engine.FrqGenerator;

/**
 * In-code representation of a voice bank. Compatible with oto.ini files. TODO: Support oto_ini.txt
 * as well
 */
public class FSVoicebank {
    // TODO: Once you have a VoicebankManager, consider sharing between voicebanks.
    private final DisjointLyricSet conversionSet;
    private final LyricConfigMap lyricConfigs;
    private final PitchMap pitchMap;
    private final Set<File> soundFiles;
    private final FrqGenerator frqGenerator;

    private File pathToVoicebank; // Example: "/Library/Iona.utau/"
    private String name; // Example: "Iona"
    private String author; // Example: "Lethe"
    private String description; // Contents of readme.txt
    private String imageName; // Example: "img.bmp"
    
    public File getLocation() {
        return pathToVoicebank;
    }

    public static class Builder implements Voicebank.Builder {
        private final FSVoicebank newVoicebank;

        private Builder(FSVoicebank newVoicebank) {
            this.newVoicebank = newVoicebank;
        }

        public Builder setPathToVoicebank(File pathToVoicebank) {
            newVoicebank.pathToVoicebank = pathToVoicebank;
            return this;
        }

        public Builder setName(String name) {
            newVoicebank.name = name;
            return this;
        }

        public Builder setAuthor(String author) {
            newVoicebank.author = author;
            return this;
        }

        public Builder setDescription(String description) {
            newVoicebank.description = description;
            return this;
        }

        public Builder setImageName(String imageName) {
            newVoicebank.imageName = imageName;
            return this;
        }

        public Builder addLyric(LyricConfig config, boolean hasFrq) {
            newVoicebank.lyricConfigs.addConfig(config);
            if (hasFrq) {
                newVoicebank.soundFiles.add(config.getPathToFile());
            }
            return this;
        }

        public Builder addPitchMap(String pitch, String prefix, String suffix) {
            newVoicebank.pitchMap.put(pitch, prefix, suffix);
            return this;
        }

        public Builder addConversionGroup(String... members) {
            newVoicebank.conversionSet.addGroup(members);
            return this;
        }

        public FSVoicebank build() {
            if (newVoicebank.pathToVoicebank == null) {
                // TODO: Handle this.
                System.out.println("Tried to build an empty voicebank!");
            }
            return newVoicebank;
        }
    }

    public FSVoicebank(
            LyricConfigMap lyricConfigs,
            PitchMap pitchMap,
            DisjointLyricSet conversionSet,
            Set<File> soundFiles,
            FrqGenerator frqGenerator) {
        this.lyricConfigs = lyricConfigs;
        this.pitchMap = pitchMap;
        this.conversionSet = conversionSet;
        this.soundFiles = soundFiles;
        this.frqGenerator = frqGenerator;

        // Default values.
        this.name = "";
        this.author = "";
        this.description = "";
        this.imageName = "";
    }

    public Builder toBuilder() {
        // Returns the builder of a new Voicebank with this one's attributes.
        // The old Voicebank's final fields are used--the objects are not regenerated.
        return new Builder(
                new FSVoicebank(
                        this.lyricConfigs,
                        this.pitchMap,
                        this.conversionSet,
                        this.soundFiles,
                        this.frqGenerator)).setPathToVoicebank(this.pathToVoicebank)
                                .setName(this.name).setAuthor(this.author)
                                .setDescription(this.description).setImageName(this.imageName);
    }

    /**
     * Should be called when lyric is expected to have an exact match in voicebank.
     */
    public Optional<LyricConfig> getLyricConfig(String trueLyric) {
        return getLyricConfig("", trueLyric, "");
    }

    public Optional<LyricConfig> getLyricConfig(String prevLyric, String lyric, String pitch) {
        String prefix = getVowel(prevLyric) + " "; // Most common VCV format.
        String suffix = pitchMap.getSuffix(pitch); // Pitch suffix.

        // Check all possible prefix/lyric/suffix combinations.
        for (String combo : allCombinations(prefix, lyric, suffix)) {
            if (lyricConfigs.hasLyric(combo)) {
                return Optional.of(lyricConfigs.getConfig(combo));
            }
        }

        SortedSet<LyricConfig> matches = new TreeSet<>();
        for (String convertedLyric : conversionSet.getGroup(lyric)) {
            if (convertedLyric.equals(lyric)) {
                // Don't check the same lyric twice.
                continue;
            }

            for (String combo : allCombinations(prefix, convertedLyric, suffix)) {
                if (lyricConfigs.hasLyric(combo)) {
                    matches.add(lyricConfigs.getConfig(combo));
                }
            }
        }
        // For now, arbitrarily but consistently return the first match.
        if (!matches.isEmpty()) {
            return Optional.of(matches.first());
        }

        return Optional.absent();
    }

    // Finds the vowel sound of a lyric by converting to ASCII and taking the last character.
    private char getVowel(String prevLyric) {
        for (String convertedLyric : conversionSet.getGroup(prevLyric)) {
            if (CharMatcher.ascii().matchesAllOf(convertedLyric)) {
                return convertedLyric.toLowerCase().charAt(convertedLyric.length() - 1);
            }
        }
        // Return this if no vowel found.
        return '-';
    }

    private List<String> allCombinations(String prefix, String lyric, String suffix) {
        // Exact lyric match is prioritized first.
        return ImmutableList.of(lyric, lyric + suffix, prefix + lyric + suffix, prefix + lyric);
    }

    /**
     * Returns a list of sub-folders for WAV files in the voicebank.
     */
    public Set<String> getCategories() {
        return lyricConfigs.getCategories();
    }

    public Iterator<LyricConfig> getLyricConfigs(String category) {
        return lyricConfigs.getConfigs(category);
    }

    /**
     * Gets iterator of lyric config data sets to print to the frontend.
     */
    public Iterator<LyricConfigData> getLyricData(String category) {
        Iterator<LyricConfig> configIterator = lyricConfigs.getConfigs(category);
        return new Iterator<LyricConfigData>() {
            @Override
            public boolean hasNext() {
                return configIterator.hasNext();
            }

            @Override
            public LyricConfigData next() {
                LyricConfig config = configIterator.next();
                if (config != null) {
                    return config.getData(soundFiles.contains(config.getPathToFile()));
                }
                return null;
            }
        };
    }

    public boolean addLyricData(LyricConfigData data) {
        LyricConfig newConfig = new LyricConfig(
                pathToVoicebank,
                data.getLyric(),
                data.getFileName(),
                data.getConfigValues());
        return lyricConfigs.addConfig(newConfig);
    }

    public void removeLyricConfig(String lyric) {
        lyricConfigs.removeConfig(lyric);
    }

    public void modifyLyricData(LyricConfigData data) {
        LyricConfig newConfig = new LyricConfig(
                pathToVoicebank,
                data.getLyric(),
                data.getFileName(),
                data.getConfigValues());
        lyricConfigs.setConfig(newConfig);
    }

    public Iterator<PitchMapData> getPitchData() {
        Iterator<String> pitchIterator = pitchMap.getOrderedPitches();
        return new Iterator<PitchMapData>() {
            @Override
            public boolean hasNext() {
                return pitchIterator.hasNext();
            }

            @Override
            public PitchMapData next() {
                String pitch = pitchIterator.next();
                return new PitchMapData(pitch, pitchMap.getPrefix(pitch), pitchMap.getSuffix(pitch));
            }
        };
    }

    public void setPitchData(PitchMapData data) {
        // Replace value that has changed, leave others the same.
        pitchMap.put(data.getPitch(), data.getPrefix(), data.getSuffix());
    }

    private boolean generateFrq(File wavFile) {
        String wavName = wavFile.getName();
        String frqName = wavName.substring(0, wavName.length() - 4) + "_wav.frq";
        File frqFile = wavFile.getParentFile().toPath().resolve(frqName).toFile();
        frqGenerator.genFrqFile(wavFile, frqFile);
        if (frqFile.canRead()) {
            soundFiles.remove(frqFile); // Removes existing frq file, if present.
            soundFiles.add(frqFile);
            return true;
        }
        return false;
    }

    /**
     * Generates the specified frq files and updates each piece of data.
     */
    public void generateFrqs(Iterator<LyricConfigData> dataIterator) {
        while (dataIterator.hasNext()) {
            LyricConfigData data = dataIterator.next();
            if (data == null) {
                continue;
            }
            data.setFrqStatus(FrqStatus.LOADING);
            if (generateFrq(data.getPathToFile())) {
                data.setFrqStatus(FrqStatus.VALID);
            } else {
                data.setFrqStatus(FrqStatus.INVALID);
            }
        }
    }

    public String getName() {
        return name;
    }

    public String getAuthor() {
        return author;
    }

    public String getImageName() {
        return imageName;
    }

    public String getImagePath() {
        return new File(pathToVoicebank, imageName).getAbsolutePath();
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        // Crappy string representation of a Voicebank object.
        String result = "";
        return result + " " + pathToVoicebank + " " + name + " " + imageName;
    }
}
