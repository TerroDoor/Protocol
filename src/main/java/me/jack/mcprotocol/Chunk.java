package me.jack.mcprotocol;

import me.jack.mcprotocol.nbt.Tag;

import javax.print.DocFlavor;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.InflaterInputStream;

public class Chunk {

    //coords
    final int x, z;
    final ChunkSection[] sections = new ChunkSection[16];
    byte[] biomes;

    public Chunk(int x, int z, byte[] biomes) {
        this.x = x;
        this.z = z;
        this.biomes = biomes;
    }

    public void setBlockTypeAndMeta(int x, int y, int z, int type, int meta) {
        //format to YZX and shifting the bits to change it from XYZ for decoding with >> 4
        final int index = ((y & 0xf) << 8) | (z << 4) | x;

        //creating a new section (16x16x16) shifting the y is the same as y/16 for one section per section object
        final ChunkSection section = sections[y >> 4] == null ? sections[y >> 4] = new ChunkSection() : sections[y >> 4];

        //encoded with << 4
        section.types[index] = (short) (type << 4 | meta);

        System.out.println("SET BLOCK TYPE AND META: " + x + " - x " + y + " - y " + z + " - z " + index + " - index " + sections.length + " - sections length "
                + section.types[index] + " - types " + section.types.length + " types length");
    }

    public void setSkyLighting(int x, int y, int z, byte value) {
        //format to YZX and shifting the bits to change it from XYZ for decoding with >> 4
        final int index = ((y & 0xf) << 8) | (z << 4) | x;

        System.out.println("MATHS OF INDEX : " + ((y & 0xf) << 8) + " - index y " + (z << 4) + " - index z " + x + " - index x");

        //getting a chunk section (16x16x16) by diving the sections array by 16. y >> 4 is the same as y/16 for one section per section object
        final ChunkSection section = sections[y >> 4];

        //half the index lighting is half byte
        //in the case of x:1 y: 81 z: 1 the index becomes y: 256 z: 16 x:1 totalling 136 when divided by two ((256 / 2) + (16 / 2) + (1 / 2)) the 0.5 does not get added
        int half = index / 2;

        //getting the byte at the sky array indexed at half (136) index % 2 == 0 is calculating the remainder of index divided by 2. if i is even then the result is 0 otherwise
        //an odd number will result in 1
        //byte eachByte = section.sky[half];

        System.out.println("SET SKY: " + x + " - x " + y + " - y " + z + " - z " + index + " - index " + half + " - half " +
                section.sky[half] + " - pre previous " + section.sky.length + " - pre section sky length " + sections.length + " - pre sections length " + value + " - pre value");

        if (index % 2 == 0) {
            section.sky[half] = (byte) ((section.sky[half] & 0xf0) | value);
            System.out.println("INSIDE STATEMENT: " + section.sky[half] + " - previous " + value + " - value");
        } else {
            section.sky[half] = (byte) ((section.sky[half] & 0x0f) | (value << 4));
            System.out.println("ELSE STATEMENT: " + section.sky[half] + " - previous " + value + " - value");
        }

        System.out.println("SET SKY: " + x + " - x " + y + " - y " + z + " - z " + index + " - index " + half + " - half " +
                section.sky[half] + " - post previous " + section.sky.length + " - post section sky length " + sections.length + " - post sections length " + value + " - post value");


    }

    public void setEmittedLighting(int x, int y, int z, byte value) {
        //format to YZX and shifting the bits to change it from XYZ for decoding with >> 4
        final int index = ((y & 0xf) << 8) | (z << 4) | x;

        //getting a chunk section (16x16x16) at index. y >> 4 is the same as y/16 for one section per section object
        final ChunkSection section = sections[y >> 4];

        //half the index
        int half = index / 2;
        byte previous = section.emitted[half];
        byte idk = section.emitted[index];

        System.out.println("SET EMITTED: " + x + " - x " + y + " - y " + z + " - z " + index + " - index " + idk + " - section " + half + " - half " +
                previous + " - pre previous ");

        //%2==0 - says it's the first half of that byte, else second half
        if (index % 2 == 0) {
            section.emitted[half] = (byte) ((previous & 0xf0) | value);
        } else {
            section.emitted[half] = (byte) ((previous & 0x0f) | (value << 4));
        }

        System.out.println("SET EMITTED: " + x + " - x " + y + " - y " + z + " - z " + index + " - index " + idk + " - section " + half + " - half " +
                previous + " - post previous ");
    }

