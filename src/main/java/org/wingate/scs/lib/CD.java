package org.wingate.scs.lib;

import java.util.ArrayList;
import java.util.List;

public class CD {

    private final String diskLocation;

    private String genre;
    private String year;
    private String uid;
    private String comment;
    private String performer;
    private String title;
    private String audioFile;

    private final List<Track> tracks;

    public CD(String diskLocation) {
        this.diskLocation = diskLocation;
        tracks = new ArrayList<>();
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getPerformer() {
        return performer;
    }

    public void setPerformer(String performer) {
        this.performer = performer;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAudioFile() {
        return audioFile;
    }

    public void setAudioFile(String audioFile) {
        this.audioFile = audioFile;
    }

    public List<Track> getTracks() {
        return tracks;
    }

    public String getDiskLocation() {
        return diskLocation;
    }
}
