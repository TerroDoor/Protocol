package me.jack.mcprotocol;

public class ChunkSection {

    final byte[] sky = new byte[2048];
    final byte[] emitted = new byte[2048];
    short[] types = new short[8192];

    public ChunkSection() {

    }

}