    public static void writeChunk(DataOutput out, Chunk chunk) {

        short mask = 0;//bitmask
        int sections_count;//section count


        for (int i = 0; i < 16; ++i) {
            //out of 16 possible sections we check them all

            if (chunk.sections[i] != null) {
                //same as += mask = mask | 1 << i
                mask |= 1 << i;
            }
        }
        //sections being used
        sections_count = Integer.bitCount(mask);

        DataConversion.writeShort(out, mask);

// calculate how big the data will need to be - types,sky,emitted combined is 3 * 4096(size of the arrays) multiplied by the sections being used,
// biomes is 256 always so its to the size of the entire 'chunk'
        final int size = (3 * 4096 * sections_count) + 256;

        DataConversion.writeVarInt(out, size);

        for (final ChunkSection section : chunk.sections) {
            if (section != null) {
                for (short types : section.types) {
                    DataConversion.writeByte(out, (byte) (types & 0xFF));
                    DataConversion.writeByte(out, (byte) (types >> 8));
                }
            }
        }

        for (ChunkSection section : chunk.sections) {
            if (section != null) {

                DataConversion.writeBytes(out, section.sky);
            }
        }

        for (final ChunkSection section : chunk.sections) {
            if (section != null) {
                DataConversion.writeBytes(out, section.emitted);

            }
        }

        //write biomes
        DataConversion.writeBytes(out, chunk.biomes);


    }

    public static Chunk loadChunk(int x, int z) throws IOException {

        final int flooredX = x >> 5;
        final int flooredZ = z >> 5;
        File path;

        if (x < 0 && z < 0) {//todo fix negative mca's
             path = new File("C:\\Users\\newja\\AppData\\Roaming\\.minecraft\\saves\\New World\\region\\r.-" + newX + ".-" + newZ + ".mca");
        } else {
             path = new File("C:\\Users\\newja\\AppData\\Roaming\\.minecraft\\saves\\New World\\region\\r." + flooredX + "." + flooredZ + ".mca");
        }

        RandomAccessFile file = new RandomAccessFile(path, "r");

        System.out.println(x + "x" + z + "z");

        //thankyou exerosis
        //the formula to point to the start of the region file where the chunks are located. we point to the ones we want
        //the location table gives you a position that already skips the timestamp table


        file.seek(4 * ((x & 32) + (z & 32) * 32));
        //this seeks to where the chunk is located (top of file to nbt section bottom of file). we point to the one corresponding to the first seek.
        file.seek(4096 * (file.readInt() >> 8));
        //get length of the file from its starting positions and store the data in a byte array to later be used for decompression(minimizing data size at a cost of CPU)
        final int length = file.readInt();


        if (DataConversion.readByte(file) == 2) {
            byte[] bytes = DataConversion.readBytes(file, length);
            //System.out.println(offsetPosition + "=offsetPos " + positionSize + "=posSize");

            try (final InputStream compressed = new ByteArrayInputStream(bytes)) {
                try (final InputStream datastream = new InflaterInputStream(compressed)) {
                    try (final DataInputStream input = new DataInputStream(datastream)) {

                        final List<Tag> tags = new ArrayList<>();
                        // Parse tags, starting with ID
                        final byte rootID = input.readByte();


                        System.out.println(rootID + " rootID");
                        //two bytes for an unsigned big endian length (short is 2 byte)
                        final short rootLength = input.readShort();

                        System.out.println(rootLength + " rootLength");
                        //utf string of that length for the name
                        // final byte[] rootBytes = new byte[rootLength];

                        //System.out.println(rootBytes + " rootBytes");
                        //   input.readFully(rootBytes);

                        Tag rootTag = readValue(input, rootID);


                        Map<String, Tag> r = (Map<String, Tag>) rootTag.getPayload();
                        //  System.out.println(r.keySet() + "." + r.entrySet());

                        Map<String, Tag> level = (Map<String, Tag>) r.get("Level").getPayload();
                        List<Tag> sections = (List<Tag>) level.get("Sections").getPayload();


                        byte[] biomes = (byte[]) level.get("Biomes").getPayload();
                        Chunk chunk = new Chunk(x,z, biomes);

                        System.out.println(biomes.length + " biomes");
                        // final ChunkSection section = chunkSection[y >> 4] == null ? chunkSection[y >> 4] = new ChunkSection() : chunkSection[y >> 4];

                        for (int i = 0; i < sections.size(); i++) {
                            Map<String, Tag> entry = (Map<String, Tag>) sections.get(i);


                            byte y = (byte) entry.get("Y").getPayload();
                            byte[] blocks = (byte[]) entry.get("Blocks").getPayload();

                            for (byte b : blocks) {
                                System.out.println(b);
                            }
                            byte[] data = (byte[]) entry.get("Data").getPayload();
                            byte[] blocklight = (byte[]) entry.get("BlockLight").getPayload();
                            byte[] skylight = (byte[]) entry.get("SkyLight").getPayload();

                            short[] types = new short[blocks.length];

                            //blocks is 4096 and data is half(2048). we need to make room for data using this maths
                            for (int j = 0; j < types.length; j++) {
                                types[j] = (short) (blocks[i] << 4 | data[i / 2] >> 4 * (i % 2) & 0xF);
                            }

                            chunk.sections[y] = new ChunkSection(types, skylight, blocklight);


                            System.out.println(blocks.length + " blocks\n" + data.length + " data\n" + blocklight.length + " blocklight\n" + skylight.length + " skylight\n");

                            System.out.println(y + " byte from section " + i);

                            System.out.println("CHUNK =" + chunk);
                        }

                        return chunk;
                    }
                }
            }
        }
        return null;
    }



/*
           for (Tag tag : tags) {
                Map<String, Tag> payloadMapTags = (Map<String, Tag>) tag.getPayload();
                System.out.println(payloadMapTags.size());
                // System.out.println(entry.getKey() + " -key\n" + entry.getValue().getID() + " -value ID\n" + entry.getValue().getPayload() + " -value paylaod\n");
                Map<String, Tag> level = (Map<String, Tag>) payloadMapTags.get("Level").getPayload();
                List<Tag> sections = (List<Tag>) level.get("Sections").getPayload();


                for (int i = 0; i < sections.size(); i++) {
                    Map<String, Tag> entry = (Map<String, Tag>) sections.get(i);

                    //System.out.println(section.entrySet());

                    for (Tag sectionTag : entry.values()) {

                        if (sectionTag.getID() == 7) {
                            byte[] b = (byte[]) sectionTag.getPayload();
                            System.out.println(b.length + " bytes from section " + i);

                        }
                        if (sectionTag.getID() == 1) {
                            byte b = (byte) sectionTag.getPayload();
                            System.out.println(b + " byte from section " + i);

                        }
                    }
                }
            }
        }
 */

