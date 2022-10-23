package me.jack.mcprotocol;

import com.google.gson.Gson;
import org.json.JSONObject;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {

    public static final String MOTD = new Gson().toJson(
            Map.of("version", Map.of("name", "1.8.8", "protocol", 47),
                    "players", Map.of("max", 1, "online", 0),
                    "description", "Testing Server")
    );

    public static void main(String[] args) throws Throwable {

        KeyPair pair = generateKeys();


        System.out.println("Starting server...");


        try (final ServerSocket server = new ServerSocket(25565)) {
            while (server.isBound()) try (final Socket client = server.accept()) {
                System.out.println("Got connection");
                DataOutputStream out = new DataOutputStream(client.getOutputStream());
                DataInputStream in = new DataInputStream(client.getInputStream());

                //handshake
                int status = getStatus(in);

                if (status == 1) {
                    DataConversion.readPacket(in, 0x00);
                    DataConversion.writePacket(out, 0x00, $ -> DataConversion.writeString($, MOTD));

                    DataConversion.readPacket(in, 0x01);
                    final long time = DataConversion.readVarLong(in);
                    DataConversion.writePacket(out, 0x01, $ -> DataConversion.writeVarLong($, time));

                } else if (status == 2) {

                    //login start
                    DataConversion.readPacket(in, 0x00);
                    final String username = DataConversion.readString(in);

                    //encryption request
                    byte[] publicArray = pair.getPublic().getEncoded();
                    final byte[] requestTokenArray = new byte[4];

                    sendEncryptRequest(requestTokenArray, publicArray, out);


                    //encryption response
                    DataConversion.readPacket(in, 0x01);

                    int sharedLength = DataConversion.readVarInt(in);
                    System.out.println(sharedLength + " sharedLength");

                    byte[] secretArray = DataConversion.readBytes(in, sharedLength);
                    int secret = secretArray.length;
                    System.out.println(secret + " secret");

                    int tokenLength = DataConversion.readVarInt(in);
                    System.out.println(tokenLength + " tokenLength");

                    byte[] responseTokenArray = DataConversion.readBytes(in, tokenLength);
                    int responseToken = responseTokenArray.length;
                    System.out.println(responseToken + " responseToken");

                    //decrypt rsponse
                    Cipher cipher = getResponseCipher(pair);

                    byte[] decryptedToken = cipher.doFinal(responseTokenArray);
                    byte[] decryptedSecret = cipher.doFinal(secretArray);


                    if (Arrays.equals(requestTokenArray, decryptedToken)) {
                        System.out.println("Match");


                        String hexdigest = getHexDigest(decryptedSecret, publicArray);
                        String hash = getHash(hexdigest, username);


                        /*

                        String symbol = "-";
                        String newHash = new String();

                        for (int i = 0; i < hash.length(); i++) {
                            // Insert the original string character
                            // into the new string
                            newHash += hash.charAt(i);

                            if (i == 7 || i == 11 || i == 15 || i == 19) {
                                newHash += symbol;
                            }
                        }



                         */


                        final UUID uuid = new UUID(
                                Long.parseUnsignedLong(hash, 0, 16, 16),
                                Long.parseUnsignedLong(hash, 16, 32, 16)
                        );


                        System.out.println(uuid + " UUID");
                        System.out.println(hash + " HASH");


                        final IvParameterSpec iv = new IvParameterSpec(decryptedSecret);
                        final SecretKeySpec keySpec = new SecretKeySpec(decryptedSecret, "AES");

                        final Cipher cipherOut = Cipher.getInstance("AES/CFB8/NoPadding");
                        cipherOut.init(Cipher.ENCRYPT_MODE, keySpec, iv);

                        final Cipher cipherIn = Cipher.getInstance("AES/CFB8/NoPadding");
                        cipherIn.init(Cipher.DECRYPT_MODE, keySpec, iv);

                        final DataOutputStream output = new DataOutputStream(new CipherOutputStream(out, cipherOut));
                        final DataInputStream input = new DataInputStream(new CipherInputStream(in, cipherIn));

                        DataConversion.writePacket(output, 0x02, $ -> {
                            DataConversion.writeString($, uuid.toString());
                            DataConversion.writeString($, username);
                        });

                        //play state
                        sendPlayStatePacket(output);
                        //pos packet (closes downlaoding terrain)
                        sendPosPacket(output);
                        //chunk
                        sendChunkPacket(output);
                        //send keepALive packet every 20 sec
                        sendKeepAlive(output);

                        //listen for incoming packets
                        while (server.isBound()) {
                            processIncomingPackets(input);
                        }
                    }
                }
            }

        } catch (Throwable reason) {
            reason.printStackTrace();
        }
    }


    public static KeyPair generateKeys() throws NoSuchAlgorithmException {
        //encrpytion
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(1024);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        return keyPair;
    }

    public static Cipher getResponseCipher(KeyPair pair) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyException {
        Cipher cipher = Cipher.getInstance("RSA");


        //encryption response
        byte[] privateArray = pair.getPrivate().getEncoded();
        //decryption
        KeyFactory factory = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec encoded = new PKCS8EncodedKeySpec(privateArray);
        PrivateKey privKey = factory.generatePrivate(encoded);


        cipher.init(Cipher.DECRYPT_MODE, privKey);

        return cipher;
    }

    public static int getStatus(DataInput in) throws IOException {
        //handshake
        DataConversion.readPacket(in, 0x00);

        int version = DataConversion.readVarInt(in);
        String hostname = DataConversion.readString(in);
        short port = in.readShort();
        int status = DataConversion.readVarInt(in);


        System.out.println("version = " + version);
        System.out.println("hostname = " + hostname);
        System.out.println("port = " + port);
        System.out.println("status = " + status);

        return status;
    }

    public static void sendEncryptRequest(byte[] requestTokenArray, byte[] publicArray, DataOutput out) {
        //id
        final byte[] serverID = "".getBytes(StandardCharsets.UTF_8);
        final int ID = serverID.length;

        //encryption key
        int publicKey = publicArray.length;

        //token
        new Random().nextBytes(requestTokenArray);
        final int requestToken = requestTokenArray.length;

        System.out.println(ID + " id");
        System.out.println(requestToken + " requestToken");

        DataConversion.writePacket(out, 0x01, $ -> {
            DataConversion.writeVarInt($, ID);
            DataConversion.writeBytes($, serverID);

            DataConversion.writeVarInt($, publicKey);
            DataConversion.writeBytes($, publicArray);

            DataConversion.writeVarInt($, requestToken);
            DataConversion.writeBytes($, requestTokenArray);
        });
    }

    private static void processIncomingPackets(DataInput input) throws IOException {

        final int length = DataConversion.readVarInt(input);
        final int packetID = DataConversion.readVarInt(input);

        //switch between packet ID's
        switch (packetID) {
            case (0x01):
            case (0x02):
            case (0x03):

            case (0x04):
                //System.out.println("detected 0x04");
                double xPos = DataConversion.readDouble(input);
                double yPos = DataConversion.readDouble(input);
                double zPos = DataConversion.readDouble(input);
                boolean onGroundPos = DataConversion.readBoolean(input);

                // System.out.println(xPos + "x" + yPos + "y" + zPos + "z" + "g" + onGroundPos);
                break;

            case (0x05):

            case (0x06):
                //System.out.println("detected 0x06");
                double xPosLook = DataConversion.readDouble(input);
                double yPosLook = DataConversion.readDouble(input);
                double zPosLook = DataConversion.readDouble(input);
                double yawPosLook = DataConversion.readFloat(input);
                double pitchPosLook = DataConversion.readFloat(input);
                boolean onGroundPosLook = DataConversion.readBoolean(input);

                //System.out.println(xPosLook + "x" + yPosLook + "y" + zPosLook + "z" + "g" + onGroundPosLook);
                break;

            case (0x07):
            case (0x08):
            case (0x09):
            case (10):
            case (11):
            case (12):
            case (13):
            case (14):
            case (15):
            case (0x10):
            case (0x11):
            case (0x12):
            case (0x13):
            case (0x14):
            case (0x15):
            case (0x16):
            case (0x17):
            case (0x18):
            case (0x19):
                System.out.println("Detected");//need to fix 0x0A-F
                break;
            default:
                System.out.println("detected UNKNOWN");
                input.skipBytes(length - 1);
        }
    }

    private static void sendChunkPacket(DataOutput output) throws IOException {
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                Chunk chunk = Chunk.loadChunk(i,j);
                DataConversion.writePacket(output, 0x21, $ -> {
                    DataConversion.writeInt($, chunk.x);
                    DataConversion.writeInt($, chunk.z);
                    DataConversion.writeBoolean($, true);

                    Chunk.writeChunk($, chunk);
                });

            }
        }
    }

    private static void sendPosPacket(DataOutput output) {
        DataConversion.writePacket(output, 0x08, $ -> {
            DataConversion.writeDouble($, 200.0D);
            DataConversion.writeDouble($, 75.0);
            DataConversion.writeDouble($, 220);
            DataConversion.writeFloat($, 300.0f);
            DataConversion.writeFloat($, 0.0f);
            DataConversion.writeByte($, (byte) 0);
            System.out.println("wrote pos");
        });
    }

    private static void sendPlayStatePacket(DataOutput output) {
        final int EID = new AtomicInteger().getAndIncrement();
        System.out.println(EID + " EID");
        final byte gm = 1;
        final byte dimension = 0;
        final byte difficulty = 1;
        final byte maxPlayers = 2;
        final String level = "default";

        //join game packet
        DataConversion.writePacket(output, 0x01, $ -> {
            DataConversion.writeInt($, EID);

            DataConversion.writeByte($, gm);
            DataConversion.writeByte($, dimension);
            DataConversion.writeByte($, difficulty);
            DataConversion.writeByte($, maxPlayers);

            DataConversion.writeString($, level);
            DataConversion.writeBoolean($, true);

        });
    }

    private static String getHexDigest(byte[] decryptedSecret, byte[] publicArray) throws NoSuchAlgorithmException {
        final MessageDigest message = MessageDigest.getInstance("SHA-1");
        message.update(decryptedSecret);
        message.update(publicArray);
        final String hexdigest = new BigInteger(message.digest()).toString(16);

        return hexdigest;
    }

    private static String getHash(String hexdigest, String username) throws IOException {

        System.out.println(hexdigest + " HEX");
        final URL url = new URL("https://sessionserver.mojang.com/session/minecraft/hasJoined?username=" +
                username + "&serverId=" +
                hexdigest);

        final HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        final InputStream stream = connection.getInputStream();


        final BufferedReader bin = new BufferedReader(
                new InputStreamReader(stream));
        String inputLine;
        final StringBuffer response = new StringBuffer();

        while ((inputLine = bin.readLine()) != null) {
            response.append(inputLine);
        }

        bin.close();

        System.out.println(response);
        System.out.println(connection.getResponseCode());

        final JSONObject myResponse = new JSONObject(response.toString());
        System.out.println("result after Reading JSON Response" + myResponse);

        final String hash = myResponse.get("id").toString();

        return hash;
    }


    private static void sendKeepAlive(DataOutput output) {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                DataConversion.writePacket(output, 0x00, $ -> DataConversion.writeVarInt($, 1));
            }
        }, 0, 20000);//wait 0 ms before doing the action and do it evry 1000ms (1second)
    }


}



