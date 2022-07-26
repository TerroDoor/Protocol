package me.jack.mcprotocol;

public class ChunkSection {

    byte[] sky;
    byte[] emitted;
    short[] types;

    public ChunkSection() {

    }

    public ChunkSection(short[] types, byte[] sky, byte[] emitted) {
        this.types = types;
        this.sky = sky;
        this.emitted = emitted;
    }

}