            /*
             Map<String, Tag> payloadMapTags = (Map<String, Tag>) tag.getPayload();
                System.out.println(payloadMapTags.size());
                // System.out.println(entry.getKey() + " -key\n" + entry.getValue().getID() + " -value ID\n" + entry.getValue().getPayload() + " -value paylaod\n");
                Map<String, Tag> level = (Map<String, Tag>) payloadMapTags.get("Level").getPayload();
                List<Tag> sections = (List<Tag>) level.get("Sections").getPayload();


                for (int i = 0; i < sections.size(); i++) {
                    Map<String, Tag> section = (Map<String, Tag>) sections.get(i);

                    //System.out.println(section.entrySet());

                    for (Tag sectionTag : section.values()) {

                        if (sectionTag.getID() == 7) {
                            byte[] b = (byte[]) sectionTag.getPayload();
                            System.out.println(b.length + " bytes from section " + i);

                        }
                        if (sectionTag.getID() == 1) {
                            byte b = (byte) sectionTag.getPayload();
                            System.out.println(b + " byte from section " + i);

                        }
                    }
                }
            }
        }

             */
/*



        final List<Tag> tags = new ArrayList<>();


        // Parse tags, starting with ID
        final byte ID = input.readByte();
        //two bytes for an unsigned big endian length (short is 2 byte)
        final short len = input.readShort();
        //utf string of that length for the name
        input.skipBytes(len);
        // final String name = new String(bytes, StandardCharsets.UTF_8);


        //   System.out.println(name);
        ///   System.out.println("first read on compound = " + name);//always null according to wiki

        //  System.out.println(ID + "ID");
        Tag tag = readValue(input, ID);
        tags.add(tag);

        Map<String, Tag> payloadMapTags = (Map<String, Tag>) tag.getPayload();

        System.out.println(payloadMapTags.size());
        // System.out.println(entry.getKey() + " -key\n" + entry.getValue().getID() + " -value ID\n" + entry.getValue().getPayload() + " -value paylaod\n");
        Map<String, Tag> level = (Map<String, Tag>) payloadMapTags.get("Level").getPayload();
        List<Tag> sections = (List<Tag>) level.get("Sections").getPayload();


        for (int i = 0; i < sections.size(); i++) {
            Map<String, Tag> section = (Map<String, Tag>) sections.get(i);

            //System.out.println(section.entrySet());

            for (Tag sectionTag : section.values()) {

                if (sectionTag.getID() == 7) {
                    byte[] b = (byte[]) sectionTag.getPayload();
                    System.out.println(b.length + " bytes from section " + i);

                }
                if (sectionTag.getID() == 1) {
                    int b = (int) sectionTag.getPayload();
                    System.out.println(b + " byte from section " + i);

                }
            }
        }
 */


    /*
    A list is like
    List type id
    List name (as this is always nested in a compound)
    Child type id
    Children length (int)
    payloads
     */

    /*
    Payloads is just the payload of the child type id
    E.g. if the length is 4 and the child type id is bytes, it's 4 bytes
    If the length is 5 and the type is int, it's 5 ints
    If the id is 10 (compound) and the length is 2, it's 2 compound tags (but just the children)
    The children in the latter case do, however, have their own type id and name
    Also, name can be read with readUTF()
    You apparently don't need to manually read the length and such
    I found out when reading the glowstone source
     */

