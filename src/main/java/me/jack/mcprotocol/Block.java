package me.jack.mcprotocol;

public class Block {

    private Chunk chunk;
    private int x;
    private int y;
    private int z;

    public Block(Chunk chunk, int x, int y, int z) {
        this.chunk = chunk;
        this.x = x;
        this.y = y;
        this.z = z;
    }

}
