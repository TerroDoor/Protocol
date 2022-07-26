package me.jack.mcprotocol;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;

public class DataConversion {

    private static final int SEGMENT_BITS = 0x7F;
    private static final int CONTINUE_BIT = 0x80;

    public static void readNBT(DataInput in) {

    }
    public static void writeUUID(DataOutput out, UUID uuid) {
        try {
            out.writeLong(uuid.getMostSignificantBits());
            out.writeLong(uuid.getLeastSignificantBits());

            System.out.println("wrote uuid");
        } catch (Throwable reason) {
            throw new RuntimeException(reason);
        }
    }

    public static void readPacket(DataInput in, int id) {
        readVarInt(in);//length (ignored to get ID)
        if (id != readVarInt(in))
            throw new IllegalStateException("Wrong packet ID");
    }

    public static void writePacket(DataOutput out, int id, Consumer<DataOutput> block) {
        try (final ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            try (final DataOutputStream data = new DataOutputStream(buffer)) {
                writeVarInt(data, id);
                block.accept(data);
                final byte[] bytes = buffer.toByteArray();
                writeVarInt(out, bytes.length);
                out.write(bytes, 0, bytes.length);
            }
        } catch (Throwable reason) {
            throw new RuntimeException(reason);
        }
    }

    public static byte readByte(DataInput in) {
        try {

            return in.readByte();

        } catch (Throwable reason) {
            throw new RuntimeException(reason);
        }
    }

    public static byte[] readBytes(DataInput in, int length) {
        try {

            byte[] bytes = new byte[length];
            in.readFully(bytes, 0, bytes.length);

            return bytes;

        } catch (Throwable reason) {
            throw new RuntimeException(reason);
        }
    }

    public static void writeBytes(DataOutput out, byte[] bytes) {
        try {
            //  writeVarInt(out, bytes.length);
            out.write(bytes, 0, bytes.length);

        } catch (Throwable reason) {
            throw new RuntimeException(reason);
        }
    }

    public static void writeByte(DataOutput out, byte b) {
        try {

            out.writeByte(b);

        } catch (Throwable reason) {
            throw new RuntimeException(reason);
        }
    }

    public static long readVarLong(DataInput in) throws IOException {
        long value = 0;
        int position = 0;
        byte currentByte;

        while (true) {
            currentByte = in.readByte();
            value |= (long) (currentByte & SEGMENT_BITS) << position;

            if ((currentByte & CONTINUE_BIT) == 0) break;

            position += 7;

            if (position >= 64) throw new RuntimeException("VarLong is too big");
        }

        return value;
    }

    public static void writeVarLong(DataOutput out, long value) {
        try {
            while (true) {
                if ((value & ~((long) SEGMENT_BITS)) == 0) {
                    out.writeLong(value);
                    return;
                }

                out.writeLong((value & SEGMENT_BITS) | CONTINUE_BIT);

                // Note: >>> means that the sign bit is shifted with the rest of the number rather than being left alone
                value >>>= 7;

            }

        } catch (Throwable reason) {
            throw new RuntimeException(reason);
        }

    }

    public static boolean readBoolean(DataInput in) {
        try {
            return in.readByte() == 0;
        } catch (Throwable reason) {
            throw new RuntimeException(reason);
        }
    }

    public static void writeBoolean(DataOutput out, boolean value) {
        try {
            out.writeBoolean(value);

        } catch (Throwable reason) {
            throw new RuntimeException(reason);
        }
    }

    public static String readString(DataInput in) {
        try {
            int length = readVarInt(in);//should this be ignored?
            if (length < 0 || length > 32762) throw new IllegalStateException("VarString too big!");
            final byte[] bytes = new byte[length];
            in.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Throwable reason) {
            throw new RuntimeException(reason);
        }
    }

    public static void writeString(DataOutput out, String string) {
        try {
            if (string.length() > 32767) throw new IllegalStateException("VarString too big!");
            byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
            writeVarInt(out, bytes.length);
            out.write(bytes, 0, bytes.length);
        } catch (Throwable reason) {
            throw new RuntimeException(reason);
        }
    }

    public static int readVarInt(DataInput in) {
       try {
           int value = 0;
           int position = 0;
           byte currentByte;

           while (true) {
               currentByte = in.readByte();
               value |= (currentByte & SEGMENT_BITS) << position;

               if ((currentByte & CONTINUE_BIT) == 0) break;

               position += 7;

               if (position >= 32) throw new RuntimeException("VarInt is too big");
           }

           return value;

       } catch (Throwable reason) {
           throw new RuntimeException(reason);
       }
    }

    public static void writeInt(DataOutput out, int value) {
        try {

            out.writeInt(value);

        } catch (Throwable reason) {
            throw new RuntimeException(reason);
        }
    }

    public static float readFloat(DataInput in) {
        try {

            return in.readFloat();

        } catch (Throwable reason) {
            throw new RuntimeException(reason);
        }
    }

    public static void writeFloat(DataOutput out, float value) {
        try {

            out.writeFloat(value);

        } catch (Throwable reason) {
            throw new RuntimeException(reason);
        }
    }

    public static void writeShort(DataOutput out, short value) {
        try {

            out.writeShort(value);

        } catch (Throwable reason) {
            throw new RuntimeException(reason);
        }
    }


    public static double readDouble(DataInput in) {
        try {

           return in.readDouble();

        } catch (Throwable reason) {
            throw new RuntimeException(reason);
        }
    }

    public static void writeDouble(DataOutput out, double value) {
        try {

            out.writeDouble(value);

        } catch (Throwable reason) {
            throw new RuntimeException(reason);
        }
    }

    public static void writeVarInt(DataOutput out, int value) {
        try {
            while (true) {
                if ((value & ~SEGMENT_BITS) == 0) {
                    out.writeByte(value);
                    return;
                }

                out.writeByte((value & SEGMENT_BITS) | CONTINUE_BIT);

                // Note: >>> means that the sign bit is shifted with the rest of the number rather than being left alone
                value >>>= 7;
            }
        } catch (Throwable reason) {
            throw new RuntimeException(reason);
        }
    }

}
