package com.hejunlin.videorecord;

import java.io.Serializable;

public class VideoCodecModel implements Serializable {

    private static final long serialVersionUID = -1307249622002520298L;
    public String srcPath;
    public String dstPath;
    public long videoCreateTime;
    public int id;


    public VideoCodecModel() {
    }

    public String getSrcPath() {
        return srcPath;
    }

    public void setSrcPath(String srcPath) {
        this.srcPath = srcPath;
    }

    public String getDstPath() {
        return dstPath;
    }

    public void setDstPath(String dstPath) {
        this.dstPath = dstPath;
    }

    public long getVideoCreateTime() {
        return videoCreateTime;
    }

    public void setVideoCreateTime(long videoCreateTime) {
        this.videoCreateTime = videoCreateTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VideoCodecModel)) return false;

        VideoCodecModel that = (VideoCodecModel) o;

        if (videoCreateTime != that.videoCreateTime) return false;
        if (!srcPath.equals(that.srcPath)) return false;
        return dstPath.equals(that.dstPath);

    }
}