    public static Tag readValue(DataInputStream input, byte ID) throws IOException {
        Object payload;
        final int length;
        final byte[] bytes;
        final String string;

        switch (ID) {
            case 0:
                return new Tag(ID, 0);
            case 1:
                payload = input.readByte();
                return new Tag(ID, payload);
            case 2:
                payload = input.readShort();
                return new Tag(ID, payload);

            //   System.out.println(ID + "=id " + name + "=NAME " + payload + "=payload SHORT\\");
            case 3:
                payload = input.readInt();
                return new Tag(ID, payload);

            //   System.out.println(ID + "=id " + name + "=NAME " + payload + "=payload INT\\");
            case 4:
                payload = input.readLong();
                return new Tag(ID, payload);

            //   System.out.println(ID + "=id " + name + "=NAME " + payload + "=payload LONG\\");
            case 5:
                payload = input.readFloat();
                return new Tag(ID, payload);

            //  System.out.println(ID + "=id " + name + "=NAME " + payload + "=payload FLOAT\\");
            case 6:
                payload = input.readDouble();
                return new Tag(ID, payload);

            //  System.out.println(ID + "=id " + name + "=NAME " + payload + "=payload DOUBLE\\");
            case 7:
                //bytearray tag
                length = input.readInt();//tag int payload size
                bytes = new byte[length];//tag bytes payload
                input.readFully(bytes);
                payload = bytes;
                return new Tag(ID, payload);

            case 8:
                //string tag
                length = input.readUnsignedShort();//tag short payload size
                bytes = new byte[length];
                input.readFully(bytes);
                string = new String(bytes, StandardCharsets.UTF_8);//utf string resembled by length in bytes
                payload = string;
                return new Tag(ID, payload);

            case 9:
                //list tag
                byte tagID = input.readByte();//list type ID
                length = input.readInt();//list name

                List<Object> payloadListTags = new ArrayList<>(length);
                //TAG_Byte's payload tagId, then TAG_Int's payload size, then size tags' payloads, all of type tagId.

                for (int i = 0; i < length; i++) {
                    Tag tag = readValue(input, tagID);
                    payloadListTags.add(tag.getPayload());
                }

                payload = payloadListTags;

                return new Tag(ID, payload);


            case 10:

                Map<String, Tag> payloadMapTags = new HashMap<>();

                byte typeID;

                while ((typeID = input.readByte()) != 0) {


                    //read name
                    String name = input.readUTF();
                    // System.out.println(name + " =compound name");
                    Tag tag = readValue(input, typeID);

                    payloadMapTags.put(name, tag);

                }

                payload = payloadMapTags;
                return new Tag(ID, payload);

            case 11:
                //int array tag
                length = input.readInt();
                int[] ints = new int[length];
                //cant readFully on int array so asign ints manually
                for (int i = 0; i < length; ++i) {
                    ints[i] = input.readInt();
                }

                payload = ints;
                return new Tag(ID, payload);

            case 12:
                //long array tag
                length = input.readInt();
                long[] longs = new long[length];
                //cant readFully on int array so asign ints manually
                for (int i = 0; i < length; ++i) {
                    longs[i] = input.readLong();
                }
                payload = longs;

                return new Tag(ID, payload);

        }

        return null;
    }
}



            /*
        String filepath = ""C:\\Users\\newja\\AppData\\Roaming\\.minecraft\\saves\\New World\\region\\r.mca"";
        int startPos = filepath.lastIndexOf("r");
        int dotPos = filepath.lastIndexOf(".");
        String str = filepath.substring(startPos, dotPos);
        System.out.println(str);
        int x = Integer.parseInt(str.substring(2, 3));
        System.out.println(str.substring(2, 3));
        System.out.println(str.substring(4, 5));
        int z = Integer.parseInt(str.substring(4, 5));
        //System.out.println(x + " x val " + z + " z val");

         */

    /*


    int nSectors = (int) file.length() / FILE_SIZE;
    List<Boolean> sectorFree = new ArrayList<>(nSectors);

        for (int i = 0; i < nSectors; ++i) {
        sectorFree.add(true);
    }

        sectorFree.set(0, false); // chunk offset table
        sectorFree.set(1, false); // for the last modified info

        file.seek(0);
        for (int i = 0; i < SECTIONS; ++i) {
        int offset = file.readInt();
        offsets[i] = offset;
        if (offset != 0 && (offset >> 8) + (offset & 0xFF) <= sectorFree.size()) {
            for (int sectorNum = 0; sectorNum < (offset & 0xFF); ++sectorNum) {
                sectorFree.set((offset >> 8) + sectorNum, false);
            }
        }
    }

    */


