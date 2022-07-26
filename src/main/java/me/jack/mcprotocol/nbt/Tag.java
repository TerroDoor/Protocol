package me.jack.mcprotocol.nbt;

import java.io.DataInputStream;

public class Tag {

    private final byte ID;
    private final Object payload;

    public Tag(byte ID, Object payload) {
        this.ID = ID;
        this.payload = payload;
    }

    public byte getID() {
        return ID;
    }

    public Object getPayload() {
        return payload;
    }

}
