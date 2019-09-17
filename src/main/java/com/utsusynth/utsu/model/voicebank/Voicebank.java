package com.utsusynth.utsu.model.voicebank;

import javafx.scene.image.Image;

public interface Voicebank {
    public static interface Builder {
        
    }
    
    public String getName();
    public String getAuthor();
    public String getDescription();
    public Image getImage();
    
    public void setName(String name);
    public void setAuthor(String author);
    public void setDescription(String description);
    public void setImage(Image image);
    
    public Builder toBuilder();
}
