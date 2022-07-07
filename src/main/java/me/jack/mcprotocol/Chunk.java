package me.jack.mcprotocol;

public class Chunk {

    //coords
    final int x, z;
    final ChunkSection[] sections = new ChunkSection[16];
    final byte[] biomes = new byte[256];

    public Chunk(int x, int z) {
        this.x = x;
        this.z = z;

    }

    public void setBlockTypeAndMeta(int x, int y, int z, int type, int meta) {

        final int index = x | z << 4 | y << 8;
        //ChunkSection section = sections[0];//bottom of chunk?
        //final int types = section.types[index] = (short) (type << 4);
        //section.types[index] = (short) (types | meta);

        final ChunkSection section = sections[y & 15] == null ? sections[y & 15] = new ChunkSection() : sections[y & 15];
        section.types[index] = (short) ((type << 4) | meta);


    }


}

